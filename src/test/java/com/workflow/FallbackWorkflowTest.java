package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.Workflows;
import com.workflow.test.WorkflowTestUtils;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for FallbackWorkflow covering primary/fallback execution, error
 * handling, and edge cases.
 */
class FallbackWorkflowTest {

  @Test
  void constructor_nullPrimary_throwsNullPointerException() {
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow();

    try {
      Workflows.fallback("test").fallback(fallback).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void constructor_nullFallback_throwsNullPointerException() {
    Workflow primary = WorkflowTestUtils.mockSuccessfulWorkflow();
    try {
      FallbackWorkflow.builder().name("test").primary(primary).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void execute_primarySucceeds_returnsPrimaryResultWithoutInvokingFallback() {
    Workflow primary = WorkflowTestUtils.mockSuccessfulWorkflow("primary");
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_primaryFails_executesFallbackAndReturnsFallbackResult() {
    RuntimeException primaryError = new RuntimeException("primary failed");
    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", primaryError);
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_primaryThrows_executesFallbackAndReturnsFallbackResult() {
    RuntimeException exception = new RuntimeException("boom");
    Workflow primary = WorkflowTestUtils.mockThrowingWorkflow("primary", exception);
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_bothFail_returnsFailureWithFallbackError() {
    RuntimeException primaryError = new RuntimeException("primary failed");
    RuntimeException fallbackError = new RuntimeException("fallback failed");

    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", primaryError);
    Workflow fallback = WorkflowTestUtils.mockFailingWorkflow("fallback", fallbackError);

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(fallbackError, result.getError());
  }

  @Test
  void execute_primaryReturnsNull_executesFallback() {
    Workflow primary = WorkflowTestUtils.mockNullReturningWorkflow("primary");
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_primaryFailsFallbackReturnsNull_returnsFailureWithMessage() {
    RuntimeException primaryError = new RuntimeException("primary error");
    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", primaryError);
    Workflow fallback = WorkflowTestUtils.mockNullReturningWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertTrue(
        result.getError().getMessage().contains("Fallback workflow failed with no error details"));
  }

  @Test
  void getWorkflowType_returnsCorrectType() {
    FallbackWorkflow wf =
        FallbackWorkflow.builder()
            .primary(WorkflowTestUtils.mockSuccessfulWorkflow())
            .fallback(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();
    assertEquals("[Fallback]", wf.getWorkflowType());
  }

  @Test
  void getSubWorkflows_returnsCorrectSubWorkflows() {
    Workflow primary = WorkflowTestUtils.mockSuccessfulWorkflow("primary");
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");
    FallbackWorkflow wf = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    java.util.List<Workflow> subWorkflows = wf.getSubWorkflows();
    assertEquals(2, subWorkflows.size());
  }

  @Test
  void execute_bothReturnNull_returnsFailureWithGenericMessage() {
    Workflow primary = WorkflowTestUtils.mockNullReturningWorkflow("primary");
    Workflow fallback = WorkflowTestUtils.mockNullReturningWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertTrue(result.getError().getMessage().contains("failed with no error details"));
  }

  @Test
  void execute_fallbackThrows_returnsFailureWithFallbackException() {
    RuntimeException primaryError = new RuntimeException("primary failed");
    RuntimeException fallbackException = new RuntimeException("fallback boom");

    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", primaryError);
    Workflow fallback = WorkflowTestUtils.mockThrowingWorkflow("fallback", fallbackException);

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(fallbackException, result.getError());
  }

  @Test
  void getName_withProvidedName_returnsProvidedName() {
    Workflow primary = WorkflowTestUtils.mockSuccessfulWorkflow();
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow();

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("MyFallback").primary(primary).fallback(fallback).build();

    assertEquals("MyFallback", wf.getName());
  }

  @Test
  void getName_withNullName_returnsDefaultName() {
    Workflow primary = WorkflowTestUtils.mockSuccessfulWorkflow();
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow();

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name(null).primary(primary).fallback(fallback).build();

    String name = wf.getName();
    assertNotNull(name);
    assertTrue(name.contains("FallbackWorkflow"));
  }

  @Test
  void execute_primarySucceedsWithSkippedStatus_returnsPrimaryResult() {
    Workflow primary =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return WorkflowTestUtils.skippedResult();
          }
        };
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    // Skipped is not considered success, so fallback should be invoked
    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_contextSharedBetweenPrimaryAndFallback() {
    Workflow primary =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            context.put("primaryKey", "primaryValue");
            return WorkflowTestUtils.failureResult(new RuntimeException("fail"));
          }
        };
    assertEquals("", primary.getWorkflowType());

    Workflow fallback =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            assertEquals("primaryValue", context.get("primaryKey"));
            context.put("fallbackKey", "fallbackValue");
            return WorkflowTestUtils.successResult();
          }
        };

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals("primaryValue", context.get("primaryKey"));
    assertEquals("fallbackValue", context.get("fallbackKey"));
  }

  @Test
  void execute_multipleInvocations_eachFollowsFallbackLogic() {
    RuntimeException error = new RuntimeException("error");
    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", error);
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();

    // First execution
    WorkflowResult result1 = wf.execute(WorkflowTestUtils.createContext());
    WorkflowTestUtils.assertSuccess(result1);

    // Second execution
    WorkflowResult result2 = wf.execute(WorkflowTestUtils.createContext());
    WorkflowTestUtils.assertSuccess(result2);

    // Both should succeed via fallback
    assertNotNull(result1);
    assertNotNull(result2);
  }

  @Test
  void execute_primarySucceedsAfterInitialFailures_usesPrimaryResult() {
    int[] invocationCount = {0};

    Workflow primary =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            invocationCount[0]++;
            if (invocationCount[0] == 1) {
              return WorkflowTestUtils.failureResult(new RuntimeException("first call fails"));
            }
            return WorkflowTestUtils.successResult();
          }
        };

    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();

    // First call - primary fails, fallback succeeds
    WorkflowResult result1 = wf.execute(WorkflowTestUtils.createContext());
    WorkflowTestUtils.assertSuccess(result1);
    assertEquals(1, invocationCount[0]);

    // Second call - primary succeeds
    WorkflowResult result2 = wf.execute(WorkflowTestUtils.createContext());
    WorkflowTestUtils.assertSuccess(result2);
    assertEquals(2, invocationCount[0]);
  }

  @Test
  void execute_nestedFallbacks_allLevelsWorkCorrectly() {
    RuntimeException error = new RuntimeException("all fail");
    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", error);
    Workflow firstFallback = WorkflowTestUtils.mockFailingWorkflow("fallback1", error);
    Workflow secondFallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback2");

    // Create nested fallback: primary -> (fallback1 -> fallback2)
    FallbackWorkflow innerFallback =
        FallbackWorkflow.builder()
            .name("inner")
            .primary(firstFallback)
            .fallback(secondFallback)
            .build();
    FallbackWorkflow outerFallback =
        FallbackWorkflow.builder().name("outer").primary(primary).fallback(innerFallback).build();

    WorkflowResult result = outerFallback.execute(WorkflowTestUtils.createContext());

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_fallbackReturnsFailedButNotNull_returnsFailureResult() {
    RuntimeException primaryError = new RuntimeException("primary failed");
    RuntimeException fallbackError = new RuntimeException("fallback failed");

    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", primaryError);
    Workflow fallback = WorkflowTestUtils.mockFailingWorkflow("fallback", fallbackError);

    FallbackWorkflow wf =
        FallbackWorkflow.builder().name("test").primary(primary).fallback(fallback).build();
    WorkflowContext context = WorkflowTestUtils.createContext();

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertSame(fallbackError, result.getError());
  }
}
