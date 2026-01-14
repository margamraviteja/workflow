package com.workflow.interpolation;

import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.resolver.EnvironmentPropertyResolver;
import com.workflow.interpolation.resolver.MapPropertyResolver;
import com.workflow.interpolation.resolver.SystemPropertiesResolver;
import com.workflow.interpolation.resolver.WorkflowContextPropertyResolver;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class providing convenient static methods for string interpolation.
 *
 * <p>This class provides shortcuts for common interpolation scenarios without needing to manually
 * construct interpolators and resolvers.
 *
 * @see StringInterpolator
 * @see DefaultStringInterpolator
 */
public final class Interpolation {

  private static final StringInterpolator DEFAULT_INTERPOLATOR =
      DefaultStringInterpolator.withDefaults();

  private static final String CONTEXT_MUST_NOT_BE_NULL = "context must not be null";
  private static final String PROPERTIES_MUST_NOT_BE_NULL = "properties must not be null";

  private Interpolation() {}

  public static String interpolate(String input) {
    return DEFAULT_INTERPOLATOR.interpolate(input);
  }

  public static String interpolate(String input, boolean strict) {
    return DEFAULT_INTERPOLATOR.interpolate(input, strict);
  }

  public static String interpolate(String input, WorkflowContext context) {
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return forContext(context).interpolate(input);
  }

  public static String interpolate(String input, WorkflowContext context, boolean strict) {
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return forContext(context).interpolate(input, strict);
  }

  public static String interpolate(String input, Map<String, ?> properties) {
    Objects.requireNonNull(properties, PROPERTIES_MUST_NOT_BE_NULL);
    return forProperties(properties).interpolate(input);
  }

  public static String interpolate(String input, Map<String, ?> properties, boolean strict) {
    Objects.requireNonNull(properties, PROPERTIES_MUST_NOT_BE_NULL);
    return forProperties(properties).interpolate(input, strict);
  }

  public static boolean containsPlaceholders(String input) {
    return DEFAULT_INTERPOLATOR.containsPlaceholders(input);
  }

  public static StringInterpolator forContext(WorkflowContext context) {
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return DefaultStringInterpolator.builder()
        .addResolver(new WorkflowContextPropertyResolver(context))
        .addResolver(new SystemPropertiesResolver())
        .addResolver(new EnvironmentPropertyResolver())
        .build();
  }

  public static StringInterpolator forProperties(Map<String, ?> properties) {
    Objects.requireNonNull(properties, PROPERTIES_MUST_NOT_BE_NULL);
    return DefaultStringInterpolator.builder()
        .addResolver(new MapPropertyResolver(properties))
        .addResolver(new SystemPropertiesResolver())
        .addResolver(new EnvironmentPropertyResolver())
        .build();
  }

  public static StringInterpolator forContextAndProperties(
      WorkflowContext context, Map<String, ?> properties) {
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    Objects.requireNonNull(properties, PROPERTIES_MUST_NOT_BE_NULL);
    return DefaultStringInterpolator.builder()
        .addResolver(new MapPropertyResolver(properties))
        .addResolver(new WorkflowContextPropertyResolver(context))
        .addResolver(new SystemPropertiesResolver())
        .addResolver(new EnvironmentPropertyResolver())
        .build();
  }

  public static StringInterpolator defaultInterpolator() {
    return DEFAULT_INTERPOLATOR;
  }

  public static DefaultStringInterpolator.Builder builder() {
    return DefaultStringInterpolator.builder();
  }
}
