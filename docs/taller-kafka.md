# Taller express: Kafka con Quarkus (30 minutos)

Aprendes Kafka en dos tiempos: primero **tú** interactúas con Kafka desde la terminal (topics,
particiones, claves, consumer groups, offsets) y después **le pides a la IA** que opere el servicio de
conciliación sobre Kafka y te explique lo que ocurre.

- **Conocimiento previo**: usar una terminal. No se necesita saber Kafka ni Quarkus.
- **Evaluación para comprobar lo aprendido**: [`evaluacion-kafka.md`](evaluacion-kafka.md).
- **Contexto del sistema**: [`contexto.md`](contexto.md). **Taller gemelo de Redis**:
  [`taller-redis.md`](taller-redis.md).

## Antes de empezar (no cuenta en los 30 min)

| Herramienta | Comprobación |
|-------------|--------------|
| Docker Desktop encendido | `docker compose version` |
| JDK 21 y Maven 3.9+ (solo parte B) | `java -version`, `mvn -version` |
| `curl` y `jq` (solo parte B) | `jq --version` |
| Claude Code en la raíz del repo (solo parte B) | `claude --version` |
| Git Bash (Windows) | Los comandos son de bash; ejecútalos desde la raíz del repositorio |

## Plan de tiempo

| Parte | Qué haces | Min |
|-------|-----------|----:|
| A | Kafka con tus manos: topics, claves, grupos y offsets | 14 |
| B | La IA opera el servicio sobre Kafka y tú verificas | 14 |
| | Limpieza | 2 |
| | **Total** | **30** |

---

# Parte A. Interactuar con Kafka (14 min)

Usarás las herramientas de línea de comandos que trae el contenedor de Kafka. Para no repetir
parámetros, define dos atajos (vuelve a definirlos si abres otra terminal):

```bash
K=payment-reconciliation-local-kafka-1
B="--bootstrap-server kafka:29092"
```

## A1. Levantar solo Kafka

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
docker compose up -d --wait kafka
docker exec $K kafka-topics $B --list
```

**Esperado**: la lista sale vacía (o con topics ya existentes). El broker escucha dentro de Docker en
`kafka:29092` y, desde tu máquina, en `localhost:9092`.

## A2. Crear un topic con 3 particiones

```bash
docker exec $K kafka-topics $B --create --topic taller-demo --partitions 3 --replication-factor 1
docker exec $K kafka-topics $B --describe --topic taller-demo
```

**Observa**: tres líneas `Partition: 0/1/2`, cada una con su `Leader`. Con un solo broker, el
factor de replicación solo puede ser 1.

## A3. Producir mensajes **con clave**

La clave decide la partición. El formato es `clave:valor`:

```bash
printf 'OP-1:pago\nOP-2:pago\nOP-3:pago\nOP-4:pago\nOP-1:movimiento\nOP-2:movimiento\nOP-3:movimiento\nOP-4:movimiento\n' |
  docker exec -i $K kafka-console-producer $B --topic taller-demo \
    --property parse.key=true --property key.separator=:
```

## A4. Consumir y ver partición y offset

```bash
docker exec $K kafka-console-consumer $B --topic taller-demo --from-beginning --timeout-ms 4000 \
  --property print.key=true --property print.partition=true --property print.offset=true 2>/dev/null | sort
```

> `--timeout-ms` corta el consumidor al no haber más mensajes. Sin `2>/dev/null` verás un
> `TimeoutException`: es normal en esa herramienta.

**Ejemplo de salida** (las particiones exactas pueden variar):

```text
Partition:1	Offset:0	OP-1	pago
Partition:1	Offset:3	OP-1	movimiento
Partition:2	Offset:0	OP-3	pago
Partition:2	Offset:1	OP-3	movimiento
...
```

**Preguntas**

1. ¿Cae `OP-1 pago` y `OP-1 movimiento` en la misma partición? ¿Y en qué orden de offset?
2. ¿Por qué eso importa para un servicio que concilia un pago con su movimiento?
3. ¿Qué pasaría si el topic tuviera más particiones y los mensajes no llevaran clave?

<details><summary>Respuestas</summary>

1. Sí, siempre la misma, con el pago en un offset menor que el movimiento: el orden se garantiza
   dentro de una partición.
2. Porque los mensajes de una misma operación se leen en el orden en que se publicaron. Por eso el
   servicio publica sus resultados con la referencia de operación como key.
3. Se repartirían entre particiones y no habría orden relativo entre ellos.
</details>

## A5. Consumer group y lag

Un grupo recuerda hasta dónde leyó (offset confirmado). Lee todo con el grupo `g1` y revisa el estado:

```bash
docker exec $K kafka-console-consumer $B --topic taller-demo --group g1 --from-beginning \
  --timeout-ms 4000 >/dev/null 2>&1
