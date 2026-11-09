package com.bancatest.reconciliation.domain.port.out;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import java.util.Optional;

/** Persistence of the {@link Reconciliation} aggregate. */
public interface ReconciliationRepository {

  /**
   * Persists a new or modified reconciliation.
   *
   * @throws com.bancatest.reconciliation.domain.exception.DuplicateOperationException if another
   *     reconciliation already holds the same operation reference
   */
  void save(Reconciliation reconciliation);

  Optional<Reconciliation> findByOperationReference(OperationReference reference);
}
