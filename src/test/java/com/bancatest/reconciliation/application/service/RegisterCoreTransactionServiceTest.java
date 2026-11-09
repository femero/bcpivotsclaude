package com.bancatest.reconciliation.application.service;

import static com.bancatest.reconciliation.support.TestData.coreCommand;
import static com.bancatest.reconciliation.support.TestData.pen;
import static com.bancatest.reconciliation.support.TestData.pending;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import com.bancatest.reconciliation.support.InMemoryReconciliationRepository;
import com.bancatest.reconciliation.support.PassThroughTransactionalExecutor;
import com.bancatest.reconciliation.support.RecordingEventPublisher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegisterCoreTransactionServiceTest {

  private InMemoryReconciliationRepository repository;
  private RecordingEventPublisher publisher;
  private RegisterCoreTransactionService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryReconciliationRepository();
    publisher = new RecordingEventPublisher();
    service =
        new RegisterCoreTransactionService(
            repository, publisher, () -> pen("1.00"), new PassThroughTransactionalExecutor());
  }

  private com.bancatest.reconciliation.domain.model.Reconciliation stored(String reference) {
    return repository.findByOperationReference(new OperationReference(reference)).orElseThrow();
  }

  @ParameterizedTest
  @CsvSource({
    "100.00, MATCHED, 0.00, matched",
    "99.50, MATCHED, 0.50, matched",
    "99.00, MATCHED, 1.00, matched",
    "98.99, MISMATCHED, 1.01, mismatched",
    "95.00, MISMATCHED, 5.00, mismatched",
    "100.50, MISMATCHED, -0.50, mismatched"
  })
  void classifiesPublishesAndRecordsTheDifference(
      String core, ReconciliationStatus expectedStatus, String difference, String channel) {
    repository.save(pending("OP-1", "100.00"));

    service.register(coreCommand("OP-1", pen(core)));

    var resolved = stored("OP-1");
    assertEquals(expectedStatus, resolved.status());
    assertEquals(difference, resolved.difference().orElseThrow().value().toPlainString());
    assertEquals(List.of(channel + ":OP-1"), publisher.events());
  }

  @Test
  void coreTransactionOverAResolvedReconciliationIsDiscardedWithoutEffect() {
    repository.save(pending("OP-1", "100.00"));
    service.register(coreCommand("OP-1", pen("100.00")));
    publisher.events().clear();

    service.register(coreCommand("OP-1", pen("50.00")));

    assertEquals(ReconciliationStatus.MATCHED, stored("OP-1").status());
    assertTrue(publisher.events().isEmpty());
  }

  @Test
  void orphanCoreTransactionCreatesAndPublishesNothing() {
    service.register(coreCommand("OP-9", pen("100.00")));

    assertEquals(0, repository.count());
    assertTrue(publisher.events().isEmpty());
  }

  @Test
  void nonPenCoreTransactionIsRejected() {
    repository.save(pending("OP-1", "100.00"));
    var usd = Amount.of("100.00", new Currency("USD"));

    assertThrows(InvalidAmountException.class, () -> service.register(coreCommand("OP-1", usd)));
    assertEquals(ReconciliationStatus.PENDING, stored("OP-1").status());
  }

  @Test
  void publicationFailureKeepsTheReconciliationResolvedAndDoesNotPropagate() {
    repository.save(pending("OP-1", "100.00"));
    publisher.failOnPublish();

    service.register(coreCommand("OP-1", pen("100.00")));

    assertEquals(ReconciliationStatus.MATCHED, stored("OP-1").status());
  }
}
