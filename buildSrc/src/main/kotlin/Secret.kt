import java.util.*

fun getEnv(key: String) : String {
    return System.getenv(key).takeIf { !it.isNullOrBlank() } ?: run {
        println("$key is not an environment variable")
        "undefined"
    }
}

fun password() = getEnv("NJORD_ADMIN_PASS")
fun user() = getEnv("NJORD_ADMIN_USER")
fun adminKey() = getEnv("NJORD_ADMIN_KEY")

fun options() = """
    {
    "adminKey": "${adminKey()}",
    "adminUser": "${user()}",
    "adminPass": "${password()}",
    "pgConnectionInfo": "postgresql://${dbUser()}@localhost:5432/${dbName()}"
    }
""".trimIndent()

fun dbHost() = getEnv("NJORD_DB_HOST")
fun dbPort() = getEnv("NJORD_DB_PORT")
fun dbName() = getEnv("NJORD_DB_NAME")
fun dbUser() = getEnv("NJORD_DB_USER")
fun dbPass() = getEnv("NJORD_DB_PASS")

fun secretYaml() = """
---
apiVersion: v1
kind: Secret
metadata:
  name: njord-pgbouncer-ini
  namespace: njord
type: Opaque
stringData:
  pgbouncer.ini: |
    [databases]
    # Points at the njord-db-relay Service, not the database directly. The pods have no
    # globally routable IPv6 address (LKE pod networking is IPv4-only), so a sidecar dialling
    # the database by name lands on its A record and Akamai bills the IPv4 transfer. The relay
    # is a hostNetwork forwarder that reaches the database over IPv6 from the node netns - see
    # the njord-db-relay DaemonSet in k8s_deploy/chart_server.yaml. The real endpoint is
    # published to that relay through the njord-db-upstream ConfigMap below.
    s57server = host=njord-db-relay.njord.svc.cluster.local port=6432 dbname=${dbName()} user=${dbUser()}

    [pgbouncer]
    # Pod loopback only. The njord server shares this pod network namespace and connects over
    # localhost, so this costs nothing, and it keeps auth_type=trust from being reachable at
    # <podIP>:5432 by anything else in the cluster.
    listen_addr = 127.0.0.1
    listen_port = 5432
    auth_type = trust
    auth_file = /etc/pgbouncer/userlist.txt
    pool_mode = transaction
    max_client_conn = 100
    default_pool_size = 10
    server_reset_query =
---
# Not a secret, but it rides along here because it is the same NJORD_DB_HOST/NJORD_DB_PORT
# pair that used to go straight into pgbouncer.ini above. Keeping both in one place is what
# stops the relay and the pooler from drifting onto different endpoints. Note that changing
# this does not restart the DaemonSet - cycle the relay pods after applying.
apiVersion: v1
kind: ConfigMap
metadata:
  name: njord-db-upstream
  namespace: njord
data:
  DB_HOST: ${dbHost()}
  DB_PORT: "${dbPort()}"
---
apiVersion: v1
kind: Secret
metadata:
  name: njord-pgbouncer-userlist-txt
  namespace: njord
type: Opaque
stringData:
  userlist.txt: |
    "${dbUser()}" "${dbPass()}"
---
apiVersion: v1
kind: Secret
metadata:
  name: admin-secret-json
  namespace: njord
data:
  chart_server_opts: "${Base64.getEncoder().encodeToString(options().toByteArray())}" 
"""
