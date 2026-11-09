package com.bancatest.reconciliation.domain.port.in;

/**
 * Resolves the PENDING reconciliation of a core transaction as MATCHED or MISMATCHED.
 *
 * <p>Contract: a core transaction without reconciliation, or over an already resolved one, is
 * discarded with a warning and publishes nothing. The result is published only after the new state
 * has been committed.
 */
public interface RegisterCoreTransactionUseCase {

  void register(RegisterCoreTransactionCommand command);
}
