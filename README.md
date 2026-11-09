# Conciliación de Pagos

Microservicio Quarkus (Java 21, hexagonal) que concilia pagos reportados con movimientos del core.
**Es una demo técnica, no un sistema productivo.**

Consume pagos y movimientos desde Kafka, los concilia por referencia de operación comparando los
importes contra una tolerancia fija de PEN 1.00 y publica el resultado como conforme
(`MATCHED`) o discrepante (`MISMATCHED`). Expone un único endpoint de consulta:
`GET /v1/reconciliations/{operationReference}`.

Documentación de diseño: [`specs/001-payment-reconciliation/`](specs/001-payment-reconciliation/).
Glosario de negocio: [`docs/glossary.md`](docs/glossary.md).

## Infraestructura (la gestiona el equipo)

El servicio **no** crea ni configura infraestructura.

- **Kafka y Schema Registry**: Confluent Cloud, topics ya creados. Los esquemas Avro de
  `src/main/avro` los registra el equipo; el auto-registro está desactivado.
- **PostgreSQL** (Supabase): conexión directa o pooler en **modo sesión**, nunca modo
  transacción. Una persona ejecuta `db/schema.sql` a mano; Hibernate nunca toca el esquema.
- **Redis**: instancia local. Requiere **Redis 7.0 o superior** (`SET ... NX GET`).

## Variables de entorno

Lista cerrada de 16, todas obligatorias y sin valor por defecto: si falta alguna, la aplicación
no arranca. Copiar `.env.example` a `.env` (que no se versiona) y rellenar los valores.

| Variable | Rol |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a PostgreSQL |
| `REDIS_HOSTS` | Conexión a Redis |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_API_KEY`, `KAFKA_API_SECRET` | Clúster Kafka (SASL_SSL / PLAIN) |
| `SCHEMA_REGISTRY_URL`, `SCHEMA_REGISTRY_API_KEY`, `SCHEMA_REGISTRY_API_SECRET` | Schema Registry (credenciales **distintas** a las del clúster) |

### Topics

| Variable | Rol |
| --- | --- |
| `KAFKA_TOPIC_PAYMENTS_IN` | Entrada: pagos reportados (`PaymentExecuted`) |
| `KAFKA_TOPIC_PAYMENTS_DLQ` | DLQ de pagos: pagos inválidos (monto ≤ 0, moneda ≠ PEN, referencia o cuenta en blanco) |
| `KAFKA_TOPIC_CORE_TRANSACTIONS_IN` | Entrada: movimientos del core (`CoreTransactionReported`) |
| `KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ` | DLQ de movimientos: movimientos inválidos |
| `KAFKA_TOPIC_RESULTS_OUT` | Salida: conciliaciones conformes (`ReconciliationMatched`) |
| `KAFKA_TOPIC_DISCREPANCIES_OUT` | Salida: conciliaciones discrepantes (`DiscrepancyDetected`) |

Los topics salientes se publican con la referencia de operación como key. Se asume que los
productores de los topics entrantes hacen lo mismo, para que los mensajes de una misma operación
se procesen en serie y en orden.

## Ejecutar

```bash
mvn spotless:apply     # formato, antes de commitear
mvn clean verify       # puerta de calidad: Checkstyle → compilación → tests → Spotless check
mvn quarkus:dev        # arranque local (Dev Services desactivado)
```

La suite completa corre **sin** base de datos, Redis ni broker (`InMemoryConnector`, datasource y
Hibernate desactivados). Los contratos viven en `src/main/avro/*.avsc` y
`src/main/resources/openapi/reconciliation-api.yaml`; las clases generadas quedan en `target/`.

## Limitaciones conocidas (aceptadas en esta demo)

1. **Sin garantía de orden entre canales.** Un movimiento del core que llega antes que su pago se
   descarta con una advertencia, y el pago, al llegar después, queda `PENDING` de forma
   permanente.
2. **Sin outbox.** La publicación ocurre después de confirmar la transacción en base de datos. Si
   el servicio cae, o la publicación falla, entre ambos pasos, la conciliación queda resuelta
   **sin** resultado publicado y no hay mecanismo de recuperación. Por eso la garantía de
   publicación es "como máximo una vez".
