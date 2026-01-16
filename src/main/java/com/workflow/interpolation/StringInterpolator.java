package com.workflow.interpolation;

import com.workflow.interpolation.exception.InterpolationException;

/**
 * Interface for string interpolation, allowing resolution of placeholders within strings.
 * Placeholders are denoted by the syntax ${key} or ${key:defaultValue}. The implementation should
 * support resolving these placeholders from a defined source, such as environment variables,
 * configuration files, or other data sources. The interface also provides methods to check for the
 * presence of placeholders and to handle strict mode for unresolved placeholders.
 *
 * <pre>
 * Example placeholders:
 * - ${username} - resolves to the value associated with 'username'
 * - ${timeout:30} - resolves to the value associated with 'timeout', or defaults to '30' if not found
 * </pre>
 *
 * @see InterpolationException
 * @see JakartaElStringInterpolator
 */
public interface StringInterpolator {

  /**
   * Interpolate all placeholders in the given string.
   *
   * @param input the string containing placeholders to resolve
   * @return the interpolated string with all placeholders resolved
   * @throws InterpolationException if a required placeholder cannot be resolved
   */
  String interpolate(String input);

  /**
   * Interpolate all placeholders in the given string, using strict mode. In strict mode, unresolved
   * placeholders without defaults will throw an exception.
   *
   * @param input the string containing placeholders to resolve
   * @param strict if {@code true}, throw exception for unresolved placeholders without defaults
   * @return the interpolated string with all placeholders resolved
   * @throws InterpolationException if strict mode is enabled and a placeholder cannot be resolved
   */
  String interpolate(String input, boolean strict);

  /**
   * Check if the given string contains any placeholders.
   *
   * @param input the string to check
   * @return {@code true} if the string contains placeholders, {@code false} otherwise
   */
  boolean containsPlaceholders(String input);
}
