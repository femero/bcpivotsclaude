package com.bancatest.reconciliation.infrastructure.adapter.out.persistence.entity;

import com.bancatest.reconciliation.domain.model.vo.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA representation of the {@code reconciliations} table. Not the domain aggregate. */
@Entity
@Table(name = "reconciliations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationEntity {

  @Id private UUID id;

  @Column(name = "operation_reference", nullable = false, length = 64)
  private String operationReference;

  @Column(name = "account_number", nullable = false, length = 34)
  private String accountNumber;

  @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal paymentAmount;

  @Column(name = "payment_currency", nullable = false, length = 3)
  private String paymentCurrency;

  @Column(name = "core_amount", precision = 19, scale = 2)
  private BigDecimal coreAmount;

  @Column(name = "core_currency", length = 3)
  private String coreCurrency;

  @Column(name = "difference_amount", precision = 19, scale = 2)
  private BigDecimal differenceAmount;

  @Column(name = "difference_currency", length = 3)
  private String differenceCurrency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReconciliationStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  /**
   * Factory for a blank entity; the mapper fills it. Needed because the constructor is protected.
   */
  public static ReconciliationEntity blank() {
    return new ReconciliationEntity();
  }
}
