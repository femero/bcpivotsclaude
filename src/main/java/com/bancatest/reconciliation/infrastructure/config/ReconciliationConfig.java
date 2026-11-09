package com.bancatest.reconciliation.infrastructure.config;

import io.smallrye.config.ConfigMapping;
import java.math.BigDecimal;
import java.time.Duration;

/** Typed business configuration. It never reaches the domain: values enter as value objects. */
@ConfigMapping(prefix = "reconciliation")
public interface ReconciliationConfig {

  Tolerance tolerance();

  Idempotency idempotency();

  interface Tolerance {

    BigDecimal amount();

    String currency();
  }

  interface Idempotency {

    Duration ttl();
  }
}
