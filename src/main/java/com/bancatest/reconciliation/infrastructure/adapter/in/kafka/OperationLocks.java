package com.bancatest.reconciliation.infrastructure.adapter.in.kafka;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mutual exclusion per operation reference <em>across</em> the two consumers.
 *
 * <p>Each consumer already processes its own channel one message at a time and in arrival order
 * (worker concurrency 1, see application.yaml), but the two channels run in parallel, so a payment
 * and the core transaction of the same operation could still overlap. A fixed set of fair {@link
 * ReentrantLock}s, picked by the hash of the reference, excludes them without unbounded memory.
 * Different operations may share a stripe; that only costs a short wait.
 *
 * <p>This is exclusion, not ordering between channels: which of the two arrives first is not
 * guaranteed (a known limitation of the service, documented in the README).
 */
@ApplicationScoped
public class OperationLocks {

  private static final int STRIPES = 64;

  private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

  public OperationLocks() {
    for (int i = 0; i < STRIPES; i++) {
      locks[i] = new ReentrantLock(true);
    }
  }

  /** Runs the work while holding the lock of the operation reference. */
  public void runExclusively(OperationReference reference, Runnable work) {
    var lock = locks[Math.floorMod(reference.value().hashCode(), STRIPES)];
    lock.lock();
    try {
      work.run();
    } finally {
      lock.unlock();
    }
  }
}
