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
 * transactions DLQ.
 */
@ApplicationScoped
public class CoreTransactionConsumer {

  private final RegisterCoreTransactionUseCase useCase;
  private final CoreTransactionReportedMapper mapper;

  public CoreTransactionConsumer(
      RegisterCoreTransactionUseCase useCase, CoreTransactionReportedMapper mapper) {
    this.useCase = useCase;
    this.mapper = mapper;
  }

  @Incoming("core-transactions-in")
  @RunOnVirtualThread
  public void consume(CoreTransactionReported event) {
    useCase.register(mapper.toCommand(event));
  }
}