docker exec $K kafka-consumer-groups $B --describe --group g1
```

**Observa**: `CURRENT-OFFSET` igual a `LOG-END-OFFSET` en cada partición y `LAG` 0.

Ahora publica 3 mensajes nuevos **sin** consumirlos:

```bash
printf 'OP-9:nuevo\nOP-9:nuevo2\nOP-5:nuevo\n' |
  docker exec -i $K kafka-console-producer $B --topic taller-demo \
    --property parse.key=true --property key.separator=:
docker exec $K kafka-consumer-groups $B --describe --group g1
```

**Esperado**: el `LAG` suma 3 entre las particiones: son los mensajes publicados que `g1` aún no
ha leído. Vuelve a consumir con `--group g1` y comprueba que el lag regresa a 0 y que solo salen
los 3 nuevos.

## A6. Los mensajes no se borran al leerlos

Lee desde el principio con **otro** grupo:

```bash
docker exec $K kafka-console-consumer $B --topic taller-demo --group g2 --from-beginning \
  --timeout-ms 4000 2>/dev/null | wc -l
```

**Esperado**: 11 mensajes (8 + 3). Aunque `g1` ya los leyó, siguen en el topic hasta que venza la
retención; cada grupo tiene sus propios offsets.

## A7. Offsets por partición

```bash
docker exec $K kafka-get-offsets $B --topic taller-demo
```

Muestra, por partición, el siguiente offset a escribir. La suma es el total de mensajes del topic.

**Pregunta de cierre**: si arrancas dos consumidores con el mismo `--group g1` sobre este topic de 3
particiones, ¿cuántos leen cada partición?
<details><summary>Respuesta</summary>Uno solo por partición dentro del grupo: el grupo se reparte las particiones. Dos consumidores de <code>g2</code> y uno de <code>g1</code> leerían todo cada grupo, sin interferirse.</details>

---

# Parte B. Pedirle a la IA que opere el servicio sobre Kafka (14 min)

Ahora el servicio de conciliación consume y publica en Kafka. Tú **no** escribes los comandos: se los
pides a Claude Code con prompts estructurados y **verificas** lo que hace con lo que aprendiste en
la parte A.

Plantilla de cada prompt: **Rol · Contexto · Tarea · Restricciones · Formato de salida · Criterio de
éxito**. Más detalle en [`taller-redis-con-ia.md`](taller-redis-con-ia.md).

Reglas de oro: una tarea por prompt; lee cada comando antes de aprobarlo; comprueba con tu terminal
los resultados clave.

## B0. Encuadre (1 min)

```text
# Rol
Actúa como instructor de Kafka para principiantes, con ejemplos cortos.

# Contexto
Estoy en la raíz de un microservicio de conciliación de pagos (Quarkus, Java 21). Consume los topics
payments-in y core-transactions-in (Avro), publica en results-out y discrepancies-out y desvía los
mensajes inválidos a payments-dlq y core-transactions-dlq. Hay un docker-compose.yml local con
Kafka, Schema Registry, PostgreSQL y Redis. Los contenedores empiezan por
payment-reconciliation-local-. Las instrucciones del proyecto están en CLAUDE.md.

# Tarea
Voy a hacer un taller de 14 minutos contigo. Me guías y ejecutas comandos cuando yo lo pida.

