---

description: "Task list for Conciliación de Pagos"
---

# Tasks: Conciliación de Pagos

**Input**: `specs/001-payment-reconciliation/` (plan.md, spec.md, data-model.md, research.md, quickstart.md, contracts/)

**Prerequisites**: plan.md, spec.md. **T001 (P-00) bloquea todo lo demás**: la constitución II prohíbe escribir configuración o llamadas a API sin verificarlas con Context7.

**Tests**: INCLUIDOS. No por TDD opcional, sino porque la constitución XIII los exige (matriz de transiciones, VOs, igualdad de montos, idempotencia por consumer, mappers; suite sin BD, Redis ni broker).

**Organization**: por historia de usuario. US1 (P1) es el MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizable (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2, US3
- Todo el código, comentarios, mensajes de excepción y tests en **inglés** (constitución XI). Javadoc en clases de dominio y puertos: invariantes y contrato, nunca repetir el nombre del método.

## Path Conventions

`B` = `src/main/java/com/bancatest/reconciliation` · `T` = `src/test/java/com/bancatest/reconciliation`

Paquetes: `domain/model`, `domain/model/vo`, `domain/port/in`, `domain/port/out`, `domain/exception`, `application/service`, `infrastructure/adapter/in|out`, `infrastructure/config` (plan.md).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: esqueleto del proyecto Maven, build gate y artefactos obligatorios. Sin provisionar infraestructura (constitución III).

- [ ] T001 Close pending **P-00**: with Context7 (fallback: official docs) re-verify every decision marked HEREDADA in `specs/001-payment-reconciliation/research.md` (R-01…R-14: schema-management, devservices, DLQ properties, Redis `setGet` signature checked against the pinned jar, java-uuid-generator, Confluent serde + `auto.register.schemas`, MapStruct `JAKARTA_CDI`, SASL/Schema Registry props, `avro.codegen.*`, pgjdbc >= 42.7.9, openapi-generator `jaxrs-spec`/`quarkus` options, ArC interception, InMemoryConnector). Pin exact versions (Quarkus, openapi-generator, MapStruct, Lombok, java-uuid-generator, Checkstyle, Spotless) and write the outcome (verified / corrected / unverifiable) into research.md. Anything unverifiable is reported to the user, never guessed.
- [ ] T002 Create `pom.xml` (Java 21, Quarkus BOM pinned in T001) with dependencies from plan.md (`quarkus-rest`, `quarkus-rest-jackson`, `quarkus-config-yaml`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-redis-client`, `quarkus-smallrye-reactive-messaging-kafka`, `quarkus-confluent-registry-avro`, `quarkus-rest-client`, `quarkus-hibernate-validator`, `quarkus-logging-json`, MapStruct + processor, Lombok, `java-uuid-generator`; test: `quarkus-junit`, `mockito-core`, `awaitility`, `smallrye-reactive-messaging-in-memory`). Add the Confluent Maven repository, ensure `org.postgresql:postgresql` >= 42.7.9 (R-11 procedure), `openapi-generator-maven-plugin` (config from research.md R-12; `inputSpec` = `src/main/resources/openapi/reconciliation-api.yaml`), Checkstyle bound to `validate` with `severity=error`, Spotless with `check` in `verify`. **No** `avro-maven-plugin`, no Flyway, no Apicurio.
- [ ] T003 [P] Create `.gitignore` (must ignore `.env`, `target/`) and `.env.example` with exactly the 16 variables of the closed list (constitution "Restricciones Técnicas"), all values empty.
- [ ] T004 [P] Copy the contracts into the source tree: the four `contracts/*.avsc` → `src/main/avro/`, and `contracts/reconciliation-api.yaml` → `src/main/resources/openapi/reconciliation-api.yaml` (byte-identical; they are the source of truth).
- [ ] T005 [P] Create `config/checkstyle/checkstyle.xml` (cyclomatic complexity max 10, method max 40 lines, max 5 parameters, line max 140, no star/unused imports) and `config/checkstyle/import-control.xml` enforcing constitution V: `domain` imports no `jakarta.persistence`, `io.quarkus`, `io.smallrye`, `org.apache.kafka`, `org.apache.avro`, nor `infrastructure`; `application` imports only `domain`. Wire both in `pom.xml`. No formatting rules (Spotless owns formatting).
- [ ] T006 [P] Create `db/schema.sql`: table `reconciliations` exactly as data-model.md (`id UUID` PK; `operation_reference VARCHAR(64) NOT NULL UNIQUE`; `account_number VARCHAR(34) NOT NULL`; `payment_amount NUMERIC(19,2) NOT NULL`; `payment_currency VARCHAR(3) NOT NULL`; `core_amount NUMERIC(19,2) NULL`; `core_currency VARCHAR(3) NULL`; `difference_amount NUMERIC(19,2) NULL`; `difference_currency VARCHAR(3) NULL`; `status VARCHAR(16) NOT NULL`; `created_at TIMESTAMPTZ NOT NULL`; `resolved_at TIMESTAMPTZ NULL`). Plain DDL, nothing executes it.
- [ ] T007 [P] Create `docs/glossary.md` with the normative table (Conciliación→Reconciliation, Pago reportado→ReportedPayment, Movimiento del core→CoreTransaction, Referencia de operación→OperationReference, Monto→Amount, Moneda→Currency, Diferencia→AmountDifference, Tolerancia→Tolerance).
- [ ] T008 Create `src/main/resources/application.yaml` (config-yaml): `quarkus.devservices.enabled=false`; `quarkus.hibernate-orm.schema-management.strategy=none` and `sql-load-script=no-file`; datasource (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, pool `min-size: 5`, `max-size: 16`, no `max-lifetime`); `quarkus.redis.hosts=${REDIS_HOSTS}`; `quarkus.http.root-path=/v1`; global `kafka.*` SASL_SSL + Schema Registry block from R-08/R-09 (`auto.register.schemas: false`); `avro.codegen.enableDecimalLogicalType: true`, `stringType: String`; the 4 channels from plan.md "Canales de mensajería" (`payments-in`, `core-transactions-in` with `failure-strategy: dead-letter-queue`, explicit `dead-letter-queue.topic`, `enable.auto.commit: false`; `results-out`, `discrepancies-out`); `reconciliation.tolerance.amount`/`currency` and `reconciliation.idempotency.ttl: PT24H`. Every topic/connection value is `${NAME}` **without default**. Property names verified in T001.
- [ ] T009 [P] Create `src/test/resources/application.properties`: `quarkus.datasource.active=false`, `quarkus.hibernate-orm.active=false`, values for tolerance and TTL (`PT24H`), and dummy values for the 16 variables so config resolves (R-14). No real hosts.

**Checkpoint**: `mvn validate` passes (Checkstyle loads); generated Avro/JAX-RS sources compile.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: dominio completo, puertos, configuración y persistencia que usan las tres historias.

**⚠️ CRITICAL**: ninguna historia empieza antes de cerrar esta fase.

### Domain (JUnit puro, sin `@QuarkusTest`)

- [ ] T010 [P] Create exceptions in `B/domain/exception/`: `InvalidAmountException`, `CurrencyMismatchException`, `IllegalStateTransitionException` (carries source and target status), `DuplicateOperationException` (thrown by `ReconciliationRepository.save` when the operation reference already exists). All unchecked (constitution X).
- [ ] T011 [P] Create `B/domain/model/vo/Currency.java`: `record Currency(String code)`, ISO-4217 3 uppercase letters, validated in the constructor. Whether only PEN is acceptable is NOT its concern (use case rule).
- [ ] T012 [P] Create `B/domain/model/vo/OperationReference.java` (`record(String value)`, not null/blank, max 64 chars), `AccountNumber.java` (not null/blank, max 34 chars; longer values fail domain validation → DLQ; matches `db/schema.sql` and the OpenAPI `maxLength`; its `toString`/any log helper must never expose the full number), `ReconciliationId.java` (`record(UUID value)`, not null).
- [ ] T013 Create `B/domain/model/vo/Amount.java`: `record Amount(BigDecimal value, Currency currency)`. Factory from `String` and `BigDecimal` (never `new BigDecimal(double)`). Compact constructor **normalizes to scale 2 with `RoundingMode.HALF_UP` BEFORE validating**, then validates value not null and **positive** (FR-022: PEN 0.004→0.00 rejected, 100.005→100.01 accepted) and currency not null → `InvalidAmountException`. Override `equals`/`hashCode` using `compareTo` on the normalized value (`120.0` equals `120.00`). `subtract(Amount other)` returns `AmountDifference`; different currencies → `CurrencyMismatchException`. Depends on T010, T011, T014.
- [ ] T014 [P] Create `B/domain/model/vo/AmountDifference.java`: `record(BigDecimal value, Currency currency)`, scale 2 HALF_UP, may be negative, `equals`/`hashCode` via `compareTo`. `isWithinTolerance(Amount tolerance)`: true if zero, or positive and `<=` tolerance (inclusive limit, FR-006); every negative value is false regardless of magnitude (FR-007); plain subtraction, currency mismatch → `CurrencyMismatchException`.
- [ ] T015 Create `B/domain/model/vo/ReconciliationStatus.java`: enum PENDING/MATCHED/MISMATCHED with an immutable static `Map<ReconciliationStatus, Set<ReconciliationStatus>>` and `canTransitionTo(target)` (only PENDING→MATCHED and PENDING→MISMATCHED). Persisted with `name()`, never `ordinal()`.
- [ ] T016 Create `B/domain/model/ReconciliationSnapshot.java` (record with the nine fields; only place nulls are tolerated) and `B/domain/model/Reconciliation.java`: private constructor, no setters; `static start(ReconciliationId, OperationReference, AccountNumber, Amount paymentAmount, Instant createdAt)` (no null args, payment in PEN, status PENDING); `static reconstitute(ReconciliationSnapshot)` (no business validation); `match(Amount coreAmount, AmountDifference difference, Instant resolvedAt)` and `markAsMismatched(...)` that call `status.canTransitionTo` and throw `IllegalStateTransitionException` otherwise, setting `coreAmount`/`difference`/`resolvedAt` together (invariant 3, difference in MATCHED **and** MISMATCHED, FR-009); `isResolved()`; getters never return `null` (`coreAmount`, `difference`, `resolvedAt` as `Optional`); difference currency equals payment currency (invariant 4). Javadoc with invariants. Depends on T011–T015.
- [ ] T017 [P] Create input ports in `B/domain/port/in/`: `ReconcilePaymentUseCase` + `ReconcilePaymentCommand(OperationReference, AccountNumber, Amount, Instant occurredAt)`; `RegisterCoreTransactionUseCase` + `RegisterCoreTransactionCommand(OperationReference, Amount, Instant occurredAt)`; `QueryReconciliationUseCase` (`Optional<Reconciliation> findByOperationReference(OperationReference)`). `eventId` is NOT in the commands (FR-019).
- [ ] T018 [P] Create output ports in `B/domain/port/out/`: `ReconciliationRepository` (`save`, `Optional<Reconciliation> findByOperationReference`), `ProcessedOperationStore` (`boolean markAsProcessed(OperationReference)`, false if already present), `ReconciliationEventPublisher` (`publishMatched`, `publishMismatched`), `ToleranceProvider` (`Amount tolerance()`), `ReconciliationIdProvider` (`ReconciliationId nextId()`), `TransactionalExecutor` (`<T> T inTransaction(Supplier<T> work)`; the transaction is committed when it returns). `ReconciliationRepository.save` throws `DuplicateOperationException` on a repeated reference. No framework types in any signature.

### Domain tests

- [ ] T019 [P] Create `T/domain/model/vo/ReconciliationStatusTest.java`: parameterized test over the full 3×3 matrix (9 combinations, 2 allowed, 7 rejected).
- [ ] T020 [P] Create `T/domain/model/vo/AmountTest.java`: `120.0` equals `120.00` (and hashCode); PEN vs USD `subtract` throws `CurrencyMismatchException`; HALF_UP (`100.005`→`100.01`, `0.004`→rejected); zero/negative rejected; null rejected.
- [ ] T021 [P] Create `T/domain/model/vo/AmountDifferenceTest.java`: the seven spec amounts (diff 0.00, 0.50, 1.00 within; 1.01, 5.00, -0.50 outside), plus -0.01 outside (SC-002).
- [ ] T022 [P] Create `T/domain/model/vo/ValueObjectValidationTest.java`: construction validation of `Currency` (format), `OperationReference`/`AccountNumber` (null, blank, length boundaries 64/65 and 34/35), `ReconciliationId` (null).
- [ ] T023 [P] Create `T/domain/model/ReconciliationTest.java`: `start()` yields PENDING with empty Optionals; `match()`/`markAsMismatched()` populate the three fields together; a second resolution throws `IllegalStateTransitionException`; `isResolved()` true only for terminal states; non-PEN payment rejected.

### Infrastructure shared by all stories

- [ ] T024 Create `B/infrastructure/config/ReconciliationConfig.java`: `@ConfigMapping(prefix = "reconciliation")` interface with tolerance (amount + currency) and idempotency TTL only. It must never reach the domain.
- [ ] T025 [P] Create `B/infrastructure/config/ConfigToleranceProvider.java` (implements `ToleranceProvider`, converts config into `Amount`) and `B/infrastructure/adapter/out/persistence/UuidV7ReconciliationIdProvider.java` (implements `ReconciliationIdProvider` with a singleton `Generators.timeBasedEpochGenerator()`). Depends on T024.
- [ ] T026 [P] Create JPA entity `B/infrastructure/adapter/out/persistence/entity/ReconciliationEntity.java` mapping the `reconciliations` table (data-model.md: amount+currency as two columns each; `status` with `@Enumerated(EnumType.STRING)`; nullable core/difference/resolved columns). Lombok **only** `@Getter`, `@Setter`, `@NoArgsConstructor(access = PROTECTED)`.
- [ ] T027 Create `B/infrastructure/adapter/out/persistence/mapper/ReconciliationPersistenceMapper.java` (MapStruct with shared `@MapperConfig`: `JAKARTA_CDI`, `unmappedTargetPolicy = ERROR`; or explicit hand-written methods where VO wrapping needs it): aggregate ↔ entity, using `Reconciliation.reconstitute(ReconciliationSnapshot)`. Depends on T016, T026.
- [ ] T028 Create `B/infrastructure/adapter/out/persistence/PanacheReconciliationRepository.java`: implements `ReconciliationRepository` on a `PanacheRepository<ReconciliationEntity>` (no Active Record); `save` persists via the mapper (it does not open the transaction; the caller does, via `TransactionalExecutor`); `findByOperationReference` queries by `operation_reference`. A `UNIQUE` violation is translated here into the domain `DuplicateOperationException` (T010) so services treat it as a processed duplicate without importing `jakarta.persistence`. Depends on T018, T027.
- [ ] T028b [P] Create `B/infrastructure/adapter/out/persistence/QuarkusTransactionalExecutor.java` implementing `TransactionalExecutor` with Quarkus' programmatic transaction API (exact class and method verified in T001 via Context7; commit happens before `inTransaction` returns). Depends on T018.
- [ ] T028c Create `B/infrastructure/config/ApplicationBeans.java`: CDI `@Produces` methods that instantiate `ReconcilePaymentService`, `RegisterCoreTransactionService` and `QueryReconciliationService` with their ports. The services stay plain classes with no framework annotations (`application` may not import `jakarta.*`, constitution V). Depends on T034, T035, T050.
- [ ] T029 [P] Create `T/infrastructure/adapter/out/persistence/ReconciliationPersistenceMapperTest.java`: round trip PENDING and resolved aggregates without loss (plain JUnit, instantiate `...MapperImpl`).
- [ ] T030 [P] Create test double `T/support/InMemoryReconciliationRepository.java` (`@Mock` alternative per R-14; `seed()` prepares state; `save()` throws `DuplicateOperationException` on a repeated reference) and a pass-through `TransactionalExecutor` double and `T/support/InMemoryChannels.java` (`QuarkusTestResourceLifecycleManager` switching the four channels to `InMemoryConnector`). Also a fake `ProcessedOperationStore` and recording `ReconciliationEventPublisher`.

**Checkpoint**: `mvn verify` green on domain tests; Checkstyle import-control passes.

---

## Phase 3: User Story 1 - Conciliación automática de un pago con su movimiento (Priority: P1) 🎯 MVP

**Goal**: pago → conciliación PENDING; movimiento → MATCHED/MISMATCHED con diferencia, y publicación en el canal correcto.

**Independent Test**: enviar `PaymentExecuted` y luego `CoreTransactionReported` (InMemoryConnector); verificar estado, diferencia y mensaje en `results-out` o `discrepancies-out`. Cubre los 7 escenarios de importes (SC-002).

### Tests for User Story 1

- [ ] T031 [P] [US1] Create `T/application/service/ReconcilePaymentServiceTest.java`: valid payment → one PENDING reconciliation saved, nothing published (scenario 1, FR-016).
- [ ] T032 [P] [US1] Create `T/application/service/RegisterCoreTransactionServiceTest.java`: parameterized over the seven spec amounts (core 100.00/99.50/99.00 → MATCHED with diff 0.00/0.50/1.00 published as matched; 98.99/95.00/100.50 → MISMATCHED with 1.01/5.00/-0.50 published as mismatched); difference is persisted in both outcomes; publish happens after save.
- [ ] T033 [P] [US1] Create `T/infrastructure/adapter/in/kafka/mapper/KafkaInboundMapperTest.java` and `T/infrastructure/adapter/out/messaging/mapper/EventMapperTest.java`: Avro→command (decimal bytes→`BigDecimal`, `Instant`) and aggregate→`ReconciliationMatched`/`DiscrepancyDetected` (field names per the `.avsc`).

### Implementation for User Story 1

- [ ] T034 [US1] Create `B/application/service/ReconcilePaymentService.java` implementing `ReconcilePaymentUseCase`: receives ports only; builds `Reconciliation.start(idProvider.nextId(), ...)` and `transactionalExecutor.inTransaction(() -> repository.save(...))`; publishes nothing. (Validation, Redis filter and duplicate handling are added in US2.)
- [ ] T035 [US1] Create `B/application/service/RegisterCoreTransactionService.java` implementing `RegisterCoreTransactionUseCase`: find by reference; `difference = payment.subtract(core)` (FR-005); `isWithinTolerance(toleranceProvider.tolerance())` → `match(...)` or `markAsMismatched(...)` always recording the difference (FR-009); find → compute → `match`/`markAsMismatched` → `save` all run inside `transactionalExecutor.inTransaction(...)`, which returns the resolved aggregate; only after it returns (committed) call `publishMatched`/`publishMismatched` (FR-015). No framework annotations in this class. (Resolved/orphan guards are added in US2, inside the same lambda.)
- [ ] T036 [P] [US1] Create `B/infrastructure/adapter/in/kafka/mapper/PaymentExecutedMapper.java` and `CoreTransactionReportedMapper.java` (MapStruct/explicit): generated Avro classes → commands; `eventId` is dropped; amounts built via `Amount` factory with `Currency`.
- [ ] T037 [P] [US1] Create `B/infrastructure/adapter/out/messaging/mapper/ReconciliationEventMapper.java`: aggregate → `ReconciliationMatched` / `DiscrepancyDetected` (new `eventId`, `reconciledAt`/`detectedAt` as `Instant`, decimal amounts, `difference` per `.avsc`).
- [ ] T038 [US1] Create `B/infrastructure/adapter/out/messaging/KafkaReconciliationEventPublisher.java`: implements `ReconciliationEventPublisher` with `@Channel("results-out")` / `@Channel("discrepancies-out")` emitters; message key = `operationReference`; blocking-friendly (no Mutiny in the contract with the domain). Publish failures are caught, logged at ERROR and not rethrown (FR-015: no retry, no compensation). Depends on T037.
- [ ] T039 [US1] Create `B/infrastructure/adapter/in/kafka/PaymentConsumer.java` (`@Incoming("payments-in")`, `@RunOnVirtualThread`, manual ack after processing) and `CoreTransactionConsumer.java` (`@Incoming("core-transactions-in")`): map → invoke use case → ack. After a successful commit the message is always acked, even if publishing failed. Depends on T034–T036 and T028c.
- [ ] T040 [US1] Create `T/infrastructure/adapter/in/kafka/ReconciliationFlowTest.java` (`@QuarkusTest` + `InMemoryChannels`, no infra): send payment then core transaction through the in-memory connector and assert the final state and the sink content for one MATCHED and one MISMATCHED case.

**Checkpoint**: US1 funcional y verificable sin las demás historias.

---

## Phase 4: User Story 2 - Descarte seguro de duplicados y entradas inválidas (Priority: P2)

**Goal**: idempotencia por referencia, descarte de resueltas/huérfanas, y desvío de inválidos a la DLQ del canal de origen.

**Independent Test**: mismo pago dos veces → una sola conciliación; movimiento sobre resuelta → sin cambios ni publicación; pago 0.00 o USD → DLQ de pagos.

### Tests for User Story 2

- [ ] T041 [P] [US2] Extend `T/application/service/ReconcilePaymentServiceTest.java`: duplicate via store (`markAsProcessed` false) → discarded as success, nothing saved; duplicate that passes the store but is rejected by UNIQUE → discarded as success; non-PEN, zero, blank reference/account → validation exception.
- [ ] T042 [P] [US2] Extend `T/application/service/RegisterCoreTransactionServiceTest.java`: movement over a resolved reconciliation → discarded, WARN, **no transition invoked** and no exception (FR-020); orphan movement → discarded, WARN, nothing created/published (FR-021); invalid movement → validation exception; when the in-transaction block discards (resolved/orphan) nothing is published; when the publisher throws, the reconciliation stays resolved and the service does not rethrow.
- [ ] T043 [P] [US2] Create `T/infrastructure/adapter/in/kafka/ConsumerIdempotencyTest.java` (`InMemoryConnector`): same `PaymentExecuted` twice → exactly one reconciliation; same `CoreTransactionReported` twice → one resolution and one published message; a poisoned message goes to the DLQ channel without stopping subsequent ones (SC-007); `PaymentExecuted` with 0.00 PEN, -5.00 PEN, 100.00 USD or blank `operationReference` each lands on the **payments** DLQ sink and creates no reconciliation; `CoreTransactionReported` with 0.00, USD or blank reference lands on the **movements** DLQ sink, not the payments one (FR-022, SC-005). Required by constitution XIII per consumer.

### Implementation for User Story 2

- [ ] T044 [US2] Create `B/infrastructure/adapter/out/redis/RedisProcessedOperationStore.java`: implements `ProcessedOperationStore` with blocking `RedisDataSource`, `ds.value(String.class).setGet(key, "1", new SetArgs().nx().ex(ttl))` on key `dedup:{operationReference}` (signature verified in T001 against the pinned jar); returns `true` when the previous value was `null`. TTL from `ReconciliationConfig`. Redis failure must not break processing: fall through to the DB UNIQUE (log WARN).
- [ ] T045 [US2] Update `ReconcilePaymentService.java`: validate the command (PEN only, positive normalized amount, non-blank reference/account) → on failure throw so the consumer nacks to the DLQ; then `markAsProcessed` (false → INFO log, return as success); then `start`/`save`; catch the duplicate signal from T028 → INFO log, return as success (FR-019, FR-022, FR-023). Account number is never logged in full.
- [ ] T046 [US2] Update `RegisterCoreTransactionService.java`: validate the command (FR-022); inside the `inTransaction` lambda: empty lookup → WARN and return empty (FR-021); **check `isResolved()` before any transition** → WARN and return empty (FR-020); an empty result means nothing is published; never use `IllegalStateTransitionException` for flow control.
- [ ] T047 [US2] Update `PaymentConsumer.java` and `CoreTransactionConsumer.java`: domain validation exceptions (including those thrown while building `Amount`/VOs in the mappers) → `nack` so the `dead-letter-queue` strategy writes to the explicit DLQ of the channel of origin (payments→payments DLQ, movements→movements DLQ); discard outcomes → `ack`. Confirm `application.yaml` DLQ topic names are explicit (T008).

**Checkpoint**: US1 y US2 funcionan; reentregas no producen efectos duplicados.

---

## Phase 5: User Story 3 - Consulta del detalle de una conciliación (Priority: P3)

**Goal**: `GET /v1/reconciliations/{operationReference}` → detalle o 404 `application/problem+json`.

**Independent Test**: sembrar una conciliación MISMATCHED (diff 5.00) y consultarla; consultar `OP-404` → 404.

### Tests for User Story 3

- [ ] T048 [P] [US3] Create `T/application/service/QueryReconciliationServiceTest.java`: found → Optional with value; unknown → empty.
- [ ] T049 [P] [US3] Create `T/infrastructure/adapter/in/rest/ReconciliationResourceTest.java` (`@QuarkusTest`, RestAssured at `/reconciliations/...`, repository double seeded via `seed()`): MISMATCHED shows `difference.amount = "5.00"`; MATCHED shows its difference (FR-009/FR-017); PENDING omits `coreAmount`, `difference`, `resolvedAt`; unknown reference → 404 with `Content-Type: application/problem+json` and RFC 7807 body (FR-018).

### Implementation for User Story 3

- [ ] T050 [US3] Create `B/application/service/QueryReconciliationService.java` implementing `QueryReconciliationUseCase` over `ReconciliationRepository`.
- [ ] T051 [P] [US3] Create `B/infrastructure/adapter/in/rest/mapper/ReconciliationResponseMapper.java`: aggregate → generated `ReconciliationResponse` (amounts as scale-2 strings, `OffsetDateTime`/`Instant` per the generated model, optional fields omitted when empty).
- [ ] T052 [US3] Create `B/infrastructure/adapter/in/rest/ReconciliationResource.java` implementing the generated API interface (`...rest.generated.api`), `@RunOnVirtualThread`, delegating to `QueryReconciliationUseCase`; empty → throw a not-found exception.
- [ ] T053 [US3] Create `B/infrastructure/adapter/in/rest/ReconciliationNotFoundExceptionMapper.java` (`ExceptionMapper`) returning status 404, `application/problem+json`, `ProblemDetail` with `type`, `title`, `status`, `detail`, `instance` (contract schema), not an untyped `Response` (R-12).

**Checkpoint**: las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T054 [P] Create `README.md`: purpose; the six topic variables (payments in/DLQ, core transactions in/DLQ, results out, discrepancies out) with their role, DLQ included; the full list of 16 variables; how to run `db/schema.sql` by hand; that the team registers the 4 Avro schemas (auto-register is off); **absence of outbox and its risk** (spec limitation 2); **out-of-order delivery limitation** (spec limitation 1); dependency that producers key messages by `operationReference`.
- [ ] T055 Run `mvn spotless:apply` then `mvn clean verify`; fix every Checkstyle (complexity, 40-line methods, 5 params, import-control) and Spotless finding. The build itself must never modify files.
- [ ] T056 [P] Review checklist (constitution "Revisión de cambios"): grep that `domain/` imports no framework and `application/` no `infrastructure`; no literal credential/URL; no variable outside the 16; no Flyway/docker-compose/Dev Services; each consumer has its idempotency test, explicit DLQ and README entry.
- [ ] T057 Walk through `quickstart.md` scenarios against the in-memory suite (scenarios 1–8) and, if the team provides real infrastructure, once with `-Djdk.tracePinnedThreads=full` (R-11). Report anything not verifiable without infra instead of assuming it works.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)**: T001 first (blocks everything); then T002; T003–T007, T009 parallel after T002; T008 after T001.
- **Foundational (2)**: after Phase 1. Domain order: T010/T011/T012/T014 → T013, T015 → T016 → ports T017/T018. Domain tests T019–T023 follow their targets. T024 → T025; T026 → T027 → T028; T028b after T018; T028c after T034/T035/T050 (run it with the last service task of each story); T029/T030 after T027/T018.
- **US1 (3)**, **US2 (4)**, **US3 (5)**: all need Phase 2. US2 modifies the files created in US1 (T045–T047 after T034–T039), so run US2 after US1. US3 is independent of US1/US2 and can run in parallel with them.
- **Polish (6)**: after the desired stories.

### Within Each Story

Tests first (they should fail), then mappers/services, then adapters, then the integration test.

### Parallel Opportunities

- Setup: T003, T004, T005, T006, T007, T009.
- Foundational: T010, T011, T012, T014; T017, T018; T019–T023; T025, T026; T029, T030.
- US1: T031–T033 together; T036, T037 together.
- US3 (T048–T053) in parallel with US1/US2 if staffed.

### Parallel Example: User Story 1

```text
Task: "ReconcilePaymentServiceTest in T/application/service/ReconcilePaymentServiceTest.java"
Task: "RegisterCoreTransactionServiceTest in T/application/service/RegisterCoreTransactionServiceTest.java"
Task: "PaymentExecutedMapper in B/infrastructure/adapter/in/kafka/mapper/"
Task: "ReconciliationEventMapper in B/infrastructure/adapter/out/messaging/mapper/"
```

## Implementation Strategy

### MVP First (US1)

1. Phase 1 (T001 P-00 first) → Phase 2 → Phase 3.
2. **STOP and VALIDATE**: `mvn verify` and the seven amount scenarios.

### Incremental Delivery

1. Setup + Foundational → domain solid.
2. US1 → MVP (reconcile and publish).
3. US2 → safe against duplicates/invalid input.
4. US3 → query endpoint.
5. Polish → README, build gate, quickstart.

## Notes

- Constitution I: nothing beyond the spec — no metrics, retries, circuit breakers, outbox, auth.
- Constitution II: any property/annotation not covered by T001 must be verified with Context7 before use; unverifiable items are reported.
- Never provision infrastructure or create scripts for it (constitution III).
- Commit after each task or logical group.
