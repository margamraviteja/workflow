package com.workflow;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.test.WorkflowTestUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for AbstractWorkflow covering various scenarios including normal
 * execution, error handling, null checks, and edge cases.
 */
class AbstractWorkflowTest {

  /** Simple test implementation of AbstractWorkflow for testing purposes. */
  private static class TestWorkflow extends AbstractWorkflow {
    private final String name;
    private final boolean shouldFail;
    private final boolean shouldThrow;
    private final RuntimeException exceptionToThrow;

    TestWorkflow(String name) {
      this(name, false, false, null);
    }

    TestWorkflow(String name, boolean shouldFail, boolean shouldThrow, RuntimeException exception) {
      this.name = name;
      this.shouldFail = shouldFail;
      this.shouldThrow = shouldThrow;
      this.exceptionToThrow = exception;
    }

    @Override
    protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
      if (shouldThrow) {
        throw exceptionToThrow != null ? exceptionToThrow : new RuntimeException("Test exception");
      }
      if (shouldFail) {
        return execContext.failure(new IllegalStateException("Intentional failure"));
      }
      return execContext.success();
    }

    @Override
    public String getName() {
      return name != null ? name : super.getName();
    }
  }

  @Test
  void execute_successfulWorkflow_returnsSuccess() {
    TestWorkflow workflow = new TestWorkflow("success-test");
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertNotNull(result.getStartedAt());
    assertNotNull(result.getCompletedAt());
    assertTrue(
        result.getCompletedAt().isAfter(result.getStartedAt())
            || result.getCompletedAt().equals(result.getStartedAt()));
  }

  @Test
  void execute_failingWorkflow_returnsFailure() {
    TestWorkflow workflow = new TestWorkflow("fail-test", true, false, null);
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertInstanceOf(IllegalStateException.class, result.getError());
    assertEquals("Intentional failure", result.getError().getMessage());
  }

  @Test
  void execute_throwingWorkflow_wrapsExceptionInFailureResult() {
    RuntimeException exception = new RuntimeException("Unexpected error");
    TestWorkflow workflow = new TestWorkflow("throw-test", false, true, exception);
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertSame(exception, result.getError());
  }

  @Test
  void execute_nullContext_throwsNullPointerException() {
    TestWorkflow workflow = new TestWorkflow("null-context-test");

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> workflow.execute(null));
    assertTrue(exception.getMessage().contains("WorkflowContext must not be null"));
  }

  @Test
  void getName_withProvidedName_returnsProvidedName() {
    TestWorkflow workflow = new TestWorkflow("MyWorkflow");
    assertEquals("MyWorkflow", workflow.getName());
  }

  @Test
  void getName_withNullName_returnsDefaultName() {
    TestWorkflow workflow = new TestWorkflow(null);
    String name = workflow.getName();

    assertNotNull(name);
    assertTrue(name.contains("TestWorkflow"));
    assertTrue(name.contains(":"));
  }

  @Test
  void executionContext_success_createsSuccessResult() {
    AbstractWorkflow.ExecutionContext execContext =
        new AbstractWorkflow.ExecutionContext(java.time.Instant.now());

    WorkflowResult result = execContext.success();

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertNull(result.getError());
    assertNotNull(result.getStartedAt());
    assertNotNull(result.getCompletedAt());
  }

  @Test
  void executionContext_failure_createsFailureResult() {
    AbstractWorkflow.ExecutionContext execContext =
        new AbstractWorkflow.ExecutionContext(java.time.Instant.now());
    RuntimeException error = new RuntimeException("Test error");

    WorkflowResult result = execContext.failure(error);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertSame(error, result.getError());
    assertNotNull(result.getStartedAt());
    assertNotNull(result.getCompletedAt());
  }

  @Test
  void executionContext_skipped_createsSkippedResult() {
    AbstractWorkflow.ExecutionContext execContext =
        new AbstractWorkflow.ExecutionContext(java.time.Instant.now());

    WorkflowResult result = execContext.skipped();

    assertEquals(WorkflowStatus.SKIPPED, result.getStatus());
    assertNull(result.getError());
    assertNotNull(result.getStartedAt());
    assertNotNull(result.getCompletedAt());
  }

  @Test
  void executionContext_resultWithStatus_createsResultWithGivenStatus() {
    AbstractWorkflow.ExecutionContext execContext =
        new AbstractWorkflow.ExecutionContext(java.time.Instant.now());

    WorkflowResult result = execContext.result(WorkflowStatus.SKIPPED);

    assertEquals(WorkflowStatus.SKIPPED, result.getStatus());
    assertNull(result.getError());
  }

  @Test
  void executionContext_resultWithStatusAndError_createsResultWithBoth() {
    AbstractWorkflow.ExecutionContext execContext =
        new AbstractWorkflow.ExecutionContext(java.time.Instant.now());
    RuntimeException error = new RuntimeException("Error");

    WorkflowResult result = execContext.result(WorkflowStatus.FAILED, error);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertSame(error, result.getError());
  }

  @Test
  void execute_multipleExecutions_eachHasUniqueTimestamps() {
    TestWorkflow workflow = new TestWorkflow("timing-test");
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result1 = workflow.execute(context);
    await().atLeast(10, TimeUnit.MILLISECONDS).until(() -> true);
    WorkflowResult result2 = workflow.execute(context);

    assertTrue(result2.getStartedAt().isAfter(result1.getStartedAt()));
    assertTrue(result2.getCompletedAt().isAfter(result1.getCompletedAt()));
  }

  @Test
  void execute_contextModificationDuringExecution_isVisible() {
    TestWorkflow workflow =
        new TestWorkflow("context-mod-test") {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            context.put("testKey", "testValue");
            return execContext.success();
          }
        };

    WorkflowContext context = WorkflowTestUtils.createContext();
    workflow.execute(context);

    assertEquals("testValue", context.get("testKey"));
  }

  @Test
  void execute_nestedExceptions_preservesStackTrace() {
    IllegalArgumentException cause = new IllegalArgumentException("Root cause");
    RuntimeException wrapper = new RuntimeException("Wrapper", cause);
    TestWorkflow workflow = new TestWorkflow("nested-exception-test", false, true, wrapper);
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(wrapper, result.getError());
    assertSame(cause, result.getError().getCause());
  }

  @Test
  void executionContext_failureWithNullError_acceptsNull() {
    AbstractWorkflow.ExecutionContext execContext =
        new AbstractWorkflow.ExecutionContext(java.time.Instant.now());

    WorkflowResult result = execContext.failure(null);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNull(result.getError());
  }

  @Test
  void execute_veryFastExecution_timestampsAreConsistent() {
    TestWorkflow workflow = new TestWorkflow("fast-test");
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = workflow.execute(context);

    assertNotNull(result.getStartedAt());
    assertNotNull(result.getCompletedAt());
    // Completed at should be >= started at
    assertFalse(result.getCompletedAt().isBefore(result.getStartedAt()));
  }

  @Test
  void execute_differentErrorTypes_allHandledCorrectly() {
    Class<?>[] exceptionTypes = {
      RuntimeException.class,
      IllegalArgumentException.class,
      IllegalStateException.class,
      NullPointerException.class,
      UnsupportedOperationException.class
    };

    WorkflowContext context = WorkflowTestUtils.createContext();

    for (Class<?> exceptionType : exceptionTypes) {
      try {
        RuntimeException exception =
            (RuntimeException) exceptionType.getConstructor(String.class).newInstance("Test");
        TestWorkflow workflow = new TestWorkflow("error-test", false, true, exception);

        WorkflowResult result = workflow.execute(context);

        WorkflowTestUtils.assertFailed(result);
        assertInstanceOf(exceptionType, result.getError());
      } catch (Exception _) {
        fail("Failed to test exception type: " + exceptionType.getName());
      }
    }
  }
}
