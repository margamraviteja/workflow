package com.workflow.interpolation;

import com.workflow.interpolation.exception.InterpolationException;
import com.workflow.interpolation.resolver.PropertyResolver;

/**
 * Interface for string interpolation with placeholder substitution.
 *
 * <p>Implementations resolve placeholders like {@code ${key}} or {@code ${key:-default}} within
 * strings using one or more {@link PropertyResolver} instances.
 *
 * <p><b>Placeholder Syntax:</b>
 *
 * <ul>
 *   <li>{@code ${key}} - Simple placeholder
 *   <li>{@code ${key:-defaultValue}} - Placeholder with default value
 *   <li>{@code \${literal}} - Escaped placeholder (becomes literal {@code ${literal}})
 * </ul>
 *
 * @see PropertyResolver
 * @see DefaultStringInterpolator
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
