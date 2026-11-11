# Code style

- **Code in English** (classes, methods, messages, tests, comments); topics and columns keep business
  naming. Normative glossary in `docs/glossary.md` (e.g. `CoreTransaction`, never `BankTransaction`).
- **Modern Java 21**: records for VO/DTO/commands; amounts in `BigDecimal` with scale 2 and
  `HALF_UP`, compared with `compareTo`; `Instant` for dates; no `null` returned by the domain, no
  `java.util.Date`; `Optional` only as a return type. Lombok only on JPA entities (`@Getter`,
  `@Setter`, `@NoArgsConstructor(PROTECTED)`).
- Checkstyle (`severity=error`): complexity <= 10, methods <= 40 lines, <= 5 parameters, lines <= 140,
  no `*` imports and no unused imports.
- Verify with Context7 before using any library, property or annotation. If it cannot be verified,
  note it and report it; never guess.
- Do not over-extend: what is not in the spec/plan is asked about. No metrics, retries, outbox,
  authentication or extra endpoints.
