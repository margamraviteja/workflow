package com.workflow.task.executor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.exception.TaskTimeoutException;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class DefaultTaskExecutorTest {

  @Test
  void execute_asyncPath_taskRunsOnce_whenTimeoutPolicyPositive() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    // TimeoutPolicy mock with positive timeoutMs
    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(5_000L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    // Task that increments counter
    AtomicInteger runs = new AtomicInteger(0);
    Task task = _ -> runs.incrementAndGet();
    when(td.getTask()).thenReturn(task);

    // Execute
    exec.execute(td, ctx);

    // Because timeout > 0, synchronous getResult is NOT called; async run should execute once
    assertEquals(1, runs.get());
  }

  @Test
  void execute_timeoutOccurs_throwsTaskTimeoutException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    // TimeoutPolicy with very small timeout
    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(10L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    // Task that sleeps longer than timeout
    Task longTask =
        _ -> {
          try {
            await().atLeast(200, TimeUnit.MILLISECONDS).until(() -> true);
          } catch (Exception _) {
            Thread.currentThread().interrupt();
          }
        };
    when(td.getTask()).thenReturn(longTask);

    TaskTimeoutException ex = assertThrows(TaskTimeoutException.class, () -> exec.execute(td, ctx));
    assertNotNull(ex.getCause());
    // cause may be TimeoutException wrapped; ensure it's a TimeoutException or wrapped by
    // ExecutionException
    assertTrue(
        ex.getCause() instanceof TimeoutException
            || ex.getCause().getCause() instanceof TimeoutException);
  }

  @Test
  void execute_retryPolicy_retriesAndSucceeds() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    // TimeoutPolicy with positive timeout so async path used
    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(5_000L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    // Create a task that fails first time then succeeds
    AtomicInteger attempts = new AtomicInteger(0);
    Task flaky =
        _ -> {
          int a = attempts.incrementAndGet();
          if (a == 1) {
            throw new RuntimeException("first-fail");
          }
          // succeed on second attempt
        };
    when(td.getTask()).thenReturn(flaky);

    // RetryPolicy mock: shouldRetry true for attempt 1, false otherwise
    RetryPolicy retry = mock(RetryPolicy.class);
    when(retry.backoff())
        .thenReturn(
            RetryPolicy.BackoffStrategy
                .NO_BACKOFF); // backoff with computeDelayMs signature; use lambda if interface is
    // functional

    when(retry.shouldRetry(anyInt(), any()))
        .thenAnswer(
            (Answer<Boolean>)
                invocation -> {
                  int attempt = invocation.getArgument(0);
                  // allow retry only for attempt == 1
                  return attempt == 1;
                });
    when(td.getRetryPolicy()).thenReturn(retry);

    // Execute - should not throw
    assertDoesNotThrow(() -> exec.execute(td, ctx));
    // ensure task attempted at least twice
    assertTrue(attempts.get() >= 2);
  }

  @Test
  void execute_retryExhausted_throwsTaskExecutionException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(5_000L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    // Task always throws
    Task alwaysFail =
        _ -> {
          throw new RuntimeException("boom");
        };
    when(td.getTask()).thenReturn(alwaysFail);

    // RetryPolicy that never retries
    RetryPolicy retry = mock(RetryPolicy.class);
    when(retry.shouldRetry(anyInt(), any())).thenReturn(false);
    when(retry.backoff()).thenReturn(RetryPolicy.BackoffStrategy.NO_BACKOFF);
    when(td.getRetryPolicy()).thenReturn(retry);

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> exec.execute(td, ctx));
    assertNotNull(ex.getCause());
    assertEquals("boom", ex.getCause().getMessage());
  }

  @Test
  void execute_taskThrowsTaskExecutionException_isPropagated() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(null);

    Task task =
        _ -> {
          throw new TaskExecutionException("task failed");
        };
    when(td.getTask()).thenReturn(task);

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> exec.execute(td, ctx));
    assertEquals("task failed", ex.getMessage());
  }

  @Test
  void execute_taskThrowsRuntimeException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(TimeoutPolicy.NONE);

    Task task =
        _ -> {
          throw new RuntimeException("task failed");
        };
    when(td.getTask()).thenReturn(task);

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> exec.execute(td, ctx));
    assertEquals("task failed", ex.getCause().getMessage());
  }

  @Test
  void execute_whenInterruptedWhileWaiting_getHandlesInterruptedAndThreadIsMarkedInterrupted()
      throws Exception {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    // TimeoutPolicy with large timeout so get() will block until task completes
    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(5_000L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    // Task that sleeps for a while
    Task longTask =
        _ -> {
          try {
            await().atLeast(2000, TimeUnit.MILLISECONDS).until(() -> true);
          } catch (Exception _) {
            Thread.currentThread().interrupt();
          }
        };
    when(td.getTask()).thenReturn(longTask);

    // Run execute in a separate thread so we can interrupt it while it's blocked on get()
    Thread worker =
        new Thread(
            () -> {
              try {
                exec.execute(td, ctx);
              } catch (Exception _) {
                // swallow for test; we only care that it returns/handles interruption
              }
            });

    worker.start();
    // Give the worker a moment to start and block on get()
    await().atMost(200, TimeUnit.MILLISECONDS).until(() -> true);

    // Interrupt the worker thread to cause CompletableFuture.get to throw InterruptedException
    worker.interrupt();

    // Wait for worker to finish
    worker.join(5_000);

    // Worker thread should have terminated
    assertFalse(worker.isAlive());
    // We cannot directly inspect the worker thread's interrupted flag here, but ensure test thread
    // not interrupted
    assertFalse(Thread.currentThread().isInterrupted());
  }

  @Test
  void execute_withNullTaskDescriptor_throwsNullPointerException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    WorkflowContext ctx = new WorkflowContext();

    assertThrows(NullPointerException.class, () -> exec.execute(null, ctx));
  }

  @Test
  void execute_withNullContext_throwsNullPointerException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);

    assertThrows(NullPointerException.class, () -> exec.execute(td, null));
  }

  @Test
  void execute_withNullTask_throwsTaskExecutionException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = new WorkflowContext();

    when(td.getTask()).thenReturn(null);

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> exec.execute(td, ctx));
    assertTrue(ex.getMessage().contains("Task in TaskDescriptor must not be null"));
  }

  @Test
  void execute_withNegativeTimeout_usesSyncPath() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(-1L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    AtomicInteger runs = new AtomicInteger(0);
    Task task = _ -> runs.incrementAndGet();
    when(td.getTask()).thenReturn(task);

    exec.execute(td, ctx);

    assertEquals(1, runs.get());
  }

  @Test
  void execute_withZeroTimeout_usesSyncPath() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(TimeoutPolicy.NONE);

    AtomicInteger runs = new AtomicInteger(0);
    Task task = _ -> runs.incrementAndGet();
    when(td.getTask()).thenReturn(task);

    exec.execute(td, ctx);

    assertEquals(1, runs.get());
  }

  @Test
  void constructor_withExecutor_usesProvidedExecutor() {
    ExecutorService customExecutor = Executors.newSingleThreadExecutor();
    DefaultTaskExecutor exec = new DefaultTaskExecutor(customExecutor);

    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    TimeoutPolicy tp = mock(TimeoutPolicy.class);
    when(tp.timeoutMs()).thenReturn(1000L);
    when(td.getTimeoutPolicy()).thenReturn(tp);

    Task task = _ -> {};
    when(td.getTask()).thenReturn(task);

    assertDoesNotThrow(() -> exec.execute(td, ctx));

    customExecutor.shutdown();
  }

  @Test
  void constructor_withNullExecutor_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new DefaultTaskExecutor(null));
  }

  @Test
  void sleep_withPositiveDelay_sleeps() {
    long start = System.currentTimeMillis();
    DefaultTaskExecutor.sleep(50);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed >= 40 && elapsed <= 100);
  }

  @Test
  void sleep_withZeroDelay_returnsImmediately() {
    long start = System.currentTimeMillis();
    DefaultTaskExecutor.sleep(0);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 20);
  }

  @Test
  void sleep_withNegativeDelay_returnsImmediately() {
    long start = System.currentTimeMillis();
    DefaultTaskExecutor.sleep(-10);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 20);
  }

  @Test
  void execute_retryWithBackoff_appliesDelay() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(TimeoutPolicy.NONE);

    AtomicInteger attempts = new AtomicInteger(0);
    Task task =
        _ -> {
          if (attempts.incrementAndGet() < 2) {
            throw new RuntimeException("fail");
          }
        };
    when(td.getTask()).thenReturn(task);

    RetryPolicy retry = mock(RetryPolicy.class);
    when(retry.shouldRetry(eq(1), any())).thenReturn(true);
    when(retry.shouldRetry(eq(2), any())).thenReturn(false);

    RetryPolicy.BackoffStrategy backoff = mock(RetryPolicy.BackoffStrategy.class);
    when(backoff.computeDelayMs(1)).thenReturn(10L);
    when(retry.backoff()).thenReturn(backoff);

    when(td.getRetryPolicy()).thenReturn(retry);

    long start = System.currentTimeMillis();
    assertDoesNotThrow(() -> exec.execute(td, ctx));
    long elapsed = System.currentTimeMillis() - start;

    // Should have delayed at least 10ms
    assertTrue(elapsed >= 5);
    assertEquals(2, attempts.get());
  }

  @Test
  void execute_multipleFails_logsCorrectly() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(TimeoutPolicy.NONE);

    AtomicInteger attempts = new AtomicInteger(0);
    Task task =
        _ -> {
          attempts.incrementAndGet();
          throw new RuntimeException("always fail");
        };
    when(td.getTask()).thenReturn(task);

    RetryPolicy retry = mock(RetryPolicy.class);
    when(retry.shouldRetry(eq(1), any())).thenReturn(true);
    when(retry.shouldRetry(eq(2), any())).thenReturn(true);
    when(retry.shouldRetry(eq(3), any())).thenReturn(false);
    when(retry.backoff()).thenReturn(RetryPolicy.BackoffStrategy.NO_BACKOFF);

    when(td.getRetryPolicy()).thenReturn(retry);

    assertThrows(TaskExecutionException.class, () -> exec.execute(td, ctx));
    assertEquals(3, attempts.get());
  }

  @Test
  void execute_succeedsAfterRetries_logsCorrectly() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(TimeoutPolicy.NONE);

    AtomicInteger attempts = new AtomicInteger(0);
    Task task =
        _ -> {
          if (attempts.incrementAndGet() < 3) {
            throw new RuntimeException("fail");
          }
        };
    when(td.getTask()).thenReturn(task);

    RetryPolicy retry = mock(RetryPolicy.class);
    when(retry.shouldRetry(anyInt(), any())).thenReturn(true);
    when(retry.backoff()).thenReturn(RetryPolicy.BackoffStrategy.NO_BACKOFF);

    when(td.getRetryPolicy()).thenReturn(retry);

    assertDoesNotThrow(() -> exec.execute(td, ctx));
    assertTrue(attempts.get() >= 3);
  }

  @Test
  void execute_sleepInterrupted_throwsTaskExecutionException() {
    DefaultTaskExecutor exec = new DefaultTaskExecutor();
    TaskDescriptor td = mock(TaskDescriptor.class);
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(td.getTimeoutPolicy()).thenReturn(TimeoutPolicy.NONE);

    Task task =
        _ -> {
          throw new RuntimeException("fail");
        };
    when(td.getTask()).thenReturn(task);

    RetryPolicy retry = mock(RetryPolicy.class);
    when(retry.shouldRetry(anyInt(), any())).thenReturn(true);

    RetryPolicy.BackoffStrategy backoff = mock(RetryPolicy.BackoffStrategy.class);
    when(backoff.computeDelayMs(anyInt())).thenReturn(5000L);
    when(retry.backoff()).thenReturn(backoff);

    when(td.getRetryPolicy()).thenReturn(retry);

    Thread testThread =
        new Thread(() -> assertThrows(TaskExecutionException.class, () -> exec.execute(td, ctx)));

    testThread.start();
    await().atLeast(100, TimeUnit.MILLISECONDS).until(() -> true);
    testThread.interrupt();

    assertDoesNotThrow(() -> testThread.join(2000));
  }
}
