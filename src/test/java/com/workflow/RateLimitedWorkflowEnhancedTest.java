package com.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.Workflows;
import com.workflow.ratelimit.RateLimitStrategy;
import com.workflow.test.WorkflowTestUtils;
import org.junit.jupiter.api.Test;

/** Enhanced test suite for RateLimitedWorkflow covering edge cases and error scenarios. */
class RateLimitedWorkflowEnhancedTest {

  @Test
  void execute_acquiresPermit_beforeExecutingWorkflow() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");

    RateLimitedWorkflow workflow =
        Workflows.rateLimited("rate-limited")
            .workflow(innerWorkflow)
            .rateLimitStrategy(limiter)
            .build();

    workflow.execute(new WorkflowContext());

    verify(limiter).acquire();
    verify(innerWorkflow).execute(any());
  }

  @Test
  void execute_innerWorkflowSucceeds_returnsSuccess() {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_innerWorkflowFails_returnsFailure() {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    RuntimeException error = new RuntimeException("Inner failed");
    Workflow innerWorkflow = WorkflowTestUtils.mockFailingWorkflow("inner", error);

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertFailed(result);
    assertSame(error, result.getError());
  }

  @Test
  void execute_rateLimiterThrowsInterruptedException_propagatesAsFailure()
      throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    doThrow(new InterruptedException("Rate limit interrupted")).when(limiter).acquire();

    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertFailed(result);
    assertInstanceOf(InterruptedException.class, result.getError());
    assertTrue(Thread.interrupted()); // Verify interrupt flag is set
  }

  @Test
  void execute_rateLimiterThrowsRuntimeException_propagatesAsFailure() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    RuntimeException rateLimitError = new RuntimeException("Rate limiter error");
    doThrow(rateLimitError).when(limiter).acquire();

    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("inner");

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertFailed(result);
    assertSame(rateLimitError, result.getError());
  }

  @Test
  void getName_returnsProvidedName() {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow();

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder()
            .name("custom-name")
            .workflow(innerWorkflow)
            .rateLimitStrategy(limiter)
            .build();

    assertEquals("custom-name", workflow.getName());
  }

  @Test
  void getName_withNull_returnsDefaultName() {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow();

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder()
            .name(null)
            .workflow(innerWorkflow)
            .rateLimitStrategy(limiter)
            .build();

    String name = workflow.getName();
    assertNotNull(name);
    assertTrue(name.contains("RateLimitedWorkflow"));
  }

  @Test
  void execute_contextShared_betweenRateLimitAndWorkflow() {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);

    WorkflowContext context = new WorkflowContext();
    context.put("key1", "value1");

    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(WorkflowContext ctx, ExecutionContext execContext) {
            assertEquals("value1", ctx.get("key1"));
            ctx.put("key2", "value2");
            return execContext.success();
          }
        };

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals("value2", context.get("key2"));
  }

  @Test
  void execute_multipleExecutions_acquiresPermitEachTime() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow();

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    workflow.execute(new WorkflowContext());
    workflow.execute(new WorkflowContext());
    workflow.execute(new WorkflowContext());

    verify(limiter, times(3)).acquire();
  }

  @Test
  void execute_innerWorkflowThrows_stillPropagatesCorrectly() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    RuntimeException exception = new RuntimeException("Inner threw");
    Workflow innerWorkflow = WorkflowTestUtils.mockThrowingWorkflow("inner", exception);

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertFailed(result);
    assertSame(exception, result.getError());
    verify(limiter).acquire(); // Still acquired permit
  }

  @Test
  void execute_nestedRateLimitedWorkflows() throws InterruptedException {
    RateLimitStrategy outerLimiter = mock(RateLimitStrategy.class);
    RateLimitStrategy innerLimiter = mock(RateLimitStrategy.class);

    Workflow baseWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("base");

    RateLimitedWorkflow innerRateLimited =
        RateLimitedWorkflow.builder()
            .workflow(baseWorkflow)
            .rateLimitStrategy(innerLimiter)
            .build();

    RateLimitedWorkflow outerRateLimited =
        RateLimitedWorkflow.builder()
            .workflow(innerRateLimited)
            .rateLimitStrategy(outerLimiter)
            .build();

    WorkflowResult result = outerRateLimited.execute(new WorkflowContext());

    WorkflowTestUtils.assertSuccess(result);
    verify(outerLimiter).acquire();
    verify(innerLimiter).acquire();
  }

  @Test
  void execute_withTaskWorkflow_works() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);

    final boolean[] executed = {false};
    Workflow taskWorkflow = new TaskWorkflow(_ -> executed[0] = true);

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(taskWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertSuccess(result);
    assertTrue(executed[0]);
    verify(limiter).acquire();
  }

  @Test
  void execute_differentRateLimitStrategies_work() throws InterruptedException {
    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow();

    // Test with different mocked strategies
    RateLimitStrategy strategy1 = mock(RateLimitStrategy.class);
    RateLimitStrategy strategy2 = mock(RateLimitStrategy.class);
    RateLimitStrategy strategy3 = mock(RateLimitStrategy.class);

    RateLimitedWorkflow workflow1 =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(strategy1).build();

    RateLimitedWorkflow workflow2 =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(strategy2).build();

    RateLimitedWorkflow workflow3 =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(strategy3).build();

    workflow1.execute(new WorkflowContext());
    workflow2.execute(new WorkflowContext());
    workflow3.execute(new WorkflowContext());

    verify(strategy1).acquire();
    verify(strategy2).acquire();
    verify(strategy3).acquire();
  }

  @Test
  void execute_acquireThrowsException_doesNotExecuteInnerWorkflow() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    doThrow(new RuntimeException("Rate limit exceeded")).when(limiter).acquire();

    Workflow innerWorkflow = mock(Workflow.class);

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowResult result = workflow.execute(new WorkflowContext());

    WorkflowTestUtils.assertFailed(result);
    verify(innerWorkflow, never()).execute(any()); // Inner workflow never called
  }

  @Test
  void execute_preservesThreadInterruptFlag() throws InterruptedException {
    RateLimitStrategy limiter = mock(RateLimitStrategy.class);
    doThrow(new InterruptedException()).when(limiter).acquire();

    Workflow innerWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow();

    RateLimitedWorkflow workflow =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    assertFalse(Thread.currentThread().isInterrupted());

    workflow.execute(new WorkflowContext());

    assertTrue(Thread.currentThread().isInterrupted());
  }
}
