package com.bancatest.reconciliation.domain.port.in;

/**
 * Registers a reported payment as a PENDING reconciliation.
 *
 * <p>Contract: a non-PEN payment fails validation; a payment whose operation reference already has
 * a reconciliation is discarded as a successfully processed duplicate; nothing is published.
 */
public interface ReconcilePaymentUseCase {

  void reconcile(ReconcilePaymentCommand command);
}
