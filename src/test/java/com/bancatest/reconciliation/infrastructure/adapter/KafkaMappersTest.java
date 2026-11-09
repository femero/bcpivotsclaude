package com.bancatest.reconciliation.infrastructure.adapter;

import static com.bancatest.reconciliation.support.TestData.NOW;
import static com.bancatest.reconciliation.support.TestData.pen;
import static com.bancatest.reconciliation.support.TestData.pending;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import com.bancatest.reconciliation.infrastructure.adapter.in.kafka.mapper.CoreTransactionReportedMapper;
import com.bancatest.reconciliation.infrastructure.adapter.in.kafka.mapper.PaymentExecutedMapper;
import com.bancatest.reconciliation.infrastructure.adapter.out.messaging.mapper.ReconciliationEventMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import pe.com.bancatest.banking.events.CoreTransactionReported;
import pe.com.bancatest.payments.events.PaymentExecuted;

class KafkaMappersTest {

  private static PaymentExecuted payment(String amount, String currency, String reference) {
    return PaymentExecuted.newBuilder()
        .setEventId("evt-1")
        .setOperationReference(reference)
        .setAccountNumber("12345678")
        .setAmount(new BigDecimal(amount))
        .setCurrency(currency)
        .setOccurredAt(NOW)
        .build();
  }

  @Test
  void paymentIsTranslatedToACommandAndNormalized() {
    var command = new PaymentExecutedMapper().toCommand(payment("100.005", "PEN", "OP-1"));

    assertEquals("OP-1", command.operationReference().value());
    assertEquals(pen("100.01"), command.amount());
    assertEquals(NOW, command.occurredAt());
  }

  @Test
  void invalidPaymentFailsDuringMapping() {
    var mapper = new PaymentExecutedMapper();

    assertThrows(
        InvalidAmountException.class, () -> mapper.toCommand(payment("0.00", "PEN", "OP-1")));
    assertThrows(
        IllegalArgumentException.class, () -> mapper.toCommand(payment("1.00", "PEN", " ")));
  }

  @Test
  void coreTransactionIsTranslatedToACommand() {
    var event =
        CoreTransactionReported.newBuilder()
            .setEventId("evt-2")
            .setOperationReference("OP-1")
            .setAmount(new BigDecimal("99.50"))
            .setCurrency("PEN")
            .setOccurredAt(NOW)
            .build();

    var command = new CoreTransactionReportedMapper().toCommand(event);

    assertEquals(pen("99.50"), command.amount());
  }

  @Test
  void matchedEventCarriesTheRecordedDifference() {
    var reconciliation = pending("OP-1", "100.00");
    var core = pen("99.50");
    reconciliation.match(core, reconciliation.paymentAmount().subtract(core), NOW);

    var event = new ReconciliationEventMapper().toMatched(reconciliation);

    assertEquals("OP-1", event.getOperationReference());
    assertEquals(new BigDecimal("0.50"), event.getDifference());
    assertEquals(reconciliation.id().value().toString(), event.getReconciliationId());
    assertEquals(NOW, event.getReconciledAt());
  }

  @Test
  void discrepancyEventCarriesBothAmountsAndANegativeDifference() {
    var reconciliation = pending("OP-1", "100.00");
    var core = pen("100.50");
    reconciliation.markAsMismatched(core, reconciliation.paymentAmount().subtract(core), NOW);

    var event = new ReconciliationEventMapper().toDiscrepancy(reconciliation);

    assertEquals(new BigDecimal("100.00"), event.getPaymentAmount());
    assertEquals(new BigDecimal("100.50"), event.getCoreAmount());
    assertEquals(new BigDecimal("-0.50"), event.getDifference());
  }
}
