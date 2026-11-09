package com.bancatest.reconciliation.domain.model.vo;

import static com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus.MATCHED;
import static com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus.MISMATCHED;
import static com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReconciliationStatusTest {

  static Stream<Arguments> transitionMatrix() {
    return Stream.of(
        Arguments.of(PENDING, PENDING, false),
        Arguments.of(PENDING, MATCHED, true),
        Arguments.of(PENDING, MISMATCHED, true),
        Arguments.of(MATCHED, PENDING, false),
        Arguments.of(MATCHED, MATCHED, false),
        Arguments.of(MATCHED, MISMATCHED, false),
        Arguments.of(MISMATCHED, PENDING, false),
        Arguments.of(MISMATCHED, MATCHED, false),
        Arguments.of(MISMATCHED, MISMATCHED, false));
  }

  @ParameterizedTest
  @MethodSource("transitionMatrix")
  void coversTheFullTransitionMatrix(
      ReconciliationStatus from, ReconciliationStatus to, boolean allowed) {
    assertEquals(allowed, from.canTransitionTo(to));
  }
}
