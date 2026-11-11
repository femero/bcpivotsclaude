---
description: Checklist for adding a new Kafka consumer
argument-hint: <channel-name>
---

Add the consumer for channel `$ARGUMENTS` following `.claude/rules/architecture.md` and
`.claude/rules/testing.md`:

- `@Incoming` + `@RunOnVirtualThread` + `@Blocking("<worker>")`, with
  `smallrye.messaging.worker.<worker>.max-concurrency: 1`.
- Ack on return, nack on exception; `dead-letter-queue` with an explicit name.
- Idempotency test (same event twice -> a single effect).
- List the DLQ topic in the `README`.
