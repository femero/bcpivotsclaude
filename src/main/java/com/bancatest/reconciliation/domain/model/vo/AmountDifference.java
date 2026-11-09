package com.bancatest.reconciliation.domain.model.vo;

import com.bancatest.reconciliation.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Difference between a payment amount and a core amount. Unlike {@link Amount} it may be zero or
 * negative (the core charged more than the payment). Scale 2, HALF_UP; equality by numeric value.
 */
public record AmountDifference(BigDecimal value, Currency currency) {

  public AmountDifference {
    Objects.requireNonNull(value, "Difference value must not be null");
    Objects.requireNonNull(currency, "Difference currency must not be null");
    value = value.setScale(Amount.SCALE, RoundingMode.HALF_UP);
  }

  /**
   * True when the difference is zero, or positive and not greater than the tolerance (inclusive
   * limit). Every negative difference is out of tolerance regardless of its magnitude.
   */
  public boolean isWithinTolerance(Amount tolerance) {
    if (!currency.equals(tolerance.currency())) {
      throw new CurrencyMismatchException(
          "Tolerance currency " + tolerance.currency() + " differs from " + currency);
    }
    return value.signum() >= 0 && value.compareTo(tolerance.value()) <= 0;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof AmountDifference other
        && value.compareTo(other.value) == 0
        && currency.equals(other.currency);
  }

  @Override
  public int hashCode() {
    var normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    return Objects.hash(normalized, currency);
  }
}
