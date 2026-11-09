# Diagrama de contexto

Qué rodea al servicio: sistemas externos, topics de Kafka y almacenes de datos. Los nombres de
topic son las variables de entorno que los configuran (el valor real lo define el equipo).

```mermaid
flowchart LR
    PS([Sistema de pagos])
    CB([Core bancario])
    US([Usuario / cliente HTTP])
    DS([Sistemas aguas abajo])
    OP([Operador])

    subgraph KAFKA[Kafka - Confluent Cloud / local]
        direction TB
        PIN[/KAFKA_TOPIC_PAYMENTS_IN<br/>PaymentExecuted/]
        CIN[/KAFKA_TOPIC_CORE_TRANSACTIONS_IN<br/>CoreTransactionReported/]
        ROUT[/KAFKA_TOPIC_RESULTS_OUT<br/>ReconciliationMatched/]
        DOUT[/KAFKA_TOPIC_DISCREPANCIES_OUT<br/>DiscrepancyDetected/]
        PDLQ[/KAFKA_TOPIC_PAYMENTS_DLQ/]
        CDLQ[/KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ/]
    end

    SR[(Schema Registry<br/>esquemas Avro)]

    SVC{{Servicio de conciliación<br/>Quarkus - REST + consumers}}

    PG[(PostgreSQL / Supabase<br/>tabla reconciliations)]
    RD[(Redis<br/>filtro de duplicados<br/>TTL 24 h)]

    PS -->|publica| PIN
    CB -->|publica| CIN
    PIN -->|consume| SVC
    CIN -->|consume| SVC
    SVC -->|conciliación MATCHED| ROUT
    SVC -->|conciliación MISMATCHED| DOUT
    SVC -->|mensaje inválido| PDLQ
    SVC -->|mensaje inválido| CDLQ
    ROUT -->|consume| DS
    DOUT -->|consume| DS
    PDLQ -.revisa.-> OP
    CDLQ -.revisa.-> OP

    SVC <-->|JDBC: INSERT / UPDATE / SELECT| PG
    SVC <-->|SET NX EX| RD
    SVC -.->|serializa / deserializa| SR
    US -->|GET /v1/reconciliations/ref| SVC
```

## Elementos

| Elemento | Tipo | Dirección | Uso |
|----------|------|-----------|-----|
| `KAFKA_TOPIC_PAYMENTS_IN` | Topic | Entrada | Pagos reportados |
| `KAFKA_TOPIC_CORE_TRANSACTIONS_IN` | Topic | Entrada | Movimientos del core |
| `KAFKA_TOPIC_RESULTS_OUT` | Topic | Salida | Conciliaciones conformes |
| `KAFKA_TOPIC_DISCREPANCIES_OUT` | Topic | Salida | Conciliaciones discrepantes |
| `KAFKA_TOPIC_PAYMENTS_DLQ` | Topic | Salida (error) | Pagos inválidos |
| `KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ` | Topic | Salida (error) | Movimientos inválidos |
| Schema Registry | Servicio | Lectura | Esquemas Avro; el servicio no los registra |
| PostgreSQL (Supabase) | Base de datos | Lectura/escritura | Única fuente de verdad; índice `UNIQUE` por referencia |
| Redis | Caché | Lectura/escritura | Solo acelera la idempotencia; si cae, se omite |
| API REST | HTTP | Entrada | `GET /v1/reconciliations/{operationReference}` |

Los topics salientes llevan la referencia de operación como key. Los topics, los esquemas y
`db/schema.sql` los crea una persona, no el servicio.
