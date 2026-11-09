package com.bancatest.reconciliation.domain.model.vo;

/**
 * Account of a reported payment. Informative only, not blank, at most 34 characters. It is never
 * logged in full: use {@link #masked()}.
 */
public record AccountNumber(String value) {

  public static final int MAX_LENGTH = 34;

  public AccountNumber {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Account number must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Account number must not exceed " + MAX_LENGTH + " characters");
    }
  }

  /** Returns the account with all but the last four characters hidden. */
  public String masked() {
    var visible = Math.min(4, value.length());
    return "*".repeat(value.length() - visible) + value.substring(value.length() - visible);
  }

  @Override
  public String toString() {
    return "AccountNumber[" + masked() + "]";
  }
}
