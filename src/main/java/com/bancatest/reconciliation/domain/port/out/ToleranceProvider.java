package com.bancatest.reconciliation.domain.port.out;

import com.bancatest.reconciliation.domain.model.vo.Amount;

/** Supplies the fixed tolerance (PEN 1.00) coming from external configuration. */
public interface ToleranceProvider {

  Amount tolerance();
}
