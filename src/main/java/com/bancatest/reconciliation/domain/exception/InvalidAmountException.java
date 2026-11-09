package com.bancatest.reconciliation.domain.exception;

/**
 * Raised when a monetary value violates its invariants (null, non-positive after normalization).
 */
public class InvalidAmountException extends RuntimeException {

  public InvalidAmountException(String message) {
    super(message);
  }
}
