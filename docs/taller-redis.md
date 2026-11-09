# Taller express: Redis con Quarkus (30 minutos)

Aprendes Redis en dos tiempos: primero los comandos básicos con `redis-cli` y después cómo este
servicio lo usa como filtro de pagos duplicados, con experimentos para romperlo y ver qué pasa.

- **Conocimiento previo**: usar una terminal. No se necesita saber Redis ni Quarkus.
- **Evaluación para comprobar lo aprendido**: [`evaluacion-redis.md`](evaluacion-redis.md).
- **Contexto del sistema**: [`contexto.md`](contexto.md).

## Antes de empezar (no cuenta en los 30 min)

| Herramienta | Comprobación |
|-------------|--------------|
| Docker Desktop encendido | `docker compose version` |
| JDK 21 y Maven 3.9+ (solo parte B) | `java -version`, `mvn -version` |
| `curl` y `jq` (solo parte B) | `jq --version` |
| Git Bash (Windows) | Los comandos son de bash; ejecútalos desde la raíz del repositorio |

## Plan de tiempo

| Parte | Qué haces | Min |
|-------|-----------|----:|
| A | Redis a solas: comandos básicos con `redis-cli` | 10 |
| B1 | Levantar el stack y arrancar el servicio | 8 |
| B2 | Ver cómo el servicio usa Redis y romperlo | 10 |
| | Limpieza | 2 |
| | **Total** | **30** |

---

# Parte A. Redis a solas (10 min)

## A1. Levantar solo Redis

El compose valida todo el archivo, así que necesita un `.env`:

```bash
cp .env.example .env
```

Completa `.env` con estos valores locales (los usarás también en la parte B):

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/reconciliation
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOSTS=redis://localhost:6379
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_API_KEY=local
KAFKA_API_SECRET=local
SCHEMA_REGISTRY_URL=http://localhost:8081
SCHEMA_REGISTRY_API_KEY=local
SCHEMA_REGISTRY_API_SECRET=local
KAFKA_TOPIC_PAYMENTS_IN=payments-in
KAFKA_TOPIC_PAYMENTS_DLQ=payments-dlq
KAFKA_TOPIC_CORE_TRANSACTIONS_IN=core-transactions-in
KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ=core-transactions-dlq
KAFKA_TOPIC_RESULTS_OUT=results-out
KAFKA_TOPIC_DISCREPANCIES_OUT=discrepancies-out
```

```bash
docker compose up -d --wait redis
rcli() { docker exec payment-reconciliation-local-redis-1 redis-cli "$@"; }   # atajo para todo el taller
rcli PING
```

**Esperado**: `PONG`. Redis escucha en el puerto 6379.

## A2. Cadenas: `SET`, `GET`, `DEL`

```bash
rcli SET saludo "hola"
rcli GET saludo
rcli GET no-existe        # (vacío): Redis devuelve nil
rcli EXISTS saludo        # 1 existe, 0 no existe
rcli DEL saludo
rcli GET saludo
```

## A3. Contadores: `INCR`

```bash
rcli INCR visitas
rcli INCR visitas
rcli GET visitas
```

## A4. Expiración: `EX`, `TTL`, `EXPIRE`

```bash
rcli SET sesion "abc" EX 5
rcli TTL sesion           # segundos que le quedan
sleep 6
rcli GET sesion           # nil: Redis la borró sola
rcli SET otra "x"
rcli TTL otra             # -1: sin expiración
rcli EXPIRE otra 60
rcli TTL otra
```

`TTL` devuelve `-1` si la clave no expira y `-2` si no existe.

## A5. `NX`: guardar solo si no existe (la pieza clave del servicio)

```bash
rcli SET pago-1 "1" NX EX 60     # OK: la clave era nueva
rcli SET pago-1 "1" NX EX 60     # (nil): ya existía, no se guardó
rcli SET pago-1 "1" NX EX 60 GET # devuelve el valor anterior ("1") sin sobrescribir
```

**Pregunta**: la primera vez devuelve `OK` y la segunda `nil`. ¿Cómo usarías eso para detectar un
pago repetido?
<details><summary>Respuesta</summary>Se intenta guardar la referencia del pago con <code>NX</code>: si el resultado indica que se guardó, el pago es nuevo; si no, ya se había procesado y se descarta.</details>

## A6. Otras estructuras (vistazo rápido)

```bash
rcli HSET cuenta:1 titular "Ana" saldo 100     # hash: campos de un objeto
rcli HGETALL cuenta:1
rcli SADD vistos A B A                          # set: sin repetidos
rcli SMEMBERS vistos
rcli --scan                                      # recorre las claves sin bloquear
```

> En un servidor real evita `KEYS *`: bloquea Redis mientras recorre todo. `SCAN` lo hace por tandas.

Limpia lo creado: `rcli FLUSHALL`.

---

# Parte B. Redis dentro del servicio (18 min)

## B1. Levantar el stack y arrancar Quarkus (8 min)

```bash
docker compose up -d --wait
set -a; source .env; set +a

