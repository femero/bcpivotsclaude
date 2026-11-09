# Phase 1 — Data Model: Conciliación de Pagos

**Fecha**: 2026-11-09 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Nomenclatura según el glosario de la constitución (Restricciones Técnicas). El dominio no
importa ningún paquete de framework.

---

## Aggregate Root — `Reconciliation`

Única entidad con identidad y ciclo de vida. Nada externo referencia sus partes internas.

| Campo | Tipo | Notas |
| --- | --- | --- |
| `id` | `ReconciliationId` | UUIDv7 generado en aplicación |
| `operationReference` | `OperationReference` | Clave de negocio, única |
| `accountNumber` | `AccountNumber` | Informativo, del pago reportado |
| `paymentAmount` | `Amount` | Monto del pago reportado |
| `coreAmount` | `Amount` | Nulo mientras PENDING; presente al resolverse |
| `difference` | `AmountDifference` | Nulo mientras PENDING; presente en MATCHED **y** MISMATCHED (FR-009) |
| `status` | `ReconciliationStatus` | PENDING, MATCHED o MISMATCHED |
| `createdAt` | `Instant` | Alta del pago reportado |
| `resolvedAt` | `Instant` | Nulo mientras PENDING |

`coreAmount`, `difference` y `resolvedAt` son nulos internamente mientras la conciliación está
PENDING, pero **ningún getter del dominio devuelve `null`**: los tres se exponen como
`Optional<...>` (constitución X).

### Construcción

- **Constructor privado.** No hay otra vía.
- **`static Reconciliation start(ReconciliationId, OperationReference, AccountNumber,
  Amount paymentAmount, Instant createdAt)`**: crea una conciliación nueva en PENDING. Valida
  invariantes: ningún argumento nulo y `paymentAmount` en PEN.
- **`static Reconciliation reconstitute(ReconciliationSnapshot)`**: rehidrata desde
  persistencia con todos los campos. No valida invariantes de negocio: asume que lo persistido
  fue válido. Lo usa exclusivamente el mapper de persistencia.

  Recibe un **`ReconciliationSnapshot`** (record con los nueve campos) y no nueve argumentos:
  Checkstyle rechaza métodos con más de cinco parámetros (constitución XII). Es el único punto
  del dominio donde se toleran nulos, porque refleja columnas nulables; el agregado los
  reexpone como `Optional`.

### Métodos de negocio

Nombres de intención, sin setters.

- **`void match(Amount coreAmount, AmountDifference difference, Instant resolvedAt)`**:
  transiciona a MATCHED. Valida la transición antes de aplicarla.
- **`void markAsMismatched(Amount coreAmount, AmountDifference difference, Instant
  resolvedAt)`**: transiciona a MISMATCHED. Misma validación.
- **`boolean isResolved()`**: `true` si el estado es terminal. Es lo que consulta el caso de
  uso **antes** de invocar una transición (FR-020).

Ambos métodos de transición llaman a `status.canTransitionTo(target)` y lanzan
`IllegalStateTransitionException` con estado origen y destino si no es válida. Esa excepción es
red de seguridad (FR-012): con `isResolved()` comprobado antes, el flujo normal nunca la
dispara.

### Invariantes protegidas

1. Una conciliación nace siempre en PENDING y con `paymentAmount` válido en PEN.
2. El estado solo avanza por transiciones declaradas; MATCHED y MISMATCHED son terminales.
3. Al resolverse, `coreAmount`, `difference` y `resolvedAt` quedan poblados los tres a la vez.
   No existe un estado resuelto con diferencia ausente.
4. `difference` siempre comparte moneda con `paymentAmount`.

---

## Máquina de estados — `ReconciliationStatus`

Enum en `domain/model/vo`, sin librería externa.

```text
PENDING    → MATCHED
PENDING    → MISMATCHED
MATCHED    → ∅   (terminal)
MISMATCHED → ∅   (terminal)
```

Las transiciones válidas viven en un `Map<ReconciliationStatus, Set<ReconciliationStatus>>`
estático e inmutable, expuesto por `canTransitionTo(ReconciliationStatus target)`.

| Origen ↓ / Destino → | PENDING | MATCHED | MISMATCHED |
| --- | --- | --- | --- |
| **PENDING** | ✗ | ✓ | ✓ |
| **MATCHED** | ✗ | ✗ | ✗ |
| **MISMATCHED** | ✗ | ✗ | ✗ |

