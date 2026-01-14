package com.workflow.interpolation.resolver;

import java.util.Optional;

/**
 * A {@link PropertyResolver} that resolves properties from system properties.
 *
 * <p>System properties are typically set via JVM arguments ({@code -Dkey=value}) or
 * programmatically via {@link System#setProperty(String, String)}.
 *
 * @see PropertyResolver
 */
public class SystemPropertiesResolver implements PropertyResolver {

  /** Default order for system properties resolver. */
  public static final int DEFAULT_ORDER = 200;

  private final int order;

  /** Creates a new system properties resolver with default order. */
  public SystemPropertiesResolver() {
    this(DEFAULT_ORDER);
  }

  /**
   * Creates a new system properties resolver with specified order.
   *
   * @param order the order of this resolver
   */
  public SystemPropertiesResolver(int order) {
    this.order = order;
  }

  @Override
  public Optional<String> resolve(String key) {
    return Optional.ofNullable(System.getProperty(key));
  }

  @Override
  public int order() {
    return order;
  }
}
