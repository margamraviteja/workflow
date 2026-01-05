package com.workflow.execution.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ThreadPoolExecutionStrategyTest {

  private ExecutorService realExecutor;

  @AfterEach
  void tearDown() {
    if (realExecutor != null && !realExecutor.isShutdown()) {
      realExecutor.shutdownNow();
    }
  }

  @Test
  void submit_shouldCompleteSuccessfully() throws Exception {
    realExecutor = Executors.newSingleThreadExecutor();
    ThreadPoolExecutionStrategy strategy = new ThreadPoolExecutionStrategy(realExecutor);

    CompletableFuture<String> future = strategy.submit(() -> "ok");

    assertEquals("ok", future.get(1, TimeUnit.SECONDS));
    assertTrue(future.isDone());
    assertFalse(future.isCompletedExceptionally());
    assertFalse(future.isCancelled());
  }

  @Test
  void submit_whenCallableThrowsRuntimeException_futureCompletesExceptionally() {
    try (ThreadPoolExecutionStrategy strategy = new ThreadPoolExecutionStrategy(1)) {

      RuntimeException ex = new RuntimeException("boom");
      CompletableFuture<String> future =
          strategy.submit(
              () -> {
                throw ex;
              });

      ExecutionException thrown =
          assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
      assertSame(ex, thrown.getCause());
      assertTrue(future.isDone());
      assertTrue(future.isCompletedExceptionally());
    }
  }

  @Test
  void submit_whenCallableThrowsCheckedException_futureCompletesExceptionally() {
    try (ThreadPoolExecutionStrategy strategy = new ThreadPoolExecutionStrategy()) {

      Exception ex = new Exception("checked");
      CompletableFuture<String> future =
          strategy.submit(
              () -> {
                throw ex;
              });

      ExecutionException thrown =
          assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
      assertSame(ex, thrown.getCause());
      assertTrue(future.isDone());
      assertTrue(future.isCompletedExceptionally());
    }
  }

  @Test
  void submit_withNullCallable_completesExceptionallyWithNpe() {
    realExecutor = Executors.newSingleThreadExecutor();
    ThreadPoolExecutionStrategy strategy = new ThreadPoolExecutionStrategy(realExecutor);

    CompletableFuture<Object> future = strategy.submit(null);

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
    assertNotNull(thrown.getCause());
    assertInstanceOf(NullPointerException.class, thrown.getCause());
    assertTrue(future.isDone());
    assertTrue(future.isCompletedExceptionally());
  }

  @Test
  void cancellingReturnedCompletableFuture_doesNotPreventUnderlyingTaskFromRunning()
      throws Exception {
    realExecutor = Executors.newSingleThreadExecutor();
    ThreadPoolExecutionStrategy strategy = new ThreadPoolExecutionStrategy(realExecutor);

    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    AtomicBoolean ran = new AtomicBoolean(false);

    Callable<String> longTask =
        () -> {
          started.countDown();
          try {
            // simulate work
            Awaitility.await().atLeast(200, TimeUnit.MILLISECONDS).until(() -> true);
          } catch (Exception _) {
            // ignore
          }
          ran.set(true);
          finished.countDown();
          return "done";
        };

    CompletableFuture<String> future = strategy.submit(longTask);

    // ensure task has started
    assertTrue(started.await(1, TimeUnit.SECONDS));

    // cancel the CompletableFuture (this does not cancel the executor's Runnable)
    boolean cancelResult = future.cancel(true);

    // future should be canceled
    assertTrue(future.isCancelled() || cancelResult);

    // underlying task should still run to completion (the strategy does not propagate cancellation)
    assertTrue(finished.await(1, TimeUnit.SECONDS));
    assertTrue(ran.get());
  }

  @Test
  void close_shouldCallExecutorClose_whenExecutorSupportsClose() throws Exception {
    // Create an interface that extends ExecutorService and adds close()
    interface ClosableExecutor extends ExecutorService {
      @Override
      void close();
    }

    ClosableExecutor mockExecutor = Mockito.mock(ClosableExecutor.class);

    // When submit is called, run the runnable immediately to simulate execution
    when(mockExecutor.submit(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              // return a dummy Future
              return CompletableFuture.completedFuture(null);
            });

    ThreadPoolExecutionStrategy strategy = new ThreadPoolExecutionStrategy(mockExecutor);

    // Submit a simple task to ensure submit path works with the mock
    CompletableFuture<String> future = strategy.submit(() -> "x");
    assertEquals("x", future.get(1, TimeUnit.SECONDS));

    // Call close and verify close() was invoked on the executor
    strategy.close();
    verify(mockExecutor, times(1)).close();
  }
}
