# Testing

- Domain and mappers: plain JUnit, no `@QuarkusTest`.
- Integration: `@QuarkusTest` + `@QuarkusTestResource(InMemoryChannels.class)`; the doubles live in
  `src/test/java/.../support` (`@Mock`: repository, Redis store, transactional executor).
  `src/test/resources/application.properties` disables datasource/Hibernate and gives inert values
  to the 16 variables.
- Every new consumer ships an idempotency test (same event twice -> a single effect), a DLQ with an
  explicit name, and its topic listed in the `README`. With `InMemoryConnector` the DLQ does not
  run: only the `nack` is verified.
- The suite runs **without** a database, Redis or broker. `mvn verify` never modifies files.