9 combinaciones, 2 permitidas, 7 rechazadas (es la matriz que recorre el test parametrizado,
constitución XIII). Se persiste con `name()`; `ordinal()` está prohibido.

---

## Value Objects

Todos son `record` inmutables que validan en construcción: si son inválidos, no existen.

### `Amount`

```text
record Amount(BigDecimal value, Currency currency)
```

- Se construye desde `String` o `BigDecimal`. **`new BigDecimal(double)` está prohibido**.
- El constructor compacto **normaliza a escala 2 con `RoundingMode.HALF_UP`** (FR-023). La
  normalización ocurre **antes** de validar; por eso PEN 0.004 pasa a 0.00 y se rechaza por
  FR-022, mientras que PEN 100.005 pasa a 100.01 y se acepta.
- Valida: `value` no nulo y positivo tras normalizar (FR-022); `currency` no nula.
- **`equals` y `hashCode` sobrescritos**: comparan con `compareTo` sobre el valor normalizado.
  Sin esto, `120.0` y `120.00` serían distintos.
- `subtract(Amount other)` devuelve un **`AmountDifference`** (no un `Amount`). Si las monedas
  difieren lanza `CurrencyMismatchException`.

> La validación de "monto positivo" pertenece al `Amount` entrante. El valor cero solo existe
> como `AmountDifference`.

### `AmountDifference`

```text
record AmountDifference(BigDecimal value, Currency currency)
```

**Tipo propio, distinto de `Amount`**: la diferencia **puede ser negativa** cuando el core cobró
de más (FR-007) y `Amount` es siempre positivo. Con un solo tipo, `-0.50` sería inexpresable.

- Normaliza a escala 2 con `RoundingMode.HALF_UP`.
- `equals`/`hashCode` por `compareTo`, nunca `BigDecimal.equals`.
- `isWithinTolerance(Amount tolerance)`: cierto si es cero, o positiva y **menor o igual** a la
  tolerancia (límite inclusivo, FR-006). Toda diferencia negativa es falsa, sin importar su
  magnitud (FR-007). Resta directa, sin división: la tolerancia es un monto fijo.

### `Currency`

```text
record Currency(String code)
```

Código ISO-4217 de 3 letras mayúsculas. Valida el formato. Que solo PEN sea aceptable es una
regla del caso de uso (FR-022), no del VO.

### `OperationReference`

```text
record OperationReference(String value)
```

No nula ni en blanco. Clave de negocio y único criterio de idempotencia (FR-019).

### `AccountNumber`

```text
record AccountNumber(String value)
```

No nulo ni en blanco. Informativo. **Nunca se loguea completo.**

### `ReconciliationId`

```text
record ReconciliationId(UUID value)
```

No nulo. UUIDv7 (ver R-05).

### `Tolerance`

La tolerancia se modela como un `Amount` (monto fijo con moneda) que entrega el puerto
`ToleranceProvider`; no necesita tipo propio mientras no tenga reglas distintas de las de
`Amount`. Valor: PEN 1.00 (FR-008).

---

## Comandos de entrada

Records en `domain/port/in`. El movimiento del core llega como comando y **no se persiste**.

```text
record ReconcilePaymentCommand(
    OperationReference operationReference,
    AccountNumber accountNumber,
    Amount amount,
    Instant occurredAt)

record RegisterCoreTransactionCommand(
    OperationReference operationReference,
    Amount amount,
    Instant occurredAt)
```

El `eventId` de los mensajes Avro **no entra en los comandos**: es informativo y no participa
en la idempotencia (FR-019).

---

## Puertos de salida

Interfaces en `domain/port/out`. Ninguna menciona un tipo de framework.

```text
interface ReconciliationRepository
    void save(Reconciliation reconciliation)
    Optional<Reconciliation> findByOperationReference(OperationReference reference)

interface ProcessedOperationStore
    boolean markAsProcessed(OperationReference reference)   // false si ya estaba

interface ReconciliationEventPublisher
    void publishMatched(Reconciliation reconciliation)
    void publishMismatched(Reconciliation reconciliation)

interface ToleranceProvider
    Amount tolerance()

interface ReconciliationIdProvider
    ReconciliationId nextId()

interface TransactionalExecutor
    <T> T inTransaction(Supplier<T> work)   // commits before returning
```

