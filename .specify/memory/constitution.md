# Constitución del Microservicio de Conciliación de Pagos

Microservicio de conciliación de pagos en Quarkus. Es una DEMO técnica, no un sistema
productivo. Estos principios buscan un diseño correcto y legible, no cobertura operativa
completa.

## Core Principles

### I. No Sobreextenderse

Se implementa lo que el spec pide y nada más. PROHIBIDO agregar funcionalidad, capas,
abstracciones o configuraciones no solicitadas.

Ante la duda entre una solución simple y una sofisticada, se elige la simple.

Si una decisión no está en el spec ni en el plan, se PREGUNTA antes de implementarla; nunca
se asume.

**Rationale**: en una demo, cada capa extra es superficie de error sin valor demostrativo.
El alcance lo fija el spec, no la iniciativa del implementador.

### II. Documentación Verificada Antes de Escribir Código

Antes de usar cualquier tecnología, librería, extensión, anotación o propiedad de
configuración, se DEBE consultar su documentación vigente con Context7 (instalado como skill
y como MCP). Sin excepciones y sin que haga falta pedirlo en cada prompt.

PROHIBIDO escribir configuración o llamadas a API desde el conocimiento entrenado del
agente: una propiedad inventada falla en arranque o se ignora en silencio.

Si Context7 no cubre el caso, se consulta la documentación oficial del proyecto. Si no se
puede verificar por ninguna vía, se anota como pendiente de verificación y se REPORTA. Nunca
se adivina.

Aplica a: Quarkus y sus extensiones, Hibernate ORM y Panache, SmallRye Reactive Messaging,
Avro y su plugin Maven, serde Avro de Confluent Schema Registry, cliente Redis, MapStruct,
openapi-generator, Checkstyle, Spotless y JUnit.

### III. Infraestructura Gestionada por el Equipo

La infraestructura ya existe y la configura una persona. El agente NO la provisiona, NO la
configura y NO genera scripts para crearla. La única excepción es el `docker-compose.yml` de
desarrollo local descrito más abajo en este principio.

- **Kafka y Schema Registry**: Confluent Cloud, topics ya creados. El código solo referencia
  nombres de topic; nunca declara particiones, réplicas ni retención.
- Solo serde Avro de Confluent. PROHIBIDO Apicurio u otro registry. El auto-registro de
  esquemas está desactivado: el equipo registra los esquemas.
- **Base de datos**: PostgreSQL en Supabase, ya creada. Conexión directa o pooler en modo
  sesión, nunca en modo transacción.
- **Redis**: instancia local, ya configurada.
- PROHIBIDO Flyway y cualquier herramienta de migración. El DDL se entrega en
  `db/schema.sql` para que una persona lo ejecute. Hibernate nunca crea, valida ni altera el
  esquema.
- PROHIBIDO Kubernetes, Terraform y scripts de creación de topics. Quarkus Dev Services
  desactivado: la aplicación y sus tests nunca levantan contenedores.
- **Entorno local con Docker Compose (excepción acotada).** El repositorio PUEDE contener UN
  `docker-compose.yml` en la raíz, solo para desarrollo local, que levanta PostgreSQL,
  Redis (>= 7.0), Kafka y Schema Registry de Confluent. Reglas:
  - No sustituye a la infraestructura del equipo (Confluent Cloud, Supabase, Redis del equipo),
    que sigue siendo la fuente para cualquier entorno compartido.
  - No crea topics ni registra esquemas: siguen prohibidos los scripts de creación de topics y el
    auto-registro de esquemas. Una persona crea los topics y registra los esquemas a mano.
  - No aplica `db/schema.sql` ni usa Flyway, `initdb` u otro mecanismo automático desde el
    repositorio: una persona ejecuta el DDL.
  - No contiene credenciales reales ni variables fuera de la lista cerrada de dieciséis. Los
    valores locales viven en `.env` (ignorado); `.env.example` solo lleva los nombres.
  - Es un apoyo para ejecutar el servicio; la suite de tests sigue sin depender de él (XIII).
- Responsabilidad del agente: declarar las variables en `.env.example` y listar en el
  `README` las variables que nombran los topics (DLQ incluidos) con su rol.

### IV. Configuración y Secretos

`application.yaml` con la extensión `quarkus-config-yaml`.

