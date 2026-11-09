package com.bancatest.reconciliation.infrastructure.adapter.in.kafka;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import com.bancatest.reconciliation.support.FakeProcessedOperationStore;
import com.bancatest.reconciliation.support.InMemoryChannels;
import com.bancatest.reconciliation.support.InMemoryReconciliationRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.bancatest.banking.events.CoreTransactionReported;
import pe.com.bancatest.payments.events.PaymentExecuted;

/** End-to-end consumer behavior over the in-memory connector: no broker, database or Redis. */
@QuarkusTest
@QuarkusTestResource(InMemoryChannels.class)
class ConsumerFlowTest {

  @Inject @Any InMemoryConnector connector;
  @Inject InMemoryReconciliationRepository repository;
  @Inject FakeProcessedOperationStore store;

  @BeforeEach
  void reset() {
    repository.clear();
    store.clear();
    connector.sink("results-out").clear();
    connector.sink("discrepancies-out").clear();
  }

  private static PaymentExecuted payment(String reference, String amount, String currency) {
    return PaymentExecuted.newBuilder()
        .setEventId("evt-" + reference)
        .setOperationReference(reference)
        .setAccountNumber("12345678")
        .setAmount(new BigDecimal(amount))
        .setCurrency(currency)
        .setOccurredAt(Instant.now())
        .build();
  }

  private static CoreTransactionReported core(String reference, String amount) {
    return CoreTransactionReported.newBuilder()
        .setEventId("evt-core-" + reference)
        .setOperationReference(reference)
        .setAmount(new BigDecimal(amount))
        .setCurrency("PEN")
        .setOccurredAt(Instant.now())
        .build();
  }

  private ReconciliationStatus statusOf(String reference) {
    return repository
        .findByOperationReference(new OperationReference(reference))
        .orElseThrow()
        .status();
  }

  private void awaitReconciliation(String reference) {
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() ->
            repository.findByOperationReference(new OperationReference(reference)).isPresent());
  }

  private void awaitStatus(String reference, ReconciliationStatus status) {
    await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(reference) == status);
  }

  @Test
  void matchedFlowPublishesOnTheResultsChannelOnly() {
    connector.source("payments-in").send(payment("OP-1", "100.00", "PEN"));
    awaitReconciliation("OP-1");
    assertEquals(0, connector.sink("results-out").received().size());

    connector.source("core-transactions-in").send(core("OP-1", "99.00"));

    awaitStatus("OP-1", ReconciliationStatus.MATCHED);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> connector.sink("results-out").received().size() == 1);
    assertEquals(0, connector.sink("discrepancies-out").received().size());
  }

  @Test
  void mismatchedFlowPublishesOnTheDiscrepanciesChannelOnly() {
    connector.source("payments-in").send(payment("OP-2", "100.00", "PEN"));
    awaitReconciliation("OP-2");

    connector.source("core-transactions-in").send(core("OP-2", "95.00"));

    awaitStatus("OP-2", ReconciliationStatus.MISMATCHED);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> connector.sink("discrepancies-out").received().size() == 1);
    assertEquals(0, connector.sink("results-out").received().size());
  }

  @Test
  void samePaymentTwiceProducesASingleReconciliation() {
    var processed = new AtomicInteger();
    for (int i = 0; i < 2; i++) {
      connector
          .source("payments-in")
          .send(
              Message.of(
                  payment("OP-3", "100.00", "PEN"),
                  () -> {
                    processed.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                  }));
    }

    await().atMost(Duration.ofSeconds(10)).until(() -> processed.get() == 2);
    assertEquals(1, repository.count());
  }

  @Test
  void sameCoreTransactionTwiceResolvesAndPublishesOnce() {
    connector.source("payments-in").send(payment("OP-4", "100.00", "PEN"));
    awaitReconciliation("OP-4");
    var processed = new AtomicInteger();
    for (int i = 0; i < 2; i++) {
      connector
          .source("core-transactions-in")
          .send(
              Message.of(
                  core("OP-4", "100.00"),
                  () -> {
                    processed.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                  }));
    }

    await().atMost(Duration.ofSeconds(10)).until(() -> processed.get() == 2);
    assertEquals(1, connector.sink("results-out").received().size());
  }

  @Test
  void invalidPaymentsAreNackedAndCreateNothing() {
    var nacked = new AtomicInteger();
    for (var invalid : new String[][] {{"0.00", "PEN"}, {"100.00", "USD"}, {"-5.00", "PEN"}}) {
      connector
          .source("payments-in")
          .send(
              Message.of(
                  payment("OP-5", invalid[0], invalid[1]),
                  Message.of(0).getMetadata(),
                  () -> CompletableFuture.completedFuture(null),
                  failure -> {
                    nacked.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                  }));
    }

    await().atMost(Duration.ofSeconds(10)).until(() -> nacked.get() == 3);
    assertEquals(0, repository.count());
  }

  @Test
  void invalidCoreTransactionIsNackedWithoutTouchingTheReconciliation() {
    connector.source("payments-in").send(payment("OP-6", "100.00", "PEN"));
    awaitReconciliation("OP-6");
    var nacked = new AtomicInteger();
    var usdCore =
        CoreTransactionReported.newBuilder(core("OP-6", "100.00")).setCurrency("USD").build();

    connector
        .source("core-transactions-in")
        .send(
            Message.of(
                usdCore,
                Message.of(0).getMetadata(),
                () -> CompletableFuture.completedFuture(null),
                failure -> {
                  nacked.incrementAndGet();
                  return CompletableFuture.completedFuture(null);
                }));

    await().atMost(Duration.ofSeconds(10)).until(() -> nacked.get() == 1);
    assertEquals(ReconciliationStatus.PENDING, statusOf("OP-6"));
    assertTrue(connector.sink("results-out").received().isEmpty());
  }

  @Test
  void messagesOfAReferenceAreProcessedInArrivalOrder() {
    int references = 30;
    for (int i = 0; i < references; i++) {
      connector.source("payments-in").send(payment("ORD-" + i, "100.00", "PEN"));
    }
    for (int i = 0; i < references; i++) {
      awaitReconciliation("ORD-" + i);
    }

    // For every reference the 99.00 movement arrives first and the 50.00 one right after it.
    // Only the first may resolve it: out-of-order processing would leave some as MISMATCHED.
    for (int i = 0; i < references; i++) {
      connector.source("core-transactions-in").send(core("ORD-" + i, "99.00"));
      connector.source("core-transactions-in").send(core("ORD-" + i, "50.00"));
    }

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> {
          for (int i = 0; i < references; i++) {
            if (statusOf("ORD-" + i) == ReconciliationStatus.PENDING) {
              return false;
            }
          }
          return true;
        });
    for (int i = 0; i < references; i++) {
      assertEquals(ReconciliationStatus.MATCHED, statusOf("ORD-" + i), "ORD-" + i);
    }
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> connector.sink("results-out").received().size() == references);
    assertEquals(0, connector.sink("discrepancies-out").received().size());
  }
}
