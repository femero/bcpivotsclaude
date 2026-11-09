# Evaluación teórica: Redis (nivel principiante)

20 preguntas de selección múltiple con una única respuesta correcta. La clave está al final.

---

**1. ¿Qué es Redis?**
a) Un motor de base de datos relacional con SQL
b) Un almacén de estructuras de datos en memoria, de tipo clave-valor
c) Un broker de mensajes exclusivo para Java
d) Un servidor web

**2. ¿Dónde guarda Redis sus datos principalmente?**
a) En el disco duro
b) En un servicio en la nube obligatoriamente
c) En la memoria RAM
d) En archivos XML

**3. ¿Qué hace el comando `SET nombre "Ana"`?**
a) Borra la clave `nombre`
b) Guarda el valor `"Ana"` bajo la clave `nombre`
c) Lee el valor de `nombre`
d) Cuenta las claves existentes

**4. ¿Qué comando se usa para leer el valor de una clave de tipo cadena?**
a) `READ`
b) `FETCH`
c) `GET`
d) `SHOW`

**5. ¿Qué devuelve `GET` cuando la clave no existe?**
a) Un error fatal que detiene Redis
b) Una cadena vacía `""`
c) El número `0`
d) `nil` (ausencia de valor)

**6. ¿Qué significa TTL en Redis?**
a) Total Transfer Limit
b) Time To Live: el tiempo que le queda de vida a una clave
c) Transaction Table Log
d) Thread Timeout Level

**7. ¿Qué ocurre cuando vence el TTL de una clave?**
a) Redis la elimina automáticamente
b) Redis la mueve a otra base de datos
c) Redis la bloquea para escritura
d) Redis la conserva y avisa por correo

**8. ¿Para qué sirve la opción `NX` en `SET clave valor NX`?**
a) Para que la clave nunca expire
b) Para guardar el valor cifrado
c) Para duplicar la clave en otro servidor
d) Para guardar solo si la clave no existe todavía

**9. ¿Cuál es el uso más habitual de Redis en una aplicación?**
a) Reemplazar por completo a la base de datos principal
b) Servir como caché para acelerar lecturas frecuentes
c) Compilar el código fuente
d) Generar los esquemas de la API

**10. ¿Qué tipo de dato de Redis almacena una colección de pares campo-valor bajo una misma clave, como un objeto?**
a) List
b) Set
c) Hash
d) Bitmap

**11. ¿Qué característica tiene un `Set` de Redis?**
a) Mantiene elementos únicos, sin duplicados
b) Mantiene el orden de inserción y permite repetidos
c) Solo admite números
d) Solo admite una clave

**12. ¿Qué comando incrementa en 1 el valor numérico de una clave?**
a) `ADD`
b) `PLUS`
c) `UP`
d) `INCR`

**13. ¿Qué comando elimina una clave?**
a) `DEL`
b) `DROP`
c) `REMOVE`
d) `CLEAR`

**14. ¿Cuál de estos comandos lista las claves que coinciden con un patrón?**
a) `LIST`
b) `KEYS`
c) `FIND`
d) `SEARCH`

**15. ¿Por qué se considera Redis muy rápido?**
a) Porque trabaja en memoria y usa estructuras de datos simples y optimizadas
b) Porque guarda todo en un archivo de texto
c) Porque ejecuta consultas SQL en paralelo
d) Porque no necesita red para funcionar

**16. ¿Qué es la persistencia en Redis (RDB y AOF)?**
a) Un método para cifrar las claves
b) Una forma de aumentar el TTL automáticamente
c) Un mecanismo para guardar los datos en disco y poder recuperarlos tras un reinicio
d) Un protocolo de autenticación

**17. ¿Cuál es el puerto por defecto de Redis?**
a) 5432
b) 6379
c) 8080
d) 9092

**18. ¿Qué herramienta de línea de comandos se usa para conectarse a Redis y ejecutar comandos?**
a) `redis-cli`
b) `redis-shell`
c) `psql`
d) `curl-redis`

**19. En un servicio de conciliación, ¿para qué se puede usar Redis con `SET clave "1" NX EX 86400` para detectar pagos duplicados?**
a) Para guardar de forma definitiva todos los pagos
b) Para calcular la diferencia entre montos
c) Para crear los topics de Kafka
d) Para marcar una referencia como ya procesada durante 24 horas y descartar repeticiones

**20. Si Redis se cae y la aplicación usa la base de datos como garantía final (por ejemplo, un índice `UNIQUE`), ¿qué conclusión es correcta?**
a) Se pierden todos los datos del negocio
b) Redis actúa como un acelerador y la garantía de datos únicos no depende de él
c) La aplicación deja de poder consultar la base de datos
d) El índice `UNIQUE` deja de funcionar

---

## Clave de respuestas

| # | Resp. | # | Resp. | # | Resp. | # | Resp. |
|---|:-----:|---|:-----:|---|:-----:|---|:-----:|
| 1 | b | 6 | b | 11 | a | 16 | c |
| 2 | c | 7 | a | 12 | d | 17 | b |
| 3 | b | 8 | d | 13 | a | 18 | a |
| 4 | c | 9 | b | 14 | b | 19 | d |
| 5 | d | 10 | c | 15 | a | 20 | b |

## Interpretación del puntaje

| Aciertos | Nivel |
|---------:|-------|
| 18–20 | Muy bueno: dominas lo básico |
| 14–17 | Bueno: repasa los tipos de datos y el TTL |
| 10–13 | Regular: revisa los comandos `SET`, `GET`, `DEL` y `INCR` |
| < 10 | Necesitas repasar los conceptos básicos |

Las preguntas 8, 19 y 20 se relacionan con este proyecto: `SET NX EX` como filtro de duplicados, con
la base de datos como garantía final.
