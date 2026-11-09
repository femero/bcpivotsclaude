package com.bancatest.reconciliation.support;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.out.ProcessedOperationStore;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Test double replacing the Redis filter. */
@Mock
@ApplicationScoped
public class FakeProcessedOperationStore implements ProcessedOperationStore {

  private final Set<String> processed = ConcurrentHashMap.newKeySet();

  @Override
  public boolean markAsProcessed(OperationReference reference) {
    return processed.add(reference.value());
  }

  public void clear() {
    processed.clear();
  }
}
