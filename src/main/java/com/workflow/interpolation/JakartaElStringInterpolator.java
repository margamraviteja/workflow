package com.workflow.interpolation;

import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.exception.InterpolationException;
import jakarta.el.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * A {@link StringInterpolator} implementation that uses Jakarta Expression Language (Jakarta EL)
 * for expression evaluation.
 *
 * <p>This interpolator supports the standard EL syntax with {@code #{expression}} placeholders and
 * provides powerful expression evaluation capabilities including:
 *
 * <ul>
 *   <li>Property access: {@code #{user.name}}, {@code #{order.items[0].price}}
 *   <li>Method calls: {@code #{user.getName()}}, {@code #{list.size()}}
 *   <li>Arithmetic operations: {@code #{price * quantity}}, {@code #{total + tax}}
 *   <li>Comparisons: {@code #{age >= 18}}, {@code #{status == 'active'}}
 *   <li>Logical operations: {@code #{a && b}}, {@code #{!empty list}}
 *   <li>Conditional expressions: {@code #{age >= 18 ? 'adult' : 'minor'}}
 *   <li>String concatenation: {@code #{firstName += ' ' += lastName}}
 *   <li>Collection operations: {@code #{items.stream().filter(i -> i.active).toList()}}
 * </ul>
 *
 * <p><b>Placeholder Syntax:</b>
 *
 * <ul>
 *   <li>{@code #{expression}} - EL expression placeholder
 *   <li>{@code \#{literal}} - Escaped placeholder (becomes literal {@code #{literal}})
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * Map<String, Object> variables = Map.of(
 *     "user", Map.of("name", "Alice", "age", 30),
 *     "items", List.of("apple", "banana")
 * );
 *
 * JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(variables);
 *
 * // Property access
 * interpolator.interpolate("Hello, #{user.name}!"); // "Hello, Alice!"
 *
 * // Conditional expression
 * interpolator.interpolate("Status: #{user.age >= 18 ? 'adult' : 'minor'}"); // "Status: adult"
 *
 * // Collection access
 * interpolator.interpolate("First item: #{items[0]}"); // "First item: apple"
 * }</pre>
 *
 * @see StringInterpolator
 * @see jakarta.el.ExpressionFactory
 */
public class JakartaElStringInterpolator implements StringInterpolator {

  /** Pattern to check if a string contains any placeholders */
  private static final Pattern HAS_PLACEHOLDER_PATTERN = Pattern.compile("(?<!\\\\)\\$\\{");

  private static final ExpressionFactory EXPRESSION_FACTORY = ELManager.getExpressionFactory();

  private static final String CONTEXT_MUST_NOT_BE_NULL = "context must not be null";

  private final Map<String, Object> variables;
  private final WorkflowContext workflowContext;
  @Getter private final boolean strictByDefault;

  private JakartaElStringInterpolator(
      Map<String, Object> variables, WorkflowContext workflowContext, boolean strictByDefault) {
    this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
    this.workflowContext = workflowContext;
    this.strictByDefault = strictByDefault;
  }

  @Override
  public String interpolate(String input) {
    return interpolate(input, strictByDefault);
  }

  @Override
  public String interpolate(String input, boolean strict) {
    if (input == null || input.isBlank()) {
      return input;
    }

    ELContext elContext = createELContext();
    try {
      return evaluateExpression(input, elContext);
    } catch (PropertyNotFoundException e) {
      if (strict) {
        throw new InterpolationException(
            "Unable to resolve EL expression" + e.getMessage(), input, e);
      }
    } catch (Exception e) {
      if (strict) {
        throw new InterpolationException(
            "Error evaluating EL expression" + e.getMessage(), input, e);
      }
    }
    return input;
  }

  private ELContext createELContext() {
    StandardELContext context = new StandardELContext(EXPRESSION_FACTORY);

    // Add variables from the map to the EL context
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      context
          .getVariableMapper()
          .setVariable(
              entry.getKey(),
              EXPRESSION_FACTORY.createValueExpression(entry.getValue(), Object.class));
    }

    // Add a custom resolver for WorkflowContext if present
    if (workflowContext != null) {
      context.addELResolver(new WorkflowContextELResolver(workflowContext));
    }

    return context;
  }

  private String evaluateExpression(String expression, ELContext context) {
    String elExpression = expression.replaceAll("(?<!\\\\)#\\{", "\\\\#{");
    ValueExpression valueExpression =
        EXPRESSION_FACTORY.createValueExpression(context, elExpression, Object.class);
    Object value = valueExpression.getValue(context);
    return value != null ? String.valueOf(value) : "";
  }

  @Override
  public boolean containsPlaceholders(String input) {
    if (input == null || input.isEmpty()) {
      return false;
    }
    return HAS_PLACEHOLDER_PATTERN.matcher(input).find();
  }

  /**
   * Creates a new builder for configuring a {@link JakartaElStringInterpolator}.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a new interpolator with the given variables.
   *
   * @param variables the variables to use for expression evaluation
   * @return a new interpolator instance
   */
  public static JakartaElStringInterpolator forVariables(Map<String, Object> variables) {
    return builder().variables(variables).build();
  }

  /**
   * Creates a new interpolator using the given workflow context as the source of variables.
   *
   * @param context the workflow context
   * @return a new interpolator instance
   */
  public static JakartaElStringInterpolator forContext(WorkflowContext context) {
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return builder().workflowContext(context).build();
  }

  /**
   * Creates a new interpolator using the given workflow context and additional variables.
   *
   * @param context the workflow context
   * @param additionalVariables additional variables to merge with context
   * @return a new interpolator instance
   */
  public static JakartaElStringInterpolator forContextAndVariables(
      WorkflowContext context, Map<String, Object> additionalVariables) {
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    Objects.requireNonNull(additionalVariables, "additionalVariables must not be null");
    return builder().workflowContext(context).addVariables(additionalVariables).build();
  }

  /** Builder for {@link JakartaElStringInterpolator}. */
  public static class Builder {
    private final Map<String, Object> variables = new HashMap<>();
    private WorkflowContext workflowContext;
    private boolean strictByDefault = false;

    private Builder() {}

    /**
     * Sets all variables for the interpolator, replacing any existing variables.
     *
     * @param variables the variables map
     * @return this builder
     */
    public Builder variables(Map<String, Object> variables) {
      Objects.requireNonNull(variables, "variables must not be null");
      this.variables.clear();
      this.variables.putAll(variables);
      return this;
    }

    /**
     * Adds a single variable to the interpolator.
     *
     * @param name the variable name
     * @param value the variable value
     * @return this builder
     */
    public Builder variable(String name, Object value) {
      Objects.requireNonNull(name, "name must not be null");
      this.variables.put(name, value);
      return this;
    }

    /**
     * Adds multiple variables to the interpolator.
     *
     * @param variables the variables to add
     * @return this builder
     */
    public Builder addVariables(Map<String, Object> variables) {
      Objects.requireNonNull(variables, "variables must not be null");
      this.variables.putAll(variables);
      return this;
    }

    /**
     * Sets the workflow context for variable resolution.
     *
     * @param context the workflow context
     * @return this builder
     */
    public Builder workflowContext(WorkflowContext context) {
      Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
      this.workflowContext = context;
      return this;
    }

    /**
     * Sets the variables from a workflow context. This is an alias for {@link
     * #workflowContext(WorkflowContext)}.
     *
     * @param context the workflow context
     * @return this builder
     */
    public Builder context(WorkflowContext context) {
      return workflowContext(context);
    }

    /**
     * Sets whether strict mode is enabled by default. In strict mode, unresolved expressions will
     * throw an exception.
     *
     * @param strict true to enable strict mode by default
     * @return this builder
     */
    public Builder strict(boolean strict) {
      this.strictByDefault = strict;
      return this;
    }

    /**
     * Builds the interpolator.
     *
     * @return a new {@link JakartaElStringInterpolator} instance
     */
    public JakartaElStringInterpolator build() {
      return new JakartaElStringInterpolator(variables, workflowContext, strictByDefault);
    }
  }

  /**
   * Custom ELResolver that resolves properties from a WorkflowContext.
   *
   * <p>This resolver is used to look up variables by name from the workflow context when they are
   * not found in the variables map.
   */
  private static class WorkflowContextELResolver extends ELResolver {
    private final WorkflowContext context;

    WorkflowContextELResolver(WorkflowContext context) {
      this.context = context;
    }

    @Override
    public Object getValue(ELContext elContext, Object base, Object property) {
      if (base == null && property instanceof String key) {
        Object value = context.get(key);
        if (value != null) {
          elContext.setPropertyResolved(true);
          return value;
        }
      }
      return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
      return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
      throw new PropertyNotWritableException("WorkflowContext is read-only");
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
      return true;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
      return String.class;
    }
  }
}