`ReconciliationRepository.save` lanza `DuplicateOperationException` si la referencia ya existe
(violación del `UNIQUE`). `TransactionalExecutor` existe porque `application` no puede importar
`jakarta.transaction` (ImportControl, constitución V) y porque el orden "confirmar y después
publicar" (FR-015) debe ser explícito. `OperationReference` admite como máximo 64 caracteres y
`AccountNumber` 34, igual que el DDL.

`ToleranceProvider` y `ReconciliationIdProvider` existen porque la capa `application` necesita
la tolerancia (que vive en una interfaz `@ConfigMapping` de `infrastructure`) y una identidad
nueva (`Generators.timeBasedEpochGenerator()` es una librería externa), y el control de imports
de Checkstyle prohíbe que `application` importe `infrastructure`. Un puerto es la salida que la
constitución IV anticipa: "sus valores entran convertidos en Value Objects".

`markAsProcessed` devuelve `boolean`, no `Optional`: la pregunta es binaria. Su implementación
en Redis usa `setGet` con `NX` y `EX` y devuelve `true` cuando el valor previo era `null`
(ver R-04).

---

## Persistencia

Una sola tabla. La entidad JPA es una clase distinta del agregado, con mapper explícito, y vive
en `infrastructure/adapter/out/persistence/entity`. Es el único lugar donde se permite Lombok, y
solo `@Getter`, `@Setter` y `@NoArgsConstructor(access = PROTECTED)`.

### Tabla `reconciliations`

| Columna | Tipo | Restricciones |
| --- | --- | --- |
| `id` | `UUID` | PK |
| `operation_reference` | `VARCHAR(64)` | **NOT NULL, UNIQUE** |
| `account_number` | `VARCHAR(34)` | NOT NULL. 34 es la longitud máxima de un IBAN; el spec no fija longitud de cuenta |
| `payment_amount` | `NUMERIC(19,2)` | NOT NULL |
| `payment_currency` | `VARCHAR(3)` | NOT NULL |
| `core_amount` | `NUMERIC(19,2)` | NULL hasta resolverse |
| `core_currency` | `VARCHAR(3)` | NULL hasta resolverse |
| `difference_amount` | `NUMERIC(19,2)` | NULL hasta resolverse; poblada en MATCHED y MISMATCHED |
| `difference_currency` | `VARCHAR(3)` | NULL hasta resolverse |
| `status` | `VARCHAR(16)` | NOT NULL, `name()` del enum |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |
| `resolved_at` | `TIMESTAMPTZ` | NULL hasta resolverse |

El **índice `UNIQUE` sobre `operation_reference` es la garantía real de idempotencia**
(constitución VIII). Redis solo la acelera. Cada monto usa dos columnas, importe y moneda,
nunca una cadena combinada. `status` se mapea con `@Enumerated(EnumType.STRING)` en la entidad
JPA, jamás en el dominio.

El DDL completo se entrega en `db/schema.sql` para que una persona lo ejecute. Sin Flyway.

---

## Trazabilidad con los requisitos

| Elemento | Requisitos que cubre |
| --- | --- |
| `Reconciliation.start()` | FR-001, FR-002 |
| `RegisterCoreTransactionCommand` + `findByOperationReference` | FR-003, FR-004 |
| `Amount.subtract()` | FR-005 |
| `AmountDifference.isWithinTolerance()` + `ToleranceProvider` | FR-006, FR-007, FR-008 |
| `difference` como atributo de toda conciliación resuelta | FR-009 |
| `ReconciliationStatus` + matriz | FR-010, FR-011, FR-012 |
| `ReconciliationEventPublisher` | FR-013, FR-014, FR-015, FR-016 |
| `findByOperationReference` | FR-017, FR-018 |
| `UNIQUE` + `ProcessedOperationStore` | FR-019 |
| `isResolved()` comprobado antes | FR-020 |
| `Optional` vacío del repositorio | FR-021 |
| Validación en `Amount`, `Currency`, `OperationReference`, `AccountNumber` | FR-022 |
| Normalización HALF_UP en `Amount` | FR-023 |
