package com.bancatest.reconciliation.infrastructure.adapter.out.messaging.mapper;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;
import pe.com.bancatest.reconciliation.events.DiscrepancyDetected;
import pe.com.bancatest.reconciliation.events.ReconciliationMatched;

/** Translates a resolved aggregate into the outgoing Avro contracts. */
@ApplicationScoped
public class ReconciliationEventMapper {

  public ReconciliationMatched toMatched(Reconciliation reconciliation) {
    return ReconciliationMatched.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setReconciliationId(reconciliation.id().value().toString())
        .setOperationReference(reconciliation.operationReference().value())
        .setAmount(reconciliation.paymentAmount().value())
        .setCurrency(reconciliation.paymentAmount().currency().code())
        .setDifference(reconciliation.difference().orElseThrow().value())
        .setReconciledAt(reconciliation.resolvedAt().orElse(Instant.now()))
        .build();
  }

  public DiscrepancyDetected toDiscrepancy(Reconciliation reconciliation) {
    return DiscrepancyDetected.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setReconciliationId(reconciliation.id().value().toString())
        .setOperationReference(reconciliation.operationReference().value())
        .setPaymentAmount(reconciliation.paymentAmount().value())
        .setCoreAmount(reconciliation.coreAmount().orElseThrow().value())
        .setDifference(reconciliation.difference().orElseThrow().value())
        .setCurrency(reconciliation.paymentAmount().currency().code())
        .setDetectedAt(reconciliation.resolvedAt().orElse(Instant.now()))
        .build();
  }
}
