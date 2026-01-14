package com.workflow.interpolation;

import com.workflow.interpolation.exception.InterpolationException;
import com.workflow.interpolation.resolver.CompositePropertyResolver;
import com.workflow.interpolation.resolver.EnvironmentPropertyResolver;
import com.workflow.interpolation.resolver.PropertyResolver;
import com.workflow.interpolation.resolver.SystemPropertiesResolver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Default implementation of {@link StringInterpolator} with support for:
 *
 * <ul>
 *   <li>Simple placeholders: {@code ${key}}
 *   <li>Placeholders with defaults: {@code ${key:-defaultValue}}
 *   <li>Escaped placeholders: {@code \${notAPlaceholder}} becomes {@code ${notAPlaceholder}}
 *   <li>Nested property resolution: values can contain placeholders
 *   <li>Configurable max recursion depth to prevent infinite loops
 * </ul>
 *
 * <p><b>Resolution Order:</b> Placeholders are resolved using a {@link PropertyResolver} chain. The
 * default order is:
 *
 * <ol>
 *   <li>Custom properties (order 50)
 *   <li>WorkflowContext (order 100)
 *   <li>System properties (order 200)
 *   <li>Environment variables (order 300)
 * </ol>
 *
 * @see StringInterpolator
 * @see PropertyResolver
 */
public class DefaultStringInterpolator implements StringInterpolator {

  /** Default maximum recursion depth for nested placeholder resolution. */
  public static final int DEFAULT_MAX_DEPTH = 10;

  /** Pattern for matching placeholders: ${key} or ${key:-default} */
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?<!\\\\)\\$\\{([^}]+)}");

  /** Pattern for escaped placeholders: \${...} */
  private static final Pattern ESCAPED_PLACEHOLDER_PATTERN = Pattern.compile("\\\\\\$\\{([^}]+)}");

  /** Pattern to check if a string contains any placeholders */
  private static final Pattern HAS_PLACEHOLDER_PATTERN = Pattern.compile("(?<!\\\\)\\$\\{");

  private static final String RESOLVER_MUST_NOT_BE_NULL = "resolver must not be null";

  @Getter private final PropertyResolver resolver;
  @Getter private final int maxDepth;
  private final boolean strictByDefault;

  private DefaultStringInterpolator(
      PropertyResolver resolver, int maxDepth, boolean strictByDefault) {
    this.resolver = Objects.requireNonNull(resolver, RESOLVER_MUST_NOT_BE_NULL);
    this.maxDepth = maxDepth;
    this.strictByDefault = strictByDefault;
  }

  @Override
  public String interpolate(String input) {
    return interpolate(input, strictByDefault);
  }

  @Override
  public String interpolate(String input, boolean strict) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return interpolateInternal(input, strict, 0, new HashSet<>());
  }

  private String interpolateInternal(
      String input, boolean strict, int depth, Set<String> resolving) {
    checkInterpolationDepth(depth);

    if (!containsPlaceholders(input)) {
      return unescapePlaceholders(input);
    }

    StringBuilder result = new StringBuilder();
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);

    while (matcher.find()) {
      String fullMatch = matcher.group(0);
      String content = matcher.group(1);

      String key;
      String defaultValue = null;

      int defaultSeparatorIndex = content.indexOf(":-");
      if (defaultSeparatorIndex >= 0) {
        key = content.substring(0, defaultSeparatorIndex);
        defaultValue = content.substring(defaultSeparatorIndex + 2);
      } else {
        key = content;
      }

      key = key.trim();

      if (resolving.contains(key)) {
        throw new InterpolationException("Circular reference detected for key: " + key, key);
      }

      Optional<String> resolved = resolver.resolve(key);
      String replacement;

      if (resolved.isPresent()) {
        replacement = resolved.get();
      } else if (defaultValue != null) {
        replacement = defaultValue;
      } else if (strict) {
        throw new InterpolationException("Unable to resolve placeholder: ${" + key + "}", key);
      } else {
        replacement = fullMatch;
      }

      // Only recurse if we got a different value (not keeping the original placeholder)
      if (!replacement.equals(fullMatch) && containsPlaceholders(replacement)) {
        Set<String> newResolving = new HashSet<>(resolving);
        newResolving.add(key);
        replacement = interpolateInternal(replacement, strict, depth + 1, newResolving);
      }

      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);

    return unescapePlaceholders(result.toString());
  }

  private void checkInterpolationDepth(int depth) {
    if (depth > maxDepth) {
      throw new InterpolationException(
          "Maximum interpolation depth ("
              + maxDepth
              + ") exceeded. "
              + "Possible circular reference or deeply nested properties.");
    }
  }

  private String unescapePlaceholders(String input) {
    return ESCAPED_PLACEHOLDER_PATTERN.matcher(input).replaceAll("\\${$1}");
  }

  @Override
  public boolean containsPlaceholders(String input) {
    if (input == null || input.isEmpty()) {
      return false;
    }
    return HAS_PLACEHOLDER_PATTERN.matcher(input).find();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static DefaultStringInterpolator withDefaults() {
    return builder()
        .addResolver(new SystemPropertiesResolver())
        .addResolver(new EnvironmentPropertyResolver())
        .build();
  }

  public static class Builder {
    private final List<PropertyResolver> resolvers = new ArrayList<>();
    private int maxDepth = DEFAULT_MAX_DEPTH;
    private boolean strictByDefault = false;

    public Builder addResolver(PropertyResolver resolver) {
      Objects.requireNonNull(resolver, RESOLVER_MUST_NOT_BE_NULL);
      resolvers.add(resolver);
      return this;
    }

    public Builder addResolvers(List<PropertyResolver> resolvers) {
      Objects.requireNonNull(resolvers, "resolvers must not be null");
      this.resolvers.addAll(resolvers);
      return this;
    }

    public Builder resolver(PropertyResolver resolver) {
      Objects.requireNonNull(resolver, RESOLVER_MUST_NOT_BE_NULL);
      this.resolvers.clear();
      this.resolvers.add(resolver);
      return this;
    }

    public Builder maxDepth(int maxDepth) {
      if (maxDepth <= 0) {
        throw new IllegalArgumentException("maxDepth must be positive");
      }
      this.maxDepth = maxDepth;
      return this;
    }

    public Builder strict(boolean strict) {
      this.strictByDefault = strict;
      return this;
    }

    public DefaultStringInterpolator build() {
      if (resolvers.isEmpty()) {
        throw new IllegalStateException("At least one resolver must be configured");
      }
      PropertyResolver compositeResolver =
          resolvers.size() == 1
              ? resolvers.getFirst()
              : CompositePropertyResolver.builder().addAll(resolvers).build();
      return new DefaultStringInterpolator(compositeResolver, maxDepth, strictByDefault);
    }
  }
}
