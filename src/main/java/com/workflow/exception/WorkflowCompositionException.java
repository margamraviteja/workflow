package com.workflow.exception;

/**
 * Exception thrown when workflow composition fails. This typically occurs during workflow
 * construction when there are issues with referenced workflows or composition configuration.
 */
public class WorkflowCompositionException extends RuntimeException {
  /**
   * Creates a new composition exception with a message.
   *
   * @param message the exception message
   */
  public WorkflowCompositionException(String message) {
    super(message);
  }

  /**
   * Creates a new composition exception with a message and cause.
   *
   * @param message the exception message
   * @param cause the underlying cause
   */
  public WorkflowCompositionException(String message, Throwable cause) {
    super(message, cause);
  }
}
