package com.workflow.test;

import static org.mockito.Mockito.*;

import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.context.WorkflowContext;
import com.workflow.helper.WorkflowResults;
import com.workflow.sleeper.ThreadSleepingSleeper;
import com.workflow.task.Task;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.Assertions;

/**
 * Enhanced utility class providing helper methods for workflow testing with reduced duplication.
 *
 * <p>This class eliminates common test boilerplate by providing:
 *
 * <ul>
 *   <li>Mock workflow creation with various behaviors
 *   <li>Result builders for all status types
 *   <li>Context creation and management helpers
 *   <li>Assertion helpers with better error messages
 *   <li>Async testing utilities
 *   <li>Performance testing helpers
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Test
 * void testWorkflow() {
 *     // Create test data
 *     WorkflowContext context = WorkflowTestUtils.contextWith("key", "value");
 *     Workflow workflow = WorkflowTestUtils.successfulWorkflow("test");
 *
 *     // Execute and assert
 *     WorkflowResult result = workflow.execute(context);
 *     WorkflowTestUtils.assertSuccess(result);
 *     WorkflowTestUtils.assertExecutionTime(result, Duration.ofSeconds(1));
 * }
 * }</pre>
 */
@UtilityClass
public class WorkflowTestUtils {

  // ==================== Result Builders ====================

  /**
   * Create a successful WorkflowResult with current timestamps.
   *
   * @return a SUCCESS WorkflowResult
   */
  public static WorkflowResult successResult() {
    return successResult(Instant.now());
  }

  /**
   * Create a successful WorkflowResult with the specified start time.
   *
   * @param startedAt when execution started
   * @return a SUCCESS WorkflowResult
   */
  public static WorkflowResult successResult(Instant startedAt) {
    return WorkflowResults.success(startedAt, Instant.now());
  }

  /**
   * Create a failed WorkflowResult with the given error.
   *
   * @param error the cause of failure
   * @return a FAILED WorkflowResult
   */
  public static WorkflowResult failureResult(Throwable error) {
    return failureResult(Instant.now(), error);
  }

  /**
   * Create a failed WorkflowResult with the specified start time and error.
   *
   * @param startedAt when execution started
   * @param error the cause of failure
   * @return a FAILED WorkflowResult
   */
  public static WorkflowResult failureResult(Instant startedAt, Throwable error) {
    return WorkflowResults.failure(startedAt, Instant.now(), error);
  }

  /**
   * Create a skipped WorkflowResult.
   *
   * @return a SKIPPED WorkflowResult
   */
  public static WorkflowResult skippedResult() {
    return WorkflowResult.builder()
        .status(WorkflowStatus.SKIPPED)
        .startedAt(Instant.now())
        .completedAt(Instant.now())
        .build();
  }

  // ==================== Mock Workflow Builders ====================

  /**
   * Create a mock Workflow that returns a successful result.
   *
   * @return a mocked Workflow
   */
  public static Workflow mockSuccessfulWorkflow() {
    return mockSuccessfulWorkflow("mock-workflow");
  }

  /**
   * Create a mock Workflow with a specific name that returns a successful result.
   *
   * @param name the workflow name
   * @return a mocked Workflow
   */
  public static Workflow mockSuccessfulWorkflow(String name) {
    Workflow workflow = mock(Workflow.class);
    when(workflow.getName()).thenReturn(name);
    when(workflow.execute(any(WorkflowContext.class))).thenReturn(successResult());
    return workflow;
  }

  /**
   * Create a mock Workflow that returns a failed result.
   *
   * @param error the error to include in the result
   * @return a mocked Workflow
   */
  public static Workflow mockFailingWorkflow(Throwable error) {
    return mockFailingWorkflow("mock-failing-workflow", error);
  }

  /**
   * Create a mock Workflow with a specific name that returns a failed result.
   *
   * @param name the workflow name
   * @param error the error to include in the result
   * @return a mocked Workflow
   */
  public static Workflow mockFailingWorkflow(String name, Throwable error) {
    Workflow workflow = mock(Workflow.class);
    when(workflow.getName()).thenReturn(name);
    when(workflow.execute(any(WorkflowContext.class))).thenReturn(failureResult(error));
    return workflow;
  }

  /**
   * Create a mock Workflow that throws an exception when executed.
   *
   * @param exception the exception to throw
   * @return a mocked Workflow
   */
  public static Workflow mockThrowingWorkflow(RuntimeException exception) {
    return mockThrowingWorkflow("mock-throwing-workflow", exception);
  }

