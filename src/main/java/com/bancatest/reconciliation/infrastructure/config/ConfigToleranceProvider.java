package com.bancatest.reconciliation.infrastructure.config;

import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.port.out.ToleranceProvider;
import jakarta.enterprise.context.ApplicationScoped;

/** Exposes the configured tolerance as a domain {@link Amount}. */
@ApplicationScoped
public class ConfigToleranceProvider implements ToleranceProvider {

  private final Amount tolerance;

  public ConfigToleranceProvider(ReconciliationConfig config) {
    this.tolerance =
        new Amount(config.tolerance().amount(), new Currency(config.tolerance().currency()));
  }

  @Override
  public Amount tolerance() {
    return tolerance;
  }
}
