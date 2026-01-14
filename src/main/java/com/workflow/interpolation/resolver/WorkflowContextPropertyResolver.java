package com.workflow.interpolation.resolver;

import com.workflow.context.WorkflowContext;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * A {@link PropertyResolver} that resolves properties from a {@link WorkflowContext}.
 *
 * <p>This resolver retrieves values stored in the workflow context and converts them to strings. It
 * supports hierarchical key lookup (e.g., "user.name") using a two-phase resolution strategy when
 * strict mode is disabled.
 *
 * <p>Resolution strategy:
 *
 * <ol>
 *   <li>First, try to resolve by full key (exact match in context)
 *   <li>If not found and strict mode is disabled, split the key by dot (.) and traverse nested
 *       maps/objects
 * </ol>
 *
 * <p>By default, strict mode is enabled, which means only exact key matches are resolved.
 *
 * @see PropertyResolver
 * @see WorkflowContext
 */
public class WorkflowContextPropertyResolver implements PropertyResolver {

  /** Default order for workflow context property resolver. */
  public static final int DEFAULT_ORDER = 100;

  @Getter private final WorkflowContext context;
  private final int order;

  @Getter private final boolean strict;

  /**
   * Creates a new workflow context property resolver with default order and strict mode enabled.
   *
   * @param context the workflow context to resolve properties from
   */
  public WorkflowContextPropertyResolver(WorkflowContext context) {
    this(context, DEFAULT_ORDER, true);
  }

  /**
   * Creates a new workflow context property resolver with specified order and strict mode enabled.
   *
   * @param context the workflow context to resolve properties from
   * @param order the order of this resolver
   */
  public WorkflowContextPropertyResolver(WorkflowContext context, int order) {
    this(context, order, true);
  }

  /**
   * Creates a new workflow context property resolver with specified order and strict mode.
   *
   * @param context the workflow context to resolve properties from
   * @param order the order of this resolver
   * @param strict if true, only exact key matches are resolved; if false, nested resolution via dot
   *     notation is enabled
   */
  public WorkflowContextPropertyResolver(WorkflowContext context, int order, boolean strict) {
    Objects.requireNonNull(context, "context must not be null");
    this.context = context;
    this.order = order;
    this.strict = strict;
  }

  @Override
  public Optional<String> resolve(String key) {
    // First, try to resolve by full key (exact match)
    Object value = context.get(key);
    if (value != null) {
      return Optional.of(String.valueOf(value));
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

    // Get the root object from context using the first part
    Object current = context.get(parts[0]);
    if (current == null) {
      return Optional.empty();
    }

    // Traverse the remaining parts
    for (int i = 1; i < parts.length; i++) {
      if (current instanceof Map) {
        current = ((Map<String, Object>) current).get(parts[i]);
        if (current == null) {
          return Optional.empty();
        }
      } else {
        return Optional.empty();
      }
    }

    return Optional.of(String.valueOf(current));
  }

  @Override
  public int order() {
    return order;
  }
}
