package com.bancatest.reconciliation.infrastructure.adapter.in.kafka;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes the processing of messages that share an operation reference.
 *
 * <p>{@code @RunOnVirtualThread} dispatches every message on its own virtual thread, so two
 * messages of the same operation (or a payment and its core transaction) could otherwise run at the
 * same time and both pass the {@code isResolved()} check. A fixed set of fair {@link
 * ReentrantLock}s, picked by the hash of the reference, gives mutual exclusion per operation
 * without unbounded memory. Different operations may share a stripe; that only costs a short wait.
 *
 * <p>Fair locks favour arrival order but the dispatch order of virtual threads is not guaranteed,
 * so this ensures "one at a time", not strict ordering. Strict order depends on producers keying
 * messages by operation reference, which keeps them in one partition.
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
