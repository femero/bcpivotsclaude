package com.bancatest.reconciliation.infrastructure.adapter.out.messaging;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.port.out.ReconciliationEventPublisher;
import com.bancatest.reconciliation.infrastructure.adapter.out.messaging.mapper.ReconciliationEventMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import pe.com.bancatest.reconciliation.events.DiscrepancyDetected;
import pe.com.bancatest.reconciliation.events.ReconciliationMatched;

/**
 * Publishes outcomes keyed by operation reference. The publication happens after the database
 * commit and there is no outbox: an asynchronous failure is only logged, never retried or
 * compensated (FR-015).
 */
@ApplicationScoped
public class KafkaReconciliationEventPublisher implements ReconciliationEventPublisher {

  private static final Logger LOG = Logger.getLogger(KafkaReconciliationEventPublisher.class);

  private final Emitter<ReconciliationMatched> matchedEmitter;
  private final Emitter<DiscrepancyDetected> discrepancyEmitter;
  private final ReconciliationEventMapper mapper;

  public KafkaReconciliationEventPublisher(
      @Channel("results-out") Emitter<ReconciliationMatched> matchedEmitter,
      @Channel("discrepancies-out") Emitter<DiscrepancyDetected> discrepancyEmitter,
      ReconciliationEventMapper mapper) {
    this.matchedEmitter = matchedEmitter;
    this.discrepancyEmitter = discrepancyEmitter;
    this.mapper = mapper;
  }

  @Override
  public void publishMatched(Reconciliation reconciliation) {
    send(matchedEmitter, mapper.toMatched(reconciliation), reconciliation);
  }

  @Override
  public void publishMismatched(Reconciliation reconciliation) {
    send(discrepancyEmitter, mapper.toDiscrepancy(reconciliation), reconciliation);
  }

  private <T> void send(Emitter<T> emitter, T payload, Reconciliation reconciliation) {
    var key = reconciliation.operationReference().value();
    var metadata = OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build();
    emitter.send(
        Message.of(payload)
            .addMetadata(metadata)
            .withNack(failure -> {
              LOG.errorf(failure, "Result not published for %s", key);
              return CompletableFuture.completedFuture(null);
            }));
  }
}
