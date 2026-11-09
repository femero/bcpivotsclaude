# Phase 0 — Research: Conciliación de Pagos

**Fecha**: 2026-11-09

## Estado de verificación — LEER PRIMERO

La constitución II exige verificar cada tecnología con Context7. **En la sesión que generó este
plan Context7 no estaba disponible** (ni como MCP ni como skill). Por decisión explícita del
responsable, la investigación se **hereda del proyecto hermano `ms-test`**, donde sí se verificó
con Context7, código fuente y documentación oficial.

Consecuencia: **ninguna decisión de este documento se re-verificó en esta sesión.** Cada una
queda marcada así:

- **HEREDADA**: verificada en `ms-test`; no re-verificada aquí.
- **CORREGIDA**: la versión de `ms-test` estaba desactualizada y se cambió (ver R-04).

**Pendiente P-00** (bloqueante para `/speckit-implement`): re-verificar con Context7 cada
decisión HEREDADA antes de escribir código, y fijar versiones exactas en el `pom.xml`. Las
versiones citadas (Quarkus 3.31.2, openapi-generator 7.24.0, pgjdbc >= 42.7.9) son las de
`ms-test` y pueden haber cambiado.

---

## R-01 — Desactivar la gestión de esquema de Hibernate · HEREDADA

**Decisión**: `quarkus.hibernate-orm.schema-management.strategy=none` y
`quarkus.hibernate-orm.sql-load-script=no-file`.

**Rationale**: `quarkus.hibernate-orm.database.generation` está deprecada desde Quarkus 3.22
(`forRemoval=true`) y remite a `schema-management.strategy`. El default ya es `none`, pero se
sobrescribe a `drop-and-create` si Dev Services está activo (R-02), por eso se fija
explícitamente. `validate` queda descartado: la constitución III prohíbe que Hibernate valide.

## R-02 — Desactivar Quarkus Dev Services · HEREDADA

**Decisión**: `quarkus.devservices.enabled=false`, global. Cumple la prohibición de levantar
contenedores (constitución III y XIII) y es lo que hace válido R-01.

## R-03 — Dead-letter-queue de los consumers · HEREDADA

**Decisión**: por canal de entrada, `failure-strategy=dead-letter-queue` y
`dead-letter-queue.topic` con nombre explícito.

**Rationale**: el default del topic es `dead-letter-topic-$channel`, nombre inexistente en
Confluent Cloud (no auto-crea topics). La estrategia commitea el offset del registro fallido y
lo escribe en la DLQ, que es lo que FR-022 pide. El conector añade las cabeceras
`dead-letter-reason`, `dead-letter-cause`, `dead-letter-topic`, `dead-letter-partition` y
`dead-letter-offset`. Descartado: `fail` (detiene el consumo, viola SC-007) e `ignore` (pierde
el mensaje, viola FR-022).

## R-04 — Filtro de idempotencia en Redis · CORREGIDA

**Decisión**: `RedisDataSource` bloqueante, `ds.value(String.class)` y
**`setGet(key, value, new SetArgs().nx().ex(ttlSeconds))`** sobre `dedup:{operationReference}`.
El método devuelve el valor **anterior**: `null` significa que esta llamada marcó la
referencia; no nulo significa que ya estaba marcada.

> **Corrección respecto a `ms-test`.** Su `research.md` (R-04/R-08) y su `data-model.md`
> decían `setAndChanged`. Su `CLAUDE.md` documenta después que `setAndChanged` existe en la
> rama principal de GitHub **pero no en la versión pinned**, y que `set(K, V, SetArgs)`
> devuelve `void` y no distingue inserción de colisión. Se adopta `setGet`. Verificar la firma
> **contra el jar de la versión fijada**, no contra el código fuente de GitHub (P-00).

**Rationale**: Redis es un filtro de rendimiento, no la garantía. La garantía es el `UNIQUE`
sobre `operation_reference` (R-05). Si Redis cae o la clave expiró, la base rechaza el duplicado
y el caso de uso lo trata como éxito. `setnx` + `expire` son dos viajes no atómicos; `setex` no
es condicional.

## R-05 — Claves primarias UUIDv7 · HEREDADA

