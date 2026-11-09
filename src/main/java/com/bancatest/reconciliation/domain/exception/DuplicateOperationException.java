package com.bancatest.reconciliation.domain.exception;

/**
 * Thrown by the reconciliation repository when a reconciliation with the same operation reference
 * already exists. The use case treats it as a successfully processed duplicate.
 */
public class DuplicateOperationException extends RuntimeException {

  public DuplicateOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
