package com.bancatest.reconciliation.infrastructure.adapter.in.kafka;

import com.bancatest.reconciliation.domain.port.in.ReconcilePaymentUseCase;
import com.bancatest.reconciliation.infrastructure.adapter.in.kafka.mapper.PaymentExecutedMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import pe.com.bancatest.payments.events.PaymentExecuted;

/**
 * Consumes reported payments. The message is acknowledged after the method returns (discards
 * included); an exception nacks it and the dead-letter-queue strategy sends it to the payments DLQ.
 * Processing is exclusive per operation reference (see {@link OperationLocks}).
 */
@ApplicationScoped
public class PaymentConsumer {

  private final ReconcilePaymentUseCase useCase;
  private final PaymentExecutedMapper mapper;
  private final OperationLocks locks;

  public PaymentConsumer(
      ReconcilePaymentUseCase useCase, PaymentExecutedMapper mapper, OperationLocks locks) {
    this.useCase = useCase;
    this.mapper = mapper;
    this.locks = locks;
  }

  @Incoming("payments-in")
  @RunOnVirtualThread
  public void consume(PaymentExecuted event) {
    var command = mapper.toCommand(event);
    locks.runExclusively(command.operationReference(), () -> useCase.reconcile(command));
  }
}
