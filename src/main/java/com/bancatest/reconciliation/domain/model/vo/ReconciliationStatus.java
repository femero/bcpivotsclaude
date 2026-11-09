package com.bancatest.reconciliation.domain.model.vo;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a reconciliation. Only PENDING -> MATCHED and PENDING -> MISMATCHED are valid;
 * MATCHED and MISMATCHED are terminal. Persisted with {@code name()}, never {@code ordinal()}.
 */
public enum ReconciliationStatus {
  PENDING,
  MATCHED,
  MISMATCHED;

  private static final Map<ReconciliationStatus, Set<ReconciliationStatus>> TRANSITIONS =
      Map.of(
          PENDING, Set.of(MATCHED, MISMATCHED),
          MATCHED, Set.of(),
          MISMATCHED, Set.of());

  public boolean canTransitionTo(ReconciliationStatus target) {
    return TRANSITIONS.get(this).contains(target);
  }

  public boolean isTerminal() {
    return TRANSITIONS.get(this).isEmpty();
  }
}