Todo dato de conexión y todo nombre de topic es variable de entorno en
`MAYUSCULAS_CON_GUION_BAJO`, con sintaxis `${NOMBRE}` sin valor por defecto: si falta, la
aplicación falla al arrancar. Degradar un nombre de topic a un default sería peor que fallar:
la aplicación consumiría o publicaría en un topic equivocado, o en uno inexistente que
Confluent Cloud no auto-crea, y el fallo aparecería como silencio en vez de como error.

PROHIBIDA cualquier credencial, URL de conexión o token literal en el repositorio.
`.env.example` lleva los nombres con valores vacíos. El `.env` real está en `.gitignore`.

La lista cerrada de variables se define en *Restricciones Técnicas y Artefactos del
Repositorio*.

Configuración de negocio con `@ConfigMapping` en una interfaz tipada que vive en
`infrastructure`. Contiene solo la tolerancia (monto fijo con moneda) y el TTL del filtro de
idempotencia en Redis. El valor de la tolerancia lo define el spec. La interfaz nunca llega
al dominio: sus valores entran convertidos en Value Objects.

### V. Arquitectura Hexagonal

El dominio no conoce frameworks. Ninguna clase en `domain` importa `jakarta.persistence`,
`io.quarkus`, `io.smallrye`, `org.apache.kafka`, `org.apache.avro` ni `infrastructure`.
`application` solo depende de `domain`.

Estructura de paquetes obligatoria:

```
domain/model
domain/model/vo
domain/port/in
domain/port/out
application/service
infrastructure/adapter/in
infrastructure/adapter/out
```

La regla se hace cumplir con el ImportControl de Checkstyle en el build (ver XII).

### VI. Dependency Inversion

El dominio define los puertos como interfaces; la infraestructura los implementa. Las
dependencias apuntan siempre hacia el dominio. Los casos de uso reciben puertos, no
implementaciones concretas.

### VII. Modelado Táctico DDD

**Aggregate Root**

- Único punto de entrada; nada externo referencia sus entidades internas.
- Constructor privado y sin setters públicos. El estado cambia con métodos de negocio con
  nombre de intención.
- Factories estáticos: `start()` para uno nuevo (valida invariantes) y `reconstitute()` para
  rehidratar (solo lo usa el mapper de infraestructura).

**Máquina de estados**

- El enum de estado declara qué transiciones son válidas desde cada estado.
- Una transición inválida lanza `IllegalStateTransitionException`; jamás se ignora en
  silencio.
- El estado se persiste como `String`, nunca como ordinal. Los estados concretos los define
  el spec.

**Value Objects**

- Todo concepto sin identidad propia es un `record` inmutable que valida en su construcción:
  si es inválido, no existe.
- PROHIBIDO `String` o `BigDecimal` crudos en firmas del dominio.
- Montos en `BigDecimal`, jamás `double` ni `float`, siempre con moneda, escala 2 y
  `RoundingMode` explícito. Se comparan con `compareTo`, nunca con `equals`. Comparar monedas
  distintas lanza una excepción de dominio.

**Entidades y repositorios**

- Una entidad del dominio NUNCA es una entidad JPA. Mapper explícito entre ambas.
- Un repositorio por aggregate root, declarado como puerto de salida.
- PROHIBIDO Panache Active Record. Se usa `PanacheRepository` sobre entidades JPA en
  `infrastructure`.

### VIII. Mensajería

- Todo consumer es idempotente por referencia de operación, nunca por id de evento.
- La garantía es una restricción `UNIQUE` en base de datos. Redis es solo un filtro rápido
  previo con TTL. Un duplicado rechazado se trata como procesado con éxito, no como error.
- Todo consumer usa `dead-letter-queue` como `failure-strategy`, con el nombre del topic de
  DLQ declarado explícitamente. PROHIBIDO el nombre por defecto del framework.
- Offsets commiteados después de procesar, nunca antes.
- Contratos Avro: los campos nuevos siempre con `default`. El esquema Avro no es el modelo de
  dominio: el adaptador de entrada lo traduce.
- La publicación a Kafka ocurre después de confirmar la transacción de base de datos. Sin
  outbox: el riesgo de perder el evento se documenta en el `README`.

### IX. Concurrencia

