package com.bancatest.reconciliation.domain.model;

import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.AmountDifference;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import java.time.Instant;

/**
 * Raw state used to rehydrate a {@link Reconciliation}. It is the only place of the domain where
 * nulls are tolerated, because it mirrors nullable columns ({@code coreAmount}, {@code difference}
 * and {@code resolvedAt} are null while the reconciliation is PENDING).
 */
public record ReconciliationSnapshot(
    ReconciliationId id,
    OperationReference operationReference,
    AccountNumber accountNumber,
    Amount paymentAmount,
    Amount coreAmount,
    AmountDifference difference,
    ReconciliationStatus status,
    Instant createdAt,
    Instant resolvedAt) {}
