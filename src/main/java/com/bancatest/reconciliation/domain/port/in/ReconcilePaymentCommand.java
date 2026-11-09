package com.bancatest.reconciliation.domain.port.in;

import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import java.time.Instant;

/**
 * Reported payment to reconcile. The event id is deliberately absent: it never drives idempotency.
 */
public record ReconcilePaymentCommand(
    OperationReference operationReference,
    AccountNumber accountNumber,
    Amount amount,
    Instant occurredAt) {}
