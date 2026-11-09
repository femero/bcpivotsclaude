# Implementation Plan: Conciliación de Pagos

**Branch**: `001-payment-reconciliation` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-payment-reconciliation/spec.md`

> **Estado de verificación.** Este plan se generó sin Context7 disponible. La investigación se
> hereda del proyecto hermano `ms-test` y **no está re-verificada** (pendiente **P-00** en
> [research.md](./research.md)). Debe cerrarse antes de `/speckit-implement`.

## Summary

Microservicio que consume pagos reportados y movimientos del core desde Kafka, los concilia por
referencia de operación comparando importes contra una tolerancia fija de PEN 1.00, y publica el
resultado como conforme o discrepante. Expone un único endpoint REST de consulta por
referencia.

Enfoque técnico: Quarkus 3.x sobre Java 21 en modelo bloqueante con virtual threads,
arquitectura hexagonal con un solo aggregate root (`Reconciliation`) y contract-first en las dos
superficies de integración: OpenAPI 3.0.3 para REST y Avro para Kafka, ninguno derivado del
código. El generador REST es el `openapi-generator-maven-plugin` de OpenAPITools (`jaxrs-spec`,
`library=quarkus`); se descarta la extensión de Quarkiverse por estar en Preview/Experimental
(R-12). Idempotencia en dos niveles: índice `UNIQUE` en PostgreSQL como garantía y Redis como
filtro previo.

## Technical Context

**Language/Version**: Java 21 (virtual threads, records, sealed interfaces, pattern matching)

**Primary Dependencies**: Quarkus 3.x (3.31.2 en `ms-test`; fijar en P-00) — `quarkus-rest`,
`quarkus-rest-jackson`, `quarkus-config-yaml`, `quarkus-hibernate-orm-panache`,
`quarkus-jdbc-postgresql`, `quarkus-redis-client`, `quarkus-smallrye-reactive-messaging-kafka`,
`quarkus-confluent-registry-avro`, `quarkus-rest-client` (lo exige el serde de Confluent),
`quarkus-hibernate-validator` (lo exige el código generado con `useBeanValidation`) y
`quarkus-logging-json`. Fuera de Quarkus: MapStruct, Lombok (solo entidades JPA),
`com.fasterxml.uuid:java-uuid-generator`. Plugin Maven: `openapi-generator-maven-plugin`.

**No hay `avro-maven-plugin`.** La generación Avro la hace el codegen de `quarkus-avro`,
configurado en `application.yaml` bajo `avro.codegen.*` (R-10).

**Dependencias de test**: `quarkus-junit` (no `quarkus-junit5`), `mockito-core`, `awaitility`,
`smallrye-reactive-messaging-in-memory`.

**Storage**: PostgreSQL en Supabase (conexión directa o pooler en modo sesión, nunca modo
transacción). Una sola tabla, `reconciliations`. DDL manual en `db/schema.sql`; Hibernate nunca
gestiona el esquema. Redis local para el filtro de idempotencia.

**Testing**: JUnit 5 puro para dominio y mappers, sin `@QuarkusTest`. `InMemoryConnector` de
SmallRye para los adaptadores de mensajería. Sin contenedores (R-14).

**Target Platform**: JVM, empaquetado JVM (no nativo).

**Project Type**: Microservicio backend orientado a eventos, con una superficie REST de solo
lectura.

**Performance Goals**: Sin objetivo cuantitativo: es una demo técnica. El techo real de
concurrencia es el pool JDBC (`min-size: 5`, `max-size: 16`, R-11); el consumo queda acotado por
las particiones de cada topic, que fija el equipo.

**Constraints**: Sin outbox: la publicación ocurre tras confirmar la transacción y se acepta
perder el evento (FR-015). Sin métricas, trazas ni dashboards. Sin autenticación. Ningún
contrato se deriva del código.

**Scale/Scope**: 1 aggregate root, 3 casos de uso, 5 puertos de salida, 4 mappers, 4 esquemas
Avro, 1 endpoint REST, 1 tabla.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra `.specify/memory/constitution.md` v1.0.0.

| Principio | Gate | Estado |
| --- | --- | --- |
| I. No sobreextenderse | Nada fuera del spec: 1 endpoint, 3 casos de uso, sin reintentos ni circuit breakers | **PASS** |
| II. Documentación verificada | Decisiones heredadas de `ms-test`, **no re-verificadas** con Context7 en esta sesión. Anotado como pendiente P-00, tal como manda el principio ("se anota como pendiente de verificación y se REPORTA") | **PASS CON PENDIENTE** |
| III. Infraestructura del equipo | Sin Flyway, docker-compose ni creación de topics. Dev Services off. DDL manual. README lista los topics | **PASS** |
| IV. Configuración y secretos | Las 16 variables, `${NOMBRE}` sin default, topics incluidos. `@ConfigMapping` en `infrastructure`, nunca en `domain` | **PASS** |
| V. Arquitectura hexagonal | Estructura de paquetes exacta; ImportControl de Checkstyle la hace cumplir | **PASS** |
| VI. Dependency inversion | 5 puertos de salida en `domain/port/out`; los servicios reciben puertos | **PASS** |
| VII. Modelado táctico DDD | Constructor privado, `start()`/`reconstitute()`, sin setters, `PanacheRepository` sobre entidad JPA separada | **PASS** |
| VIII. Mensajería | `UNIQUE` sobre `operation_reference`, Redis como filtro, DLQ explícita, offsets tras procesar, sin outbox documentado | **PASS** |
| IX. Concurrencia | Bloqueante con `@RunOnVirtualThread`, sin Mutiny ni Panache reactive | **PASS** |
| X. Java 21 moderno | Records, `Optional` solo en retorno de puertos, Lombok solo en entidades JPA, `Instant` | **PASS** |
| XI. Idioma | Código en inglés; glosario aplicado (`CoreTransaction`, nunca `BankTransaction`) | **PASS** |
| XII. Calidad | Checkstyle `severity=error` con ImportControl; Spotless `check` en verify | **PASS** |
| XIII. Pruebas | Matriz de transiciones, VOs, igualdad de montos, idempotencia por consumer, mappers | **PASS** |

### Desviación menor, aceptada

**XII. Calidad — fase de Checkstyle.** La constitución dice "Checkstyle en `mvn verify`"; el plan
lo vincula a la fase `validate`. `validate` precede a `verify`, así que `mvn verify` sigue
ejecutándolo y falla antes.

### Decisiones de nomenclatura

- `ProcessedOperationStore` (no `ProcessedEventStore`): la idempotencia se ancla en la
  referencia de operación, no en el identificador de evento (FR-019).
- `AmountDifference` es un tipo distinto de `Amount`: una diferencia puede ser negativa.
- El puerto `ToleranceProvider` y `ReconciliationIdProvider` evitan que `application` importe
  `infrastructure`.

## Project Structure

### Documentation (this feature)

```text
specs/001-payment-reconciliation/
├── plan.md              # Este archivo
├── spec.md              # Especificación funcional
├── research.md          # Fase 0 — decisiones heredadas, con P-00 abierto
├── data-model.md        # Fase 1 — agregado, VOs, esquema de tabla
├── quickstart.md        # Fase 1 — guía de validación end-to-end
├── contracts/           # Fase 1 — OpenAPI y esquemas Avro
│   ├── reconciliation-api.yaml
│   ├── PaymentExecuted.avsc
│   ├── CoreTransactionReported.avsc
│   ├── ReconciliationMatched.avsc
│   └── DiscrepancyDetected.avsc
├── checklists/
│   └── requirements.md
└── tasks.md             # Fase 2 — lo genera /speckit-tasks, no este comando
```

### Source Code (repository root)

```text
db/schema.sql                              # DDL manual, lo ejecuta una persona
src/main/avro/*.avsc                       # Contratos Avro, fuente de verdad
src/main/resources/application.yaml
src/main/resources/openapi/reconciliation-api.yaml   # Contrato REST, fuente de verdad

src/main/java/com/bancatest/reconciliation/
├── domain/
│   ├── model/Reconciliation.java          # Único aggregate root
│   ├── model/vo/                          # Amount, AmountDifference, Currency,
│   │                                      # OperationReference, AccountNumber,
│   │                                      # ReconciliationId, ReconciliationStatus
│   ├── port/in/                           # ReconcilePaymentUseCase,
│   │                                      # RegisterCoreTransactionUseCase,
│   │                                      # QueryReconciliationUseCase
│   ├── port/out/                          # ReconciliationRepository, ProcessedOperationStore,
│   │                                      # ReconciliationEventPublisher, ToleranceProvider,
│   │                                      # ReconciliationIdProvider
│   └── exception/                         # InvalidAmountException, CurrencyMismatchException,
│                                          # IllegalStateTransitionException
├── application/service/                   # ReconcilePaymentService,
│                                          # RegisterCoreTransactionService,
│                                          # QueryReconciliationService
└── infrastructure/
    ├── adapter/in/kafka/                  # PaymentConsumer, CoreTransactionConsumer, mapper/
    ├── adapter/in/rest/                   # ReconciliationResource (implements la interfaz generada)
    ├── adapter/out/persistence/           # entity/, mapper/, PanacheReconciliationRepository
    ├── adapter/out/redis/                 # RedisProcessedOperationStore
    ├── adapter/out/messaging/             # KafkaReconciliationEventPublisher, mapper/
    └── config/ReconciliationConfig.java   # @ConfigMapping: tolerancia y TTL

src/test/java/com/bancatest/reconciliation/   # domain/, application/, infrastructure/
.env.example
```

**Structure Decision**: proyecto Maven único con la estructura hexagonal obligatoria de la
constitución V. La frontera que importa es la de paquetes, y la hace cumplir el ImportControl de
Checkstyle en el build. Los artefactos generados (clases Avro, interfaces JAX-RS y DTOs) viven
en `target/` y no se versionan; las fuentes de verdad son los `.avsc` y el `.yaml`.

## Canales de mensajería

El nombre de canal es interno y estable; el nombre de topic llega por entorno y no aparece en el
repositorio. Los mensajes salientes se publican con `operationReference` como key; para los
entrantes, el spec declara como dependencia que los productores hacen lo mismo.

| Dirección | Canal | Topic desde | DLQ desde |
| --- | --- | --- | --- |
| incoming | `payments-in` | `${KAFKA_TOPIC_PAYMENTS_IN}` | `${KAFKA_TOPIC_PAYMENTS_DLQ}` |
| incoming | `core-transactions-in` | `${KAFKA_TOPIC_CORE_TRANSACTIONS_IN}` | `${KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ}` |
| outgoing | `results-out` | `${KAFKA_TOPIC_RESULTS_OUT}` | — |
| outgoing | `discrepancies-out` | `${KAFKA_TOPIC_DISCREPANCIES_OUT}` | — |

```yaml
mp:
  messaging:
    incoming:
      payments-in:
        connector: smallrye-kafka
        topic: ${KAFKA_TOPIC_PAYMENTS_IN}
        enable.auto.commit: false
        failure-strategy: dead-letter-queue
        dead-letter-queue.topic: ${KAFKA_TOPIC_PAYMENTS_DLQ}
        partition.assignment.strategy: >-
          org.apache.kafka.clients.consumer.CooperativeStickyAssignor
    outgoing:
      results-out:
        connector: smallrye-kafka
        topic: ${KAFKA_TOPIC_RESULTS_OUT}
        key.serializer: org.apache.kafka.common.serialization.StringSerializer
        auto.register.schemas: false
```

`core-transactions-in` y `discrepancies-out` replican la forma de su pareja. Ninguna propiedad
lleva valor por defecto. `auto.register.schemas: false` es obligatorio y no es el default del
serde de Confluent (R-06). *Propiedades pendientes de re-verificar (P-00).*

## Flujo de los dos casos de uso de escritura

**ReconcilePaymentService** (pago reportado):

1. Validar el comando: `Amount` normalizado (FR-023) y positivo, moneda PEN, cuenta y
   referencia no vacías. Inválido → excepción → DLQ del canal de pagos (FR-022).
2. Filtro Redis: `setGet` con `NX` y `EX` sobre `dedup:{operationReference}`. Valor previo no
   nulo → descartar como éxito, log INFO (FR-019).
3. `Reconciliation.start(...)` → agregado PENDING con `ReconciliationId` UUIDv7.
4. Persistir. Si el `UNIQUE` rechaza → duplicado que burló el filtro → descartar como éxito.
5. No se publica nada (FR-016).

**RegisterCoreTransactionService** (movimiento del core):

1. Validar el comando igual que arriba. Inválido → DLQ del canal de movimientos.
2. Buscar la conciliación por `OperationReference`. Vacío → descartar, log WARN (FR-021).
3. **Comprobar `isResolved()` antes de transicionar.** Resuelta → descartar, log WARN, sin
   invocar transición (FR-020).
4. Calcular la diferencia (`paymentAmount − coreAmount`, FR-005) y compararla con la tolerancia.
5. `match(...)` o `markAsMismatched(...)` sobre el agregado, registrando siempre la diferencia
   (FR-009).
6. Persistir y **confirmar la transacción**.
7. Publicar el evento en el canal correspondiente, ya fuera de la transacción (FR-015).

`IllegalStateTransitionException` es una red de seguridad, no control de flujo: si se usara para
detectar duplicados, cada reentrega acabaría en la DLQ. `resolveInTransaction()` debe quedar no
privado y no final (R-13).

## Complexity Tracking

> Sin violaciones de la constitución pendientes de justificar.

| Elemento | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| `quarkus-rest-client` como dependencia | Lo exige `quarkus-confluent-registry-avro`: el serde de Confluent usa el cliente REST de Jakarta | No hay alternativa; sin él el serde no resuelve sus dependencias (R-06) |
| Versión mínima de `org.postgresql:postgresql` >= 42.7.9, sobrescribiendo el BOM solo si resuelve 42.7.8 | La 42.7.8 reintrodujo pinning de virtual threads; JEP 491 es de Java 24 y esto es Java 21 | Fijar una versión anterior cambia un riesgo por otro: se pierden correcciones posteriores (R-11) |

## Post-Design Constitution Re-check

- **V y VI**: los contratos Avro y OpenAPI viven fuera de `domain`; ningún tipo generado aparece
  en una firma del dominio. **PASS**
- **VII**: el modelo confirma constructor privado, factories y ausencia de setters; la entidad
  JPA es una clase distinta con mapper explícito. **PASS**
- **VIII**: los cuatro esquemas llevan `default` en todo campo y el `UNIQUE` está en el DDL.
  **PASS**
- **X**: `Instant` en todo instante técnico (`timestamp-millis` en Avro), `BigDecimal` con
  `logicalType decimal` en toda la cadena, sin `double`. **PASS**
- **XI**: glosario aplicado a clases, canales, esquemas y columnas. **PASS**

Sin nuevas violaciones. **Abierto:** P-00 (re-verificación con Context7).
