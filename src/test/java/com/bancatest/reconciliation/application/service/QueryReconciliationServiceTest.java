package com.bancatest.reconciliation.application.service;

import static com.bancatest.reconciliation.support.TestData.pending;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.support.InMemoryReconciliationRepository;
import org.junit.jupiter.api.Test;

class QueryReconciliationServiceTest {

  @Test
  void returnsTheReconciliationWhenItExists() {
    var repository = new InMemoryReconciliationRepository();
    repository.seed(pending("OP-1", "100.00"));
    var service = new QueryReconciliationService(repository);

    var found = service.findByOperationReference(new OperationReference("OP-1"));

    assertEquals("OP-1", found.orElseThrow().operationReference().value());
  }

  @Test
  void returnsEmptyWhenItDoesNotExist() {
    var service = new QueryReconciliationService(new InMemoryReconciliationRepository());

    assertTrue(service.findByOperationReference(new OperationReference("OP-404")).isEmpty());
  }
}
