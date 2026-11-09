package com.bancatest.reconciliation.application.service;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.in.QueryReconciliationUseCase;
import com.bancatest.reconciliation.domain.port.out.ReconciliationRepository;
import java.util.Optional;

/** Looks up a reconciliation by operation reference. */
public class QueryReconciliationService implements QueryReconciliationUseCase {

  private final ReconciliationRepository repository;

  public QueryReconciliationService(ReconciliationRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Reconciliation> findByOperationReference(OperationReference reference) {
    return repository.findByOperationReference(reference);
  }
}
