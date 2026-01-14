package com.workflow.interpolation.resolver;

import com.workflow.interpolation.StringInterpolator;
import java.util.Optional;

/**
 * Strategy interface for resolving property values by name.
 *
 * <p>Implementations can resolve properties from various sources such as:
 *
 * <ul>
 *   <li>Environment variables
 *   <li>System properties
 *   <li>{@link com.workflow.context.WorkflowContext}
 *   <li>Custom property maps
 *   <li>External configuration services
 * </ul>
 *
 * @see StringInterpolator
 * @see CompositePropertyResolver
 */
@FunctionalInterface
public interface PropertyResolver {

  /**
   * Resolve a property value by its key.
   *
   * @param key the property key to resolve
   * @return an {@link Optional} containing the resolved value, or empty if not found
   */
  Optional<String> resolve(String key);

  /**
   * Returns the order of this resolver in a chain of resolvers. Lower values have higher priority.
   *
   * @return the order value (default is 0)
   */
  default int order() {
    return 0;
  }

  /**
   * Returns whether this resolver supports the given key. Can be used to skip resolution attempts
   * for unsupported keys.
   *
   * @param key the property key to check
   * @return {@code true} if this resolver supports the key, {@code false} otherwise
   */
  default boolean supports(String key) {
    return true;
  }
}
