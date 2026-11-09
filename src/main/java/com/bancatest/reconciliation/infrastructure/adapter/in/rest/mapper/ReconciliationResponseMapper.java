package com.bancatest.reconciliation.infrastructure.adapter.in.rest.mapper;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.AmountDifference;
import com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.model.MonetaryAmount;
import com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.model.ReconciliationResponse;
import com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.model.ReconciliationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Translates the aggregate into the generated REST response; absent data stays absent. */
@ApplicationScoped
public class ReconciliationResponseMapper {

  public ReconciliationResponse toResponse(Reconciliation reconciliation) {
    var response =
        new ReconciliationResponse()
            .reconciliationId(reconciliation.id().value())
            .operationReference(reconciliation.operationReference().value())
            .status(ReconciliationStatus.valueOf(reconciliation.status().name()))
            .paymentAmount(toMonetary(reconciliation.paymentAmount()))
            .createdAt(toOffset(reconciliation.createdAt()));
    reconciliation.coreAmount().map(this::toMonetary).ifPresent(response::setCoreAmount);
    reconciliation.difference().map(this::toMonetary).ifPresent(response::setDifference);
    reconciliation.resolvedAt().map(this::toOffset).ifPresent(response::setResolvedAt);
    return response;
  }

  private MonetaryAmount toMonetary(Amount amount) {
    return new MonetaryAmount()
        .amount(amount.value().toPlainString())
        .currency(amount.currency().code());
  }

  private MonetaryAmount toMonetary(AmountDifference difference) {
    return new MonetaryAmount()
        .amount(difference.value().toPlainString())
        .currency(difference.currency().code());
  }

  private OffsetDateTime toOffset(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
