# Architecture

Base package `com.bancatest.reconciliation`; mandatory structure:

```
domain/{model,model/vo,port/in,port/out,exception}
application/service
infrastructure/{adapter/in/{kafka,rest},adapter/out/{persistence,redis,messaging},config}
```

- `domain` imports no frameworks; `application` depends only on `domain`. Enforced by the Checkstyle
  ImportControl (`config/checkstyle/import-control.xml`); if a legitimate import fails, fix the
  design, do not relax the rule.
- `application` services are plain classes **without annotations**; `infrastructure/config/
  ApplicationBeans` exposes them as beans. The transaction lives in the `TransactionalExecutor`
  port (implemented with `QuarkusTransaction`), not in `@Transactional`.
- A single aggregate root: `Reconciliation` (private constructor, `start()`/`reconstitute()`, no
  setters). The JPA entity is a separate class with an explicit mapper. No Panache Active Record.
- Write flow: validate -> Redis filter (`setGet` NX EX) -> save inside `inTransaction` -> **publish
  after commit**. `isResolved()` is checked before any transition; `IllegalStateTransitionException`
  is a safety net, not flow control.
- Idempotency only by `operationReference`, never by event id. The real guarantee is the `UNIQUE`
  index (`db/schema.sql`); Redis only speeds things up. A duplicate is treated as success.
- Consumers (`@Incoming` + `@RunOnVirtualThread`) carry `@Blocking("<worker>")` with
  `smallrye.messaging.worker.<worker>.max-concurrency: 1`, and `OperationLocks` excludes across
  channels; they ack on return and nack on exception; the `dead-letter-queue` strategy routes to the
  DLQ topic of their channel.
