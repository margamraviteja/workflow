package com.workflow.exception;

/** Exception indicating an error during JSON processing. */
public class JsonProcessingException extends RuntimeException {
  /**
   * Create an execution exception with a message.
   *
   * @param message the error message
   */
  public JsonProcessingException(String message) {
    super(message);
  }

  /**
   * Create an execution exception with message and cause.
   *
   * @param message the error message
   * @param cause the cause
   */
  public JsonProcessingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create an execution exception with a cause.
   *
   * @param cause the cause
   */
  public JsonProcessingException(Throwable cause) {
    super(cause);
  }
}