# Restricciones
- Solo el stack local de Docker Compose.
- Antes de un comando destructivo (borrar topics, docker compose down -v) pides mi confirmación.
- No modifiques archivos del repositorio, no hagas commits ni push, no imprimas valores del .env.
- Si no estás seguro de algo, dilo; no lo inventes. Responde en español.

# Formato de salida
Respuestas cortas: comando, resultado y explicación de una o dos frases.

# Criterio de éxito
Confirma en una línea que entendiste las reglas y espera mi siguiente instrucción.
```

## B1. Preparar el entorno (3 min)

```text
# Tarea
Prepárame el entorno para probar el servicio: levanta todos los servicios de docker compose, aplica
db/schema.sql en PostgreSQL, crea los seis topics de Kafka (1 partición cada uno) y registra los
cuatro esquemas de src/main/avro en el Schema Registry. Usa los nombres de topic de .env.example y
los subjects <topic>-value.

# Restricciones
Solo local. No arranques Quarkus: te aviso yo. Si un paso falla, detente y explícame el error en
lugar de improvisar.

# Formato de salida
Checklist con OK o ERROR por paso.

# Criterio de éxito
Los seis topics existen y los cuatro subjects aparecen en el Schema Registry.
```

Cuando termine, arranca tú el servicio en otra terminal (con el `.env` cargado:
`set -a; source .env; set +a`):

```bash
mvn quarkus:dev
```

**Verifica tú**: `docker exec $K kafka-topics $B --list` debe mostrar los seis topics.

## B2. Seguir el recorrido de los mensajes (4 min)

```text
# Rol
Instructor que me hace razonar antes de ejecutar.

# Contexto
El servicio está corriendo. El script docs/collections/scenarios.sh publica pagos y movimientos Avro
de prueba; la referencia de cada uno lleva el prefijo de la variable RUN.

# Tarea
1. Antes de enviar nada, pídeme una hipótesis: ¿en qué topics habrá mensajes nuevos al ejecutar el
   escenario cu02 y cuántos?
2. Anota los offsets finales de los seis topics (kafka-get-offsets), ejecuta
   RUN=K1 bash docs/collections/scenarios.sh cu02 y vuelve a medir.
3. Muéstrame una tabla de topics con offsets antes, después y mensajes nuevos, y compárala con mi
   hipótesis.

# Restricciones
No modifiques archivos ni borres nada. Si mi hipótesis falla, explica por qué sin ajustar el relato.

# Formato de salida
Tabla (topic, antes, después, nuevos) y dos frases de conclusión.

# Criterio de éxito
La tabla refleja mediciones reales y yo entiendo por qué results-out y discrepancies-out recibieron
mensajes distintos.
```

**Verifica tú**: repite la medición de un topic (`kafka-get-offsets`). En el escenario hay dos
resultados `MATCHED` y dos `MISMATCHED`, así que `results-out` y `discrepancies-out` deberían subir
en 2 cada uno.

## B3. Consumer group del servicio y lag (3 min)

```text
# Tarea
Averigua el consumer group que usa el servicio y muéstrame su estado con kafka-consumer-groups
--describe: partición, offset actual, offset final y lag de payments-in y core-transactions-in.
Explícame con una frase qué significa que el lag sea 0.

# Restricciones
Solo lectura. Si hay varios grupos, distingue el del servicio de los grupos temporales de las
herramientas de consola.

# Formato de salida
Tabla (topic, partición, offset actual, offset final, lag) y la frase de explicación.

# Criterio de éxito
El lag de ambos topics es 0 o me explicas por qué no.
```

**Verifica tú**: `docker exec $K kafka-consumer-groups $B --list` incluye `payment-reconciliation`.

## B4. La dead-letter queue (3 min)

```text
# Contexto
El escenario cu05 envía entradas inválidas (pago en USD, monto 0.00, sin cuenta, sin referencia,
movimiento en USD y movimiento 0.004).

# Tarea
1. Mide los offsets de payments-dlq y core-transactions-dlq, ejecuta
   RUN=K1 bash docs/collections/scenarios.sh cu05 y vuelve a medir.
