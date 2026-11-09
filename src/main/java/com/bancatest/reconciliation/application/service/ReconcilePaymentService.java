package com.bancatest.reconciliation.application.service;

import com.bancatest.reconciliation.domain.exception.DuplicateOperationException;
import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.port.in.ReconcilePaymentCommand;
import com.bancatest.reconciliation.domain.port.in.ReconcilePaymentUseCase;
import com.bancatest.reconciliation.domain.port.out.ProcessedOperationStore;
import com.bancatest.reconciliation.domain.port.out.ReconciliationIdProvider;
import com.bancatest.reconciliation.domain.port.out.ReconciliationRepository;
import com.bancatest.reconciliation.domain.port.out.TransactionalExecutor;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Creates a PENDING reconciliation for each valid reported payment. Duplicates (detected by the
 * fast store or by the database unique index) are discarded as successfully processed.
 */
public class ReconcilePaymentService implements ReconcilePaymentUseCase {

  private static final Logger LOG = System.getLogger(ReconcilePaymentService.class.getName());

  private final ReconciliationRepository repository;
  private final ProcessedOperationStore processedOperations;
  private final ReconciliationIdProvider idProvider;
  private final TransactionalExecutor transactions;

  public ReconcilePaymentService(
      ReconciliationRepository repository,
      ProcessedOperationStore processedOperations,
      ReconciliationIdProvider idProvider,
      TransactionalExecutor transactions) {
    this.repository = repository;
    this.processedOperations = processedOperations;
    this.idProvider = idProvider;
    this.transactions = transactions;
  }

  @Override
  public void reconcile(ReconcilePaymentCommand command) {
    if (!command.amount().currency().isPen()) {
      throw new InvalidAmountException("Only PEN payments are supported");
    }
    if (!processedOperations.markAsProcessed(command.operationReference())) {
      LOG.log(Level.INFO, "Duplicate payment discarded: {0}", command.operationReference().value());
      return;
    }
    var reconciliation =
        Reconciliation.start(
            idProvider.nextId(),
            command.operationReference(),
            command.accountNumber(),
            command.amount(),
            command.occurredAt());
    try {
      transactions.inTransaction(() -> {
        repository.save(reconciliation);
        return null;
      });
    } catch (DuplicateOperationException e) {
      LOG.log(
          Level.INFO,
          "Duplicate payment rejected by unique index: {0}",
          command.operationReference().value());
    }
  }
}
