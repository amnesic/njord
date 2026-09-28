# Déploiement GeoGarage de njord (`caas-mvt.geogarage.com`)

Runbook de passage de la version en prod (`1.2-SNAPSHOT`, commit `c0a5883`, mars 2026, base en
schéma v1) à la branche `Test-S-101` synchronisée avec manimaul/njord (`1.4-SNAPSHOT`, schéma v3,
`chartZoomOffset`).

## Où ça tourne

- Machine : sd-158847 (`caas.geogarage.com`, 51.159.56.21).
- `~/Development/Njord/` (hors dépôt) :
  - `docker-compose.yml` et `.env` : la config de prod, projet Compose `njord` ;
  - `chart_server_db/pgbouncer.ini` ;
  - `secrets/userlist.txt` : le hash md5 de pgbouncer (`chmod 600`) ;
  - `njord/` : le clone git d'`amnesic/njord`, qui ne sert qu'à construire l'image.
- Conteneurs : `njord-njord-1` (image `ghcr.io/amnesic/njord-chart-server`, `127.0.0.1:9001->9000`),
  `njord-pgbouncer-1` (`127.0.0.1:5432`), `njord-postgres-1` (PostGIS 13, `127.0.0.1:6432`, données
  dans `/home/loic/docker-data/Development/njord-postgres`).
- nginx hôte : `/etc/nginx/sites-available/caas-mvt.geogarage.com.conf`, upstream `docker-njord`
  → `127.0.0.1:9001`. Il envoie `Host` et `X-Forwarded-Proto: https`, dont njord se sert pour
  construire les URLs de `tile_json` et du style.
- Environ 15 000 cartes en base.

Ne pas renommer `~/Development/Njord` : le nom du projet Compose en dépend.

## Ce qui change

1. **Migrations de base v1 → v3, automatiques au premier démarrage et sans retour arrière.**
   La v3 ajoute `features.chart_name` et réécrit toute la table `features` (`UPDATE` complet),
   puis reconstruit un index. Le serveur ne répond pas pendant ce temps : compter une coupure
   de plusieurs dizaines de minutes, et environ le double de la taille de `features` en disque
   pendant l'opération.
2. **`chartZoomOffset: 1`** dans `container/application.json` : chaque carte apparaît un niveau
   de zoom plus tôt. `CHART_SERVER_OPTS` ne fait que surcharger les clés qu'il contient, donc
   rien à ajouter dans le compose.
3. **`externalScheme`, `externalHostName`, `externalPort`** n'existent plus : l'URL publique est
   déduite des en-têtes envoyés par nginx. Les laisser dans `CHART_SERVER_OPTS` est sans effet
   (clés inconnues ignorées), on peut les retirer.
4. **Cache de tuiles** (`useTileCache: true`) : il est dans `/tmp/njord/tiles`, dans le conteneur,
   sans volume. Recréer le conteneur le vide.

## 0. Avant

```bash
df -h /                       # place disque : dump + ~2x la table features
docker exec njord-postgres-1 psql -U admin -d s57server -c \
  "select pg_size_pretty(pg_total_relation_size('features')), pg_size_pretty(pg_database_size('s57server'))"
docker exec njord-postgres-1 psql -U admin -d s57server -Atc "select key, value from meta_data"
```

## 1. Sauvegarde de la base

```bash
cd ~/Development/Njord
docker exec njord-postgres-1 pg_dump -U admin -Fc s57server > s57server_v1_$(date +%F).dump
ls -lh s57server_v1_*.dump
```

## 2. Code

```bash
cd ~/Development/Njord/njord
git status --short            # fichiers suivis modifiés localement : à traiter avant le pull
git fetch origin
git checkout Test-S-101
git pull --ff-only origin Test-S-101
git log --oneline -1          # doit montrer ae06d5d ou plus récent
```

Le clone ne doit contenir aucun secret. `njord/chart_server_db/userlist.txt` est le fichier de dev
de l'amont (`mysecretpassword`) ; celui de la prod est `~/Development/Njord/secrets/userlist.txt`,
monté par le compose (chemin relatif au compose : `./secrets/userlist.txt`).

## 3. Image

Le `Containerfile` ne compile pas le front : il copie `web/build/dist/js/productionExecutable`,
qui doit donc exister dans le contexte de build. Deux méthodes :

### A. Sur le Mac, puis ghcr (méthode de la 1.2)

La 1.2 a été construite ainsi (image créée le 2026-03-28 sur le Mac, poussée sur ghcr, récupérée
sur le serveur par `docker pull`). Le front est compilé sur le Mac et part dans l'image. La
compilation Kotlin/Native en `linux/amd64` passe par l'émulation sur Apple Silicon : c'est lent.

