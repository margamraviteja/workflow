package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.ConditionalWorkflow.ConditionalWorkflowBuilder;
import com.workflow.DynamicBranchingWorkflow.DynamicBranchingWorkflowBuilder;
import com.workflow.FallbackWorkflow.FallbackWorkflowBuilder;
import com.workflow.ParallelWorkflow.ParallelWorkflowBuilder;
import com.workflow.RateLimitedWorkflow.RateLimitedWorkflowBuilder;
import com.workflow.SequentialWorkflow.SequentialWorkflowBuilder;
import com.workflow.TimeoutWorkflow.TimeoutWorkflowBuilder;
import org.junit.jupiter.api.Test;

class WorkflowsTest {

  @Test
  void sequential_createsSequentialWorkflowBuilder() {
    SequentialWorkflowBuilder builder = Workflows.sequential("TestSequential");

    assertNotNull(builder);
  }

  @Test
  void sequential_builderCanBuildWorkflow() {
    SequentialWorkflowBuilder builder = Workflows.sequential("TestSequential");

    assertDoesNotThrow(builder::build);
  }

  @Test
  void sequential_withDifferentNames_createsDifferentBuilders() {
    SequentialWorkflowBuilder builder1 = Workflows.sequential("Workflow1");
    SequentialWorkflowBuilder builder2 = Workflows.sequential("Workflow2");

    assertNotSame(builder1, builder2);
  }

  @Test
  void parallel_createsParallelWorkflowBuilder() {
    ParallelWorkflowBuilder builder = Workflows.parallel("TestParallel");

    assertNotNull(builder);
  }

  @Test
  void parallel_builderCanBuildWorkflow() {
    ParallelWorkflowBuilder builder = Workflows.parallel("TestParallel");

    assertDoesNotThrow(builder::build);
  }

  @Test
  void conditional_createsConditionalWorkflowBuilder() {
    ConditionalWorkflowBuilder builder = Workflows.conditional("TestConditional");

    assertNotNull(builder);
  }

  @Test
  void conditional_builderRequiresCondition() {
    ConditionalWorkflowBuilder builder = Workflows.conditional("TestConditional");

    // Should throw because condition is not set
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void dynamic_createsDynamicBranchingWorkflowBuilder() {
    DynamicBranchingWorkflowBuilder builder = Workflows.dynamic("TestDynamic");

    assertNotNull(builder);
  }

  @Test
  void dynamic_builderRequiresSelector() {
    DynamicBranchingWorkflowBuilder builder = Workflows.dynamic("TestDynamic");

    // Should throw because selector is not set
    try {
      builder.build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void fallback_createsFallbackWorkflowBuilder() {
    FallbackWorkflowBuilder builder = Workflows.fallback("TestFallback");

    assertNotNull(builder);
  }

  @Test
  void fallback_builderRequiresPrimaryWorkflow() {
    FallbackWorkflowBuilder builder = Workflows.fallback("TestFallback");

    // Should throw because primary workflow is not set
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void rateLimited_createsRateLimitedWorkflowBuilder() {
    RateLimitedWorkflowBuilder builder = Workflows.rateLimited("TestRateLimited");

    assertNotNull(builder);
  }

  @Test
  void rateLimited_builderRequiresWorkflowAndStrategy() {
    RateLimitedWorkflowBuilder builder = Workflows.rateLimited("TestRateLimited");

    // Should throw because required fields are not set
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void timeout_createsTimeoutWorkflowBuilder() {
    TimeoutWorkflowBuilder builder = Workflows.timeout("TestTimeout");

    assertNotNull(builder);
  }

  @Test
  void timeout_builderRequiresWorkflow() {
    TimeoutWorkflowBuilder builder = Workflows.timeout("TestTimeout");

    // Should throw because workflow is not set
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void multipleWorkflowTypes_canBeCreatedSimultaneously() {
    SequentialWorkflowBuilder sequential = Workflows.sequential("S1");
    ParallelWorkflowBuilder parallel = Workflows.parallel("P1");
    ConditionalWorkflowBuilder conditional = Workflows.conditional("C1");

    assertNotNull(sequential);
    assertNotNull(parallel);
    assertNotNull(conditional);
  }
}
