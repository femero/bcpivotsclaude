# Evaluación teórica: Kafka (nivel principiante)

20 preguntas de selección múltiple con una única respuesta correcta. Las respuestas están en
[`evaluacion-kafka-respuestas.md`](evaluacion-kafka-respuestas.md).

---

**1. ¿Qué es Apache Kafka?**
a) Una base de datos relacional
b) Una plataforma distribuida para publicar, almacenar y consumir flujos de eventos
c) Un servidor web para APIs REST
d) Un sistema de archivos en red

**2. ¿Qué es un topic?**
a) Un servidor del clúster
b) Una consulta SQL guardada
c) Un tipo de mensaje cifrado
d) Una categoría con nombre donde se publican y se leen los mensajes

**3. ¿Qué hace un producer?**
a) Publica mensajes en un topic
b) Lee mensajes de un topic
c) Administra los permisos del clúster
d) Registra esquemas en el Schema Registry

**4. ¿Qué hace un consumer?**
a) Crea los topics del clúster
b) Lee mensajes de uno o más topics
c) Borra mensajes antiguos
d) Reparte las particiones entre brokers

**5. ¿Qué es un broker?**
a) Un servidor Kafka que almacena particiones y atiende a productores y consumidores
b) El cliente que publica mensajes
c) Un formato de serialización
d) Una librería de Java

**6. ¿Qué es una partición?**
a) Una copia de seguridad del topic en otro clúster
b) Un grupo de consumidores
c) Cada una de las divisiones ordenadas de un topic, sobre las que se escribe en secuencia
d) Un error de red

**7. ¿Qué garantiza Kafka sobre el orden de los mensajes?**
a) Orden global entre todas las particiones de un topic
b) Orden dentro de cada partición
c) Ningún orden en ningún caso
d) Orden alfabético por clave

**8. ¿Qué es el offset?**
a) La posición de un mensaje dentro de su partición
b) El tamaño máximo de un mensaje
c) El tiempo de vida de un topic
d) La dirección IP del broker

**9. ¿Para qué sirve la clave (key) de un mensaje?**
a) Para cifrar el contenido
b) Para identificar al consumidor
c) Para decidir la partición: mensajes con la misma clave van a la misma partición
d) Para fijar el tiempo de retención

**10. ¿Qué es un consumer group?**
a) Un conjunto de consumidores que se reparten las particiones de un topic para procesarlo en paralelo
b) Un conjunto de brokers
c) Una lista de topics relacionados
d) Un permiso de acceso

**11. En un mismo consumer group, ¿cuántos consumidores pueden leer a la vez una misma partición?**
a) Todos los del grupo
b) Cuantos se configuren
c) Ninguno
d) Uno solo

**12. ¿Qué ocurre cuando un consumidor confirma (commit) un offset?**
a) Se borra el mensaje del topic
b) Se registra hasta dónde ha leído, para continuar desde ahí después
c) El mensaje se envía otra vez
d) La partición se reasigna a otro broker

**13. ¿Se borra un mensaje de Kafka cuando un consumidor lo lee?**
a) Sí, siempre
b) Sí, si es el último consumidor
c) No: permanece hasta que vence la retención configurada
d) Sí, pero solo si es texto

**14. ¿Para qué sirve el factor de replicación?**
a) Para aumentar la velocidad de lectura de un solo consumidor
b) Para guardar copias de cada partición en varios brokers y tolerar fallos
c) Para limitar el número de productores
d) Para comprimir los mensajes

**15. ¿Qué es una dead-letter queue (DLQ)?**
a) Un topic al que se desvían los mensajes que no se pudieron procesar
b) El topic con más mensajes
c) Una partición dañada
d) Un tipo de consumer group

**16. ¿Qué es el Schema Registry?**
a) Un servicio que guarda y valida los esquemas (por ejemplo Avro) de los mensajes
b) Una base de datos de usuarios
c) Un plugin que crea topics
d) Un monitor de consumo

**17. ¿Qué ventaja ofrece Avro frente a enviar texto libre?**
a) Hace innecesario el broker
b) Elimina la necesidad de consumidores
c) Es el único formato que Kafka acepta
d) Un formato binario compacto con un esquema que define los campos y sus tipos

**18. ¿Qué es el consumer lag?**
a) La latencia de la red entre cliente y broker
b) La diferencia entre el último offset del topic y el offset que ha confirmado el consumidor
c) El tiempo que tarda un producer en arrancar
d) El número de particiones de un topic

**19. Un servicio recibe un pago con una referencia ya procesada y lo descarta sin error, de modo que reenviarlo produce un único efecto. ¿Cómo se llama esta propiedad?**
a) Idempotencia
b) Particionado
c) Replicación
d) Serialización

**20. En este proyecto, el servicio hace `ack` del mensaje al terminar y `nack` si lanza una excepción, y la estrategia `dead-letter-queue` envía los rechazados a un topic aparte. ¿Qué mensajes terminan en la DLQ?**
a) Todos los que se procesan correctamente
b) Los que ya existían en la base de datos
c) Los movimientos del core sin pago asociado
d) Los que fallan con una excepción, como un pago con moneda distinta de PEN
