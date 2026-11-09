package com.bancatest.reconciliation.domain.model.vo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectValidationTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "pen", "PE", "PENN", "P3N"})
  void currencyRejectsInvalidCodes(String code) {
    assertThrows(IllegalArgumentException.class, () -> new Currency(code));
  }

  @Test
  void currencyAcceptsIsoCodes() {
    assertDoesNotThrow(() -> new Currency("USD"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void operationReferenceRejectsNullAndBlank(String value) {
    assertThrows(IllegalArgumentException.class, () -> new OperationReference(value));
  }

  @Test
  void operationReferenceLengthBoundary() {
    assertDoesNotThrow(() -> new OperationReference("A".repeat(64)));
    assertThrows(IllegalArgumentException.class, () -> new OperationReference("A".repeat(65)));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void accountNumberRejectsNullAndBlank(String value) {
    assertThrows(IllegalArgumentException.class, () -> new AccountNumber(value));
  }

  @Test
  void accountNumberLengthBoundary() {
    assertDoesNotThrow(() -> new AccountNumber("1".repeat(34)));
    assertThrows(IllegalArgumentException.class, () -> new AccountNumber("1".repeat(35)));
  }

  @Test
  void accountNumberIsNeverPrintedInFull() {
    var account = new AccountNumber("1234567890");

    assertEquals("******7890", account.masked());
    assertEquals(false, account.toString().contains("1234567890"));
  }

  @Test
  void reconciliationIdRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> new ReconciliationId(null));
    assertDoesNotThrow(() -> new ReconciliationId(UUID.randomUUID()));
  }
}
