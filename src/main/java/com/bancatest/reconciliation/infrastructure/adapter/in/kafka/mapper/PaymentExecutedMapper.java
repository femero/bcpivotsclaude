package com.bancatest.reconciliation.infrastructure.adapter.in.kafka.mapper;

import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.in.ReconcilePaymentCommand;
import jakarta.enterprise.context.ApplicationScoped;
import pe.com.bancatest.payments.events.PaymentExecuted;

/** Translates the Avro contract into a command. The event id is dropped on purpose. */
@ApplicationScoped
public class PaymentExecutedMapper {

  public ReconcilePaymentCommand toCommand(PaymentExecuted event) {
    return new ReconcilePaymentCommand(
        new OperationReference(event.getOperationReference()),
        new AccountNumber(event.getAccountNumber()),
        new Amount(event.getAmount(), new Currency(event.getCurrency())),
        event.getOccurredAt());
  }
}