**Decisión**: `com.fasterxml.uuid:java-uuid-generator`, `Generators.timeBasedEpochGenerator()`
como singleton (thread-safe). UUIDv7 evita la fragmentación de índice del v4 y permite que
`Reconciliation.start()` nazca con identidad antes de tocar la base. Se expone mediante el
puerto `ReconciliationIdProvider` para que el dominio no conozca la librería.

## R-06 — Serde Avro contra Confluent Schema Registry · HEREDADA

**Decisión**: `quarkus-confluent-registry-avro` más `quarkus-rest-client` (lo exige el serde) y
`io.confluent:kafka-avro-serializer` excluyendo `jakarta.ws.rs:jakarta.ws.rs-api`, con el
repositorio Maven `packages.confluent.io` declarado (el artefacto no está en Maven Central).

**Hallazgo decisivo**: en la implementación de Confluent el **auto-registro de esquemas está
activo por defecto** (`auto.register.schemas=true`). La constitución III lo prohíbe, así que se
fija explícitamente a `false`.

**Trampa de entorno**: tras un proxy con interceptación TLS, Maven falla con
`PKIX path building failed` aunque `curl` funcione (curl usa el almacén del SO; Maven, el del
JDK). Solución: `keytool -importcert -cacerts -alias <proxy-root> -file <cert>`.

## R-07 — Mappers con MapStruct · HEREDADA

**Decisión**: `@MapperConfig` compartido con
`componentModel = MappingConstants.ComponentModel.JAKARTA_CDI` y
`unmappedTargetPolicy = ReportingPolicy.ERROR`. `CDI` apunta al namespace `javax` y genera
beans que Quarkus no ve. Con `ERROR`, añadir un campo a un contrato o una columna a la entidad
rompe el build nombrándolo en vez de escribir un nulo silencioso. Los tests instancian
`XxxMapperImpl`.

## R-08 — Autenticación SASL del bróker · HEREDADA

**Decisión**: prefijo global `kafka.`, propagado a todos los canales.

```yaml
kafka:
  bootstrap.servers: ${KAFKA_BOOTSTRAP_SERVERS}
  security.protocol: SASL_SSL
  sasl.mechanism: PLAIN
  sasl.jaas.config: >-
    org.apache.kafka.common.security.plain.PlainLoginModule required
    username='${KAFKA_API_KEY}' password='${KAFKA_API_SECRET}';
```

El valor de `sasl.jaas.config` **debe terminar en punto y coma** y las credenciales van entre
comillas simples.

## R-09 — Autenticación del Schema Registry · HEREDADA

```yaml
kafka:
  schema.registry.url: ${SCHEMA_REGISTRY_URL}
  basic.auth.credentials.source: USER_INFO
  basic.auth.user.info: ${SCHEMA_REGISTRY_API_KEY}:${SCHEMA_REGISTRY_API_SECRET}
```

Las credenciales del Schema Registry son **distintas** de las del clúster (dos pares
independientes en Confluent Cloud); reutilizar las del bróker da 401. El nombre es
`basic.auth.user.info` (sin prefijo `schema.registry.`) cuando lo consume el serde directamente.
El fallo de esta autenticación reportado en quarkusio/quarkus#15448 ocurre solo en modo nativo;
el empaquetado es JVM.

## R-10 — Generación de código Avro · HEREDADA

**Decisión**: un solo generador, el de `quarkus-avro` (arrastrado por
`quarkus-confluent-registry-avro`). **No** se añade `avro-maven-plugin`: generaría las mismas
clases y el build fallaría con `duplicate class`.

```yaml
avro:
  codegen:
    enableDecimalLogicalType: true
    stringType: String
```

Prefijo `avro.codegen.*`, **sin** `quarkus.`. `enableDecimalLogicalType` vale `false` por
defecto: los campos `bytes` con `logicalType decimal` se generarían como `ByteBuffer` y se
rompería la precisión justo en la frontera Avro. `stringType=String` evita `CharSequence`. No
se usa el `logicalType uuid` en ningún esquema (AVRO-2548).

## R-11 — Pool JDBC y pinning de virtual threads · HEREDADA

**Decisión**: `org.postgresql:postgresql >= 42.7.9`. La 42.7.8 reintrodujo pinning de virtual
threads (síntoma: `VirtualThread$VThreadContinuation.onPinned` con `reason:MONITOR`); el
PR #3849 lo corrigió en 42.7.9. JEP 491 (fix en runtime) es de Java 24 y el proyecto está en
Java 21, así que la versión del driver es la única defensa.

