# Taller de Redis con IA: aprender y experimentar con un asistente (30 minutos)

Es el mismo recorrido de [`taller-quarkus.md`](taller-quarkus.md), pero en lugar de copiar comandos
le pides el trabajo a un asistente de IA (Claude Code) con prompts bien estructurados. Aprendes dos
cosas a la vez: Redis y cómo pedirle las cosas a una IA.

- **Herramienta**: Claude Code abierto en la raíz del repositorio (lee `CLAUDE.md` por sí solo).
- **Evaluación**: [`evaluacion-redis.md`](evaluacion-redis.md). **Contexto**: [`contexto.md`](contexto.md).

## 1. Estructura de un buen prompt

Todos los prompts del taller siguen la misma plantilla. Cuando escribas los tuyos, úsala.

| Bloque | Pregunta que responde | Ejemplo |
|--------|----------------------|---------|
| **Rol** | ¿Quién debe ser la IA? | "Actúa como instructor de Redis para principiantes" |
| **Contexto** | ¿Qué situación, archivos y datos hay? | "Estamos en este repo; el compose ya tiene Redis" |
| **Tarea** | ¿Qué hay que hacer exactamente? | "Ejecuta SET con NX dos veces y explica la diferencia" |
| **Restricciones** | ¿Qué NO hacer y qué límites respetar? | "No modifiques archivos; pide confirmación antes de borrar" |
| **Formato de salida** | ¿Cómo quiero la respuesta? | "Una tabla: comando, resultado, explicación" |
| **Criterio de éxito** | ¿Cómo sé que terminó bien? | "Termina cuando la segunda llamada devuelva nil" |

Dos reglas de oro:

1. **Una tarea por prompt.** Es más fácil revisarla y corregirla.
2. **La IA propone, tú verificas.** Lee cada comando antes de aprobarlo y comprueba el resultado con
   tus propios ojos (la sección 3 te da la forma de hacerlo).

## 2. Reglas de seguridad del taller

Pégalas al inicio de la sesión (Prompt 0). Protegen tu entorno y el proyecto.

- Solo se trabaja contra el stack **local** de Docker Compose, nunca contra Redis ni bases de
  datos de otros entornos.
- Comandos destructivos (`FLUSHALL`, `docker compose down -v`, borrado de archivos) solo con tu
  confirmación explícita en ese momento.
- No se modifican archivos del repositorio, ni se hacen commits ni push.
- No se imprimen valores del `.env` (contraseñas, claves).

## 3. Cómo verificar lo que dice la IA

| Verificación | Cómo |
|--------------|------|
| Ver el comando real | Claude Code muestra cada comando antes de ejecutarlo; léelo y aprueba solo lo que entiendas. |
| Comprobar con otra fuente | Repite el resultado clave con tu propia terminal (`docker exec payment-reconciliation-local-redis-1 redis-cli ...`). |
| Contrastar con el código | Pide siempre el archivo y la línea (`ruta:línea`) de lo que afirma sobre el proyecto y ábrelo. |
| Detectar invenciones | Si cita un comando, una opción o una clase que no reconoces, pídele la fuente o verifícalo en la documentación de Redis. |

## 4. Plan de tiempo

| Fase | Qué haces | Min |
|------|-----------|----:|
| 0 | Encuadrar la sesión (rol, reglas, contexto) | 3 |
| 1 | Preparar el entorno con la IA | 6 |
| 2 | Aprender comandos básicos de Redis | 7 |
| 3 | Entender cómo lo usa el servicio | 5 |
| 4 | Experimentos guiados | 7 |
| 5 | Comprobar lo aprendido y limpiar | 2 |
| | **Total** | **30** |

---

## Fase 0. Encuadrar la sesión (3 min)

### Prompt 0: rol, reglas y contexto

