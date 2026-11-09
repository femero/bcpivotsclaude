package com.bancatest.reconciliation.domain.port.out;

import com.bancatest.reconciliation.domain.model.Reconciliation;

/** Publishes the outcome of a resolved reconciliation, at most once, after its commit. */
public interface ReconciliationEventPublisher {

  void publishMatched(Reconciliation reconciliation);

  void publishMismatched(Reconciliation reconciliation);
}
