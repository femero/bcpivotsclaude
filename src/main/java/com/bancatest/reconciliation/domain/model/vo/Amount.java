package com.bancatest.reconciliation.domain.model.vo;

import com.bancatest.reconciliation.domain.exception.CurrencyMismatchException;
import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Strictly positive monetary amount with its currency.
 *
 * <p>The value is normalized to scale 2 with HALF_UP before validation, so PEN 100.005 becomes
 * 100.01 and PEN 0.004 becomes 0.00 and is rejected. Equality compares the numeric value, so {@code
 * 120.0} equals {@code 120.00}. Never built from a {@code double}.
 */
public record Amount(BigDecimal value, Currency currency) {

  public static final int SCALE = 2;

  public Amount {
    if (value == null) {
      throw new InvalidAmountException("Amount value must not be null");
    }
    if (currency == null) {
      throw new InvalidAmountException("Amount currency must not be null");
    }
    value = value.setScale(SCALE, RoundingMode.HALF_UP);
    if (value.signum() <= 0) {
      throw new InvalidAmountException("Amount must be positive after normalization: " + value);
    }
  }

  public static Amount of(String value, Currency currency) {
    if (value == null) {
      throw new InvalidAmountException("Amount value must not be null");
    }
    try {
      return new Amount(new BigDecimal(value), currency);
    } catch (NumberFormatException e) {
      throw new InvalidAmountException("Amount is not a number: " + value);
    }
  }

  /** Returns {@code this - other}; it may be negative, hence a distinct type. */
  public AmountDifference subtract(Amount other) {
    if (!currency.equals(other.currency)) {
      throw new CurrencyMismatchException(
          "Cannot subtract " + other.currency + " from " + currency);
    }
    return new AmountDifference(value.subtract(other.value), currency);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Amount other
        && value.compareTo(other.value) == 0
        && currency.equals(other.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value.stripTrailingZeros(), currency);
  }
}
