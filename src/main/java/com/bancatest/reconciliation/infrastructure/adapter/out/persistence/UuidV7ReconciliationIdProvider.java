package com.bancatest.reconciliation.infrastructure.adapter.out.persistence;

import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.domain.port.out.ReconciliationIdProvider;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import jakarta.enterprise.context.ApplicationScoped;

/** Generates time-ordered UUIDv7 identities; the generator is thread-safe and shared. */
@ApplicationScoped
public class UuidV7ReconciliationIdProvider implements ReconciliationIdProvider {

  private static final NoArgGenerator GENERATOR = Generators.timeBasedEpochGenerator();

  @Override
  public ReconciliationId nextId() {
    return new ReconciliationId(GENERATOR.generate());
  }
}