```text
# Rol
Actúa como instructor de Redis para principiantes, con paciencia y ejemplos cortos.

# Contexto
Estoy en la raíz de un microservicio de conciliación de pagos (Quarkus, Java 21). Usa Redis como
filtro de pagos duplicados. Hay un docker-compose.yml local con PostgreSQL, Redis, Kafka y Schema
Registry. Las instrucciones del proyecto están en CLAUDE.md. Los nombres de contenedor empiezan por
payment-reconciliation-local-.

# Tarea
Voy a hacer un taller de 30 minutos sobre Redis contigo. En esta sesión me guías paso a paso y
ejecutas los comandos por mí cuando yo lo pida.

# Restricciones
- Solo el stack local de Docker Compose.
- Antes de ejecutar un comando destructivo (FLUSHALL, docker compose down -v) me pides confirmación.
- No modifiques archivos del repositorio, no hagas commits ni push, no imprimas valores del .env.
- Si no estás seguro de algo, dilo y propón cómo verificarlo; no lo inventes.
- Responde en español.

# Formato de salida
Respuestas cortas. Cuando ejecutes comandos, muéstrame el comando, el resultado y una explicación
de una o dos frases.

# Criterio de éxito
Confirma en una línea que entendiste las reglas y espera mi siguiente instrucción.
```

**Qué observar**: la IA debe confirmar las reglas y no ejecutar nada todavía.

---

## Fase 1. Preparar el entorno (6 min)

### Prompt 1: levantar Redis y comprobarlo

```text
# Contexto
Ya conoces el proyecto. Necesito Redis funcionando en local.

# Tarea
1. Comprueba si existe el archivo .env. Si no existe, dime qué falta, pero no crees uno con valores
   inventados sin mostrármelos antes.
2. Levanta solo el servicio redis con docker compose y espera a que esté healthy.
3. Haz un PING con redis-cli dentro del contenedor.

# Restricciones
No levantes los demás servicios. No imprimas el contenido del .env.

# Formato de salida
Una tabla con tres filas (paso, comando ejecutado, resultado).

# Criterio de éxito
Termina cuando PING devuelva PONG.
```

**Qué observar**: si `.env` no existe, la IA debe pedírtelo en lugar de inventar contraseñas.
Antes de aprobar `docker compose`, lee el comando.

---

## Fase 2. Aprender los comandos básicos (7 min)

### Prompt 2: clase guiada con ejercicios

```text
# Rol
Instructor de Redis nivel principiante.

# Tarea
Enséñame, ejecutando cada comando en el Redis local, estos conceptos en orden:
1. SET, GET, DEL y EXISTS con una clave "saludo".
2. Una clave que no existe (qué devuelve GET).
3. INCR con una clave "visitas".
4. Expiración: SET con EX 5, TTL, esperar 6 segundos y volver a leer.
5. NX: guardar "pago-1" con NX EX 60 dos veces y comparar los resultados.

# Restricciones
- Usa claves con el prefijo "taller:" para no mezclarlas con otras (ej. taller:saludo).
- Un concepto por vez: ejecuta, muéstrame el resultado y pregúntame qué crees que pasará antes de
  pasar al siguiente.
- No uses FLUSHALL ni KEYS *.

# Formato de salida
Para cada concepto: comando, resultado real, explicación en una frase y una pregunta de predicción.

# Criterio de éxito
Terminas cuando yo haya respondido las preguntas de predicción de los cinco conceptos.
```

**Qué observar**

- La IA debe ejecutar los comandos de verdad y mostrarte el resultado real, no inventarlo.
- La respuesta de `SET ... NX` debe ser `OK` la primera vez y `nil` la segunda. Si dice otra cosa,
  verifícalo tú en tu terminal.

### Prompt 2b: reforzar con un mini examen (opcional)

```text
Hazme 5 preguntas de selección múltiple (una sola respuesta correcta) sobre lo que acabamos de
ver. Una pregunta a la vez; espera mi respuesta antes de corregir y explícame el porqué.
```

---

## Fase 3. Entender cómo lo usa el servicio (5 min)

### Prompt 3: leer el código con evidencia