```bash
# sur le Mac
./gradlew :web:jsBrowserDistribution
docker buildx build --platform linux/amd64 -f Containerfile \
  -t ghcr.io/amnesic/njord-chart-server:1.4-SNAPSHOT --push .
# sur le serveur
docker pull ghcr.io/amnesic/njord-chart-server:1.4-SNAPSHOT
```

### B. Sur le serveur (méthode de la 1.4)

Compilation native amd64, plus rapide, mais qui charge une machine servant d'autres sites.
Le serveur n'a pas de JDK : le front vient du Mac, où il est déjà compilé (du JS, indépendant
de la plateforme).

```bash
# depuis le Mac (le rsync 3.1 du serveur ne connaît pas --mkpath)
ssh caas.geogarage.com mkdir -p Development/Njord/njord/web/build/dist/js/productionExecutable
rsync -a --delete ~/Development/01_GeoGarage/20_Dev/njord/web/build/dist/js/productionExecutable/ \
  caas.geogarage.com:Development/Njord/njord/web/build/dist/js/productionExecutable/
```

Le Docker du serveur (19.03) ne connaît pas `RUN --mount` sans la syntaxe Dockerfile récente,
qu'on active par argument sans toucher au `Containerfile` :

```bash
cd ~/Development/Njord/njord
DOCKER_BUILDKIT=1 docker build --build-arg BUILDKIT_SYNTAX=docker/dockerfile:1 \
  -f Containerfile -t ghcr.io/amnesic/njord-chart-server:1.4-SNAPSHOT . > ../build-1.4.log 2>&1
```

L'image n'existe alors que sur le serveur. La pousser sur ghcr si on veut la garder ailleurs.

Dans les deux cas, l'image embarque GDAL 3.6.2 (Debian 12), comme la 1.2.

## 4. Mise en service

Dans `~/Development/Njord/docker-compose.yml`, passer l'image du service njord à
`ghcr.io/amnesic/njord-chart-server:1.4-SNAPSHOT`. Puis :

```bash
cd ~/Development/Njord
docker compose config --quiet  # valide le fichier et les chemins
docker compose up -d           # recrée njord (nouvelle image) et pgbouncer si son montage a changé
docker compose logs -f njord   # attendre "DB schema migrated to version 3" puis "Responding at"
```

Le conteneur étant recréé, le cache interne de njord repart vide.

Dès que njord répond, mettre à jour les statistiques : la colonne `chart_name` est nouvelle et la
dernière analyse date d'avant la migration. Sans ça, le planificateur choisit mal ses index et
certaines tuiles z10 dépassent 90 s (vu le 2026-09-28), au-delà du délai de nginx :

```bash
docker exec njord-postgres-1 psql -U admin -d s57server -c "ANALYZE charts" -c "ANALYZE features"
```

**Cache nginx.** nginx garde lui aussi les tuiles, 30 jours (`proxy_cache mvt_cache`, dossier
`/var/cache/nginx/mvt`, déclaré en tête de `/etc/nginx/sites-available/caas-mvt.geogarage.com.conf`).
Sans purge, les anciennes tuiles (sans l'offset) restent servies (`X-Cache-Status: HIT`). Il
appartient à `www-data` : il faut `sudo`.

```bash
sudo find /var/cache/nginx/mvt -type f -delete
sudo systemctl reload nginx
curl -sI https://caas-mvt.geogarage.com/v1/tile/11/1011/719 | grep -i x-cache   # MISS, puis HIT
```

## 5. Vérifications

```bash
curl -s https://caas-mvt.geogarage.com/v1/about/version     # 1.4-SNAPSHOT, gitHash ae06d5d
docker exec njord-postgres-1 psql -U admin -d s57server -Atc "select value from meta_data where key='version'"   # 3
# estuaire de la Loire en z10 : doit contenir FR370680 (1:90 000) grâce à l'offset
curl -s "https://caas-mvt.geogarage.com/v1/tile/10/505/359?info=true" | grep -o 'FR[0-9A-Z]*\.000' | sort | uniq -c
```

Puis contrôle visuel sur `demo.geogarage.com`, couche X-ray ENC.

## 6. Après

La migration v3 laisse environ une table `features` de lignes mortes. En heure creuse :

```bash
docker exec njord-postgres-1 psql -U admin -d s57server -c "VACUUM (VERBOSE, ANALYZE) features;"
# ou, pour rendre la place au disque (verrou exclusif sur features, donc coupure) :
docker exec njord-postgres-1 psql -U admin -d s57server -c "VACUUM FULL features;"
```

## Retour arrière

La base v3 n'est plus lisible par la 1.2. Il faut restaurer la sauvegarde **et** revenir à l'image :

```bash
cd ~/Development/Njord
docker compose stop njord
docker exec -i njord-postgres-1 pg_restore -U admin -d s57server --clean --if-exists < ~/Development/Njord/s57server_v1_<date>.dump
# remettre l'image 1.2-SNAPSHOT dans le compose
docker compose up -d njord
```
