package com.bancatest.reconciliation.domain.model.vo;

import java.util.regex.Pattern;

/**
 * ISO-4217 currency code: exactly three uppercase letters. Whether a currency is acceptable for the
 * business (only PEN) is a use case rule, not an invariant of this type.
 */
public record Currency(String code) {

  // FORMAT must be initialized before PEN, whose construction already validates the format.
  private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}$");

  public static final Currency PEN = new Currency("PEN");

  public Currency {
    if (code == null || !FORMAT.matcher(code).matches()) {
      throw new IllegalArgumentException("Currency code must be three uppercase letters: " + code);
    }
  }

  public boolean isPen() {
    return PEN.equals(this);
  }
}
