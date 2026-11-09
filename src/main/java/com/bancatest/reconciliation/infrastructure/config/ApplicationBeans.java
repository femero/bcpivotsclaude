package com.bancatest.reconciliation.infrastructure.config;

import com.bancatest.reconciliation.application.service.QueryReconciliationService;
import com.bancatest.reconciliation.application.service.ReconcilePaymentService;
import com.bancatest.reconciliation.application.service.RegisterCoreTransactionService;
import com.bancatest.reconciliation.domain.port.in.QueryReconciliationUseCase;
import com.bancatest.reconciliation.domain.port.in.ReconcilePaymentUseCase;
import com.bancatest.reconciliation.domain.port.in.RegisterCoreTransactionUseCase;
import com.bancatest.reconciliation.domain.port.out.ProcessedOperationStore;
import com.bancatest.reconciliation.domain.port.out.ReconciliationEventPublisher;
import com.bancatest.reconciliation.domain.port.out.ReconciliationIdProvider;
import com.bancatest.reconciliation.domain.port.out.ReconciliationRepository;
import com.bancatest.reconciliation.domain.port.out.ToleranceProvider;
import com.bancatest.reconciliation.domain.port.out.TransactionalExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Exposes the framework-free application services as CDI beans. The services carry no annotations
 * because {@code application} may only depend on {@code domain}.
 */
public class ApplicationBeans {

  @Produces
  @ApplicationScoped
  public ReconcilePaymentUseCase reconcilePayment(
      ReconciliationRepository repository,
      ProcessedOperationStore processedOperations,
      ReconciliationIdProvider idProvider,
      TransactionalExecutor transactions) {
    return new ReconcilePaymentService(repository, processedOperations, idProvider, transactions);
  }

  @Produces
  @ApplicationScoped
  public RegisterCoreTransactionUseCase registerCoreTransaction(
      ReconciliationRepository repository,
      ReconciliationEventPublisher publisher,
      ToleranceProvider toleranceProvider,
      TransactionalExecutor transactions) {
    return new RegisterCoreTransactionService(
        repository, publisher, toleranceProvider, transactions);
  }

  @Produces
  @ApplicationScoped
  public QueryReconciliationUseCase queryReconciliation(ReconciliationRepository repository) {
    return new QueryReconciliationService(repository);
  }
}
