package com.bancatest.reconciliation.infrastructure.adapter.in.rest;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.in.QueryReconciliationUseCase;
import com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.api.ReconciliationsApi;
import com.bancatest.reconciliation.infrastructure.adapter.in.rest.generated.model.ReconciliationResponse;
import com.bancatest.reconciliation.infrastructure.adapter.in.rest.mapper.ReconciliationResponseMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;

/** Implements the contract-first interface generated from the OpenAPI document. */
@ApplicationScoped
@RunOnVirtualThread
public class ReconciliationResource implements ReconciliationsApi {

  private final QueryReconciliationUseCase useCase;
  private final ReconciliationResponseMapper mapper;

  public ReconciliationResource(
      QueryReconciliationUseCase useCase, ReconciliationResponseMapper mapper) {
    this.useCase = useCase;
    this.mapper = mapper;
  }

  @Override
  public ReconciliationResponse getReconciliationByOperationReference(String operationReference) {
    return useCase
        .findByOperationReference(new OperationReference(operationReference))
        .map(mapper::toResponse)
        .orElseThrow(() -> new ReconciliationNotFoundException(operationReference));
  }
}
