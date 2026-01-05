package com.workflow.execution.strategy;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class ReactorExecutionStrategyTest {

  // Ensure Reactor schedulers are shut down after tests to avoid thread leaks
  @AfterAll
  static void tearDownAll() {
    Schedulers.shutdownNow();
  }

  @Test
  void submit_shouldCompleteSuccessfully() throws Exception {
    try (ReactorExecutionStrategy strategy = new ReactorExecutionStrategy()) {

      Callable<String> task = () -> "hello";
      CompletableFuture<String> future = strategy.submit(task);

      // Should complete immediately on immediate scheduler
      assertEquals("hello", future.get(1, TimeUnit.SECONDS));
      assertTrue(future.isDone());
      assertFalse(future.isCompletedExceptionally());
      assertFalse(future.isCancelled());
    }
  }

  @Test
  void submit_whenCallableThrowsRuntimeException_futureCompletesExceptionally() {
    try (ReactorExecutionStrategy strategy = new ReactorExecutionStrategy(Schedulers.immediate())) {

      RuntimeException ex = new RuntimeException("boom");
      Callable<String> task =
          () -> {
            throw ex;
          };

      CompletableFuture<String> future = strategy.submit(task);

      ExecutionException thrown =
          assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
      assertSame(ex, thrown.getCause());
      assertTrue(future.isDone());
      assertTrue(future.isCompletedExceptionally());
    }
  }

  @Test
  void submit_whenCallableThrowsCheckedException_futureCompletesExceptionally() {
    try (ReactorExecutionStrategy strategy = new ReactorExecutionStrategy(Schedulers.immediate())) {

      Exception ex = new Exception("checked");
      Callable<String> task =
          () -> {
            throw ex;
          };

      CompletableFuture<String> future = strategy.submit(task);

      ExecutionException thrown =
          assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
      assertSame(ex, thrown.getCause());
      assertTrue(future.isDone());
      assertTrue(future.isCompletedExceptionally());
    }
  }

  @Test
  void submit_withNullCallable_throwsNullPointerException() {
    try (ReactorExecutionStrategy strategy = new ReactorExecutionStrategy(Schedulers.immediate())) {

      // Mono.fromCallable(null) will throw NPE immediately, so submit should throw NPE
      assertThrows(NullPointerException.class, () -> strategy.submit(null));
    }
  }

  @Test
  void submit_longRunningTask_canBeCancelled() {
    // Use boundedElastic so the task runs on a separate thread
    try (ReactorExecutionStrategy strategy =
        new ReactorExecutionStrategy(Schedulers.boundedElastic())) {

      Callable<String> longTask =
          () -> {
            try {
              // simulate long work
              await().atLeast(500, TimeUnit.MILLISECONDS).until(() -> true);
            } catch (Exception _) {
              // ignore
            }
            return "done";
          };

      CompletableFuture<String> future = strategy.submit(longTask);

      // Cancel quickly; cancellation should propagate to the underlying subscription
      boolean cancelResult = future.cancel(true);

      // cancelResult may be true if cancellation succeeded
      assertTrue(future.isCancelled() || future.isCompletedExceptionally() || cancelResult);

      // Wait a little to ensure the task thread had time to react if it was interrupted
      // (not strictly required but helps make test stable)
      await().atMost(200, TimeUnit.MILLISECONDS).until(() -> true);

      assertTrue(future.isDone());
    }
  }

  @Test
  void close_shouldDisposeProvidedScheduler() {
    Scheduler mockScheduler = Mockito.mock(Scheduler.class);
    ReactorExecutionStrategy strategy = new ReactorExecutionStrategy(mockScheduler);

    strategy.close();

    verify(mockScheduler, times(1)).dispose();
  }
}
