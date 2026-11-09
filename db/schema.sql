-- DDL for the payment reconciliation service. Executed manually by a person.
-- Hibernate never creates, validates or alters the schema.
CREATE TABLE reconciliations (
    id                  UUID           PRIMARY KEY,
    operation_reference VARCHAR(64)    NOT NULL,
    account_number      VARCHAR(34)    NOT NULL,
    payment_amount      NUMERIC(19, 2) NOT NULL,
    payment_currency    VARCHAR(3)     NOT NULL,
    core_amount         NUMERIC(19, 2),
    core_currency       VARCHAR(3),
    difference_amount   NUMERIC(19, 2),
    difference_currency VARCHAR(3),
    status              VARCHAR(16)    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    resolved_at         TIMESTAMPTZ
);

-- Real idempotency guarantee (constitution VIII); Redis only accelerates it.
CREATE UNIQUE INDEX uq_reconciliations_operation_reference
    ON reconciliations (operation_reference);
