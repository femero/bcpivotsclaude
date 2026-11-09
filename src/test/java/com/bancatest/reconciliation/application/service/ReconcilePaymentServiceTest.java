package com.bancatest.reconciliation.application.service;

import static com.bancatest.reconciliation.support.TestData.paymentCommand;
import static com.bancatest.reconciliation.support.TestData.pen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import com.bancatest.reconciliation.support.FakeProcessedOperationStore;
import com.bancatest.reconciliation.support.InMemoryReconciliationRepository;
import com.bancatest.reconciliation.support.PassThroughTransactionalExecutor;
import com.bancatest.reconciliation.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconcilePaymentServiceTest {

  private InMemoryReconciliationRepository repository;
  private FakeProcessedOperationStore store;
  private ReconcilePaymentService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryReconciliationRepository();
    store = new FakeProcessedOperationStore();
    service =
        new ReconcilePaymentService(
            repository,
            store,
            () -> new ReconciliationId(UUID.randomUUID()),
            new PassThroughTransactionalExecutor());
  }

  @Test
  void validPaymentCreatesOnePendingReconciliation() {
    service.reconcile(paymentCommand("OP-1", pen("100.00")));

    var saved = repository.findByOperationReference(new OperationReference("OP-1")).orElseThrow();
    assertEquals(ReconciliationStatus.PENDING, saved.status());
    assertEquals(pen("100.00"), saved.paymentAmount());
  }

  @Test
  void duplicateCaughtByTheFastStoreIsDiscardedAsSuccess() {
    service.reconcile(paymentCommand("OP-1", pen("100.00")));
    service.reconcile(paymentCommand("OP-1", pen("100.00")));

    assertEquals(1, repository.count());
  }

  @Test
  void duplicateThatBypassesTheStoreIsRejectedByTheUniqueIndexAndDiscarded() {
    service.reconcile(paymentCommand("OP-1", pen("100.00")));
    store.clear();

    service.reconcile(paymentCommand("OP-1", pen("100.00")));

    assertEquals(1, repository.count());
  }

  @Test
  void nonPenPaymentIsRejectedAndNothingIsCreated() {
    var usd = Amount.of("100.00", new Currency("USD"));

    assertThrows(
        InvalidAmountException.class, () -> service.reconcile(paymentCommand("OP-1", usd)));
    assertEquals(0, repository.count());
  }

  @Test
  void nothingIsPublishedForAPendingPayment() {
    // The service has no publisher port at all: creating a PENDING reconciliation cannot publish.
    service.reconcile(paymentCommand("OP-2", pen("5.00")));

    assertEquals(1, repository.count());
    assertEquals(
        TestData.PEN,
        repository
            .findByOperationReference(new OperationReference("OP-2"))
            .orElseThrow()
            .paymentAmount()
            .currency());
  }
}
