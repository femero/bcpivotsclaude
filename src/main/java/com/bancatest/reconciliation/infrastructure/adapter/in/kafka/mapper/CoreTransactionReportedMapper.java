package com.bancatest.reconciliation.infrastructure.adapter.in.kafka.mapper;

import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.in.RegisterCoreTransactionCommand;
import jakarta.enterprise.context.ApplicationScoped;
import pe.com.bancatest.banking.events.CoreTransactionReported;

/** Translates the Avro contract into a command. The event id is dropped on purpose. */
@ApplicationScoped
public class CoreTransactionReportedMapper {

  public RegisterCoreTransactionCommand toCommand(CoreTransactionReported event) {
    return new RegisterCoreTransactionCommand(
        new OperationReference(event.getOperationReference()),
        new Amount(event.getAmount(), new Currency(event.getCurrency())),
        event.getOccurredAt());
  }
}
