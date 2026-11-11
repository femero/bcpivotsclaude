# CLAUDE.md

Microservicio de conciliación de pagos (Quarkus 3.31.2, Java 21, hexagonal). **Demo técnica**, no
sistema productivo. Consume pagos y movimientos del core desde Kafka, los concilia por
`operationReference` con tolerancia fija PEN 1.00 y publica el resultado. Un único endpoint:
`GET /v1/reconciliations/{operationReference}`.

## Fuentes de verdad (leer antes de cambiar nada)

- `.specify/memory/constitution.md` — restricciones no negociables (13 principios). Prevalece sobre
  cualquier spec, plan o sugerencia; un conflicto se reporta, no se esquiva.
- `specs/001-payment-reconciliation/` — `spec.md` (alcance), `plan.md`, `data-model.md`,
  `research.md` (incluye la re-verificación P-00), `tasks.md`, `quickstart.md`, `contracts/`.
- Flujo Spec Kit: `/speckit-specify` → `clarify` → `plan` → `tasks` → `analyze` → `implement`.

## Comandos

```bash
mvn spotless:apply     # formato; ejecutar antes de commitear
mvn clean verify       # puerta única: Checkstyle (validate) → compile → tests → Spotless check
mvn test               # solo tests
mvn quarkus:dev        # arranque local; requiere las 16 variables de entorno (.env)
```

`mvn verify` nunca modifica archivos. La suite corre **sin** base de datos, Redis ni broker.

## Arquitectura

Paquete base `com.bancatest.reconciliation`; estructura obligatoria:

```
domain/{model,model/vo,port/in,port/out,exception}
application/service
infrastructure/{adapter/in/{kafka,rest},adapter/out/{persistence,redis,messaging},config}
```

- `domain` no importa frameworks; `application` solo depende de `domain`. Lo hace cumplir el
  ImportControl de Checkstyle (`config/checkstyle/import-control.xml`); si un import legítimo falla,
  se corrige el diseño, no se relaja la regla.
- Los servicios de `application` son clases planas **sin anotaciones**; `infrastructure/config/
  ApplicationBeans` los expone como beans. La transacción vive en el puerto `TransactionalExecutor`
  (implementado con `QuarkusTransaction`), no en `@Transactional`.
- Un solo aggregate root: `Reconciliation` (constructor privado, `start()`/`reconstitute()`, sin
  setters). La entidad JPA es otra clase, con mapper explícito. Sin Panache Active Record.
- Flujo de escritura: validar → filtro Redis (`setGet` NX EX) → guardar dentro de
  `inTransaction` → **publicar después del commit**. `isResolved()` se comprueba antes de cualquier
  transición; `IllegalStateTransitionException` es red de seguridad, no control de flujo.
- Idempotencia solo por `operationReference`, nunca por id de evento. La garantía real es el índice
  `UNIQUE` (`db/schema.sql`); Redis solo acelera. Un duplicado se trata como éxito.
- Los consumers (`@Incoming` + `@RunOnVirtualThread`) llevan `@Blocking("<worker>")` con
  `smallrye.messaging.worker.<worker>.max-concurrency: 1` (orden de llegada por canal; sin eso los
  hilos virtuales corren hasta 1024 en paralelo y se adelantan) y `OperationLocks` excluye entre
  canales; hacen ack al volver y nack al lanzar
  excepción; la estrategia `dead-letter-queue` desvía al topic DLQ de su canal.

## Contratos

Contract-first, nunca derivados del código: `src/main/avro/*.avsc` y
`src/main/resources/openapi/reconciliation-api.yaml`. Las clases generadas (Avro por `quarkus-avro`,
JAX-RS por `openapi-generator-maven-plugin`) viven en `target/` y no se versionan. No hay
`avro-maven-plugin`. Campos Avro nuevos siempre con `default`.

## Reglas que más se rompen

- **YAML sin claves con puntos**: en `application.yaml` escribe `dead-letter-queue: {topic: ...}`
  anidado, nunca `dead-letter-queue.topic:`. La clave con puntos se ignora en silencio y la DLQ cae
  al nombre por defecto. Las propiedades del Schema Registry van bajo
  `mp.messaging.connector.smallrye-kafka`.

- **Verificar con Context7 antes de usar** cualquier librería, propiedad o anotación. Si no se puede
  verificar, se anota y se reporta; nunca se adivina.
- **No sobreextenderse**: lo que no está en el spec/plan se pregunta. Sin métricas, reintentos,
  outbox, autenticación ni endpoints extra.
- **Infraestructura la gestiona el equipo**: PROHIBIDO Flyway, docker-compose (salvo el local de
  la constitución III), Kubernetes, Terraform, scripts de topics, Dev Services activo, Apicurio.
  Hibernate nunca crea ni valida el esquema (`schema-management.strategy: none`).
- **Configuración**: `application.yaml`; variables de entorno `${NOMBRE}` **sin default**. Lista
  cerrada de 16 (ver `.env.example`); no añadir ninguna. Ningún secreto, URL ni token literal.
- **Código en inglés** (clases, métodos, mensajes, tests, comentarios); topics y columnas conservan
  nomenclatura de negocio. Glosario normativo en `docs/glossary.md` (p. ej. `CoreTransaction`,
  nunca `BankTransaction`).
- **Java 21 moderno**: records para VO/DTO/comandos; montos en `BigDecimal` con escala 2 y
  `HALF_UP`, comparados con `compareTo`; `Instant` para fechas; sin `null` como retorno del dominio,
  sin `java.util.Date`; `Optional` solo como retorno. Lombok solo en entidades JPA (`@Getter`,
  `@Setter`, `@NoArgsConstructor(PROTECTED)`).
- Checkstyle (`severity=error`): complejidad ≤ 10, métodos ≤ 40 líneas, ≤ 5 parámetros, líneas ≤ 140,
  sin imports con `*` ni sin usar.

## Tests

- Dominio y mappers: JUnit puro, sin `@QuarkusTest`.
- Integración: `@QuarkusTest` + `@QuarkusTestResource(InMemoryChannels.class)`; los dobles están en
  `src/test/java/.../support` (`@Mock`: repositorio, store de Redis, executor transaccional).
  `src/test/resources/application.properties` desactiva datasource/Hibernate y da valores inertes a
  las 16 variables.
- Todo consumer nuevo trae test de idempotencia (mismo evento dos veces → un solo efecto), DLQ con
  nombre explícito y su topic listado en el `README`. Con `InMemoryConnector` la DLQ no se ejecuta:
  solo se verifica el `nack`.

## Limitaciones conocidas (aceptadas, documentadas en el README)

1. Un movimiento que llega antes que su pago se descarta y el pago queda `PENDING` para siempre.
2. Sin outbox: un fallo entre el commit y la publicación deja la conciliación resuelta sin evento.

## Entorno local

`docker-compose.yml` (solo local, constitución III v1.2.0): PostgreSQL, Redis 7.4, Kafka, Schema
Registry y, opcional, Redpanda Console en `http://localhost:8085` (solo inspección). Topics,
esquemas y `db/schema.sql` los aplica una persona. Pasos en el `README`.

## Pendiente de verificar

Verificado contra el compose local: arranque, consumo, DLQ, publicación y consulta REST. Sin
verificar: Supabase, Confluent Cloud (SASL_SSL) y `-Djdk.tracePinnedThreads=full`. El
orden dentro de un canal se garantiza con concurrencia 1 por worker (probado en memoria; falta
repetirlo contra un clúster Kafka de varias particiones).