# Tabla, 6 topics y 4 esquemas (el compose no los crea)
docker exec -i payment-reconciliation-local-postgres-1 \
  psql -U "$DB_USERNAME" -d reconciliation < db/schema.sql
for t in payments-in payments-dlq core-transactions-in core-transactions-dlq results-out discrepancies-out; do
  docker exec payment-reconciliation-local-kafka-1 kafka-topics --bootstrap-server kafka:29092 \
    --create --if-not-exists --topic "$t" --partitions 1 --replication-factor 1
done
register() {
  jq -c '{schema: tostring}' "src/main/avro/$2" |
    curl -s -X POST -H 'Content-Type: application/vnd.schemaregistry.v1+json' \
      --data @- "http://localhost:8081/subjects/$1/versions"; echo
}
register payments-in-value PaymentExecuted.avsc
register core-transactions-in-value CoreTransactionReported.avsc
register results-out-value ReconciliationMatched.avsc
register discrepancies-out-value DiscrepancyDetected.avsc
```

En una terminal **aparte**, con el `.env` cargado (`set -a; source .env; set +a`):

```bash
mvn quarkus:dev
```

Espera `Listening on: http://localhost:8080`. La primera compilación puede tardar. Si Quarkus no
arranca y nombra una propiedad, falta cargar el `.env`.

## B2. Cómo usa Redis el servicio (lectura de 2 min)

Abre `src/main/java/com/bancatest/reconciliation/infrastructure/adapter/out/redis/RedisProcessedOperationStore.java`:

- La clave es `dedup:` + `operationReference`, y el valor es `"1"`.
- Ejecuta `SET dedup:<referencia> 1 NX EX <ttl> GET`, el mismo comando que probaste en A5.
- El TTL sale de `reconciliation.idempotency.ttl` en `application.yaml` (`PT24H`, 24 horas).
- Si Redis falla, registra un `WARN` y deja pasar el mensaje.

La regla del proyecto: **Redis solo acelera; la garantía real es el índice `UNIQUE` de PostgreSQL**.

## B3. Experimento 1: ver la clave que crea un pago

```bash
RUN=R1 bash docs/collections/scenarios.sh cu01
rcli --scan --pattern 'dedup:*'
rcli GET dedup:R1-CU01-PENDING
rcli TTL dedup:R1-CU01-PENDING
```

**Esperado**: existe `dedup:R1-CU01-PENDING` con valor `1` y un TTL cercano a 86 400 segundos.

**Pregunta**: ¿cuántas horas dura la marca de un pago?
<details><summary>Respuesta</summary>24 horas (86 400 s ÷ 3 600). Pasado ese tiempo Redis la borra y solo el índice <code>UNIQUE</code> sigue frenando un duplicado.</details>

## B4. Experimento 2: mirar los comandos en vivo con `MONITOR`

En una **segunda terminal**:

```bash
docker exec payment-reconciliation-local-redis-1 redis-cli monitor
```

En la primera, envía un pago nuevo y luego su duplicado:

```bash
RUN=R2 bash docs/collections/scenarios.sh cu04
```

Verás pasar un `SET "dedup:R2-CU04-DUP" "1" "NX" "EX" "86400" "GET"` por cada pago enviado. Corta
`monitor` con Ctrl+C.

**Pregunta**: se enviaron dos pagos con la misma referencia. ¿Cuántas filas hay en PostgreSQL?
```bash
docker exec payment-reconciliation-local-postgres-1 psql -U "$DB_USERNAME" -d reconciliation -Atc \
  "select count(*) from reconciliations where operation_reference='R2-CU04-DUP'"
```
<details><summary>Respuesta</summary>Una. El segundo <code>SET ... NX</code> encontró la clave y el pago se descartó antes de llegar a la base de datos.</details>

