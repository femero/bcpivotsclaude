package com.bancatest.reconciliation.support;

import com.bancatest.reconciliation.domain.exception.DuplicateOperationException;
import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.out.ReconciliationRepository;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test double replacing the Panache repository; simulates the unique index. */
@Mock
@ApplicationScoped
public class InMemoryReconciliationRepository implements ReconciliationRepository {

  private final Map<String, Reconciliation> store = new ConcurrentHashMap<>();

  @Override
  public void save(Reconciliation reconciliation) {
    var existing = store.get(reconciliation.operationReference().value());
    if (existing != null && !existing.id().equals(reconciliation.id())) {
      throw new DuplicateOperationException(
          "duplicate " + reconciliation.operationReference(), null);
    }
    store.put(reconciliation.operationReference().value(), reconciliation);
  }

  @Override
  public Optional<Reconciliation> findByOperationReference(OperationReference reference) {
    return Optional.ofNullable(store.get(reference.value()));
  }

  /** Prepares state for read tests. */
  public void seed(Reconciliation reconciliation) {
    store.put(reconciliation.operationReference().value(), reconciliation);
  }

  public int count() {
    return store.size();
  }

  public void clear() {
    store.clear();
  }
}
