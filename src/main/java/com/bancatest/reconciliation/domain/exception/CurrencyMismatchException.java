package com.bancatest.reconciliation.domain.exception;

/** Raised when an operation combines or compares amounts expressed in different currencies. */
public class CurrencyMismatchException extends RuntimeException {

  public CurrencyMismatchException(String message) {
    super(message);
  }
}
