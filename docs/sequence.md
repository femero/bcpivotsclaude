# Diagramas de secuencia

Flujos del servicio tal como están implementados. Los diagramas usan [Mermaid](https://mermaid.js.org/):
se ven directamente en GitHub y en el preview de Markdown de la mayoría de IDEs.

Participantes comunes:

- **Consumer**: `PaymentConsumer` o `CoreTransactionConsumer` (hilos virtuales, concurrencia 1 por
  canal, por lo que procesan los mensajes de su canal de uno en uno y en orden de llegada).
- **Locks**: `OperationLocks`, exclusión mutua por referencia entre los dos canales.
- **Tx**: `TransactionalExecutor` (puerto), implementado con `QuarkusTransaction`. Confirma la
  transacción antes de devolver el control.

## 1. Pago reportado (`PaymentExecuted`)

Crea la conciliación en `PENDING`. No publica nada.

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka (pay-in)
    participant C as PaymentConsumer
    participant L as OperationLocks
    participant S as ReconcilePaymentService
    participant R as Redis
    participant T as Tx
    participant DB as PostgreSQL
    participant D as Kafka (pay-dlq)

    K->>C: PaymentExecuted
    C->>C: mapper a comando (Amount, Currency, referencia, cuenta)
    alt dato inválido (monto <= 0 tras redondeo, referencia o cuenta en blanco)
        C-->>K: nack
        K->>D: mensaje a la DLQ de pagos
    else dato válido
        C->>L: runExclusively(referencia)
        L->>S: reconcile(comando)
        alt moneda distinta de PEN
            S-->>C: InvalidAmountException
            C-->>K: nack
            K->>D: mensaje a la DLQ de pagos
        else moneda PEN
            S->>R: markAsProcessed (SET NX EX GET)
            alt la clave ya existía
                R-->>S: false
                Note over S: duplicado descartado, log INFO
            else clave nueva
                R-->>S: true
                S->>S: Reconciliation.start (PENDING)
                S->>T: inTransaction(save)
                T->>DB: INSERT reconciliations
                alt índice UNIQUE rechaza
                    DB-->>T: violación de unicidad
                    T-->>S: DuplicateOperationException
                    Note over S: duplicado descartado, log INFO
                else insertado
                    DB-->>T: ok, COMMIT
                end
            end
        end
        C-->>K: ack
    end
```

Si Redis no responde, el filtro se omite con un WARN y la garantía la da el índice `UNIQUE`.

## 2. Movimiento del core (`CoreTransactionReported`)

Resuelve la conciliación como `MATCHED` o `MISMATCHED` y publica el resultado **después** del commit.

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka (core-in)
    participant C as CoreTransactionConsumer
    participant L as OperationLocks
    participant S as RegisterCoreTransactionService
    participant T as Tx
    participant DB as PostgreSQL
    participant P as KafkaReconciliationEventPublisher
    participant M as Kafka (res-out)
    participant X as Kafka (disc-out)
    participant D as Kafka (core-dlq)

    K->>C: CoreTransactionReported
    C->>C: mapper a comando
    alt dato inválido
        C-->>K: nack
        K->>D: mensaje a la DLQ de movimientos
    else dato válido
        C->>L: runExclusively(referencia)
        L->>S: register(comando)
        alt moneda distinta de PEN
            S-->>C: InvalidAmountException
            C-->>K: nack
            K->>D: mensaje a la DLQ de movimientos
        else moneda PEN
            S->>T: inTransaction(resolver)
            T->>DB: SELECT por operation_reference
            alt no existe conciliación (huérfano)
                Note over S: descartado, log WARN, nada se publica
            else ya está resuelta
                Note over S: isResolved() es true, descartado con WARN, sin transición
            else está PENDING
                S->>S: diferencia = monto del pago - monto del core
                alt diferencia >= 0 y <= tolerancia (PEN 1.00)
                    S->>S: match(...) MATCHED
                else mayor que la tolerancia o negativa
                    S->>S: markAsMismatched(...) MISMATCHED
                end
                S->>DB: UPDATE reconciliations (estado, core, diferencia, resolved_at)
                DB-->>T: COMMIT
                T-->>S: conciliación resuelta
                alt estado MATCHED
                    S->>P: publishMatched
                    P->>M: ReconciliationMatched (key = referencia)
                else estado MISMATCHED
                    S->>P: publishMismatched
                    P->>X: DiscrepancyDetected (key = referencia)
                end
                Note over S,P: un fallo al publicar solo se registra, sin reintento ni compensación
            end
        end
        C-->>K: ack
    end
```

El `ack` ocurre siempre que el método vuelve sin excepción, incluidos los descartes. Solo los mensajes
inválidos terminan en la DLQ.

## 3. Consulta por referencia

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant R as ReconciliationResource
    participant Q as QueryReconciliationService
    participant DB as PostgreSQL
    participant E as ReconciliationNotFoundExceptionMapper

    U->>R: GET /v1/reconciliations/{operationReference}
    R->>Q: findByOperationReference
    Q->>DB: SELECT por operation_reference
    alt existe
        DB-->>Q: fila
        Q-->>R: Reconciliation
        R-->>U: 200 application/json (estado y, si está resuelta, la diferencia)
    else no existe
        DB-->>Q: vacío
        Q-->>R: Optional vacío
        R->>E: ReconciliationNotFoundException
        E-->>U: 404 application/problem+json
    end
```

## 4. Vista de conjunto

```mermaid
sequenceDiagram
    autonumber
    participant PS as Sistema de pagos
    participant CB as Core bancario
    participant SVC as Servicio de conciliación
    participant OUT as Sistemas aguas abajo
    participant U as Usuario

    PS->>SVC: PaymentExecuted (pay-in)
    Note over SVC: conciliación PENDING, no se publica nada
    CB->>SVC: CoreTransactionReported (core-in)
    Note over SVC: compara importes con tolerancia PEN 1.00
    alt dentro de la tolerancia
        SVC->>OUT: ReconciliationMatched (res-out)
    else fuera de la tolerancia o core cobró de más
        SVC->>OUT: DiscrepancyDetected (disc-out)
    end
    U->>SVC: GET /v1/reconciliations/{referencia}
    SVC-->>U: estado y diferencia
```

## Limitaciones que el diagrama no muestra

1. **Orden entre canales**: un movimiento que llega antes que su pago se descarta (rama "huérfano") y
   el pago queda `PENDING` para siempre.
2. **Sin outbox**: si el servicio cae entre el `COMMIT` y la publicación, la conciliación queda
   resuelta sin evento de salida.
