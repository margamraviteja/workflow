package com.workflow.exception;

/**
 * Exception thrown when a task fails validation before execution. This indicates that the task's
 * preconditions were not met, such as missing required context values or invalid input data.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Missing required context keys
 *   <li>Context values of incorrect type
 *   <li>Invalid configuration parameters for the task
 * </ul>
 *
 * <p><b>Handling:</b> This exception should be caught by the workflow engine to prevent execution
 * of the task and to provide feedback on what validation failed.
 *
 * @see TaskExecutionException
 */
public class TaskValidationException extends TaskExecutionException {
  /**
   * Create a task validation exception with a message.
   *
   * @param message the error message
   */
  public TaskValidationException(String message) {
    super(message);
  }

  /**
   * Create a task validation exception with message and cause.
   *
   * @param message the error message
   * @param cause the cause
   */
  public TaskValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create a task validation exception with a cause.
   *
   * @param cause the cause
   */
  public TaskValidationException(Throwable cause) {
    super(cause);
  }
}