- Modelo bloqueante con virtual threads, no reactivo.
- `@RunOnVirtualThread` en los consumers Kafka y en los endpoints REST.
- PROHIBIDO mezclar Mutiny o Panache reactive con virtual threads.
- PROHIBIDO `synchronized` en secciones con I/O: usar `ReentrantLock`.

### X. Java 21 Moderno

- `record` para Value Objects, DTOs y comandos.
- `sealed interface` con pattern matching for switch, sin `default` sobre un tipo sealed.
  PROHIBIDO `instanceof` seguido de cast. `var` donde el tipo es evidente.
- `Optional` solo como retorno de puertos. PROHIBIDO como parámetro o campo, y PROHIBIDO
  `isPresent()` seguido de `get()`.
- PROHIBIDOS: `null` como retorno del dominio, `java.util.Date`, colecciones mutables
  expuestas desde el agregado y excepciones checked en el dominio. `Instant` para fechas y
  horas.
- Lombok PROHIBIDO en `domain` y `application`. Solo en entidades JPA, y solo `@Getter`,
  `@Setter` y `@NoArgsConstructor(access = PROTECTED)`. PROHIBIDOS `@Data`, `@Builder` y
  `@EqualsAndHashCode`.

### XI. Idioma

- TODO el código fuente en inglés: clases, métodos, variables, paquetes, mensajes de
  excepción, tests y comentarios.
- Los topics de Kafka y las columnas de base de datos mantienen la nomenclatura de negocio.
- `docs/glossary.md` con la equivalencia normativa de *Restricciones Técnicas y Artefactos
  del Repositorio*.
- Javadoc en clases del dominio y puertos: invariantes, validaciones y contrato. PROHIBIDO el
  Javadoc que solo repite el nombre del método.

### XII. Calidad

- Checkstyle en `mvn verify` con `severity=error`. Solo reglas semánticas, nunca formato:
  - complejidad ciclomática máx. 10
  - métodos máx. 40 líneas
  - parámetros máx. 5
  - línea máx. 140
  - imports sin estrella ni sin usar
  - control de imports del Principio V
- Spotless para el formato: `spotless:apply` en local, `spotless:check` en `mvn verify`. El
  build nunca modifica archivos.
- Sin umbral de cobertura: los tests que existan deben ser significativos.

### XIII. Pruebas

- Dominio con JUnit puro, sin `@QuarkusTest`.
- Test de la matriz completa de transiciones de estado.
- Test de validación en construcción de cada Value Object.
- Test del monto: `120.0` y `120.00` son iguales; monedas distintas fallan.
- Test de idempotencia por consumer: el mismo evento dos veces produce un solo efecto.
- La suite completa corre SIN base de datos, Redis ni broker: `InMemoryConnector` de
  SmallRye, datasource y hibernate-orm desactivados, dobles de repositorio. Sin contenedores.

## Restricciones Técnicas y Artefactos del Repositorio

**Variables de entorno — lista cerrada.** Estas dieciséis y ninguna más:

*Conexión y credenciales*

```
DB_URL, DB_USERNAME, DB_PASSWORD
REDIS_HOSTS
KAFKA_BOOTSTRAP_SERVERS, KAFKA_API_KEY, KAFKA_API_SECRET
SCHEMA_REGISTRY_URL, SCHEMA_REGISTRY_API_KEY, SCHEMA_REGISTRY_API_SECRET
```

*Nombres de topic*

```
KAFKA_TOPIC_PAYMENTS_IN, KAFKA_TOPIC_PAYMENTS_DLQ
KAFKA_TOPIC_CORE_TRANSACTIONS_IN, KAFKA_TOPIC_CORE_TRANSACTIONS_DLQ
KAFKA_TOPIC_RESULTS_OUT, KAFKA_TOPIC_DISCREPANCIES_OUT
```

Las de topic no son secretos, pero siguen la misma regla de `${NOMBRE}` sin default. Que
estén en `.env.example` con valor vacío es deliberado: obliga a rellenarlas con los nombres
reales que el equipo haya creado.

**Glosario de negocio obligatorio** (`docs/glossary.md`):

| Término de negocio (es) | Clase (en)         |
| ----------------------- | ------------------ |
| Conciliación            | Reconciliation     |
| Pago reportado          | ReportedPayment    |
| Movimiento del core     | CoreTransaction    |
| Referencia de operación | OperationReference |
| Monto                   | Amount             |
| Moneda                  | Currency           |
| Diferencia              | AmountDifference   |
| Tolerancia              | Tolerance          |