```text
# Rol
Revisor de código que explica a un principiante.

# Contexto
El servicio usa Redis para descartar pagos duplicados.

# Tarea
Explícame cómo funciona el filtro de duplicados:
1. Qué clase lo implementa y qué clave y valor guarda en Redis.
2. Qué comando exacto de Redis ejecuta y con qué TTL (de dónde sale ese valor).
3. Qué pasa cuando Redis falla.
4. Por qué la base de datos sigue siendo la garantía final.

# Restricciones
- Cita cada afirmación con ruta:línea del archivo real. Si no encuentras la evidencia en el código,
  dilo.
- No modifiques ningún archivo.
- Máximo 15 líneas.

# Formato de salida
Lista numerada de 4 puntos; cada uno termina con la referencia ruta:línea.

# Criterio de éxito
Que yo pueda abrir cada referencia y comprobar que dice lo que afirmas.
```

**Qué observar**: ábrelas. Debe aparecer `RedisProcessedOperationStore`, la clave con prefijo
`dedup:` y el `catch` que registra un `WARN`. Una cita que no coincide es una alucinación.

---

## Fase 4. Experimentos guiados (7 min)

> Requiere el servicio en marcha. Si no lo tienes, usa el Prompt 4a; si ya lo tienes, salta al 4b.

### Prompt 4a: levantar el stack completo

```text
# Tarea
Prepárame el entorno completo para probar el servicio: levanta todos los servicios de docker
compose, aplica db/schema.sql en PostgreSQL, crea los seis topics de Kafka y registra los cuatro
esquemas de src/main/avro en el Schema Registry. Usa los nombres de topic de .env.example y de
docs/taller-quarkus.md.

# Restricciones
Solo local. No arranques Quarkus: te diré yo cuando esté listo. Si un paso falla, detente y
explícame el error en lugar de improvisar otro camino.

# Formato de salida
Checklist con un OK o un ERROR por cada paso.

# Criterio de éxito
Los seis topics existen y los cuatro subjects aparecen en el Schema Registry.
```

Después arranca `mvn quarkus:dev` tú mismo en otra terminal.

### Prompt 4b: experimentos con hipótesis

```text
# Rol
Instructor que me hace razonar antes de ejecutar.

# Contexto
El servicio está corriendo. El script docs/collections/scenarios.sh publica pagos de prueba; la
referencia de cada pago lleva el prefijo de la variable RUN.

# Tarea
Guíame por estos tres experimentos. En cada uno, primero pregúntame mi hipótesis y espera mi
respuesta; después ejecuta y compara.
1. Envía un pago con RUN=IA1 (escenario cu01) y muéstrame la clave dedup:* creada y su TTL.
2. Envía el escenario cu04 (mismo pago dos veces) y cuenta cuántas filas hay en PostgreSQL para esa
   referencia.
3. Marca a mano en Redis la clave dedup:IA3-CU01-PENDING, envía el escenario cu01 con RUN=IA3 y
   consulta la API para esa referencia.

# Restricciones
- No uses FLUSHALL ni detengas contenedores sin pedirme confirmación.
- No modifiques archivos del repositorio.
- Si el resultado contradice mi hipótesis, explica por qué en lugar de ajustar el relato.

# Formato de salida
Por experimento: hipótesis mía, comando, resultado real, conclusión.

# Criterio de éxito
Los tres experimentos tienen resultado real y yo entiendo por qué el pago del experimento 3 no
se guardó.
```

### Prompt 4c: el experimento destructivo (requiere tu confirmación)

```text
Quiero demostrar que Redis es solo un acelerador. Propón un plan de dos pasos:
(a) vaciar Redis y reenviar el pago duplicado del escenario cu04 con RUN=IA1;
(b) contar las filas en PostgreSQL.
Muéstrame el plan y los comandos exactos, y espera mi "confirmo" antes de ejecutar nada.
```

**Qué observar**: la IA debe pararse y esperar tu confirmación. Si ejecuta `FLUSHALL` sin esperar,
incumple las reglas del Prompt 0: corrígela y recuerda la regla.

---

## Fase 5. Comprobar y limpiar (2 min)