  /**
   * Create a mock Workflow with a specific name that throws an exception when executed.
   *
   * @param name the workflow name
   * @param exception the exception to throw
   * @return a mocked Workflow
   */
  public static Workflow mockThrowingWorkflow(String name, RuntimeException exception) {
    Workflow workflow = mock(Workflow.class);
    when(workflow.getName()).thenReturn(name);
    when(workflow.execute(any(WorkflowContext.class))).thenThrow(exception);
    return workflow;
  }

  /**
   * Create a mock Workflow that returns null when executed (for testing error handling).
   *
   * @return a mocked Workflow
   */
  public static Workflow mockNullReturningWorkflow() {
    return mockNullReturningWorkflow("mock-null-workflow");
  }

  /**
   * Create a mock Workflow with a specific name that returns null when executed.
   *
   * @param name the workflow name
   * @return a mocked Workflow
   */
  public static Workflow mockNullReturningWorkflow(String name) {
    Workflow workflow = mock(Workflow.class);
    when(workflow.getName()).thenReturn(name);
    when(workflow.execute(any(WorkflowContext.class))).thenReturn(null);
    return workflow;
  }

  /**
   * Create a mock Workflow that returns SKIPPED status.
   *
   * @param name the workflow name
   * @return a mocked Workflow
   */
  public static Workflow mockSkippedWorkflow(String name) {
    Workflow workflow = mock(Workflow.class);
    when(workflow.getName()).thenReturn(name);
    when(workflow.execute(any(WorkflowContext.class))).thenReturn(skippedResult());
    return workflow;
  }

  /**
   * Create a mock Workflow with custom behavior.
   *
   * @param name the workflow name
   * @param behavior custom execution behavior
   * @return a mocked Workflow
   */
  public static Workflow mockWorkflowWithBehavior(String name, Consumer<WorkflowContext> behavior) {
    Workflow workflow = mock(Workflow.class);
    when(workflow.getName()).thenReturn(name);
    when(workflow.execute(any(WorkflowContext.class)))
        .thenAnswer(
            invocation -> {
              WorkflowContext ctx = invocation.getArgument(0);
              behavior.accept(ctx);
              return successResult();
            });
    return workflow;
  }

  // ==================== Context Builders ====================

  /**
   * Create a mock WorkflowContext.
   *
   * @return a mocked WorkflowContext
   */
  public static WorkflowContext mockContext() {
    return mock(WorkflowContext.class);
  }

  /**
   * Create a real WorkflowContext.
   *
   * @return a new WorkflowContext instance
   */
  public static WorkflowContext createContext() {
    return new WorkflowContext();
  }

  /**
   * Create a WorkflowContext with a single key-value pair.
   *
   * @param key the context key
   * @param value the context value
   * @return a new WorkflowContext with the specified entry
   */
  public static WorkflowContext contextWith(String key, Object value) {
    WorkflowContext context = new WorkflowContext();
    context.put(key, value);
    return context;
  }

