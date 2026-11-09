package com.bancatest.reconciliation.application.service;

import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.port.in.RegisterCoreTransactionCommand;
import com.bancatest.reconciliation.domain.port.in.RegisterCoreTransactionUseCase;
import com.bancatest.reconciliation.domain.port.out.ReconciliationEventPublisher;
import com.bancatest.reconciliation.domain.port.out.ReconciliationRepository;
import com.bancatest.reconciliation.domain.port.out.ToleranceProvider;
import com.bancatest.reconciliation.domain.port.out.TransactionalExecutor;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Optional;

/**
 * Resolves a PENDING reconciliation with its core transaction. The state check ({@code
 * isResolved()}) happens before any transition, and the result is published only after the
 * transaction has been committed.
 */
public class RegisterCoreTransactionService implements RegisterCoreTransactionUseCase {

  private static final Logger LOG =
      System.getLogger(RegisterCoreTransactionService.class.getName());

  private final ReconciliationRepository repository;
  private final ReconciliationEventPublisher publisher;
  private final ToleranceProvider toleranceProvider;
  private final TransactionalExecutor transactions;

  public RegisterCoreTransactionService(
      ReconciliationRepository repository,
      ReconciliationEventPublisher publisher,
      ToleranceProvider toleranceProvider,
      TransactionalExecutor transactions) {
    this.repository = repository;
    this.publisher = publisher;
    this.toleranceProvider = toleranceProvider;
    this.transactions = transactions;
  }

  @Override
  public void register(RegisterCoreTransactionCommand command) {
    if (!command.amount().currency().isPen()) {
      throw new InvalidAmountException("Only PEN core transactions are supported");
    }
    transactions.inTransaction(() -> resolve(command)).ifPresent(this::publishAfterCommit);
  }

  /** Publication failures never undo the commit nor trigger a retry (FR-015). */
  private void publishAfterCommit(Reconciliation reconciliation) {
    try {
      publish(reconciliation);
    } catch (RuntimeException e) {
      LOG.log(
          Level.ERROR,
          "Result not published for " + reconciliation.operationReference().value(),
          e);
    }
  }

  private Optional<Reconciliation> resolve(RegisterCoreTransactionCommand command) {
    return repository
        .findByOperationReference(command.operationReference())
        .map(reconciliation -> resolveIfPending(reconciliation, command))
        .orElseGet(() -> {
          LOG.log(
              Level.WARNING,
              "Core transaction without reconciliation discarded: {0}",
              command.operationReference().value());
          return Optional.empty();
        });
  }

  private Optional<Reconciliation> resolveIfPending(
      Reconciliation reconciliation, RegisterCoreTransactionCommand command) {
    if (reconciliation.isResolved()) {
      LOG.log(
          Level.WARNING,
          "Core transaction over resolved reconciliation discarded: {0}",
          command.operationReference().value());
      return Optional.empty();
    }
    var difference = reconciliation.paymentAmount().subtract(command.amount());
    if (difference.isWithinTolerance(toleranceProvider.tolerance())) {
      reconciliation.match(command.amount(), difference, Instant.now());
    } else {
      reconciliation.markAsMismatched(command.amount(), difference, Instant.now());
    }
    repository.save(reconciliation);
    return Optional.of(reconciliation);
  }

  private void publish(Reconciliation reconciliation) {
    switch (reconciliation.status()) {
      case MATCHED -> publisher.publishMatched(reconciliation);
      case MISMATCHED -> publisher.publishMismatched(reconciliation);
      case PENDING ->
          throw new IllegalStateException("A resolved reconciliation cannot be PENDING");
    }
  }
}
