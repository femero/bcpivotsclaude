# Configuration and contracts

- **YAML without dotted keys**: in `application.yaml` write `dead-letter-queue: {topic: ...}` nested,
  never `dead-letter-queue.topic:`. A dotted key is silently ignored and the DLQ falls back to the
  default name. Schema Registry properties go under `mp.messaging.connector.smallrye-kafka`.
- Configuration lives in `application.yaml`; environment variables are `${NAME}` **without default**.
  Closed list of 16 (see `.env.example`); do not add any. No literal secret, URL or token.
- Infrastructure is managed by the team: FORBIDDEN Flyway, docker-compose (except local, constitution
  III v1.2.0), Kubernetes, Terraform, topic scripts, active Dev Services, Apicurio. Hibernate never
  creates or validates the schema (`schema-management.strategy: none`).
- The local compose may include an optional Redpanda Console (inspection only, exact image version,
  no application variables or credentials). It never creates topics or registers schemas.
- Contracts are contract-first, never derived from code: `src/main/avro/*.avsc` and
  `src/main/resources/openapi/reconciliation-api.yaml`. Generated classes live in `target/` and are
  not versioned. There is no `avro-maven-plugin`. New Avro fields always with `default`.
