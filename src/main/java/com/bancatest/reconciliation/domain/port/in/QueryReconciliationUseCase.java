package com.bancatest.reconciliation.domain.port.in;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import java.util.Optional;

/** Read-only lookup of a reconciliation by operation reference. */
public interface QueryReconciliationUseCase {

  Optional<Reconciliation> findByOperationReference(OperationReference reference);
}
