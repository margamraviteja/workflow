package com.workflow.interpolation.resolver;

import java.util.Optional;

/**
 * A {@link PropertyResolver} that resolves properties from environment variables.
 *
 * <p>Environment variables are typically set by the operating system or container runtime. Common
 * examples include {@code PATH}, {@code HOME}, {@code USER}, and custom application variables.
 *
 * <p><b>Key Normalization:</b> This resolver attempts to resolve the key as-is first, then tries
 * common environment variable naming conventions:
 *
 * <ul>
 *   <li>Original key: {@code database.url}
 *   <li>With underscores: {@code database_url}
 *   <li>Uppercase: {@code DATABASE_URL}
 * </ul>
 *
 * @see PropertyResolver
 */
public class EnvironmentPropertyResolver implements PropertyResolver {

  /** Default order for environment property resolver. */
  public static final int DEFAULT_ORDER = 300;

  private final int order;
  private final boolean normalizeKeys;

  /**
   * Creates a new environment property resolver with default order and key normalization enabled.
   */
  public EnvironmentPropertyResolver() {
    this(DEFAULT_ORDER, true);
  }

  /**
   * Creates a new environment property resolver with specified order.
   *
   * @param order the order of this resolver
   */
  public EnvironmentPropertyResolver(int order) {
    this(order, true);
  }

  /**
   * Creates a new environment property resolver with specified order and normalization setting.
   *
   * @param order the order of this resolver
   * @param normalizeKeys whether to try normalized key variants
   */
  public EnvironmentPropertyResolver(int order, boolean normalizeKeys) {
    this.order = order;
    this.normalizeKeys = normalizeKeys;
  }

  @Override
  public Optional<String> resolve(String key) {
    // Try original key first
    String value = System.getenv(key);
    if (value != null) {
      return Optional.of(value);
    }

    if (normalizeKeys) {
      // Try with dots replaced by underscores
      String normalizedKey = key.replace('.', '_').replace('-', '_');
      value = System.getenv(normalizedKey);
      if (value != null) {
        return Optional.of(value);
      }

      // Try uppercase variant
      value = System.getenv(normalizedKey.toUpperCase());
      if (value != null) {
        return Optional.of(value);
      }
    }

    return Optional.empty();
  }

  @Override
  public int order() {
    return order;
  }
}