**Artefactos que el repositorio DEBE contener:**

| Artefacto          | Contenido                                                                              |
| ------------------ | -------------------------------------------------------------------------------------- |
| `.env.example`     | Los nombres de la lista cerrada, con valores vacíos                                    |
| `db/schema.sql`    | El DDL completo, incluida la restricción `UNIQUE` sobre la referencia de operación     |
| `docs/glossary.md` | La tabla de equivalencias anterior                                                     |
| `README`           | Las variables que nombran los topics usados, DLQ incluidos, con su rol, y la ausencia de outbox con su riesgo |

**Artefactos que el repositorio PUEDE contener:**

| Artefacto            | Condiciones                                                                         |
| -------------------- | ----------------------------------------------------------------------------------- |
| `docker-compose.yml` | Uno solo, en la raíz, solo para desarrollo local, con PostgreSQL, Redis >= 7.0, Kafka y Schema Registry de Confluent. Sujeto a las reglas del Principio III |

**Artefactos y dependencias PROHIBIDOS:** Apicurio y cualquier registry o serde que no sea el
de Confluent, cualquier `docker-compose` distinto del único permitido por el Principio III,
manifiestos de Kubernetes, Terraform, scripts de creación de topics o de registro de esquemas,
migraciones Flyway, Quarkus Dev Services activo y cualquier archivo con una credencial, URL de
conexión o token literal.

## Flujo de Trabajo y Puertas de Calidad

**Antes de escribir código**, en este orden:

1. Confirmar que lo pedido está en el spec o en el plan. Si no está, PREGUNTAR (Principio I).
2. Consultar Context7 para cada tecnología, propiedad o anotación implicada (Principio II).
3. Si Context7 no cubre el caso, consultar la documentación oficial del proyecto.
4. Si sigue sin poder verificarse, anotarlo como pendiente de verificación y reportarlo.
   Nunca se adivina.

**Puerta de build.** `mvn verify` es la puerta única y es bloqueante: `spotless:check` falla
si el formato no está aplicado y Checkstyle falla con `severity=error`, incluido el control de
imports por paquete. El build nunca modifica archivos: `spotless:apply` es una acción local
previa.

**Revisión de cambios.** Toda revisión verifica, como mínimo:

- `domain` no importa ningún paquete de framework y `application` no importa
  `infrastructure` (Principio V).
- Ninguna credencial literal ni variable fuera de la lista cerrada (Principio IV).
- No se añadió infraestructura, migraciones, Dev Services ni funcionalidad no solicitada
  (Principios I, III).
- Los consumers nuevos traen su test de idempotencia, su DLQ con nombre explícito y su topic
  listado en el `README` (Principios III, VIII, XIII).

## Governance

Esta constitución prevalece sobre cualquier otra práctica, convención o preferencia. Ante un
conflicto entre esta constitución y un spec, un plan o una sugerencia del agente, prevalece
la constitución y el conflicto se reporta explícitamente.

**Procedimiento de enmienda.** Una enmienda requiere: (a) la propuesta escrita del cambio y
su motivo, (b) aprobación explícita del responsable del proyecto, y (c) la actualización de
esta constitución mediante `/speckit-constitution`, con su Sync Impact Report. El agente no
enmienda esta constitución por iniciativa propia.

**Política de versionado.** Versionado semántico sobre el documento:

- **MAJOR**: se elimina o redefine un principio de forma incompatible con el trabajo previo.
- **MINOR**: se añade un principio o una sección, o se amplía materialmente una guía.
- **PATCH**: aclaraciones, redacción, erratas y refinamientos sin cambio semántico.

**Revisión de cumplimiento.** El cumplimiento se verifica en cada revisión de cambios según
la lista de *Flujo de Trabajo y Puertas de Calidad*. Una desviación no se implementa "y se
documenta después": se plantea antes y se resuelve con una enmienda o con un cambio de diseño
que cumpla.

**Guía en tiempo de desarrollo.** Los specs y planes generados por Spec Kit bajo `specs/` son
la fuente de alcance; esta constitución es la fuente de restricciones.

**Version**: 1.1.0 | **Ratified**: 2026-11-09 | **Last Amended**: 2026-11-09
