package com.bancatest.reconciliation.domain.exception;

import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;

/**
 * Safety net for invalid status transitions. It is never used for flow control: callers check
 * {@code isResolved()} first, so the normal flow cannot trigger it.
 */
public class IllegalStateTransitionException extends RuntimeException {

  private final ReconciliationStatus source;
  private final ReconciliationStatus target;

  public IllegalStateTransitionException(ReconciliationStatus source, ReconciliationStatus target) {
    super("Illegal status transition: " + source + " -> " + target);
    this.source = source;
    this.target = target;
  }

  public ReconciliationStatus source() {
    return source;
  }

  public ReconciliationStatus target() {
    return target;
  }
}
