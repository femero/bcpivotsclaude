# Phase 1 — Quickstart: validación end-to-end

**Fecha**: 2026-11-09 | **Plan**: [plan.md](./plan.md)

Guía para arrancar el servicio y comprobar que la conciliación funciona de punta a punta. No
contiene código de implementación: eso pertenece a `tasks.md`.

---

## Prerequisitos

La infraestructura ya existe y la administra el equipo. Este servicio no la provisiona.

| Recurso | Estado | Quién lo prepara |
| --- | --- | --- |
| PostgreSQL en Supabase | Creado | Equipo |
| Tabla `reconciliations` | **Pendiente**: ejecutar `db/schema.sql` | Una persona, a mano |
| Kafka en Confluent Cloud | Creado | Equipo |
| Los 6 topics (2 entrada, 2 DLQ, 2 salida) | Creados; sus nombres se inyectan por entorno | Equipo |
| Schema Registry de Confluent | Creado | Equipo |
| Los 4 esquemas Avro registrados | **Pendiente**: el auto-registro está desactivado | Equipo, desde `src/main/avro` |
| Redis local | Configurado | Equipo |

> **Pendiente P-00.** Antes de implementar hay que re-verificar con Context7 la investigación
> heredada de `ms-test` y fijar las versiones (ver [research.md](./research.md)).

### Dos trampas que verás si algo se copió mal

- **El driver PostgreSQL debe ser 42.7.9 o superior** (la 42.7.8 reintrodujo pinning de
  virtual threads). Comprobar qué resuelve el BOM antes de tocar nada:

  ```bash
  mvn dependency:tree -Dincludes=org.postgresql:postgresql
  ```

  Si el log muestra `VirtualThread$VThreadContinuation.onPinned` con `reason:MONITOR`
  atravesando `org.postgresql`, es esto (R-11).
- **`avro.codegen.enableDecimalLogicalType` debe ser `true`.** Con el default (`false`) los
  montos llegan como `ByteBuffer` en vez de `BigDecimal` y el mapper no compila contra `Amount`
  (R-10).

---

## Configuración local

Crear un `.env` en la raíz a partir de `.env.example`. Nunca se versiona.

```bash
cp .env.example .env
# Rellenar los 16 valores. Si falta cualquiera, la aplicación falla al arrancar:
# la sintaxis ${NOMBRE} no lleva valor por defecto, a propósito.
```

Variables requeridas, las dieciséis y ninguna más:

```text
# Conexión y credenciales
DB_URL DB_USERNAME DB_PASSWORD
REDIS_HOSTS
KAFKA_BOOTSTRAP_SERVERS KAFKA_API_KEY KAFKA_API_SECRET
SCHEMA_REGISTRY_URL SCHEMA_REGISTRY_API_KEY SCHEMA_REGISTRY_API_SECRET

# Nombres de topic
KAFKA_TOPIC_PAYMENTS_IN            KAFKA_TOPIC_PAYMENTS_DLQ
KAFKA_TOPIC_CORE_TRANSACTIONS_IN   KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ
KAFKA_TOPIC_RESULTS_OUT            KAFKA_TOPIC_DISCREPANCIES_OUT
```

Las credenciales del Schema Registry son **distintas** de las del clúster (R-09).
`DB_URL` debe apuntar a la conexión directa de Supabase o a su pooler **en modo sesión**; el
modo transacción rompe los prepared statements de Hibernate.

---

## Preparar la base de datos

```bash
# Lo ejecuta una persona contra Supabase. No hay Flyway ni migración automática.
psql "$DB_URL" -f db/schema.sql
```

Comprobar que el índice único quedó creado (es la garantía real de idempotencia):

```sql
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'reconciliations';
-- Debe aparecer un UNIQUE sobre operation_reference
```

---

## Arrancar

```bash
mvn quarkus:dev          # hot reload, Dev Services desactivado
```

Señales de arranque correcto:

- No se crea ningún contenedor. Si aparece uno, `quarkus.devservices.enabled=false` no se
  aplicó.
- No hay sentencias DDL en el log. Si Hibernate emite `create table`,
  `quarkus.hibernate-orm.schema-management.strategy` no está en `none` (R-01).
- Los cuatro canales aparecen conectados y ningún esquema se registra al arrancar.

Verificación empírica de virtual threads (una vez): `mvn quarkus:dev
-Djdk.tracePinnedThreads=full`, enviar unos mensajes y comprobar que no aparece ninguna traza
`onPinned` que atraviese `org.postgresql` (R-11).

---

## Escenarios de validación

Cada escenario corresponde a un criterio de aceptación de [spec.md](./spec.md). Los importes
viajan como `bytes` con `logicalType decimal`, no como número.

### 1. Conciliación conforme (camino feliz)

Publicar en el canal de pagos un `PaymentExecuted` con `OP-1` y `100.00 PEN`.

