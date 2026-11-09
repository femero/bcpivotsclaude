package com.bancatest.reconciliation.domain.model.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bancatest.reconciliation.domain.exception.CurrencyMismatchException;
import com.bancatest.reconciliation.domain.exception.InvalidAmountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountTest {

  private static final Currency PEN = new Currency("PEN");
  private static final Currency USD = new Currency("USD");

  @Test
  void equalsIgnoresScale() {
    var a = Amount.of("120.0", PEN);
    var b = Amount.of("120.00", PEN);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void normalizesHalfUp() {
    assertEquals(new BigDecimal("100.01"), Amount.of("100.005", PEN).value());
    assertEquals(new BigDecimal("100.00"), Amount.of("100.004", PEN).value());
  }

  @Test
  void rejectsAmountThatNormalizesToZero() {
    assertThrows(InvalidAmountException.class, () -> Amount.of("0.004", PEN));
  }

  @Test
  void rejectsZeroNegativeAndNull() {
    assertThrows(InvalidAmountException.class, () -> Amount.of("0.00", PEN));
    assertThrows(InvalidAmountException.class, () -> Amount.of("-5.00", PEN));
    assertThrows(InvalidAmountException.class, () -> new Amount(null, PEN));
    assertThrows(InvalidAmountException.class, () -> new Amount(BigDecimal.ONE, null));
    assertThrows(InvalidAmountException.class, () -> Amount.of("abc", PEN));
  }

  @Test
  void subtractReturnsDifferenceAndCanBeNegative() {
    var diff = Amount.of("100.00", PEN).subtract(Amount.of("100.50", PEN));

    assertEquals(new BigDecimal("-0.50"), diff.value());
  }

  @Test
  void subtractingDifferentCurrenciesFails() {
    var pen = Amount.of("100.00", PEN);
    var usd = Amount.of("100.00", USD);

    assertThrows(CurrencyMismatchException.class, () -> pen.subtract(usd));
  }

  @Test
  void differentCurrenciesAreNotEqual() {
    assertEquals(false, Amount.of("1.00", PEN).equals(Amount.of("1.00", USD)));
  }
}
