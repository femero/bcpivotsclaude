package com.bancatest.reconciliation.domain.port.out;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;

/**
 * Fast pre-filter for duplicate operations. It only accelerates idempotency: the database unique
 * index is the real guarantee.
 */
public interface ProcessedOperationStore {

  /** Marks the reference as processed; returns {@code false} if it was already marked. */
  boolean markAsProcessed(OperationReference reference);
}
