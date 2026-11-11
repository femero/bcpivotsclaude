# Evaluación de Kafka: clave de respuestas

| # | Resp. | # | Resp. | # | Resp. | # | Resp. |
|---|:-----:|---|:-----:|---|:-----:|---|:-----:|
| 1 | b | 6 | c | 11 | d | 16 | a |
| 2 | d | 7 | b | 12 | b | 17 | d |
| 3 | a | 8 | a | 13 | c | 18 | b |
| 4 | b | 9 | c | 14 | b | 19 | a |
| 5 | a | 10 | a | 15 | a | 20 | d |

## Interpretación del puntaje

| Aciertos | Nivel |
|---------:|-------|
| 18–20 | Muy bueno: dominas lo básico |
| 14–17 | Bueno: repasa particiones, offsets y consumer groups |
| 10–13 | Regular: revisa topics, claves y el orden dentro de una partición |
| < 10 | Necesitas repasar los conceptos básicos |

## Notas sobre preguntas del proyecto

- **P7 y P9**: el orden solo se garantiza dentro de una partición; por eso los topics salientes
  usan la referencia de operación como key.
- **P15 y P20**: un mensaje inválido (monto ≤ 0, moneda ≠ PEN, referencia en blanco) lanza una
  excepción, se hace `nack` y va a la DLQ de su canal. Un movimiento huérfano o un duplicado se
  descarta con ack y **no** va a la DLQ.
- **P19**: en este servicio la idempotencia se decide por `operationReference`.
