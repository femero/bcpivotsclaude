package com.bancatest.reconciliation.support;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.domain.port.in.ReconcilePaymentCommand;
import com.bancatest.reconciliation.domain.port.in.RegisterCoreTransactionCommand;
import java.time.Instant;
import java.util.UUID;

/** Builders shared by the tests. */
public final class TestData {

  public static final Currency PEN = new Currency("PEN");
  public static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

  private TestData() {}

  public static Amount pen(String value) {
    return Amount.of(value, PEN);
  }

  public static Reconciliation pending(String reference, String paymentAmount) {
    return Reconciliation.start(
        new ReconciliationId(UUID.randomUUID()),
        new OperationReference(reference),
        new AccountNumber("12345678"),
        pen(paymentAmount),
        NOW);
  }

  public static ReconcilePaymentCommand paymentCommand(String reference, Amount amount) {
    return new ReconcilePaymentCommand(
        new OperationReference(reference), new AccountNumber("12345678"), amount, NOW);
  }

  public static RegisterCoreTransactionCommand coreCommand(String reference, Amount amount) {
    return new RegisterCoreTransactionCommand(new OperationReference(reference), amount, NOW);
  }
}
