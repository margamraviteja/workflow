package com.workflow.interpolation.exception;

import com.workflow.interpolation.StringInterpolator;
import lombok.Getter;

/**
 * Exception thrown when string interpolation fails.
 *
 * <p>Common causes include:
 *
 * <ul>
 *   <li>Unresolved placeholder without a default value in strict mode
 *   <li>Circular reference in nested placeholder resolution
 *   <li>Maximum recursion depth exceeded
 *   <li>Invalid placeholder syntax
 * </ul>
 *
 * @see StringInterpolator
 */
@Getter
public class InterpolationException extends RuntimeException {

  private final String placeholder;

  public InterpolationException(String message) {
    super(message);
    this.placeholder = null;
  }

  public InterpolationException(String message, String placeholder) {
    super(message);
    this.placeholder = placeholder;
  }

  public InterpolationException(String message, Throwable cause) {
    super(message, cause);
    this.placeholder = null;
  }

  public InterpolationException(String message, String placeholder, Throwable cause) {
    super(message, cause);
    this.placeholder = placeholder;
  }
}
