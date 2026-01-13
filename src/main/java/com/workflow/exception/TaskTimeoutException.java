package com.workflow.exception;

/**
 * Exception thrown when a task exceeds its configured timeout duration.
 *
 * <p><b>When Thrown:</b> Raised when a task does not complete within the time specified by {@link
 * com.workflow.policy.TimeoutPolicy}. The timeout applies to the entire task execution, including
 * all retry attempts.
 *
 * <p><b>Timeout Implementation:</b> Uses {@link java.util.concurrent.CompletableFuture#orTimeout}
 * to enforce the deadline. The task is interrupted if possible, but cannot be forcibly stopped from
 * executing.
 *
 * <p><b>Implications:</b>
 *
 * <ul>
 *   <li>The task execution may continue in the background
 *   <li>No further retries are attempted after timeout
 *   <li>Partial state changes from the timed-out task may persist in the context
 * </ul>
 *
 * <p><b>Recovery Strategies:</b>
 *
 * <ul>
 *   <li>Increase timeout if task legitimately needs more time
 *   <li>Reduce scope of task (split into smaller tasks)
 *   <li>Use fallback workflow if available (FallbackWorkflow)
 *   <li>Optimize task implementation
 *   <li>Add caching to avoid re-execution
 * </ul>
 *
 * <p><b>Example Usage - Handling Timeout:</b>
 *
 * <pre>{@code
 * TaskDescriptor descriptor = TaskDescriptor.builder()
 *     .task(slowTask)
 *     .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
 *     .retryPolicy(RetryPolicy.limitedRetries(2))
 *     .build();
 *
 * TaskExecutor executor = new DefaultTaskExecutor();
 * try {
 *     executor.execute(descriptor, context);
 * } catch (TaskTimeoutException e) {
 *     System.err.println("Task timed out after 5 seconds");
 *     // Fallback logic
 *     useCachedResult(context);
 * }
 * }</pre>
 *
 * @see com.workflow.policy.TimeoutPolicy
 * @see com.workflow.task.executor.DefaultTaskExecutor
 * @see TaskExecutionException
 */
public class TaskTimeoutException extends TaskExecutionException {
  /**
   * Create a timeout exception with a message.
   *
   * @param message the error message
   */
  public TaskTimeoutException(String message) {
    super(message);
  }

  /**
   * Create a timeout exception with message and cause.
   *
   * @param message the error message
   * @param cause the cause
   */
  public TaskTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create a timeout exception with a cause.
   *
   * @param cause the cause
   */
  public TaskTimeoutException(Throwable cause) {
    super(cause);
  }
}
