package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.chaos.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.ChaosException;
import com.workflow.test.WorkflowTestUtils;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChaosWorkflowTest {

  @Test
  void builder_withWorkflowAndStrategy_buildsSuccessfully() {
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");
    ChaosStrategy strategy = FailureInjectionStrategy.builder().probability(0.5).build();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder()
            .name("TestChaos")
            .workflow(innerWorkflow)
            .strategy(strategy)
            .build();

    assertNotNull(chaosWorkflow);
    assertEquals("TestChaos", chaosWorkflow.getName());
  }

  @Test
  void builder_withoutWorkflow_throwsException() {
    ChaosStrategy strategy = FailureInjectionStrategy.builder().probability(0.5).build();

    ChaosWorkflow.ChaosWorkflowBuilder builder =
        ChaosWorkflow.builder().name("TestChaos").strategy(strategy);
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void builder_withoutStrategy_buildsWithWarning() {
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");

    Workflow chaosWorkflow = ChaosWorkflow.builder().workflow(innerWorkflow).build();

    assertNotNull(chaosWorkflow);
  }

  @Test
  void execute_withNoStrategies_executesWorkflowNormally() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    Workflow chaosWorkflow = ChaosWorkflow.builder().workflow(innerWorkflow).build();

    WorkflowResult result = chaosWorkflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals(true, context.get("executed"));
  }

  @Test
  void execute_withFailureInjection_injectsFailures() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    // Always fail strategy
    ChaosStrategy strategy = FailureInjectionStrategy.alwaysFail();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder()
            .name("AlwaysFail")
            .workflow(innerWorkflow)
            .strategy(strategy)
            .build();

    WorkflowResult result = chaosWorkflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertInstanceOf(ChaosException.class, result.getError());

    // Inner workflow should not execute
    assertNull(context.get("executed"));
  }

  @Test
  void execute_withFailureInjectionNeverFail_alwaysSucceeds() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    ChaosStrategy strategy = FailureInjectionStrategy.neverFail();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    WorkflowResult result = chaosWorkflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals(true, context.get("executed"));
  }

  @Test
  void execute_withLatencyInjection_addsDelay() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    // Fixed 200ms delay
    ChaosStrategy strategy = LatencyInjectionStrategy.withFixedDelay(200);

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    Instant start = Instant.now();
    WorkflowResult result = chaosWorkflow.execute(context);
    Duration duration = Duration.between(start, Instant.now());

    WorkflowTestUtils.assertSuccess(result);
    assertTrue(duration.toMillis() >= 200, "Expected at least 200ms delay");
    assertEquals(true, context.get("executed"));
  }

  @Test
  void execute_withLatencyInjectionRandomDelay_addsVariableDelay() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    // Random delay between 100ms and 300ms
    ChaosStrategy strategy = LatencyInjectionStrategy.withRandomDelay(100, 300);

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    Instant start = Instant.now();
    WorkflowResult result = chaosWorkflow.execute(context);
    Duration duration = Duration.between(start, Instant.now());

    WorkflowTestUtils.assertSuccess(result);
    assertTrue(duration.toMillis() >= 100, "Expected at least 100ms delay");
    assertTrue(duration.toMillis() <= 500, "Expected at most 500ms delay"); // Allow buffer
    assertEquals(true, context.get("executed"));
  }

  @Test
  void execute_withExceptionInjection_throwsSpecificException() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    IllegalStateException expectedException = new IllegalStateException("Test exception");
    ChaosStrategy strategy = ExceptionInjectionStrategy.alwaysThrow(expectedException);

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    WorkflowResult result = chaosWorkflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertInstanceOf(ChaosException.class, result.getError());

    ChaosException chaosException = (ChaosException) result.getError();
    assertEquals(expectedException, chaosException.getCause());

    // Inner workflow should not execute
    assertNull(context.get("executed"));
  }

  @Test
  void execute_withMultipleStrategies_appliesInOrder() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    // First add latency, then fail
    ChaosStrategy latencyStrategy = LatencyInjectionStrategy.withFixedDelay(100);
    ChaosStrategy failureStrategy = FailureInjectionStrategy.alwaysFail();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder()
            .workflow(innerWorkflow)
            .strategy(latencyStrategy)
            .strategy(failureStrategy)
            .build();

    Instant start = Instant.now();
    WorkflowResult result = chaosWorkflow.execute(context);
    Duration duration = Duration.between(start, Instant.now());

    // Should have delay and then fail
    WorkflowTestUtils.assertFailed(result);
    assertTrue(duration.toMillis() >= 100, "Expected latency before failure");
    assertNull(context.get("executed"));
  }

  @Test
  void execute_withProbabilisticStrategy_respectsProbability() {
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    // 0% probability - should never fail
    ChaosStrategy neverFail = FailureInjectionStrategy.withProbability(0.0);

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(neverFail).build();

    // Execute multiple times - all should succeed
    for (int i = 0; i < 10; i++) {
      WorkflowContext context = new WorkflowContext();
      WorkflowResult result = chaosWorkflow.execute(context);
      WorkflowTestUtils.assertSuccess(result);
      assertEquals(true, context.get("executed"));
    }

    // 100% probability - should always fail
    ChaosStrategy alwaysFail = FailureInjectionStrategy.withProbability(1.0);

    Workflow alwaysFailWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(alwaysFail).build();

    // Execute multiple times - all should fail
    for (int i = 0; i < 10; i++) {
      WorkflowContext context = new WorkflowContext();
      WorkflowResult result = alwaysFailWorkflow.execute(context);
      WorkflowTestUtils.assertFailed(result);
      assertNull(context.get("executed"));
    }
  }

  @Test
  void execute_withResourceExhaustion_simulatesResourcePressure() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    ChaosStrategy strategy =
        ResourceExhaustionStrategy.builder()
            .resourceType(ResourceExhaustionStrategy.ResourceType.MEMORY)
            .intensity(ResourceExhaustionStrategy.Intensity.LOW)
            .throwException(false) // Don't throw, just simulate
            .build();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    WorkflowResult result = chaosWorkflow.execute(context);

    // Should succeed but with resource pressure applied
    WorkflowTestUtils.assertSuccess(result);
    assertEquals(true, context.get("executed"));
  }

  @Test
  void execute_withResourceExhaustionThrows_fails() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    ChaosStrategy strategy =
        ResourceExhaustionStrategy.builder()
            .resourceType(ResourceExhaustionStrategy.ResourceType.CPU)
            .intensity(ResourceExhaustionStrategy.Intensity.LOW)
            .throwException(true)
            .build();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    WorkflowResult result = chaosWorkflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertInstanceOf(ChaosException.class, result.getError());
    ChaosException exception = (ChaosException) result.getError();
    assertEquals("CPU", exception.getMetadata("resourceType"));
  }

  @Test
  void integration_withFallbackWorkflow_triggersFailover() {
    Workflow primaryWorkflow = new TaskWorkflow(ctx -> ctx.put("source", "primary"));

    // Primary wrapped with high failure rate
    Workflow unreliablePrimary =
        ChaosWorkflow.builder()
            .workflow(primaryWorkflow)
            .strategy(FailureInjectionStrategy.alwaysFail())
            .build();

    Workflow fallbackWorkflow = new TaskWorkflow(ctx -> ctx.put("source", "fallback"));

    Workflow resilient =
        FallbackWorkflow.builder().primary(unreliablePrimary).fallback(fallbackWorkflow).build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = resilient.execute(context);

    // Should succeed via fallback
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("fallback", context.get("source"));
  }

  @Test
  void integration_withTimeoutWorkflow_timesOut() {
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    // Add 500ms latency
    Workflow slowWorkflow =
        ChaosWorkflow.builder()
            .workflow(innerWorkflow)
            .strategy(LatencyInjectionStrategy.withFixedDelay(500))
            .build();

    // Timeout at 200ms
    Workflow timedWorkflow =
        TimeoutWorkflow.builder().workflow(slowWorkflow).timeoutMs(200).build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = timedWorkflow.execute(context);

    // Should time out
    WorkflowTestUtils.assertFailed(result);
    assertNull(context.get("executed"));
  }

  @Test
  void integration_withSequentialWorkflow_failsEarly() {
    Workflow step1 = new TaskWorkflow(ctx -> ctx.put("step1", true));

    Workflow step2Chaos =
        ChaosWorkflow.builder()
            .workflow(new TaskWorkflow(ctx -> ctx.put("step2", true)))
            .strategy(FailureInjectionStrategy.alwaysFail())
            .build();

    Workflow step3 = new TaskWorkflow(ctx -> ctx.put("step3", true));

    Workflow sequential =
        SequentialWorkflow.builder().workflow(step1).workflow(step2Chaos).workflow(step3).build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = sequential.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertEquals(true, context.get("step1")); // Step 1 executed
    assertNull(context.get("step2")); // Step 2 failed before execution
    assertNull(context.get("step3")); // Step 3 never executed
  }

  @Test
  void chaosException_includesMetadata() {
    WorkflowContext context = new WorkflowContext();
    Workflow innerWorkflow = new TaskWorkflow(ctx -> ctx.put("executed", true));

    ChaosStrategy strategy =
        FailureInjectionStrategy.builder()
            .probability(1.0)
            .errorMessage("Custom chaos message")
            .build();

    Workflow chaosWorkflow =
        ChaosWorkflow.builder().workflow(innerWorkflow).strategy(strategy).build();

    WorkflowResult result = chaosWorkflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    ChaosException exception = (ChaosException) result.getError();

    assertNotNull(exception.getMetadata("strategy"));
    assertEquals("FailureInjection", exception.getMetadata("strategy"));
    assertNotNull(exception.getMetadata("probability"));
    assertEquals(1.0, exception.getMetadata("probability"));
  }

  @Test
  void getWorkflowType_returnsChaos() {
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");
    Workflow chaosWorkflow = ChaosWorkflow.builder().workflow(innerWorkflow).build();

    assertTrue(chaosWorkflow.getWorkflowType().contains("Chaos"));
  }

  @Test
  void getSubWorkflows_returnsWrappedWorkflow() {
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");
    ChaosWorkflow chaosWorkflow = ChaosWorkflow.builder().workflow(innerWorkflow).build();

    assertEquals(1, chaosWorkflow.getSubWorkflows().size());
    assertEquals(innerWorkflow, chaosWorkflow.getSubWorkflows().getFirst());
  }
}