Aplicar en orden: (1) `mvn dependency:tree -Dincludes=org.postgresql:postgresql`; (2) si el BOM
resuelve >= 42.7.9, no tocar nada; (3) si resuelve exactamente 42.7.8, fijar la última 42.7.x
en `<properties>`. Comprobar en ejecución con `-Djdk.tracePinnedThreads=full`.

Pool: `min-size: 5`, `max-size: 16`. Con virtual threads el techo real de concurrencia es
`max-size`, no el número de hilos. No configurar `max-lifetime` (interacción conocida con
`min-size` que puede vaciar el pool).

## R-12 — Generador OpenAPI y versión del contrato · HEREDADA

**Decisión**: `openapi-generator-maven-plugin` de OpenAPITools con `jaxrs-spec` y
`library=quarkus`, contrato en **OpenAPI 3.0.3**. Se descarta `quarkus-openapi-generator-server`
de Quarkiverse: sus dos generadores están en Preview y Experimental.

**3.0.3 a propósito**: ningún generador del ecosistema soporta 3.1 con garantías; bajo 3.1
openapi-generator deja de compartir tipos por `$ref` (OpenAPITools#20722) y generaría cuatro
clases para `MonetaryAmount`. El contrato no necesita capacidades de 3.1; las descripciones
junto a un `$ref` se expresan con `allOf` de un solo elemento.

**Aviso de constitución**: la prohibición de Apicurio (III) es sobre el registry y el serde de
Kafka. El "generador apicurio" de la extensión de Quarkiverse es otro proyecto; no aplica y
tampoco se usa.

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <version>7.24.0</version> <!-- P-00: confirmar la última 7.x estable -->
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
      <phase>generate-sources</phase>
      <configuration>
        <inputSpec>${project.basedir}/src/main/resources/openapi/reconciliation-api.yaml</inputSpec>
        <generatorName>jaxrs-spec</generatorName>
        <library>quarkus</library>
        <apiPackage>com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.api</apiPackage>
        <modelPackage>com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.model</modelPackage>
        <generateApiTests>false</generateApiTests>
        <generateModelTests>false</generateModelTests>
        <generateApiDocumentation>false</generateApiDocumentation>
        <generateModelDocumentation>false</generateModelDocumentation>
        <configOptions>
          <interfaceOnly>true</interfaceOnly>
          <useJakartaEe>true</useJakartaEe>
          <useSwaggerAnnotations>false</useSwaggerAnnotations>
          <dateLibrary>java8</dateLibrary>
          <returnResponse>false</returnResponse>
        </configOptions>
      </configuration>
    </execution>
  </executions>
</plugin>
```

| Opción | Default | Por qué se cambia |
| --- | --- | --- |
| `dateLibrary` | `legacy` | Genera `java.util.Date`, prohibido por la constitución X |
| `useSwaggerAnnotations` | `true` | Pone `@Schema`/`@Operation` en el código; contract-first lo prohíbe |
| `useJakartaEe` | `false` | Genera `javax.*`; Quarkus 3 es `jakarta.*` |

`quarkus.http.root-path=/v1` aporta el base path que la interfaz generada omite. El 404 con
`ProblemDetail` lo produce un `ExceptionMapper`, no un `Response` sin tipo.

## R-13 — Interceptación transaccional de ArC · HEREDADA

Quarkus ArC intercepta **por subclase**, no por proxy dinámico. `register()` puede llamar a su
propio método `@Transactional` y la transacción sí se abre, porque `this` ya es la subclase
generada. El método debe ser **no privado y no final** o la subclase no puede sobrescribirlo y
el interceptor desaparece sin aviso. No se "arregla" partiendo la clase.

## R-14 — Doble de pruebas sin infraestructura · HEREDADA

- `quarkus.datasource.active=false` y `quarkus.hibernate-orm.active=false` en
  `src/test/resources/application.properties` (desactivar, no apuntar a un host falso).
- Los cuatro canales se sustituyen por el `InMemoryConnector` mediante un
  `QuarkusTestResourceLifecycleManager` (`InMemoryChannels`); fijar solo la propiedad del
  conector no basta. Se inyecta `@Any InMemoryConnector` y se llama a `connector.source(...)`;
  inyectar `InMemorySource`/`InMemorySink` por tipo no funciona.
- `InMemoryReconciliationRepository` como `@Mock` alternativo. Su `save()` **exige una
  transacción activa** y falla si no la hay (el repositorio real muta una entidad gestionada y
  depende del flush al commit; sin transacción la escritura se perdería en silencio). `seed()`
  prepara estado para pruebas de lectura sin esa guarda.
- En `@QuarkusTest`, RestAssured ya aplica el root-path: pedir `/reconciliations/...`.
- El artefacto de test es `quarkus-junit` (no `quarkus-junit5`, relocalizado).

---

## Hallazgos para el responsable

1. **`ms-test/specs/001-payment-reconciliation/research.md` y `data-model.md` están
   desactualizados** respecto a su propio `CLAUDE.md`: siguen diciendo `setAndChanged`. No se
   tocó `ms-test`; se reporta.
2. **P-00 abierto**: re-verificar con Context7 todo lo marcado HEREDADA y fijar versiones.

---

## P-00 — Re-verificación (2026-11-09, Context7 + jars)

Cerrado salvo las líneas marcadas **pendiente**. Quarkus fijado en **3.31.2** (versión que Context7 documenta).

| Decisión | Resultado | Fuente |
| --- | --- | --- |
| R-01 `schema-management.strategy=none`, `sql-load-script=no-file` | Verificada | Context7 Quarkus 3.31.2 |
| R-02 `quarkus.devservices.enabled=false` | Verificada (propiedad global documentada) | Context7 |
| R-03 DLQ: estrategia, `dead-letter-queue.topic`, cabeceras `dead-letter-*`, offset commiteado | Verificada | Context7 |
| R-04 `setGet(K,V,SetArgs)` con `SetArgs.nx().ex(...)` | Verificada contra el jar `quarkus-redis-client-3.31.2` (`javap`); `setAndChanged` **no existe**. `SET NX GET` exige Redis >= 7.0 | jar |
| R-06 Confluent: `quarkus-confluent-registry-avro` + `quarkus-rest-client` + `kafka-avro-serializer` excluyendo `jakarta.ws.rs-api`, repo `packages.confluent.io`; `auto.register.schemas` default `true` | Verificada. Serializer 8.4.0 (resuelve Avro 1.12.1) | Context7 + Maven |
| R-07 MapStruct `JAKARTA_CDI`, `@MapperConfig.unmappedTargetPolicy` | Verificada (1.6.3) | Context7 |
| R-10 Avro codegen en `quarkus-avro`, sin plugin Maven | Verificada. Nombres exactos `avro.codegen.*` (`enableDecimalLogicalType`, `stringType`): **pendiente**, se comprueban al primer build | Context7 |
| R-11 pgjdbc | El BOM de Quarkus 3.31.2 resuelve **42.7.9** (>= mínimo). No se sobrescribe | `mvn dependency:tree` |
| R-12 openapi-generator `jaxrs-spec`/`quarkus`, `useJakartaEe`, `useSwaggerAnnotations`, `dateLibrary=java8`, `returnResponse`, `interfaceOnly` | Verificada. Plugin **7.27.0** | Context7 |
| R-13 (ya no aplica) | La frontera transaccional pasa a `TransactionalExecutor` | plan.md |
| R-14 `InMemoryConnector.switchIncomingChannelsToInMemory/OutgoingChannelsToInMemory` + `clear()`; `@RunOnVirtualThread` en métodos de mensajería | Verificada | Context7 |
| `QuarkusTransaction.requiringNew().call(...)` (`io.quarkus.narayana.jta`) | Verificada | Context7 |

**Correcciones al plan**
- La extensión Kafka se llama hoy **`quarkus-messaging-kafka`** (no `quarkus-smallrye-reactive-messaging-kafka`).
- Con `quarkus-config-yaml`, Context7 recomienda no tener `application.properties`; si coexisten, gana el YAML. Para los tests se usa `src/test/resources/application.yaml` en lugar de `.properties` (**a confirmar en el primer test**).

**Versiones fijadas**: Quarkus 3.31.2, openapi-generator 7.27.0, MapStruct 1.6.3, Lombok 1.18.48, java-uuid-generator 5.2.0, kafka-avro-serializer 8.4.0, maven-checkstyle-plugin 3.6.0 + Checkstyle 14.4.0, spotless-maven-plugin 3.11.0, awaitility 4.3.0.
