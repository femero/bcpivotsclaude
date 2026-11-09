package com.bancatest.reconciliation.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bancatest.reconciliation.domain.exception.IllegalStateTransitionException;
import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.AmountDifference;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationTest {

  private static final Currency PEN = new Currency("PEN");
  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

  private static Reconciliation pending() {
    return Reconciliation.start(
        new ReconciliationId(UUID.randomUUID()),
        new OperationReference("OP-1"),
        new AccountNumber("12345678"),
        Amount.of("100.00", PEN),
        NOW);
  }

  @Test
  void startsPendingWithoutResolutionData() {
    var reconciliation = pending();

    assertEquals(ReconciliationStatus.PENDING, reconciliation.status());
    assertFalse(reconciliation.isResolved());
    assertTrue(reconciliation.coreAmount().isEmpty());
    assertTrue(reconciliation.difference().isEmpty());
    assertTrue(reconciliation.resolvedAt().isEmpty());
  }

  @Test
  void matchPopulatesTheThreeFieldsTogether() {
    var reconciliation = pending();
    var core = Amount.of("99.50", PEN);

    reconciliation.match(core, reconciliation.paymentAmount().subtract(core), NOW);

    assertEquals(ReconciliationStatus.MATCHED, reconciliation.status());
    assertTrue(reconciliation.isResolved());
    assertEquals(core, reconciliation.coreAmount().orElseThrow());
    assertEquals(
        reconciliation.paymentAmount().subtract(core), reconciliation.difference().orElseThrow());
    assertEquals(NOW, reconciliation.resolvedAt().orElseThrow());
  }

  @Test
  void markAsMismatchedKeepsTheDifference() {
    var reconciliation = pending();
    var core = Amount.of("100.50", PEN);

    reconciliation.markAsMismatched(core, reconciliation.paymentAmount().subtract(core), NOW);

    assertEquals(ReconciliationStatus.MISMATCHED, reconciliation.status());
    assertEquals("-0.50", reconciliation.difference().orElseThrow().value().toPlainString());
  }

  @Test
  void resolvingTwiceThrows() {
    var reconciliation = pending();
    var core = Amount.of("100.00", PEN);
    var difference = reconciliation.paymentAmount().subtract(core);
    reconciliation.match(core, difference, NOW);

    assertThrows(
        IllegalStateTransitionException.class,
        () -> reconciliation.markAsMismatched(core, difference, NOW));
    assertEquals(ReconciliationStatus.MATCHED, reconciliation.status());
  }

  @Test
  void rejectsDifferenceInAnotherCurrency() {
    var reconciliation = pending();
    var usdDifference = new AmountDifference(java.math.BigDecimal.ONE, new Currency("USD"));

    assertThrows(
        IllegalArgumentException.class,
        () -> reconciliation.match(Amount.of("99.00", PEN), usdDifference, NOW));
  }

  @Test
  void startRejectsNonPenPaymentAndNullArguments() {
    var id = new ReconciliationId(UUID.randomUUID());
    var reference = new OperationReference("OP-1");
    var account = new AccountNumber("12345678");
    var usd = Amount.of("100.00", new Currency("USD"));

    assertThrows(
        IllegalArgumentException.class,
        () -> Reconciliation.start(id, reference, account, usd, NOW));
    assertThrows(
        NullPointerException.class,
        () -> Reconciliation.start(id, reference, account, Amount.of("1.00", PEN), null));
  }
}
