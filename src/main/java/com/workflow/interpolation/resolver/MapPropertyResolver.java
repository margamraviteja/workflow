package com.workflow.interpolation.resolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * A {@link PropertyResolver} that resolves properties from a custom {@link Map}.
 *
 * <p>This resolver is useful for providing custom properties that are specific to a particular
 * operation or context. It supports nested property resolution using dot notation when strict mode
 * is disabled.
 *
 * <p>Resolution strategy:
 *
 * <ol>
 *   <li>First, try to resolve by full key (exact match)
 *   <li>If not found and strict mode is disabled, split the key by dot (.) and traverse nested maps
 * </ol>
 *
 * <p>By default, strict mode is enabled, which means only exact key matches are resolved.
 *
 * @see PropertyResolver
 */
public class MapPropertyResolver implements PropertyResolver {

  /** Default order for map property resolver. */
  public static final int DEFAULT_ORDER = 50;

  private final Map<String, Object> properties;
  private final int order;

  @Getter private final boolean strict;

  /**
   * Creates a new map property resolver with default order and strict mode enabled.
   *
   * @param properties the properties map
   */
  public MapPropertyResolver(Map<String, ?> properties) {
    this(properties, DEFAULT_ORDER, true);
  }

  /**
   * Creates a new map property resolver with specified order and strict mode enabled.
   *
   * @param properties the properties map
   * @param order the order of this resolver
   */
  public MapPropertyResolver(Map<String, ?> properties, int order) {
    this(properties, order, true);
  }

  /**
   * Creates a new map property resolver with specified order and strict mode.
   *
   * @param properties the properties map
   * @param order the order of this resolver
   * @param strict if true, only exact key matches are resolved; if false, nested resolution via dot
   *     notation is enabled
   */
  @SuppressWarnings("unchecked")
  public MapPropertyResolver(Map<String, ?> properties, int order, boolean strict) {
    Objects.requireNonNull(properties, "properties must not be null");
    this.properties = (Map<String, Object>) properties;
    this.order = order;
    this.strict = strict;
  }

  @Override
  public Optional<String> resolve(String key) {
    // First, try to resolve by full key (exact match)
    Object value = properties.get(key);
    if (value != null) {
      return Optional.of(convertToString(value));
    }

    // If not found and not strict, try to resolve by splitting the key by dot
    if (!strict) {
      return resolveNested(key);
    }

    return Optional.empty();
  }

  /**
   * Resolves a nested property by splitting the key by dot and traversing nested maps.
   *
   * @param key the dot-separated key
   * @return the resolved value, or empty if not found
   */
  @SuppressWarnings("unchecked")
  private Optional<String> resolveNested(String key) {
    String[] parts = key.split("\\.");
    if (parts.length <= 1) {
      return Optional.empty();
    }

    Object current = properties;
    for (String part : parts) {
      if (current instanceof Map) {
        current = ((Map<String, Object>) current).get(part);
        if (current == null) {
          return Optional.empty();
        }
      } else {
        return Optional.empty();
      }
    }

    return Optional.of(convertToString(current));
  }

  /**
   * Converts an object to its string representation.
   *
   * @param value the value to convert
   * @return the string representation
   */
  private String convertToString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  @Override
  public int order() {
    return order;
  }

  /**
   * Creates a new resolver wrapping the given properties with strict mode enabled.
   *
   * @param properties the properties map
   * @return a new map property resolver
   */
  public static MapPropertyResolver of(Map<String, ?> properties) {
    return new MapPropertyResolver(properties);
  }

  /**
   * Creates a new resolver wrapping the given properties with specified strict mode.
   *
   * @param properties the properties map
   * @param strict if true, only exact key matches are resolved
   * @return a new map property resolver
   */
  public static MapPropertyResolver of(Map<String, ?> properties, boolean strict) {
    return new MapPropertyResolver(properties, DEFAULT_ORDER, strict);
  }

  /**
   * Creates a new resolver with the given key-value pairs and strict mode enabled.
   *
   * @param keyValuePairs alternating key-value pairs
   * @return a new map property resolver
   * @throws IllegalArgumentException if odd number of arguments
   */
  public static MapPropertyResolver of(String... keyValuePairs) {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("Must provide key-value pairs (even number of arguments)");
    }
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      map.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return new MapPropertyResolver(map);
  }
}
