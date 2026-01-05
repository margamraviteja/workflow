package com.workflow.helper;

import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;

/**
 * Utility class providing helper methods for working with CompletableFutures in workflow execution.
 *
 * <p><b>Primary Purpose:</b> Handles coordination of multiple futures with optional fail-fast
 * behavior for efficient parallel workflow execution. This is particularly useful in {@link
 * com.workflow.ParallelWorkflow} and similar constructs.
 *
 * <p><b>Fail-Fast Semantics:</b> When {@code failFast=true}:
 *
 * <ul>
 *   <li>If any future fails, all other futures are immediately canceled
 *   <li>The combined future completes with the first error encountered
 *   <li>Prevents unnecessary work when early failure is preferred
 *   <li>Useful for short-circuit scenarios (e.g., one failed validation fails entire workflow)
 * </ul>
 *
 * <p><b>Normal Mode Semantics:</b> When {@code failFast=false}:
 *
 * <ul>
 *   <li>All futures are allowed to complete
 *   <li>The combined future fails if any constituent future fails
 *   <li>First error is propagated; subsequent errors are suppressed
 * </ul>
 *
 * <p><b>Example usage - parallel tasks with fail-fast:</b>
 *
 * <pre>{@code
 * List<CompletableFuture<WorkflowResult>> futures = List.of(
 *     strategy.submit(() -> task1.execute(ctx)),
 *     strategy.submit(() -> task2.execute(ctx)),
 *     strategy.submit(() -> task3.execute(ctx))
 * );
 *
 * CompletableFuture<Void> all = FutureUtils.allOf(futures, true);
 * all.thenAccept(_ -> System.out.println("All tasks completed"));
 * all.exceptionally(ex -> {
 *     System.err.println("Task failed: " + ex.getMessage());
 *     return null;
 * });
 * }</pre>
 *
 * @see CompletableFuture
 * @see com.workflow.ParallelWorkflow
 */
@UtilityClass
public class FutureUtils {

  /**
   * Safely attempts to cancel the execution of a {@link Future} task.
   *
   * <p>This method provides a null-safe and exception-safe wrapper around the standard {@link
   * Future#cancel(boolean)} method. It is designed to be used in cleanup blocks or timeout handlers
   * where the caller needs to ensure a task is stopped without dealing with the nuances of the
   * Future's current state.
   *
   * <p><b>Interruption Behavior:</b> This method passes {@code true} to the {@code
   * mayInterruptIfRunning} parameter. If the task has already started, the thread executing the
   * task will be interrupted. For this to be effective, the code running inside the Future must be
   * responsive to interrupts (e.g., checking {@link Thread#interrupted()} or catching {@link
   * InterruptedException}).
   *
   * <p><b>Implementation Details:</b>
   *
   * <ul>
   *   <li>Checks if the future is {@code null} before proceeding.
   *   <li>Checks {@link Future#isDone()} to avoid unnecessary cancellation attempts on completed or
   *       already canceled tasks.
   *   <li>Swallows all exceptions (using the {@code _} unnamed pattern for Java 21+) to ensure that
   *       cleanup logic does not interfere with the primary exception handling flow.
   * </ul>
   *
   * @param future the future representing the pending or running task; may be {@code null}.
   * @see Future#cancel(boolean)
   * @see Thread#interrupt()
   */
  public static void cancelFuture(Future<?> future) {
    if (future != null && !future.isDone()) {
      try {
        // Passes true to ensure running threads are signaled to stop.
        future.cancel(true);
      } catch (Exception _) {
        // Silent catch to ensure robust cleanup during workflow teardown.
      }
    }
  }