```bash
curl -s http://localhost:8080/v1/reconciliations/OP-1 | jq
# Esperado: status PENDING, sin coreAmount ni difference, y NADA publicado
```

Publicar un `CoreTransactionReported` con `OP-1` y `100.00 PEN`.

```bash
curl -s http://localhost:8080/v1/reconciliations/OP-1 | jq
# Esperado: status MATCHED, difference "0.00", y un ReconciliationMatched en el canal de conformes
```

### 2. Tabla de tolerancia completa (FR-006, FR-007, SC-002)

| Referencia | Pago | Movimiento | Estado | Diferencia | Canal |
| --- | --- | --- | --- | --- | --- |
| OP-2 | 100.00 | 100.00 | MATCHED | 0.00 | conformes |
| OP-3 | 100.00 | 99.50 | MATCHED | 0.50 | conformes |
| OP-4 | 100.00 | 99.00 | MATCHED | 1.00 | conformes |
| OP-5 | 100.00 | 98.99 | MISMATCHED | 1.01 | discrepancias |
| OP-6 | 100.00 | 95.00 | MISMATCHED | 5.00 | discrepancias |
| OP-7 | 100.00 | 100.50 | MISMATCHED | -0.50 | discrepancias |

OP-4 y OP-5 separan el límite inclusivo de PEN 1.00 del primer céntimo fuera de tolerancia.
OP-7 comprueba que **toda** diferencia negativa es MISMATCHED. La consulta devuelve la
diferencia en las seis filas, MATCHED incluidas (FR-009, FR-017).

### 3. Redondeo de montos (FR-023)

| Referencia | Pago | Movimiento | Esperado |
| --- | --- | --- | --- |
| OP-10 | 100.005 | 100.01 | MATCHED, diferencia 0.00 (el pago se normaliza a 100.01) |
| OP-11 | 0.004 | — | Rechazado: se normaliza a 0.00 y va a la DLQ de pagos |

### 4. Idempotencia del pago (FR-019)

Publicar dos veces el mismo `PaymentExecuted` con `OP-8`.

```sql
SELECT count(*) FROM reconciliations WHERE operation_reference = 'OP-8';  -- Esperado: 1
```

En el log debe haber un INFO de descarte, **no un ERROR**. Si el mensaje acabó en la DLQ, el
caso de uso trata el duplicado como fallo. Repetir con Redis detenido: el resultado debe ser el
mismo, rechazado por el `UNIQUE`.

### 5. Movimiento sobre una conciliación ya resuelta (FR-020)

Sobre `OP-2`, ya MATCHED, publicar otro `CoreTransactionReported`. Esperado: no cambia nada, no
se publica nada y aparece un WARN. **No** debe lanzarse `IllegalStateTransitionException`; si
aparece, el orden de comprobación está invertido.

### 6. Movimiento huérfano (FR-021)

Un `CoreTransactionReported` con `OP-999`, sin pago previo. Esperado: descarte con WARN, nada
publicado, ninguna conciliación creada.

### 7. Entradas inválidas (FR-022)

| Caso | Entrada | Esperado |
| --- | --- | --- |
| Monto cero | `PaymentExecuted` con `0.00 PEN` | Sin conciliación; mensaje en la DLQ de pagos |
| Monto negativo | `PaymentExecuted` con `-5.00 PEN` | Igual |
| Otra moneda | `PaymentExecuted` con `100.00 USD` | Igual |
| Referencia en blanco | Pago o movimiento sin `operationReference` | DLQ del canal de origen |

El registro en la DLQ trae las cabeceras `dead-letter-reason` y `dead-letter-cause` (R-03).

### 8. Consulta inexistente (FR-018)

```bash
curl -s -i http://localhost:8080/v1/reconciliations/OP-404
# Esperado: 404, Content-Type: application/problem+json, cuerpo RFC 7807
```

---

## Suite de tests

```bash
mvn spotless:apply       # antes de commitear
mvn clean verify         # la puerta: Checkstyle → compilación → tests → Spotless check
```

El build **nunca** modifica archivos. La suite corre sin base de datos, Redis ni broker
(constitución XIII). Debe cubrir:

- Matriz completa de transiciones: 9 combinaciones, 2 permitidas, 7 rechazadas.
- Validación en construcción de cada Value Object.
- `Amount`: `120.0` y `120.00` iguales; PEN contra USD falla; redondeo half-up.
- Los siete casos de importes del spec.
- Idempotencia por consumer, con `InMemoryConnector`.
- Los mappers, de ida y vuelta.

Los tests contra infraestructura real usan referencias con prefijo identificable y limpian lo
que crean.

---

## Limitaciones que verás y no son defectos

1. **Un movimiento que llega antes que su pago se descarta**, y el pago queda PENDING para
   siempre.
2. **Si el servicio cae entre el commit y la publicación, el evento se pierde.** La
   conciliación queda resuelta en base pero sin resultado publicado; no hay outbox.

Ambas están en [spec.md](./spec.md) y deben figurar en el README.
