package com.bancatest.reconciliation.domain.port.in;

import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import java.time.Instant;

/** Core transaction that resolves a reconciliation. It is not persisted as an entity. */
public record RegisterCoreTransactionCommand(
    OperationReference operationReference, Amount amount, Instant occurredAt) {}