  /**
   * Combines multiple futures into a single future that completes when all complete.
   *
   * <p><b>Null/Empty Handling:</b> Returns immediately completed future if list is null or empty.
   *
   * <p><b>Fail-Fast Behavior (failFast=true):</b>
   *
   * <ul>
   *   <li>Attaches handlers to each future that monitor for failures
   *   <li>On first failure, immediately cancels all other futures
   *   <li>Combined future completes exceptionally with the first error
   *   <li>Preferred for workflows where early failure prevents further work
   * </ul>
   *
   * <p><b>Normal Behavior (failFast=false):</b>
   *
   * <ul>
   *   <li>Waits for all futures to complete (success or failure)
   *   <li>If any future fails, combined future fails with the first error
   *   <li>If all succeed, combined future completes normally
   *   <li>Preferred for all-or-nothing workflows where all work must complete
   * </ul>
   *
   * <p><b>Exception Handling:</b>
   *
   * <ul>
   *   <li>CompletionExceptions are unwrapped to expose underlying cause
   *   <li>WorkflowResult failures are checked for error details
   *   <li>If result is failed with no error object, synthetic RuntimeException is created
   * </ul>
   *
   * @param futures list of futures to combine (maybe null or empty)
   * @param failFast if true, cancel all futures when any fails; if false, wait for all
   * @return a combined CompletableFuture<Void> that completes when all futures complete; never null
   * @throws NullPointerException never - gracefully handles null lists
   */
  public static CompletableFuture<Void> allOf(
      List<CompletableFuture<WorkflowResult>> futures, boolean failFast) {
    if (futures == null || futures.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<Void> combined = new CompletableFuture<>();
    AtomicBoolean failureHandled = new AtomicBoolean(false);

    if (failFast) {
      // Attach failure handler to each future for fail-fast behavior
      for (CompletableFuture<WorkflowResult> future : futures) {
        future.whenComplete(
            (result, ex) -> {
              // Determine if this completion represents an error first
              Throwable error = getThrowable(result, ex);

              // Only handle the first failure to avoid race conditions
              if (error != null && failureHandled.compareAndSet(false, true)) {
                // Cancel other futures (skip the one that triggered this handler)
                cancelFuturesExcept(futures, future);

                completeFutureWithException(combined, error);
              }
            });
      }
    }

    // Normal allOf behavior - wait for all futures to complete
    CompletableFuture<Void> allOfFuture =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));

    allOfFuture.whenComplete(
        (_, ex) -> {
          if (combined.isDone()) {
            return; // Already completed due to fail-fast
          }
          if (ex != null) {
            combined.completeExceptionally(unwrapCompletionException(ex));
            return;
          }

          // Check if any result has failed status (for non-fail-fast mode)
          Throwable firstError = getFirstError(futures);

          completeFutureWithException(combined, firstError);
        });

    return combined;
  }

  private static Throwable getFirstError(List<CompletableFuture<WorkflowResult>> futures) {
    for (CompletableFuture<WorkflowResult> future : futures) {
      try {
        WorkflowResult result = future.getNow(null);
        if (result != null && result.getStatus() == WorkflowStatus.FAILED) {
          return result.getError();
        }
      } catch (Exception e) {
        return e;
      }
    }
    return null;
  }

  private static void completeFutureWithException(
      CompletableFuture<Void> combined, Throwable cause) {
    // Complete combined future exceptionally if not already done
    if (combined.isDone()) {
      return;
    }
    if (cause != null) {
      // Prefer underlying cause if wrapped
      combined.completeExceptionally(unwrapCompletionException(cause));
    } else {
      combined.complete(null);
    }
  }

  private static Throwable getThrowable(WorkflowResult result, Throwable ex) {
    if (ex != null) {
      return ex;
    } else if (result != null && result.getStatus() == WorkflowStatus.FAILED) {
      return result.getError() != null
          ? result.getError()
          : new RuntimeException("Workflow failed with no error details");
    }
    return null;
  }

  private static void cancelFuturesExcept(
      List<CompletableFuture<WorkflowResult>> futures, CompletableFuture<WorkflowResult> skip) {
    futures.forEach(
        f -> {
          if (f == skip || f.isDone()) return;
          try {
            f.cancel(true);
          } catch (Exception _) {
            // ignore
          }
        });
  }

  private static Throwable unwrapCompletionException(Throwable t) {
    if (t instanceof CompletionException && t.getCause() != null) {
      return t.getCause();
    }
    return t;
  }
}
