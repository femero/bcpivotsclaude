package com.bancatest.reconciliation.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import java.util.HashMap;
import java.util.Map;

/** Switches the four channels to the in-memory connector: no broker is needed. */
public class InMemoryChannels implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    Map<String, String> properties = new HashMap<>();
    properties.putAll(
        InMemoryConnector.switchIncomingChannelsToInMemory("payments-in", "core-transactions-in"));
    properties.putAll(
        InMemoryConnector.switchOutgoingChannelsToInMemory("results-out", "discrepancies-out"));
    return properties;
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}
