# Plan : Enrichir le coverage PLY vecteur avec métadonnées de carte

## Contexte

Le coverage raster (B2B) fonctionne parfaitement via CartoDB : polygones colorés par zoom avec noms de cartes. Pour le vecteur (ENC), les features PLY dans les tuiles MVT sont des **LineStrings sans aucune propriété** — juste des contours géométriques bruts, sans nom de carte ni metadata.

La cause est dans `TileEncoder.kt:178` : les features PLY sont créées avec `emptyMap()` comme propriétés. Pourtant, les métadonnées (`name`, `scale`, `fileName`) sont déjà disponibles dans l'objet `chart` (type `ChartInfo`) à la même ligne de code.

### Données disponibles dans la table `charts`

- `name` (DSID_DSNM — ex: "US4AK6GN")
- `file_name` (nom du fichier S-57, ex: "US4AK6GN.000")
- `scale` (DSPM_CSCL — échelle de la carte)
- `dsid_props` (JSONB complet des propriétés DSID)
- `covr` (géométrie M_COVR avec CATCOV=1)

### État actuel du code

```
S-57 Chart File
    ↓
OgrS57Dataset.chartInsertInfo()
    ├─ Extrait M_COVR (CATCOV=1)
    ├─ Merge la géométrie
    ├─ Extrait les props DSID (name, scale, etc.)
    └─ Stocke dans Chart object
        ↓
ChartDao.insertAsync()
    └─ Insère dans table charts (géométrie dans covr, metadata dans colonnes séparées)
        ↓
TileEncoder.addCharts()
    ├─ Lit chart.covrWKB depuis la DB
    ├─ Extrait boundary() du polygon de couverture
    └─ Crée feature PLY avec emptyMap() ← LE PROBLÈME
        ↓
MVT Tile (PLY features SANS propriétés)
```

## Objectif

Enrichir les features PLY pour permettre un rendu coverage vecteur similaire au raster : polygones colorés avec noms de cartes affichés.

## Étapes d'implémentation

### Étape 1 — Enrichir `ChartInfo` avec le nom de la carte

**Fichier** : `shared/src/commonMain/kotlin/io/madrona/njord/model/Chart.kt` (ligne 11)

`ChartInfo` ne contient que `id`, `scale`, `zoom`, `covrWKB`. Ajouter `name: String` :

```kotlin
@Serializable
class ChartInfo(
    val id: Long,
    val name: String,   // ← ajouter
    val scale: Int,
    val zoom: Int,
    val covrWKB: ByteArray
)
```

### Étape 2 — Mettre à jour la requête SQL dans `ChartDao`

**Fichier** : `server/src/nativeMain/kotlin/io/madrona/njord/db/ChartDao.kt`

La méthode `findInfoAsync()` (utilisée par TileEncoder) doit aussi sélectionner la colonne `name` de la table `charts` et la mapper dans `ChartInfo`.

### Étape 3 — Passer les propriétés aux features PLY dans `TileEncoder`

**Fichier** : `server/src/nativeMain/kotlin/io/madrona/njord/geo/TileEncoder.kt` (lignes 175-179)

Remplacer :
```kotlin
val boundary = chartGeo.boundary()
boundary?.let {
    transformToTilePixels(it, x, y, z, tileSystem)
    vectorTileEncoder.addFeature("PLY", emptyMap(), it)
}
```

Par :
```kotlin
val props = mapOf(
    "name" to chart.name.json,
    "scale" to chart.scale.json
)

// Polygon fill (pour remplissage coloré)
val chartGeoClipped = chartGeo.intersection(include)
chartGeoClipped?.takeIf { it.isValid && !it.isEmpty() }?.let { clipped ->
    val polyGeo = clipped.clone()
    transformToTilePixels(polyGeo, x, y, z, tileSystem)
    vectorTileEncoder.addFeature("PLY", props, polyGeo)
}

// Boundary line (contour)
val boundary = chartGeo.boundary()
boundary?.let {
    transformToTilePixels(it, x, y, z, tileSystem)
    vectorTileEncoder.addFeature("PLY", props, it)
}
```

Cela ajoute `name` (ex: "US4AK6GN") et `scale` à chaque feature PLY, et inclut les polygones en plus des lignes.

### Étape 4 — Mettre à jour le layer definition `Ply.kt`

**Fichier** : `server/src/nativeMain/kotlin/io/madrona/njord/layers/Ply.kt`

Ajouter les layers de style : fill (polygon), line (contour), et symbol (label texte) :

```kotlin
class Ply : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            // Polygon fill
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = sourceLayer,
                filter = listOf(Filters.any, Filters.eqTypePolygon).json,
                paint = Paint(
                    fillColor = "#dfca15".json,
                    fillOpacity = 0.1f
                )
            ),
            // Boundary line
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = sourceLayer,
                filter = listOf(Filters.any, Filters.eqTypeLineString).json,
                paint = Paint(
                    lineColor = colorFrom(Color.CURSR, options.theme).json,
                    lineWidth = 2f
                )
            ),
            // Chart name label
            Layer(
                id = "${key}_label",
                type = LayerType.SYMBOL,
                sourceLayer = sourceLayer,
                filter = listOf(Filters.any, Filters.eqTypePolygon).json,
                layout = Layout(
                    textField = listOf("get", "name").json,
                    textSize = 12f
                ),
                paint = Paint(
                    textColor = "#964000".json
                )
            )
        )
    }
}
```

### Étape 5 — Côté B2B frontend (b2b.geogarage.com_V2)

**Aucun changement côté frontend requis.** `toggleVectorCoverage` dans `useCoverage.ts` toggle déjà la visibilité de tous les layers avec `source-layer === 'PLY'`. Les nouveaux layers (fill, line, label) seront automatiquement inclus.

Les PLY sont déjà cachés par défaut dans `useMapLayers.ts` et ne s'affichent que quand l'utilisateur active le checkbox Coverage.

## Fichiers à modifier

| Fichier | Changement |
|---------|------------|
| `shared/.../model/Chart.kt` | Ajouter `name: String` à `ChartInfo` |
| `server/.../db/ChartDao.kt` | Inclure `name` dans la requête SQL de `findInfoAsync()` |
| `server/.../geo/TileEncoder.kt` | Passer props `name`/`scale` + ajouter polygon fill |
| `server/.../layers/Ply.kt` | Ajouter fill + label layers au style |

## Vérification

1. Compiler Njord, ingérer des cartes ENC
2. Inspecter les tuiles MVT : vérifier que les features PLY ont les propriétés `name` et `scale`
3. Charger la couche ENC dans B2B, activer Coverage
4. Vérifier que les polygones colorés et noms de cartes s'affichent
5. Comparer visuellement avec le coverage raster pour cohérence

## Notes

- Le rendu pourrait être amélioré avec un CartoCSS-like zoom-dependent styling côté `Ply.kt` (opacité et couleur variant selon `scale`)
- L'ajout de `area` calculé (ST_Area) dans la requête SQL permettrait un styling encore plus proche du raster
- Les features PLY Points (55 trouvés) semblent être des artefacts — à investiguer
