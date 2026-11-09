package com.bancatest.reconciliation.domain.model.vo;

/**
 * Business key linking a reported payment with its core transaction. Not blank, at most 64
 * characters. It is the only idempotency criterion of the service.
 */
public record OperationReference(String value) {

  public static final int MAX_LENGTH = 64;

  public OperationReference {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Operation reference must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Operation reference must not exceed " + MAX_LENGTH + " characters");
    }
  }
}