2. Dime cuántos mensajes nuevos hay en cada DLQ y por qué ese número.
3. Con el escenario cu06 (movimiento sin pago previo), comprueba si algo llega a alguna DLQ y
   explica la diferencia con cu05.

# Restricciones
Solo lectura de topics; no borres nada. Cita ruta:línea del código que decide si un mensaje va a la
DLQ y ábrelo tú antes de afirmarlo.

# Formato de salida
Tabla (DLQ, nuevos mensajes, motivo) y un párrafo de máximo cuatro líneas sobre cu06.

# Criterio de éxito
payments-dlq gana 4, core-transactions-dlq gana 2 y cu06 no añade nada a ninguna DLQ.
```

**Verifica tú**: abre el archivo que cite la IA. Si la ruta o la línea no existen, es una
alucinación: pídele que lo corrija.

## B5. Tu propio prompt (opcional, si te sobra tiempo)

Escribe con los seis bloques una petición para: *"muéstrame los últimos 3 mensajes de
`results-out` y dime a qué partición pertenece cada uno"*. Usa
`kafka-avro-console-consumer` (el esquema es Avro; el consumidor de consola normal mostraría bytes).
Pruébalo y comprueba que la IA no escribe en el topic.

<details><summary>Ejemplo de solución</summary>

```text
# Rol
Instructor de Kafka nivel principiante.

# Contexto
Stack local del compose; los mensajes de results-out están en Avro y el Schema Registry está en
http://localhost:8081 (contenedor payment-reconciliation-local-schema-registry-1).

# Tarea
Lee los últimos 3 mensajes de results-out con kafka-avro-console-consumer y muestra, para cada uno,
partición, offset, clave y contenido.

# Restricciones
Solo lectura: no publiques ni borres. Usa un group.id temporal que no sea el del servicio.

# Formato de salida
Tabla (partición, offset, clave, estado/referencia).

# Criterio de éxito
Puedo repetir la lectura con mi terminal y obtener los mismos offsets.
```
</details>

---

## Limpieza (2 min)

```bash
# En la terminal de Quarkus: pulsa q (o Ctrl+C)
docker exec $K kafka-topics $B --delete --topic taller-demo   # si aún existe
docker compose down -v      # -v borra también los datos
```

Si la IA te propone el borrado, léelo antes de confirmar.

## Resumen de comandos Kafka del taller

| Comando (dentro del contenedor) | Para qué sirve |
|---------------------------------|----------------|
| `kafka-topics --create / --describe / --list / --delete` | Administrar topics |
| `kafka-console-producer` (`parse.key`, `key.separator`) | Publicar mensajes, con o sin clave |
| `kafka-console-consumer` (`--from-beginning`, `--group`, `--timeout-ms`) | Leer mensajes |
| `kafka-consumer-groups --describe / --list` | Ver offsets y lag de un grupo |
| `kafka-get-offsets` | Ver el offset final de cada partición |
| `kafka-avro-console-consumer` (en el contenedor de Schema Registry) | Leer topics Avro |

## Conceptos del taller

| Concepto | Dónde lo viste |
|----------|----------------|
| Topic y partición | A2 |
| Clave y orden por partición | A3, A4 |
| Consumer group, offset y lag | A5, B3 |
| Retención (leer no borra) | A6 |
| Avro y Schema Registry | B1, B5 |
| Dead-letter queue | B4 |

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| `docker compose` pide `DB_USERNAME` | Falta el `.env` | Crea el `.env` (A1) |
| `$K` o `$B` vacíos | Cambiaste de terminal | Vuelve a definir los dos atajos |
| `TimeoutException` al consumir | `--timeout-ms` cortó el consumidor | Es normal; añade `2>/dev/null` |
| Los mismos mensajes no salen con `--group g1` | El grupo ya los leyó | Usa otro grupo o `--from-beginning` con un grupo nuevo |
| Quarkus no arranca y nombra una propiedad | Variables sin cargar | `set -a; source .env; set +a` |
| Un escenario no cambia nada | Referencia ya usada | Cambia el prefijo `RUN=` |
| Rutas raras en Windows | Git Bash reescribe rutas | Usa Git Bash, no PowerShell |
