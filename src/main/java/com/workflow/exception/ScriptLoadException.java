package com.workflow.exception;

/**
 * Exception thrown when a workflow script cannot be retrieved or prepared for execution.
 *
 * <p>This typically occurs during the {@code ScriptProvider} phase, before the script is actually
 * executed by the engine. Common causes include:
 *
 * <ul>
 *   <li>I/O errors when reading from the file system
 *   <li>Network timeouts when fetching scripts from remote location
 * </ul>
 *
 * @see com.workflow.script.ScriptProvider
 */
public class ScriptLoadException extends RuntimeException {

  /**
   * Constructs a new script load exception with the specified detail message.
   *
   * @param message the detail message explaining why the load failed
   */
  public ScriptLoadException(String message) {
    super(message);
  }

  /**
   * Constructs a new script load exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the underlying cause (e.g., {@link java.io.IOException})
   */
  public ScriptLoadException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new script load exception with the specified cause.
   *
   * @param cause the underlying cause
   */
  public ScriptLoadException(Throwable cause) {
    super(cause);
  }
}