## B5. Experimento 3: bloquear un pago desde Redis

Marca a mano una referencia que el servicio nunca ha visto y luego envía ese pago:

```bash
rcli SET dedup:R3-CU01-PENDING "1" EX 600
RUN=R3 bash docs/collections/scenarios.sh cu01
curl -s http://localhost:8080/v1/reconciliations/R3-CU01-PENDING | jq .title
```

**Esperado**: la consulta devuelve un 404 (`Reconciliation not found` o similar): el pago nunca se
guardó, porque Redis dijo "ya procesado".

**Pregunta**: ¿por qué es peligroso que alguien escriba claves `dedup:*` a mano en producción?
<details><summary>Respuesta</summary>Porque Redis manda sobre el filtro: una clave falsa hace que se descarte un pago legítimo sin dejar rastro en la base de datos. Por eso Redis no se toca a mano en entornos reales.</details>

## B6. Experimento 4: borrar Redis y repetir un duplicado

```bash
rcli FLUSHALL
RUN=R2 bash docs/collections/scenarios.sh cu04
docker exec payment-reconciliation-local-postgres-1 psql -U "$DB_USERNAME" -d reconciliation -Atc \
  "select count(*) from reconciliations where operation_reference='R2-CU04-DUP'"
```

**Esperado**: sigue habiendo **una** fila. Sin Redis el filtro no frena nada, pero el índice
`UNIQUE` rechaza el `INSERT` duplicado y el servicio lo descarta como éxito.

## B7. Experimento 5: apagar Redis

```bash
docker compose stop redis
RUN=R4 bash docs/collections/scenarios.sh cu01
docker compose start redis
```

**Esperado**: la conciliación se crea igual (`PENDING`). En la terminal de Quarkus aparece
`Redis unavailable, duplicate filter skipped for R4-CU01-PENDING`. Puede tardar unos segundos hasta
que expire el intento de conexión.

**Preguntas**

1. ¿Qué pierde el servicio sin Redis? ¿Qué NO pierde?
2. ¿Qué pasaría si Redis fuera la única defensa contra duplicados?

<details><summary>Respuestas</summary>

1. Pierde velocidad: cada duplicado llega hasta la base de datos. No pierde corrección, porque el
   índice `UNIQUE` sigue garantizando una sola conciliación por referencia.
2. Una caída o un borrado de Redis permitiría crear conciliaciones duplicadas. Por eso Redis es un
   pre-filtro y no la garantía.
</details>

---

## Limpieza (2 min)

```bash
# En la terminal de Quarkus: pulsa q (o Ctrl+C)
docker compose down -v      # -v borra también los datos
```

## Resumen de comandos Redis del taller

| Comando | Para qué sirve |
|---------|----------------|
| `SET clave valor` / `GET clave` | Guardar y leer una cadena |
| `DEL clave` / `EXISTS clave` | Borrar y comprobar existencia |
| `INCR clave` | Incrementar un contador |
| `SET ... EX n` / `TTL` / `EXPIRE` | Poner y consultar expiración |
| `SET ... NX` | Guardar solo si no existe |
| `SET ... NX EX n GET` | Lo que usa el servicio como filtro de duplicados |
| `HSET` / `HGETALL` | Hash (campos de un objeto) |
| `SADD` / `SMEMBERS` | Set (sin repetidos) |
| `--scan` / `SCAN` | Recorrer claves sin bloquear |
| `MONITOR` | Ver los comandos en vivo (solo para depurar) |
| `FLUSHALL` | Vaciar todo (solo en local) |

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| `docker compose` pide `DB_USERNAME` | Falta el `.env` | Crea el `.env` (A1) |
| `rcli: command not found` | Cambiaste de terminal | Vuelve a definir la función `rcli` (A1) |
| Quarkus no arranca y nombra una propiedad | Variables sin cargar | `set -a; source .env; set +a` |
| Los mensajes no llegan | Topics o esquemas sin crear | Repite el bloque de B1 |
| Un experimento no cambia nada | Referencia ya usada | Cambia el prefijo `RUN=` |
| Rutas raras en Windows | Git Bash reescribe rutas | Usa Git Bash, no PowerShell |
