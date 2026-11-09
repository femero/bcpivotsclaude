package com.bancatest.reconciliation.support;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.port.out.ReconciliationEventPublisher;
import java.util.ArrayList;
import java.util.List;

/** Unit-test publisher that records calls and can be told to fail. */
public class RecordingEventPublisher implements ReconciliationEventPublisher {

  private final List<String> events = new ArrayList<>();
  private boolean failing;

  @Override
  public void publishMatched(Reconciliation reconciliation) {
    record("matched", reconciliation);
  }

  @Override
  public void publishMismatched(Reconciliation reconciliation) {
    record("mismatched", reconciliation);
  }

  private void record(String kind, Reconciliation reconciliation) {
    if (failing) {
      throw new IllegalStateException("broker down");
    }
    events.add(kind + ":" + reconciliation.operationReference().value());
  }

  public void failOnPublish() {
    this.failing = true;
  }

  public List<String> events() {
    return events;
  }
}
