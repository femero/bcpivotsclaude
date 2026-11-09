package com.bancatest.reconciliation.infrastructure.adapter.in.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OperationLocksTest {

  private static final int THREADS = 200;

  @Test
  void sameReferenceNeverRunsTwoTasksAtTheSameTime() throws InterruptedException {
    var locks = new OperationLocks();
    var reference = new OperationReference("OP-1");
    var running = new AtomicInteger();
    var maxRunning = new AtomicInteger();
    var completed = new AtomicInteger();

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      threads.add(
          Thread.ofVirtual()
              .start(() ->
                  locks.runExclusively(
                      reference,
                      () -> {
                        maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                        Thread.yield();
                        running.decrementAndGet();
                        completed.incrementAndGet();
                      })));
    }
    for (var thread : threads) {
      thread.join();
    }

    assertEquals(1, maxRunning.get());
    assertEquals(THREADS, completed.get());
  }

  @Test
  void theLockIsReleasedWhenTheTaskFails() {
    var locks = new OperationLocks();
    var reference = new OperationReference("OP-1");

    try {
      locks.runExclusively(
          reference,
          () -> {
            throw new IllegalStateException("boom");
          });
    } catch (IllegalStateException expected) {
      // the next call would block forever if the lock had leaked
    }
    var ran = new AtomicInteger();
    locks.runExclusively(reference, ran::incrementAndGet);

    assertEquals(1, ran.get());
  }
}