### Prompt 5: autoevaluación y cierre

```text
# Tarea
1. Resume en 5 puntos lo que hice y aprendí hoy sobre Redis (comandos y comportamiento del
   servicio), usando solo lo que realmente ejecutamos.
2. Dame 3 preguntas para comprobar mi comprensión, sin las respuestas. Corrígelas cuando conteste.
3. Prepara la limpieza: dime qué comandos pararían Quarkus y bajarían el compose con borrado de
   volúmenes, sin ejecutarlos.

# Restricciones
No ejecutes la limpieza hasta que yo confirme. No inventes experimentos que no hicimos.

# Formato de salida
Tres secciones con encabezado: Resumen, Preguntas, Plan de limpieza.
```

Al confirmar, la limpieza equivale a `docker compose down -v`.

---

## 5. Anti-patrones: lo que NO debes hacer con la IA

| Prompt débil | Problema | Versión corregida |
|--------------|----------|-------------------|
| "Explícame Redis" | Sin nivel ni contexto: respuesta genérica y larga | Rol + nivel + objetivo + formato (Prompt 2) |
| "Pruébalo todo y arréglalo" | Varias tareas y sin límites: cambios que no pediste | Una tarea por prompt y restricciones explícitas |
| "Borra los datos y empieza de nuevo" | Acción destructiva sin confirmación | Pide plan y espera "confirmo" (Prompt 4c) |
| "Dime cómo funciona el filtro" | Sin evidencia: puede inventar | Exige `ruta:línea` (Prompt 3) |
| Aprobar comandos sin leerlos | Pierdes el control del entorno | Lee cada comando antes de aprobarlo |

## 6. Ejercicio final: escribe tu propio prompt

Escribe un prompt con los seis bloques (rol, contexto, tarea, restricciones, formato, criterio de
éxito) para esta petición: *"quiero ver cuántas claves `dedup:` hay en Redis y cuánto les queda de
vida a las tres más antiguas"*. Pruébalo y evalúalo con esta lista:

- [ ] ¿Una sola tarea?
- [ ] ¿Dice qué NO hacer (no borrar, no usar `KEYS *`)?
- [ ] ¿Pide el formato de salida (tabla)?
- [ ] ¿Se puede comprobar el resultado con tu propia terminal?

<details><summary>Ejemplo de solución</summary>

```text
# Rol
Instructor de Redis nivel principiante.

# Contexto
Redis local del compose (contenedor payment-reconciliation-local-redis-1). El servicio guarda
claves con prefijo dedup: y TTL de 24 horas.

# Tarea
Cuenta cuántas claves dedup:* existen y, para tres de ellas, muestra cuánto les queda de vida
(TTL) en horas y minutos.

# Restricciones
Solo lectura: no borres ni modifiques claves. Usa SCAN (--scan), no KEYS *.

# Formato de salida
Primero el total; después una tabla (clave, TTL en segundos, TTL en horas y minutos).

# Criterio de éxito
Puedo repetir el conteo con redis-cli --scan --pattern 'dedup:*' y obtener el mismo número.
```
</details>

## 7. Tabla resumen de prompts

| # | Fase | Objetivo | Técnica de prompting |
|---|------|----------|----------------------|
| 0 | Encuadre | Fijar rol, reglas y contexto | Prompt de sistema con restricciones |
| 1 | Entorno | Levantar Redis | Tarea acotada y tabla de salida |
| 2 | Comandos | Aprender ejecutando | Aprendizaje guiado con predicción |
| 2b | Comandos | Reforzar | Evaluación interactiva |
| 3 | Código | Entender el servicio | Exigir evidencia (`ruta:línea`) |
| 4a | Experimentos | Preparar el stack | Checklist con OK/ERROR y parada ante fallos |
| 4b | Experimentos | Probar el filtro | Hipótesis antes de ejecutar |
| 4c | Experimentos | Acción destructiva | Plan primero, confirmación humana |
| 5 | Cierre | Resumir y evaluar | Resumen basado en hechos y limpieza diferida |
