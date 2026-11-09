package com.bancatest.reconciliation.domain.port.out;

import com.bancatest.reconciliation.domain.model.vo.ReconciliationId;

/** Generates the identity of a new reconciliation (UUIDv7). */
public interface ReconciliationIdProvider {

  ReconciliationId nextId();
}
