package com.bancatest.reconciliation.infrastructure.adapter.in.kafka;

import com.bancatest.reconciliation.domain.port.in.RegisterCoreTransactionUseCase;
import com.bancatest.reconciliation.infrastructure.adapter.in.kafka.mapper.CoreTransactionReportedMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import pe.com.bancatest.banking.events.CoreTransactionReported;

/**
 * Consumes core transactions. The message is acknowledged after the method returns (discards
 * included); an exception nacks it and the dead-letter-queue strategy sends it to the core
 * transactions DLQ. Processing is exclusive per operation reference (see {@link OperationLocks}).
 */
@ApplicationScoped
public class CoreTransactionConsumer {

  private final RegisterCoreTransactionUseCase useCase;
  private final CoreTransactionReportedMapper mapper;
  private final OperationLocks locks;

  public CoreTransactionConsumer(
      RegisterCoreTransactionUseCase useCase,
      CoreTransactionReportedMapper mapper,
      OperationLocks locks) {
    this.useCase = useCase;
    this.mapper = mapper;
    this.locks = locks;
  }

  @Incoming("core-transactions-in")
  @RunOnVirtualThread
  public void consume(CoreTransactionReported event) {
    var command = mapper.toCommand(event);
    locks.runExclusively(command.operationReference(), () -> useCase.register(command));
  }
}
