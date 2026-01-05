package com.workflow.exception;

/**
 * Root exception type thrown by task execution logic. Wraps lower-level exceptions to indicate a
 * failure while executing a {@code Task} within a workflow.
 */
public class TaskExecutionException extends RuntimeException {
  /**
   * Create an execution exception with a message.
   *
   * @param message the error message
   */
  public TaskExecutionException(String message) {
    super(message);
  }

  /**
   * Create an execution exception with message and cause.
   *
   * @param message the error message
   * @param cause the cause
   */
  public TaskExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create an execution exception with a cause.
   *
   * @param cause the cause
   */
  public TaskExecutionException(Throwable cause) {
    super(cause);
  }
}
