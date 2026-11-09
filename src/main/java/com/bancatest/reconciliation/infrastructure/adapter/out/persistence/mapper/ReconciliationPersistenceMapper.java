package com.bancatest.reconciliation.infrastructure.adapter.out.persistence.mapper;

import com.bancatest.reconciliation.domain.model.Reconciliation;
import com.bancatest.reconciliation.domain.model.ReconciliationSnapshot;
import com.bancatest.reconciliation.domain.model.vo.AccountNumber;
import com.bancatest.reconciliation.domain.model.vo.Amount;
import com.bancatest.reconciliation.domain.model.vo.AmountDifference;
import com.bancatest.reconciliation.domain.model.vo.Currency;
import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;
import com.bancatest.reconciliation.infrastructure.adapter.out.persistence.entity.ReconciliationEntity;
import jakarta.enterprise.context.ApplicationScoped;

/** Explicit two-way mapping between the aggregate and its JPA entity. */
@ApplicationScoped
public class ReconciliationPersistenceMapper {

  public ReconciliationEntity toNewEntity(Reconciliation reconciliation) {
    var entity = ReconciliationEntity.blank();
    entity.setId(reconciliation.id().value());
    entity.setOperationReference(reconciliation.operationReference().value());
    entity.setAccountNumber(reconciliation.accountNumber().value());
    entity.setCreatedAt(reconciliation.createdAt());
    copyMutableState(reconciliation, entity);
    return entity;
  }

  /** Copies the fields that can change after creation onto a managed entity. */
  public void copyMutableState(Reconciliation reconciliation, ReconciliationEntity entity) {
    entity.setPaymentAmount(reconciliation.paymentAmount().value());
    entity.setPaymentCurrency(reconciliation.paymentAmount().currency().code());
    entity.setStatus(reconciliation.status());
    entity.setCoreAmount(reconciliation.coreAmount().map(Amount::value).orElse(null));
    entity.setCoreCurrency(reconciliation.coreAmount().map(a -> a.currency().code()).orElse(null));
    entity.setDifferenceAmount(
        reconciliation.difference().map(AmountDifference::value).orElse(null));
    entity.setDifferenceCurrency(
        reconciliation.difference().map(d -> d.currency().code()).orElse(null));
    entity.setResolvedAt(reconciliation.resolvedAt().orElse(null));
  }

  public Reconciliation toDomain(ReconciliationEntity entity) {
    var coreAmount =
        entity.getCoreAmount() == null
            ? null
            : new Amount(entity.getCoreAmount(), new Currency(entity.getCoreCurrency()));
    var difference =
        entity.getDifferenceAmount() == null
            ? null
            : new AmountDifference(
                entity.getDifferenceAmount(), new Currency(entity.getDifferenceCurrency()));
    return Reconciliation.reconstitute(
        new ReconciliationSnapshot(
            new ReconciliationId(entity.getId()),
            new OperationReference(entity.getOperationReference()),
            new AccountNumber(entity.getAccountNumber()),
            new Amount(entity.getPaymentAmount(), new Currency(entity.getPaymentCurrency())),
            coreAmount,
            difference,
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getResolvedAt()));
  }
}
