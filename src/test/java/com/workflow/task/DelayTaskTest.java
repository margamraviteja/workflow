package com.workflow.task;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DelayTaskTest {

  @Test
  void execute_withZeroMillis_returnsImmediately() {
    DelayTask dt = new DelayTask(0);
    WorkflowContext ctx = Mockito.mock(WorkflowContext.class);
    // Should not throw and should return quickly
    assertTimeoutPreemptively(Duration.ofMillis(200), () -> dt.execute(ctx));
  }

  @Test
  void execute_withSmallMillis_sleepsApproximatelyThatLong() {
    long sleep = 100;
    DelayTask dt = new DelayTask(sleep);
    WorkflowContext ctx = Mockito.mock(WorkflowContext.class);

    long start = System.nanoTime();
    dt.execute(ctx);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    // allow some leeway for scheduling; ensure it slept at least close to requested time
    assertTrue(elapsedMs >= 80, "Expected at least ~80ms sleep but was " + elapsedMs + "ms");
  }

  @Test
  void execute_withNegativeMillis_throwsTaskExecutionException() {
    DelayTask dt = new DelayTask(-1);
    WorkflowContext ctx = Mockito.mock(WorkflowContext.class);
    // Thread.sleep with negative value throws IllegalArgumentException which gets wrapped
    assertThrows(TaskExecutionException.class, () -> dt.execute(ctx));
  }

  @Test
  void execute_whenInterrupted_throwsTaskExecutionException_and_setsThreadInterruptFlag()
      throws Exception {
    DelayTask dt = new DelayTask(5_000); // long sleep so we can interrupt reliably

    AtomicReference<TaskExecutionException> captured = new AtomicReference<>();
    AtomicReference<Boolean> interruptedFlag = new AtomicReference<>(false);

    Thread t = getThread(dt, interruptedFlag, captured);

    // Give the thread a moment to enter sleep
    await().atLeast(100, TimeUnit.MILLISECONDS).until(() -> true);

    // Interrupt the sleeping thread
    t.interrupt();

    // Wait for thread to finish
    t.join(1000);

    assertNotNull(captured.get(), "Expected TaskExecutionException to be thrown");
    assertTrue(captured.get().getMessage().contains("Delay interrupted"));
    assertNotNull(captured.get().getCause());
    assertInstanceOf(InterruptedException.class, captured.get().getCause());
    // The implementation re-interrupts the thread before throwing; the worker thread's interrupted
    // flag should be true
    assertTrue(interruptedFlag.get(), "Worker thread should have been re-interrupted");
  }

  private static @NonNull Thread getThread(
      DelayTask dt,
      AtomicReference<Boolean> interruptedFlag,
      AtomicReference<TaskExecutionException> captured) {
    Thread t =
        new Thread(
            () -> {
              try {
                WorkflowContext ctx = Mockito.mock(WorkflowContext.class);
                dt.execute(ctx);
                // If no exception, mark interruptedFlag false
                interruptedFlag.set(false);
              } catch (TaskExecutionException e) {
                captured.set(e);
                // capture the interrupted status of this worker thread after the exception
                interruptedFlag.set(Thread.currentThread().isInterrupted());
              }
            });

    t.start();
    return t;
  }

  @Test
  void execute_doesNotAffectCallingThreadInterruptStatus_whenInterruptedInWorker()
      throws Exception {
    DelayTask dt = new DelayTask(5_000);

    Thread worker =
        new Thread(
            () -> {
              try {
                WorkflowContext ctx = Mockito.mock(WorkflowContext.class);
                dt.execute(ctx);
              } catch (TaskExecutionException _) {
                // ignore
              }
            });

    // Ensure main thread is not interrupted
    assertFalse(Thread.currentThread().isInterrupted());

    worker.start();
    await().atLeast(100, TimeUnit.MILLISECONDS).until(() -> true);
    worker.interrupt();
    worker.join(1000);

    // main thread should remain unaffected
    assertFalse(Thread.currentThread().isInterrupted());
  }
}
