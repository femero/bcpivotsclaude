package com.bancatest.reconciliation.infrastructure.adapter.out.persistence;

import com.bancatest.reconciliation.domain.exception.DuplicateOperationException;
import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.out.ReconciliationRepository;
import com.bancatest.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationEntity;
import com.bancatest.reconciliation.infrastructure.adapter.out.persistence.mapper.ReconciliationPersistenceMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Panache-backed repository. It expects the caller to provide the transaction (see {@code
 * TransactionalExecutor}); the unique index on {@code operation_reference} is translated into a
 * {@link DuplicateOperationException}.
 */
@ApplicationScoped
public class PanacheReconciliationRepository
    implements ReconciliationRepository, PanacheRepositoryBase<ReconciliationEntity, UUID> {

  private final ReconciliationPersistenceMapper mapper;

  public PanacheReconciliationRepository(ReconciliationPersistenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(Reconciliation reconciliation) {
    var existing = findById(reconciliation.id().value());
    if (existing == null) {
      persist(mapper.toNewEntity(reconciliation));
    } else {
      mapper.copyMutableState(reconciliation, existing);
    }
    flushOrTranslateDuplicate(reconciliation);
  }

  @Override
  public Optional<Reconciliation> findByOperationReference(OperationReference reference) {
    return find("operationReference", reference.value())
        .firstResultOptional()
        .map(mapper::toDomain);
  }

  private void flushOrTranslateDuplicate(Reconciliation reconciliation) {
    try {
      flush();
    } catch (PersistenceException e) {
      if (e.getCause() instanceof ConstraintViolationException) {
        throw new DuplicateOperationException(
            "Reconciliation already exists for " + reconciliation.operationReference().value(), e);
      }
      throw e;
    }
  }
}
