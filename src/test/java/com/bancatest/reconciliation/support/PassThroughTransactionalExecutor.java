package com.bancatest.reconciliation.support;

import com.bancatest.reconciliation.domain.port.out.TransactionalExecutor;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/** Test double: runs the work directly, there is no database to commit to. */
@Mock
@ApplicationScoped
public class PassThroughTransactionalExecutor implements TransactionalExecutor {

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return work.get();
  }
}
