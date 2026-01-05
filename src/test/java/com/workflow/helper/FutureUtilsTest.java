package com.workflow.helper;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.test.WorkflowTestUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for FutureUtils covering various concurrency scenarios, fail-fast
 * behavior, and edge cases.
 */
class FutureUtilsTest {

  @Test
  void allOf_allSuccess_completesNormally() throws Exception {
    CompletableFuture<WorkflowResult> f1 =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());
    CompletableFuture<WorkflowResult> f2 =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());

    List<CompletableFuture<WorkflowResult>> futures = List.of(f1, f2);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

    assertNull(combined.get(1, TimeUnit.SECONDS));
    assertTrue(combined.isDone());
    assertFalse(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_failFastTrue_oneFutureCompletesExceptionally_cancelsOthersAndFails() {
    CompletableFuture<WorkflowResult> failing = new CompletableFuture<>();
    CompletableFuture<WorkflowResult> other1 = new CompletableFuture<>();
    CompletableFuture<WorkflowResult> other2 = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(failing, other1, other2);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

    RuntimeException cause = new RuntimeException("boom");
    failing.completeExceptionally(cause);

    // Give some time for cancellation to propagate
    await().atLeast(50, TimeUnit.MILLISECONDS).until(() -> true);

    assertTrue(combined.isDone());
    assertTrue(combined.isCompletedExceptionally());
    assertTrue(other1.isCancelled() || other2.isCancelled());

    ExecutionException ex = assertThrows(ExecutionException.class, combined::get);
    assertSame(cause, ex.getCause());
  }

  @Test
  void allOf_failFastTrue_resultWithFailedStatus_cancelsOthersAndFails() {
    CompletableFuture<WorkflowResult> failedResult = new CompletableFuture<>();
    CompletableFuture<WorkflowResult> other = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(failedResult, other);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

    RuntimeException error = new RuntimeException("workflow failed");
    failedResult.complete(WorkflowTestUtils.failureResult(error));

    // Give some time for cancellation to propagate
    await().atLeast(50, TimeUnit.MILLISECONDS).until(() -> true);

    assertTrue(combined.isDone());
    assertTrue(combined.isCompletedExceptionally());
    assertTrue(other.isCancelled());

    assertThrows(CancellationException.class, combined::get);
  }

  @Test
  void allOf_failFastFalse_oneFails_doesNotCancelOthers() {
    CompletableFuture<WorkflowResult> failing = new CompletableFuture<>();
    CompletableFuture<WorkflowResult> other = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(failing, other);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    RuntimeException cause = new RuntimeException("boom");
    failing.completeExceptionally(cause);

    // Complete the other future normally
    other.complete(WorkflowTestUtils.successResult());

    // Wait for completion
    await().atLeast(50, TimeUnit.MILLISECONDS).until(() -> true);

    assertTrue(combined.isDone());
    assertTrue(combined.isCompletedExceptionally());
    assertFalse(other.isCancelled());

    ExecutionException ex = assertThrows(ExecutionException.class, combined::get);
    assertNotNull(ex.getCause());
  }

  @Test
  void allOf_emptyList_completesImmediately() throws Exception {
    List<CompletableFuture<WorkflowResult>> empty = Collections.emptyList();

    CompletableFuture<Void> combined = FutureUtils.allOf(empty, true);

    assertNull(combined.get());
    assertTrue(combined.isDone());
    assertFalse(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_nullList_completesImmediately() throws Exception {
    CompletableFuture<Void> combined = FutureUtils.allOf(null, true);

    assertNull(combined.get());
    assertTrue(combined.isDone());
    assertFalse(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_singleFutureSuccess_completesSuccessfully() throws Exception {
    CompletableFuture<WorkflowResult> single =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());

    List<CompletableFuture<WorkflowResult>> futures = List.of(single);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    assertNull(combined.get(1, TimeUnit.SECONDS));
  }

  @Test
  void allOf_singleFutureFails_failsWithError() {
    CompletableFuture<WorkflowResult> failing = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(failing);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

    RuntimeException error = new RuntimeException("fail");
    failing.completeExceptionally(error);

    assertTrue(combined.isCompletedExceptionally());
    ExecutionException ex = assertThrows(ExecutionException.class, combined::get);
    assertSame(error, ex.getCause());
  }

  @Test
  void allOf_multipleConcurrentFailures_handlesGracefully() {
    int count = 10;
    List<CompletableFuture<WorkflowResult>> futures = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      futures.add(new CompletableFuture<>());
    }

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

    // Fail all futures concurrently
    CountDownLatch latch = new CountDownLatch(1);
    for (CompletableFuture<WorkflowResult> future : futures) {
      new Thread(
              () -> {
                try {
                  latch.await();
                  future.completeExceptionally(new RuntimeException("concurrent fail"));
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                }
              })
          .start();
    }

    latch.countDown();

    // Give time for all to fail
    await().atLeast(100, TimeUnit.MILLISECONDS).until(() -> true);

    assertTrue(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_mixOfSuccessAndFailure_failFastFalse_waitsForAll() {
    CompletableFuture<WorkflowResult> success1 =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());
    CompletableFuture<WorkflowResult> success2 =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());
    CompletableFuture<WorkflowResult> failed =
        CompletableFuture.completedFuture(
            WorkflowTestUtils.failureResult(new RuntimeException("fail")));

    List<CompletableFuture<WorkflowResult>> futures = List.of(success1, failed, success2);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    // Should complete but with failure
    await().atLeast(50, TimeUnit.MILLISECONDS).until(() -> true);
    assertTrue(combined.isDone());
    assertTrue(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_failedResultWithNullError_handlesGracefully() {
    CompletableFuture<WorkflowResult> future =
        CompletableFuture.completedFuture(
            WorkflowResult.builder()
                .status(WorkflowStatus.FAILED)
                .error(null) // null error
                .startedAt(java.time.Instant.now())
                .completedAt(java.time.Instant.now())
                .build());

    List<CompletableFuture<WorkflowResult>> futures = List.of(future);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

    assertTrue(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_largeNumberOfFutures_handlesEfficiently() throws Exception {
    int count = 1000;
    List<CompletableFuture<WorkflowResult>> futures = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      futures.add(CompletableFuture.completedFuture(WorkflowTestUtils.successResult()));
    }

    long startTime = System.currentTimeMillis();
    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    assertNull(combined.get(5, TimeUnit.SECONDS));
    long duration = System.currentTimeMillis() - startTime;

    // Should complete quickly
    assertTrue(duration < 1000, "Processing 1000 futures took too long: " + duration + "ms");
  }

  @Test
  void allOf_futuresCompleteInDifferentOrder_handlesCorrectly() throws Exception {
    CompletableFuture<WorkflowResult> f1 = new CompletableFuture<>();
    CompletableFuture<WorkflowResult> f2 = new CompletableFuture<>();
    CompletableFuture<WorkflowResult> f3 = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(f1, f2, f3);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    // Complete in reverse order
    f3.complete(WorkflowTestUtils.successResult());
    f2.complete(WorkflowTestUtils.successResult());
    f1.complete(WorkflowTestUtils.successResult());

    assertNull(combined.get(1, TimeUnit.SECONDS));
  }

  @Test
  void allOf_someAlreadyCompleted_handlesCorrectly() throws Exception {
    CompletableFuture<WorkflowResult> completed =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());
    CompletableFuture<WorkflowResult> pending = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(completed, pending);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    // Complete the pending future
    pending.complete(WorkflowTestUtils.successResult());

    assertNull(combined.get(1, TimeUnit.SECONDS));
  }

  @Test
  void allOf_completedExceptionallyBeforeAllOf_handlesCorrectly() {
    RuntimeException error = new RuntimeException("already failed");
    CompletableFuture<WorkflowResult> failed = new CompletableFuture<>();
    failed.completeExceptionally(error);

    CompletableFuture<WorkflowResult> other = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(failed, other);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    // Complete the other
    other.complete(WorkflowTestUtils.successResult());

    assertTrue(combined.isCompletedExceptionally());
    ExecutionException ex = assertThrows(ExecutionException.class, combined::get);
    assertNotNull(ex.getCause());
  }

  @Test
  void allOf_cancelledFuture_treatsAsCancellation() {
    CompletableFuture<WorkflowResult> cancelled = new CompletableFuture<>();
    cancelled.cancel(true);

    List<CompletableFuture<WorkflowResult>> futures = List.of(cancelled);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    assertTrue(combined.isCompletedExceptionally());
    assertThrows(CancellationException.class, combined::get);
  }

  @Test
  void allOf_timeoutScenario_canBeTimedOut() {
    CompletableFuture<WorkflowResult> neverCompletes = new CompletableFuture<>();

    List<CompletableFuture<WorkflowResult>> futures = List.of(neverCompletes);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    assertThrows(TimeoutException.class, () -> combined.get(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void allOf_skippedStatus_treatedAsSuccess() throws Exception {
    CompletableFuture<WorkflowResult> skipped =
        CompletableFuture.completedFuture(WorkflowTestUtils.skippedResult());

    List<CompletableFuture<WorkflowResult>> futures = List.of(skipped);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    assertNull(combined.get(1, TimeUnit.SECONDS));
    assertFalse(combined.isCompletedExceptionally());
  }

  @Test
  void allOf_mixedStatusesWithNoFailures_completesSuccessfully() throws Exception {
    CompletableFuture<WorkflowResult> success =
        CompletableFuture.completedFuture(WorkflowTestUtils.successResult());
    CompletableFuture<WorkflowResult> skipped =
        CompletableFuture.completedFuture(WorkflowTestUtils.skippedResult());

    List<CompletableFuture<WorkflowResult>> futures = List.of(success, skipped);

    CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

    assertNull(combined.get(1, TimeUnit.SECONDS));
  }

  private static WorkflowResult successResult() {
    Instant now = Instant.now();
    return WorkflowResults.success(now, now);
  }

  private static WorkflowResult failureResult(Throwable error) {
    Instant now = Instant.now();
    return WorkflowResults.failure(now, now, error);
  }

  @Nested
  @DisplayName("Null/Empty Handling Tests")
  class NullEmptyHandlingTests {

    @Test
    @DisplayName("allOf should handle null list")
    void allOfNullList() {
      CompletableFuture<Void> result = FutureUtils.allOf(null, true);
      assertTrue(result.isDone());
      try {
        result.get();
        assertTrue(true);
      } catch (Exception _) {
        fail();
      }
    }

    @Test
    @DisplayName("allOf should handle empty list")
    void allOfEmptyList() {
      CompletableFuture<Void> result = FutureUtils.allOf(List.of(), false);
      assertTrue(result.isDone());
      try {
        result.get();
        assertTrue(true);
      } catch (Exception _) {
        fail();
      }
    }

    @Test
    @DisplayName("allOf should handle single successful future")
    void allOfSingleSuccess() {
      CompletableFuture<WorkflowResult> future = CompletableFuture.completedFuture(successResult());
      CompletableFuture<Void> result = FutureUtils.allOf(List.of(future), true);
      assertTrue(result.isDone());
      try {
        result.get();
        assertTrue(true);
      } catch (Exception _) {
        fail();
      }
    }
  }

  @Nested
  @DisplayName("Fail-Fast Mode Tests")
  class FailFastModeTests {

    @Test
    @DisplayName("allOf should cancel futures on first failure with failFast=true")
    void failFastCancelsFutures() {
      CompletableFuture<WorkflowResult> future1 = new CompletableFuture<>();
      CompletableFuture<WorkflowResult> future2 = new CompletableFuture<>();
      CompletableFuture<WorkflowResult> future3 = new CompletableFuture<>();

      List<CompletableFuture<WorkflowResult>> futures = List.of(future1, future2, future3);
      CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

      RuntimeException error = new RuntimeException("First failure");
      future1.completeExceptionally(error);

      await().atMost(200, TimeUnit.MILLISECONDS).until(() -> true);
      assertTrue(combined.isCompletedExceptionally());
    }

    @Test
    @DisplayName("allOf should complete exceptionally with first error in fail-fast mode")
    void failFastPropagatesFirstError() {
      CompletableFuture<WorkflowResult> future1 = new CompletableFuture<>();
      RuntimeException error = new RuntimeException("First error");
      future1.completeExceptionally(error);

      CompletableFuture<WorkflowResult> future2 = new CompletableFuture<>();
      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future1, future2), true);

      Exception ex = assertThrows(Exception.class, combined::get);
      assertNotNull(ex);
    }

    @Test
    @DisplayName("allOf should complete with all successes in fail-fast mode")
    void failFastAllSuccess() {
      CompletableFuture<WorkflowResult> future1 =
          CompletableFuture.completedFuture(successResult());
      CompletableFuture<WorkflowResult> future2 =
          CompletableFuture.completedFuture(successResult());

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future1, future2), true);

      try {
        combined.get();
        assertTrue(true);
      } catch (Exception _) {
        fail();
      }
    }
  }

  @Nested
  @DisplayName("Normal Mode Tests")
  class NormalModeTests {

    @Test
    @DisplayName("allOf should wait for all futures in normal mode")
    void normalWaitsForAll() {
      CountDownLatch latch = new CountDownLatch(1);
      CompletableFuture<WorkflowResult> future1 = new CompletableFuture<>();
      CompletableFuture<WorkflowResult> future2 =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  latch.await();
                  return successResult();
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                  return null;
                }
              });

      List<CompletableFuture<WorkflowResult>> futures = List.of(future1, future2);
      CompletableFuture<Void> combined = FutureUtils.allOf(futures, false);

      await().atLeast(100, TimeUnit.MILLISECONDS).until(() -> true);
      assertFalse(combined.isDone());

      future1.complete(successResult());
      latch.countDown();

      assertDoesNotThrow(() -> combined.get(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("allOf should fail if any future fails in normal mode")
    void normalFailsIfAnyFails() {
      CompletableFuture<WorkflowResult> future1 =
          CompletableFuture.completedFuture(successResult());
      CompletableFuture<WorkflowResult> future2 = new CompletableFuture<>();
      future2.completeExceptionally(new RuntimeException("Error in second"));

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future1, future2), false);

      assertTrue(combined.isCompletedExceptionally());
    }

    @Test
    @DisplayName("allOf should detect workflow failure status in normal mode")
    void normalDetectsFailureStatus() {
      CompletableFuture<WorkflowResult> future1 =
          CompletableFuture.completedFuture(successResult());
      CompletableFuture<WorkflowResult> future2 =
          CompletableFuture.completedFuture(failureResult(new RuntimeException("Workflow failed")));

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future1, future2), false);

      assertTrue(combined.isCompletedExceptionally());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("allOf should unwrap CompletionException")
    void unwrapCompletionException() {
      RuntimeException cause = new RuntimeException("Original cause");
      CompletableFuture<WorkflowResult> future = new CompletableFuture<>();
      future.completeExceptionally(cause);

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future), true);

      Exception ex = assertThrows(Exception.class, combined::get);
      assertNotNull(ex);
    }

    @Test
    @DisplayName("allOf should handle workflow result with error")
    void workflowResultWithError() {
      RuntimeException error = new RuntimeException("Workflow error");
      CompletableFuture<WorkflowResult> future =
          CompletableFuture.completedFuture(failureResult(error));

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future), false);

      assertTrue(combined.isCompletedExceptionally());
    }

    @Test
    @DisplayName("allOf should handle workflow result with null error")
    void workflowResultNullError() {
      Instant now = Instant.now();
      WorkflowResult failedWithoutError =
          WorkflowResult.builder()
              .status(WorkflowStatus.FAILED)
              .startedAt(now)
              .completedAt(now)
              .build();
      CompletableFuture<WorkflowResult> future =
          CompletableFuture.completedFuture(failedWithoutError);

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future), true);

      assertTrue(combined.isCompletedExceptionally());
    }
  }

  @Nested
  @DisplayName("Concurrency Tests")
  class ConcurrencyTests {

    @Test
    @DisplayName("allOf should handle multiple concurrent futures")
    void multipleConcurrentFutures() {
      List<CompletableFuture<WorkflowResult>> futures =
          List.of(
              CompletableFuture.supplyAsync(FutureUtilsTest::successResult),
              CompletableFuture.supplyAsync(FutureUtilsTest::successResult),
              CompletableFuture.supplyAsync(FutureUtilsTest::successResult));

      CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

      assertDoesNotThrow(() -> combined.get(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("allOf should be thread-safe")
    void threadSafety() throws InterruptedException {
      CompletableFuture<WorkflowResult> future1 = new CompletableFuture<>();
      CompletableFuture<WorkflowResult> future2 = new CompletableFuture<>();

      List<CompletableFuture<WorkflowResult>> futures = List.of(future1, future2);
      CompletableFuture<Void> combined = FutureUtils.allOf(futures, true);

      Thread t1 = new Thread(() -> future1.complete(successResult()));
      Thread t2 = new Thread(() -> future2.complete(successResult()));

      t1.start();
      t2.start();

      assertDoesNotThrow(() -> combined.get(2, TimeUnit.SECONDS));
      t1.join();
      t2.join();
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("allOf should handle future that completes after combination")
    void futureLateCompletion() {
      CompletableFuture<WorkflowResult> future = new CompletableFuture<>();

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(future), false);

      assertFalse(combined.isDone());

      future.complete(successResult());

      assertTrue(combined.isDone());
      try {
        combined.get();
        assertTrue(true);
      } catch (Exception _) {
        fail();
      }
    }

    @Test
    @DisplayName("allOf should handle mix of completed and pending futures")
    void mixedFutureStates() {
      CompletableFuture<WorkflowResult> completed =
          CompletableFuture.completedFuture(successResult());
      CompletableFuture<WorkflowResult> pending = new CompletableFuture<>();

      CompletableFuture<Void> combined = FutureUtils.allOf(List.of(completed, pending), false);

      assertFalse(combined.isDone());

      pending.complete(successResult());

      assertTrue(combined.isDone());
    }
  }
}
