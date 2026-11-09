package com.bancatest.reconciliation.infrastructure.adapter.in.rest;

import com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.model.ProblemDetail;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

/** Produces the RFC 7807 {@code application/problem+json} body defined in the OpenAPI contract. */
@Provider
public class ReconciliationNotFoundExceptionMapper
    implements ExceptionMapper<ReconciliationNotFoundException> {

  private static final String PROBLEM_JSON = "application/problem+json";

  @Override
  public Response toResponse(ReconciliationNotFoundException exception) {
    var problem =
        new ProblemDetail()
            .type(URI.create("https://bancatest.com/problems/reconciliation-not-found"))
            .title("Reconciliation not found")
            .status(Response.Status.NOT_FOUND.getStatusCode())
            .detail(exception.getMessage())
            .instance(URI.create("/v1/reconciliations/" + exception.operationReference()));
    return Response.status(Response.Status.NOT_FOUND).type(PROBLEM_JSON).entity(problem).build();
  }
}
