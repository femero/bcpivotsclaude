package com.bancatest.reconciliation.infrastructure.adapter.out.persistence;

import static com.bancatest.reconciliation.support.TestData.NOW;
import static com.bancatest.reconciliation.support.TestData.pen;
import static com.bancatest.reconciliation.support.TestData.pending;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import com.bancatest.reconciliation.infrastructure.adapter.out.persistence.mapper.ReconciliationPersistenceMapper;
import org.junit.jupiter.api.Test;

class ReconciliationPersistenceMapperTest {

  private final ReconciliationPersistenceMapper mapper = new ReconciliationPersistenceMapper();

  @Test
  void pendingRoundTripsWithoutLoss() {
    var original = pending("OP-1", "100.00");

    var restored = mapper.toDomain(mapper.toNewEntity(original));

    assertEquals(original.id(), restored.id());
    assertEquals(original.paymentAmount(), restored.paymentAmount());
    assertEquals(ReconciliationStatus.PENDING, restored.status());
    assertTrue(restored.coreAmount().isEmpty());
    assertTrue(restored.difference().isEmpty());
    assertTrue(restored.resolvedAt().isEmpty());
  }

  @Test
  void resolvedRoundTripsWithoutLoss() {
    var original = pending("OP-1", "100.00");
    var core = pen("100.50");
    original.markAsMismatched(core, original.paymentAmount().subtract(core), NOW);

    var entity = mapper.toNewEntity(original);
    var restored = mapper.toDomain(entity);

    assertEquals("MISMATCHED", entity.getStatus().name());
    assertEquals(core, restored.coreAmount().orElseThrow());
    assertEquals(original.difference().orElseThrow(), restored.difference().orElseThrow());
    assertEquals(NOW, restored.resolvedAt().orElseThrow());
  }

  @Test
  void copyMutableStateUpdatesAManagedEntity() {
    var reconciliation = pending("OP-1", "100.00");
    var entity = mapper.toNewEntity(reconciliation);
    var core = pen("100.00");
    reconciliation.match(core, reconciliation.paymentAmount().subtract(core), NOW);

    mapper.copyMutableState(reconciliation, entity);

    assertEquals(ReconciliationStatus.MATCHED, entity.getStatus());
    assertEquals("0.00", entity.getDifferenceAmount().toPlainString());
  }
}
