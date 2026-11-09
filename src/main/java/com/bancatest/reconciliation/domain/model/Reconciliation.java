package com.bancatest.reconciliation.domain.model;

import com.bancatest.reconciliation.domain.exception.IllegalStateTransitionException;
import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.AmountDifference;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root and only entity of the domain.
 *
 * <p>Invariants: it is born PENDING with a PEN payment amount; the status only advances through
 * declared transitions; on resolution {@code coreAmount}, {@code difference} and {@code resolvedAt}
 * are populated together (also for MATCHED); the difference shares the payment currency. There are
 * no setters: state changes only through {@link #match} and {@link #markAsMismatched}.
 */
public final class Reconciliation {

  private final ReconciliationId id;
  private final OperationReference operationReference;
  private final AccountNumber accountNumber;
  private final Amount paymentAmount;
  private final Instant createdAt;
  private Amount coreAmount;
  private AmountDifference difference;
  private ReconciliationStatus status;
  private Instant resolvedAt;

  private Reconciliation(ReconciliationSnapshot snapshot) {
    this.id = snapshot.id();
    this.operationReference = snapshot.operationReference();
    this.accountNumber = snapshot.accountNumber();
    this.paymentAmount = snapshot.paymentAmount();
    this.coreAmount = snapshot.coreAmount();
    this.difference = snapshot.difference();
    this.status = snapshot.status();
    this.createdAt = snapshot.createdAt();
    this.resolvedAt = snapshot.resolvedAt();
  }

  /** Creates a new PENDING reconciliation; no argument may be null and the payment must be PEN. */
  public static Reconciliation start(
      ReconciliationId id,
      OperationReference operationReference,
      AccountNumber accountNumber,
      Amount paymentAmount,
      Instant createdAt) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(operationReference, "operationReference");
    Objects.requireNonNull(accountNumber, "accountNumber");
    Objects.requireNonNull(paymentAmount, "paymentAmount");
    Objects.requireNonNull(createdAt, "createdAt");
    if (!paymentAmount.currency().isPen()) {
      throw new IllegalArgumentException("Payment amount must be in PEN");
    }
    return new Reconciliation(
        new ReconciliationSnapshot(
            id,
            operationReference,
            accountNumber,
            paymentAmount,
            null,
            null,
            ReconciliationStatus.PENDING,
            createdAt,
            null));
  }

  /** Rehydrates persisted state without re-validating business rules. Mapper use only. */
  public static Reconciliation reconstitute(ReconciliationSnapshot snapshot) {
    return new Reconciliation(snapshot);
  }

  /** Resolves this reconciliation as MATCHED, recording the difference. */
  public void match(Amount coreAmount, AmountDifference difference, Instant resolvedAt) {
    resolve(ReconciliationStatus.MATCHED, coreAmount, difference, resolvedAt);
  }

  /** Resolves this reconciliation as MISMATCHED, recording the difference. */
  public void markAsMismatched(Amount coreAmount, AmountDifference difference, Instant resolvedAt) {
    resolve(ReconciliationStatus.MISMATCHED, coreAmount, difference, resolvedAt);
  }

  /** True when the status is terminal. Callers check it before attempting a transition. */
  public boolean isResolved() {
    return status.isTerminal();
  }

  private void resolve(
      ReconciliationStatus target, Amount core, AmountDifference diff, Instant at) {
    if (!status.canTransitionTo(target)) {
      throw new IllegalStateTransitionException(status, target);
    }
    Objects.requireNonNull(core, "coreAmount");
    Objects.requireNonNull(diff, "difference");
    Objects.requireNonNull(at, "resolvedAt");
    if (!diff.currency().equals(paymentAmount.currency())) {
      throw new IllegalArgumentException("Difference currency must match the payment currency");
    }
    this.coreAmount = core;
    this.difference = diff;
    this.resolvedAt = at;
    this.status = target;
  }

  public ReconciliationId id() {
    return id;
  }

  public OperationReference operationReference() {
    return operationReference;
  }

  public AccountNumber accountNumber() {
    return accountNumber;
  }

  public Amount paymentAmount() {
    return paymentAmount;
  }

  public Optional<Amount> coreAmount() {
    return Optional.ofNullable(coreAmount);
  }

  public Optional<AmountDifference> difference() {
    return Optional.ofNullable(difference);
  }

  public ReconciliationStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> resolvedAt() {
    return Optional.ofNullable(resolvedAt);
  }
}
