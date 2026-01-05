package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.test.WorkflowTestUtils;
import org.junit.jupiter.api.Test;

class FallbackWorkflowEnhancedTest {

  @Test
  void builder_primarySucceeds_returnsPrimaryResult() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary = new TaskWorkflow(ctx -> ctx.put("source", "primary"));
    Workflow fallback = new TaskWorkflow(ctx -> ctx.put("source", "fallback"));

    Workflow workflow =
        FallbackWorkflow.builder().name("TestFallback").primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("primary", context.get("source"));
  }

  @Test
  void builder_primaryFails_executesFallback() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary =
        WorkflowTestUtils.mockFailingWorkflow("primary", new RuntimeException("Primary failed"));
    Workflow fallback = new TaskWorkflow(ctx -> ctx.put("source", "fallback"));

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("fallback", context.get("source"));
  }

  @Test
  void builder_bothFail_returnsFailure() {
    WorkflowContext context = new WorkflowContext();

    RuntimeException primaryError = new RuntimeException("Primary failed");
    RuntimeException fallbackError = new RuntimeException("Fallback failed");

    Workflow primary = WorkflowTestUtils.mockFailingWorkflow("primary", primaryError);
    Workflow fallback = WorkflowTestUtils.mockFailingWorkflow("fallback", fallbackError);

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertFailed(result);
    // Should contain the fallback error
    assertNotNull(result.getError());
  }

  @Test
  void builder_withoutPrimary_throwsException() {
    try {
      FallbackWorkflow.builder().fallback(WorkflowTestUtils.mockSuccessfulWorkflow()).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void builder_withoutFallback_throwsException() {
    try {
      FallbackWorkflow.builder().primary(WorkflowTestUtils.mockSuccessfulWorkflow()).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void execute_primaryThrowsException_executesFallback() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary =
        WorkflowTestUtils.mockThrowingWorkflow("primary", new RuntimeException("Boom"));
    Workflow fallback = new TaskWorkflow(ctx -> ctx.put("recovered", true));

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals(true, context.get("recovered"));
  }

  @Test
  void execute_primaryReturnsSkipped_executesFallback() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary = WorkflowTestUtils.mockSkippedWorkflow("primary");
    Workflow fallback = new TaskWorkflow(ctx -> ctx.put("source", "fallback"));

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("fallback", context.get("source"));
  }

  @Test
  void execute_contextSharedBetweenPrimaryAndFallback() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary =
        new TaskWorkflow(
            ctx -> {
              ctx.put("primaryRan", true);
              throw new RuntimeException("Primary failed");
            });
    Workflow fallback =
        new TaskWorkflow(
            ctx -> {
              assertTrue(ctx.getTyped("primaryRan", Boolean.class));
              ctx.put("fallbackRan", true);
            });

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals(true, context.get("primaryRan"));
    assertEquals(true, context.get("fallbackRan"));
  }

  @Test
  void execute_fallbackThrowsException_returnsFailure() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary =
        WorkflowTestUtils.mockFailingWorkflow("primary", new RuntimeException("Primary failed"));
    Workflow fallback =
        WorkflowTestUtils.mockThrowingWorkflow(
            "fallback", new RuntimeException("Fallback exception"));

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
  }

  @Test
  void execute_nestedFallbacks_cascadesCorrectly() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary1 = WorkflowTestUtils.mockFailingWorkflow("primary1", new RuntimeException());
    Workflow primary2 = WorkflowTestUtils.mockFailingWorkflow("primary2", new RuntimeException());
    Workflow finalFallback = new TaskWorkflow(ctx -> ctx.put("level", 3));

    Workflow level2 = FallbackWorkflow.builder().primary(primary2).fallback(finalFallback).build();

    Workflow level1 = FallbackWorkflow.builder().primary(primary1).fallback(level2).build();

    WorkflowResult result = level1.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals(3, context.get("level"));
  }

  @Test
  void getName_returnsProvidedNameOrDefault() {
    Workflow named =
        FallbackWorkflow.builder()
            .name("MyFallback")
            .primary(WorkflowTestUtils.mockSuccessfulWorkflow())
            .fallback(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    assertEquals("MyFallback", named.getName());

    Workflow unnamed =
        FallbackWorkflow.builder()
            .primary(WorkflowTestUtils.mockSuccessfulWorkflow())
            .fallback(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    assertNotNull(unnamed.getName());
  }

  @Test
  void getSubWorkflows_returnsBothWorkflows() {
    Workflow primary = WorkflowTestUtils.mockSuccessfulWorkflow("primary");
    Workflow fallback = WorkflowTestUtils.mockSuccessfulWorkflow("fallback");

    FallbackWorkflow workflow =
        FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    assertEquals(2, workflow.getSubWorkflows().size());
  }

  @Test
  void execute_primaryReturnsNull_executesFallback() {
    WorkflowContext context = new WorkflowContext();

    Workflow primary = WorkflowTestUtils.mockNullReturningWorkflow("primary");
    Workflow fallback = new TaskWorkflow(ctx -> ctx.put("source", "fallback"));

    Workflow workflow = FallbackWorkflow.builder().primary(primary).fallback(fallback).build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("fallback", context.get("source"));
  }

  @Test
  void execute_cacheApiPattern_workAsExpected() {
    WorkflowContext context = new WorkflowContext();
    context.put("key", "user:123");

    // Simulate cache miss
    Workflow cacheRead =
        new TaskWorkflow(
            _ -> {
              throw new RuntimeException("Cache miss");
            });

    // Fallback to API call
    Workflow apiCall =
        new TaskWorkflow(
            ctx -> {
              ctx.put("data", "Data from API");
              ctx.put("source", "api");
            });

    Workflow workflow =
        FallbackWorkflow.builder()
            .name("CacheThenAPI")
            .primary(cacheRead)
            .fallback(apiCall)
            .build();

    WorkflowResult result = workflow.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("Data from API", context.get("data"));
    assertEquals("api", context.get("source"));
  }
}
