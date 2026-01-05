package com.workflow.exception;

/** Exception thrown when there is an error processing an HTTP response. */
public class HttpResponseProcessingException extends RuntimeException {
  /**
   * Create an execution exception with a message.
   *
   * @param message the error message
   */
  public HttpResponseProcessingException(String message) {
    super(message);
  }

  /**
   * Create an execution exception with message and cause.
   *
   * @param message the error message
   * @param cause the cause
   */
  public HttpResponseProcessingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create an execution exception with a cause.
   *
   * @param cause the cause
   */
  public HttpResponseProcessingException(Throwable cause) {
    super(cause);
  }
}
