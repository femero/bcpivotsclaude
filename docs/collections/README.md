# Colecciones de prueba por caso de uso

Casos de uso en [`../casos-de-uso.md`](../casos-de-uso.md). Solo CU-03 es REST; el resto se dispara
con mensajes Avro, que Postman no puede producir, así que se publican con `scenarios.sh` y se
verifican con la colección Postman (o con la salida del propio script).

| Archivo | Contenido |
|---|---|
| `scenarios.sh` | Publica mensajes Avro en el stack local (`kafka-avro-console-producer`) y consulta la API |
| `reconciliation-api.postman_collection.json` | Colección Postman v2.1 con tests; variables `baseUrl` y `run` |

## Uso

```bash
RUN=DEMO bash docs/collections/scenarios.sh all     # o: cu01 | cu02 | cu04 | cu05 | cu06
```

Requiere `docker compose up -d --wait`, topics y esquemas registrados y el servicio corriendo. Una
referencia ya usada se trata como duplicado: para repetir, cambia `RUN` (y la variable `run` de
Postman). Importa la colección en Postman y ejecútala con el mismo `run`.

## Mapa caso de uso → escenario → resultado esperado

| Caso de uso | Escenario (`scenarios.sh`) | Entrada | Resultado esperado |
|---|---|---|---|
| CU-01 Registrar pago reportado | `cu01` | Pago 100.00 PEN | `PENDING`, sin diferencia, no se publica nada |
| CU-02 Conciliar movimiento | `cu02` MATCH | Pago 100.00, core 100.00 | `MATCHED`, diferencia 0.00 |
| CU-02 | `cu02` LIMIT | 100.00 / 99.00 | `MATCHED`, diferencia 1.00 (límite inclusivo) |
| CU-02 | `cu02` OVER | 100.00 / 98.99 | `MISMATCHED`, diferencia 1.01 |
| CU-02 | `cu02` CHARGED-MORE | 100.00 / 100.50 | `MISMATCHED`, diferencia −0.50 |
| CU-02 (flujo 4a) | `cu02`, final | Segundo movimiento (50.00) sobre una conciliación resuelta | Se descarta; el estado no cambia |
| CU-03 Consultar conciliación | Colección Postman, carpeta CU-03 | `GET /v1/reconciliations/{ref}` | 200 `application/json` (resuelta y `PENDING`) y 404 `application/problem+json` |
| CU-04 Descartar duplicados | `cu04` | Mismo `operationReference`, distinto `eventId` y monto (100.00 y 999.00) | Una sola fila; gana el primero (100.00) |
| CU-05 Rechazar entradas inválidas | `cu05` | Pago USD; pago 0.00; pago sin cuenta; pago sin referencia; movimiento USD; movimiento 0.004 | No se crea ni modifica nada; +4 en `payments-dlq` y +2 en `core-transactions-dlq` |
| CU-06 Descartar movimiento huérfano | `cu06` | Movimiento sin pago previo | Sin fila, sin DLQ (solo `WARN` en el log) |
| CU-07 Publicar resultado | Incluido en `cu02` | Conciliaciones resueltas | `MATCHED` → `results-out`; `MISMATCHED` → `discrepancies-out` |

## Verificar CU-07 (eventos publicados)

```bash
docker exec payment-reconciliation-local-schema-registry-1 kafka-avro-console-consumer \
  --bootstrap-server kafka:29092 --topic results-out --from-beginning --timeout-ms 8000 \
  --property schema.registry.url=http://localhost:8081
```

(Usar `discrepancies-out` para las diferencias.)

## Notas

- El escenario `cu05` mide las DLQ por diferencia de offsets, así que funciona aunque tengan mensajes
  previos.
- La colección no se ejecutó con Newman (no está instalado); se validó el JSON y las respuestas de
  cada endpoint con `curl`.