  /**
   * Create a WorkflowContext with multiple key-value pairs.
   *
   * @param entries key-value pairs (must be even number of arguments)
   * @return a new WorkflowContext with the specified entries
   */
  public static WorkflowContext contextWith(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException("Must provide even number of arguments (key-value pairs)");
    }
    WorkflowContext context = new WorkflowContext();
    for (int i = 0; i < entries.length; i += 2) {
      context.put((String) entries[i], entries[i + 1]);
    }
    return context;
  }

  // ==================== Enhanced Assertions ====================

  /**
   * Assert that a WorkflowResult is successful.
   *
   * @param result the result to check
   * @throws AssertionError if the result is not successful
   */
  public static void assertSuccess(WorkflowResult result) {
    Assertions.assertNotNull(result, "WorkflowResult is null");
    Assertions.assertEquals(
        WorkflowStatus.SUCCESS,
        result.getStatus(),
        () ->
            "Expected SUCCESS but got "
                + result.getStatus()
                + (result.getError() != null
                    ? " with error: " + result.getError().getMessage()
                    : ""));
  }

  /**
   * Assert that a WorkflowResult failed.
   *
   * @param result the result to check
   * @throws AssertionError if the result is not failed
   */
  public static void assertFailed(WorkflowResult result) {
    Assertions.assertNotNull(result, "WorkflowResult is null");
    Assertions.assertEquals(
        WorkflowStatus.FAILED, result.getStatus(), "Expected FAILED but got " + result.getStatus());
  }

  /**
   * Assert that a WorkflowResult failed with a specific error type.
   *
   * @param result the result to check
   * @param expectedErrorType the expected error class
   */
  public static void assertFailedWith(
      WorkflowResult result, Class<? extends Throwable> expectedErrorType) {
    assertFailed(result);
    Assertions.assertNotNull(result.getError(), "Expected error but got null");
    Assertions.assertInstanceOf(
        expectedErrorType,
        result.getError(),
        "Expected error type " + expectedErrorType.getSimpleName());
  }

  /**
   * Assert that a WorkflowResult was skipped.
   *
   * @param result the result to check
   * @throws AssertionError if the result is not skipped
   */
  public static void assertSkipped(WorkflowResult result) {
    Assertions.assertNotNull(result, "WorkflowResult is null");
    Assertions.assertEquals(
        WorkflowStatus.SKIPPED,
        result.getStatus(),
        "Expected SKIPPED but got " + result.getStatus());
  }

  /**
   * Assert that context contains expected key with expected value.
   *
   * @param context the workflow context
   * @param key the expected key
   * @param expectedValue the expected value
   */
  public static void assertContextContains(
      WorkflowContext context, String key, Object expectedValue) {
    Assertions.assertTrue(context.containsKey(key), "Context missing key: " + key);
    Assertions.assertEquals(
        expectedValue, context.get(key), "Context value mismatch for key: " + key);
  }

  /**
   * Assert that context does not contain specified key.
   *
   * @param context the workflow context
   * @param key the key that should not exist
   */
  public static void assertContextNotContains(WorkflowContext context, String key) {
    Assertions.assertFalse(context.containsKey(key), "Context should not contain key: " + key);
  }

  // ==================== Performance Testing Helpers ====================

  /**
   * Measure workflow execution time.
   *
   * @param workflow the workflow to execute
   * @param context the execution context
   * @return the execution duration
   */
  public static Duration measureExecutionTime(Workflow workflow, WorkflowContext context) {
    Instant start = Instant.now();
    workflow.execute(context);
    return Duration.between(start, Instant.now());
  }

  /**
   * Execute workflow multiple times and return average duration.
   *
   * @param workflow the workflow to execute
   * @param iterations number of iterations
   * @return average execution duration
   */
  public static Duration averageExecutionTime(Workflow workflow, int iterations) {
    long totalMillis = 0;
    for (int i = 0; i < iterations; i++) {
      WorkflowContext context = createContext();
      Duration duration = measureExecutionTime(workflow, context);
      totalMillis += duration.toMillis();
    }
    return Duration.ofMillis(totalMillis / iterations);
  }

  // ==================== Task Helpers ====================

  /**
   * Create a simple task that sets a context value.
   *
   * @param key the context key
   * @param value the value to set
   * @return a task that sets the specified key-value pair
   */
  public static Task taskThatSets(String key, Object value) {
    return context -> context.put(key, value);
  }

  /**
   * Create a task that throws an exception.
   *
   * @param exception the exception to throw
   * @return a task that throws the specified exception
   */
  public static Task taskThatThrows(RuntimeException exception) {
    return _ -> {
      throw exception;
    };
  }

  /**
   * Create a task that sleeps for specified duration.
   *
   * @param duration how long to sleep
   * @return a task that sleeps
   */
  public static Task taskThatSleeps(Duration duration) {
    return _ -> {
      try {
        new ThreadSleepingSleeper().sleep(duration);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted", e);
      }
    };
  }

  // ==================== Async Testing Helpers ====================

  /**
   * Execute workflow asynchronously and return CompletableFuture.
   *
   * @param workflow the workflow to execute
   * @param context the execution context
   * @return CompletableFuture of the workflow result
   */
  public static CompletableFuture<WorkflowResult> executeAsync(
      Workflow workflow, WorkflowContext context) {
    return CompletableFuture.supplyAsync(() -> workflow.execute(context));
  }

  /**
   * Wait for async workflow execution to complete with timeout.
   *
   * @param future the future to wait for
   * @param timeout maximum wait time
   * @param unit time unit
   * @return the workflow result
   * @throws Exception if execution fails or times out
   */
  public static WorkflowResult awaitResult(
      CompletableFuture<WorkflowResult> future, long timeout, TimeUnit unit) throws Exception {
    return future.get(timeout, unit);
  }
}
