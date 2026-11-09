package com.bancatest.reconciliation.infrastructure.adapter.out.persistence;

import com.bancatest.reconciliation.domain.port.out.TransactionalExecutor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/** Runs the work in a new transaction that is committed before this method returns. */
@ApplicationScoped
public class QuarkusTransactionalExecutor implements TransactionalExecutor {

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return QuarkusTransaction.requiringNew().call(work::get);
  }
}
