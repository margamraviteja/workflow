package com.workflow.task;

import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import lombok.Builder;
import lombok.Value;

/**
 * Descriptor bundling a {@link Task} and optional execution policies such as retries and timeouts.
 * Used by {@link com.workflow.TaskWorkflow} and executors to determine runtime behavior.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Task myTask = new MyTaskImplementation();
 * TaskDescriptor descriptor = TaskDescriptor.builder()
 *     .task(myTask)
 *     .name("MyTaskDescriptor")
 *     .retryPolicy(myRetryPolicy)
 *     .timeoutPolicy(myTimeoutPolicy)
 *     .build();
 * }</pre>
 *
 * <p>Note: If no retry or timeout policies are specified, defaults of {@link RetryPolicy#NONE} and
 * {@link TimeoutPolicy#NONE} are used.
 */
@Value
@Builder
public class TaskDescriptor {
  /** Optional name for the task workflow. */
  String name;

  /** The task to execute (required). */
  Task task;

  /**
   * Retry policy controlling retry attempts. When omitted the default is {@link RetryPolicy#NONE}.
   */
  @Builder.Default RetryPolicy retryPolicy = RetryPolicy.NONE;

  /**
   * Timeout policy that determines how long the task is allowed to run. Default is {@link
   * TimeoutPolicy#NONE}.
   */
  @Builder.Default TimeoutPolicy timeoutPolicy = TimeoutPolicy.NONE;
}
