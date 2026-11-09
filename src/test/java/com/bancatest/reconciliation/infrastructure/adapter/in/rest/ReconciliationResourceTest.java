package com.bancatest.reconciliation.infrastructure.adapter.in.rest;

import static com.bancatest.reconciliation.support.TestData.NOW;
import static com.bancatest.reconciliation.support.TestData.pen;
import static com.bancatest.reconciliation.support.TestData.pending;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.bancatest.reconciliation.support.InMemoryChannels;
import com.bancatest.reconciliation.support.InMemoryReconciliationRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(InMemoryChannels.class)
class ReconciliationResourceTest {

  @Inject InMemoryReconciliationRepository repository;

  @BeforeEach
  void reset() {
    repository.clear();
  }

  @Test
  void mismatchedReconciliationShowsItsDifference() {
    var reconciliation = pending("OP-1", "100.00");
    var core = pen("95.00");
    reconciliation.markAsMismatched(core, reconciliation.paymentAmount().subtract(core), NOW);
    repository.seed(reconciliation);

    given()
        .when()
        .get("/reconciliations/OP-1")
        .then()
        .statusCode(200)
        .body("status", equalTo("MISMATCHED"))
        .body("difference.amount", equalTo("5.00"))
        .body("difference.currency", equalTo("PEN"))
        .body("coreAmount.amount", equalTo("95.00"));
  }

  @Test
  void matchedReconciliationAlsoShowsItsDifference() {
    var reconciliation = pending("OP-2", "100.00");
    var core = pen("99.50");
    reconciliation.match(core, reconciliation.paymentAmount().subtract(core), NOW);
    repository.seed(reconciliation);

    given()
        .when()
        .get("/reconciliations/OP-2")
        .then()
        .statusCode(200)
        .body("status", equalTo("MATCHED"))
        .body("difference.amount", equalTo("0.50"));
  }

  @Test
  void pendingReconciliationOmitsResolutionData() {
    repository.seed(pending("OP-3", "100.00"));

    given()
        .when()
        .get("/reconciliations/OP-3")
        .then()
        .statusCode(200)
        .body("status", equalTo("PENDING"))
        .body("paymentAmount.amount", equalTo("100.00"))
        .body("coreAmount", nullValue())
        .body("difference", nullValue())
        .body("resolvedAt", nullValue());
  }

  @Test
  void unknownReferenceReturnsProblemJson404() {
    given()
        .when()
        .get("/reconciliations/OP-404")
        .then()
        .statusCode(404)
        .contentType(containsString("application/problem+json"))
        .body("title", equalTo("Reconciliation not found"))
        .body("status", equalTo(404))
        .body("instance", equalTo("/v1/reconciliations/OP-404"));
  }
}
