package com.bancatest.reconciliation.domain.model.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bancatest.reconciliation.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmountDifferenceTest {

  private static final Currency PEN = new Currency("PEN");
  private static final Amount TOLERANCE = Amount.of("1.00", PEN);
  private static final Amount PAYMENT = Amount.of("100.00", PEN);

  @ParameterizedTest
  @CsvSource({
    "100.00, 0.00, true",
    "99.50, 0.50, true",
    "99.00, 1.00, true",
    "98.99, 1.01, false",
    "95.00, 5.00, false",
    "100.50, -0.50, false",
    "100.01, -0.01, false"
  })
  void classifiesTheSpecAmounts(String core, String expectedDifference, boolean withinTolerance) {
    var difference = PAYMENT.subtract(Amount.of(core, PEN));

    assertEquals(new BigDecimal(expectedDifference), difference.value());
    assertEquals(withinTolerance, difference.isWithinTolerance(TOLERANCE));
  }

  @Test
  void equalsByNumericValue() {
    assertEquals(
        new AmountDifference(new BigDecimal("0.5"), PEN),
        new AmountDifference(new BigDecimal("0.50"), PEN));
  }

  @Test
  void toleranceInAnotherCurrencyFails() {
    var difference = new AmountDifference(BigDecimal.ONE, PEN);

    assertThrows(
        CurrencyMismatchException.class,
        () -> difference.isWithinTolerance(Amount.of("1.00", new Currency("USD"))));
  }
}
