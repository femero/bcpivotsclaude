package com.bancatest.reconciliation.domain.port.out;

import java.util.function.Supplier;

/**
 * Runs a unit of work inside a transaction. The transaction is committed before this method
 * returns, so callers can safely publish events afterwards.
 */
public interface TransactionalExecutor {

  <T> T inTransaction(Supplier<T> work);
}
