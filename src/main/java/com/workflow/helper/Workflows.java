package com.workflow.helper;

import com.workflow.*;
import com.workflow.ConditionalWorkflow.ConditionalWorkflowBuilder;
import com.workflow.DynamicBranchingWorkflow.DynamicBranchingWorkflowBuilder;
import com.workflow.FallbackWorkflow.FallbackWorkflowBuilder;
import com.workflow.ParallelWorkflow.ParallelWorkflowBuilder;
import com.workflow.RateLimitedWorkflow.RateLimitedWorkflowBuilder;
import com.workflow.SequentialWorkflow.SequentialWorkflowBuilder;
import com.workflow.TimeoutWorkflow.TimeoutWorkflowBuilder;
import lombok.experimental.UtilityClass;

/**
 * Utility class providing factory methods for creating various workflow builders.
 *
 * <p>This class serves as the primary entry point for defining workflow structures using a fluent
 * API. It simplifies the instantiation of different workflow types such as sequential, parallel,
 * and conditional flows.
 */
@UtilityClass
public class Workflows {

  /**
   * Creates a builder for a {@link SequentialWorkflow}, where tasks are executed one after another.
   *
   * @param name The unique name of the sequential workflow.
   * @return A new {@link SequentialWorkflowBuilder} instance.
   */
  public static SequentialWorkflowBuilder sequential(String name) {
    return SequentialWorkflow.builder().name(name);
  }

  /**
   * Creates a builder for a {@link ParallelWorkflow}, allowing multiple tasks to run
   * simultaneously.
   *
   * @param name The unique name of the parallel workflow.
   * @return A new {@link ParallelWorkflowBuilder} instance.
   */
  public static ParallelWorkflowBuilder parallel(String name) {
    return ParallelWorkflow.builder().name(name);
  }

  /**
   * Creates a builder for a {@link ConditionalWorkflow}, which branches based on specific logic.
   *
   * @param name The unique name of the conditional workflow.
   * @return A new {@link ConditionalWorkflowBuilder} instance.
   */
  public static ConditionalWorkflowBuilder conditional(String name) {
    return ConditionalWorkflow.builder().name(name);
  }

  /**
   * Creates a builder for a {@link DynamicBranchingWorkflow}, which determines execution paths at
   * runtime.
   *
   * @param name The unique name of the dynamic workflow.
   * @return A new {@link DynamicBranchingWorkflowBuilder} instance.
   */
  public static DynamicBranchingWorkflowBuilder dynamic(String name) {
    return DynamicBranchingWorkflow.builder().name(name);
  }

  /**
   * Creates a builder for a {@link FallbackWorkflow}, providing error handling and alternative
   * paths.
   *
   * @param name The unique name of the fallback workflow.
   * @return A new {@link FallbackWorkflowBuilder} instance.
   */
  public static FallbackWorkflowBuilder fallback(String name) {
    return FallbackWorkflow.builder().name(name);
  }

  /**
   * Creates a builder for a {@link RateLimitedWorkflow}, ensuring execution stays within defined
   * limits.
   *
   * @param name The unique name of the rate-limited workflow.
   * @return A new {@link RateLimitedWorkflowBuilder} instance.
   */
  public static RateLimitedWorkflowBuilder rateLimited(String name) {
    return RateLimitedWorkflow.builder().name(name);
  }

  /**
   * Creates a builder for a {@link TimeoutWorkflow}, which enforces a maximum duration for
   * execution.
   *
   * @param name The unique name of the timeout workflow.
   * @return A new {@link TimeoutWorkflowBuilder} instance.
   */
  public static TimeoutWorkflowBuilder timeout(String name) {
    return TimeoutWorkflow.builder().name(name);
  }
}
