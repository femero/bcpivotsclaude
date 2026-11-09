package com.bancatest.reconciliation.infrastructure.adapter.in.rest;

/** No reconciliation exists for the requested operation reference (HTTP 404). */
public class ReconciliationNotFoundException extends RuntimeException {

  private final String operationReference;

  public ReconciliationNotFoundException(String operationReference) {
    super("No reconciliation exists for operation reference " + operationReference);
    this.operationReference = operationReference;
  }

  public String operationReference() {
    return operationReference;
  }
}
