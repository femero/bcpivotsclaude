# Cobertura de código con JaCoCo

Documentación de cómo se mide la cobertura en este proyecto. Fuentes consultadas con Context7:
guía oficial de Quarkus *Test coverage* (`/websites/quarkus_io_guides`, página `tests-with-coverage`
y `all-config`) y documentación de JaCoCo (`/websites/jacoco_jacoco_trunk_doc`, `check-mojo`).

## Configuración actual

| Dónde | Qué |
|---|---|
| `pom.xml` | Dependencia `io.quarkus:quarkus-jacoco` con `scope=test` (versión gestionada por el BOM de Quarkus) |
| `src/test/resources/application.properties` | `quarkus.jacoco.excludes=**/generated/**,**/avro/**` |

La extensión `quarkus-jacoco` funciona "out of the box": no requiere declarar `jacoco-maven-plugin`
para los tests `@QuarkusTest`.

## Uso

```bash
mvn clean verify     # ejecuta los tests y genera el reporte
```

Resultado (valores por defecto de la extensión):

- Reporte: `target/jacoco-report/` (abrir `index.html`)
- Datos de ejecución: `target/jacoco-quarkus.exec`

> La guía de Quarkus menciona `target/jacoco-reports` en prosa, pero la tabla de configuración
> oficial indica `target/jacoco-report` como valor por defecto de `quarkus.jacoco.report-location`.

## Propiedades `quarkus.jacoco.*`

| Propiedad | Defecto | Descripción |
|---|---|---|
| `enabled` | `true` | Activa/desactiva la extensión |
| `report` | `true` | Genera el reporte |
| `data-file` | `target/jacoco-quarkus.exec` | Archivo de datos de ejecución |
| `reuse-data-file` | `false` | `true` conserva el `.exec` entre ejecuciones; `false` lo borra |
| `report-location` | `target/jacoco-report` | Carpeta del reporte |
| `includes` | `**` | Clases incluidas (comodines `*` y `?`) |
| `excludes` | _(ninguna)_ | Clases excluidas del reporte |
| `title` / `footer` | `${quarkus.application.name}` / vacío | Textos del HTML |
| `source-encoding` / `output-encoding` | `UTF-8` | Codificaciones |

Cada propiedad tiene su variable de entorno equivalente (p. ej. `QUARKUS_JACOCO_EXCLUDES`). Aquí se
fijan en `src/test/resources/application.properties` porque solo aplican a tests; **no** se añaden
al `application.yaml` ni a la lista cerrada de 16 variables de entorno del servicio.

### Patrones de `includes` / `excludes`

- `**/fo/**/*` → todas las clases bajo `fo` y subpaquetes
- `**/bar/*` → clases directamente bajo `bar`
- `**/*BAR*.class` → clases cuyo nombre contenga `BAR`

El patrón actual excluye las clases generadas (Avro y JAX-RS de OpenAPI) para no distorsionar el
porcentaje.

## Limitación importante: tests JUnit puros

Según la guía de Quarkus, la configuración automática **solo cubre tests anotados con
`@QuarkusTest`**. Este proyecto tiene además tests de dominio y mappers en JUnit puro (sin
`@QuarkusTest`), cuya cobertura **no** aparece con la configuración actual.

Para incluirlos hay que añadir `jacoco-maven-plugin` (la guía lo describe así):

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>${jacoco.version}</version>
  <executions>
    <execution>
      <id>default-prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
      <configuration>
        <exclClassLoaders>*QuarkusClassLoader</exclClassLoaders>
        <destFile>${project.build.directory}/jacoco-quarkus.exec</destFile>
        <append>true</append>
      </configuration>
    </execution>
  </executions>
</plugin>
```

- `exclClassLoaders=*QuarkusClassLoader` evita medir dos veces las clases cargadas por Quarkus.
- `destFile` + `append=true` escriben en el mismo `.exec` que usa `quarkus-jacoco`, de modo que el
  reporte combinado incluye ambos tipos de test.
- Solo funciona si se ejecuta al menos un `@QuarkusTest`.
- Si en el futuro se define `argLine` en Surefire, usar evaluación tardía (`@{argLine}`) para no
  perder el agente de JaCoCo.

**Estado:** aplicado en el `pom.xml` con `jacoco.version=0.8.13` (propiedad en `<properties>`).
Surefire no define `argLine`, así que recoge el agente automáticamente. La versión 0.8.13 no se
pudo contrastar con Context7 (no la publica); `mvn clean verify` pasa con ella.

## Umbral mínimo opcional (`jacoco:check`)

Si se quisiera fallar el build por debajo de cierta cobertura, el goal `check` de
`jacoco-maven-plugin` admite reglas. Parámetros según la documentación oficial:

- `haltOnFailure` (defecto `true`, propiedad `jacoco.haltOnFailure`)
- `rules`: `element` (`BUNDLE`, `PACKAGE`, `CLASS`, `SOURCEFILE`, `METHOD`) con `limits` →
  `counter` (`INSTRUCTION`, `LINE`, `BRANCH`, `COMPLEXITY`, `METHOD`, `CLASS`), `value`
  (`TOTALCOUNT`, `COVEREDCOUNT`, `MISSEDCOUNT`, `COVEREDRATIO`, `MISSEDRATIO`), `minimum`/`maximum`
  (ratio 0.0–1.0 o porcentaje, `0.80` ≡ `80%`)

```xml
<rules>
  <rule>
    <element>BUNDLE</element>
    <limits>
      <limit>
        <counter>LINE</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.80</minimum>
      </limit>
    </limits>
  </rule>
</rules>
```

**Estado:** no aplicado. El proyecto no define hoy ningún umbral de cobertura.

## Pendiente de verificar

- `mvn clean verify` (86 tests, 0 fallos) genera el reporte con cobertura total de instrucciones
  del 90 % (173 de 1.748 instrucciones sin cubrir), incluyendo los paquetes de dominio y mappers.
- El comportamiento de `quarkus-jacoco` con `@RunOnVirtualThread` / `InMemoryChannels` no está
  verificado.
