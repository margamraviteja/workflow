package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.Objects;

/**
 * An abstract base class for tasks that provides common functionality such as context validation
 * and error handling.
 *
 * <p><b>Key Responsibilities:</b>
 *
 * <ul>
 *   <li>Enforces that context is not null
 *   <li>Catches exceptions and wraps them consistently in {@link TaskExecutionException}
 *   <li>Delegates actual work to {@link #doExecute(WorkflowContext)} implemented by subclasses
 *   <li>Provides helper methods for context value retrieval with type safety
 *   <li>Provides a human-friendly task name for logging
 * </ul>
 *
 * <p><b>Exception Handling:</b> TaskExecutionExceptions are propagated as-is; other exceptions are
 * wrapped in TaskExecutionException for consistent error handling.
 *
 * <p><b>Helper Methods:</b>
 *
 * <ul>
 *   <li>{@link #require(WorkflowContext, String, Class)} - Retrieve required value, throw if
 *       missing
 *   <li>{@link #getOrDefault(WorkflowContext, String, Class, Object)} - Retrieve with fallback
 *       value
 * </ul>
 *
 * <p><b>Example implementation:</b>
 *
 * <pre>{@code
 * public class ValidatingTask extends AbstractTask {
 *     private final String requiredKey;
 *
 *     public ValidatingTask(String requiredKey) {
 *         this.requiredKey = requiredKey;
 *     }
 *
 *     @Override
 *     protected void doExecute(WorkflowContext context) throws TaskExecutionException {
 *         String value = require(context, requiredKey, String.class);
 *         // Process value...
 *     }
 * }
 * }</pre>
 *
 * @see Task
 * @see TaskExecutionException
 */
public abstract class AbstractTask implements Task {

  @Override
  public final void execute(WorkflowContext context) throws TaskExecutionException {
    Objects.requireNonNull(context, "WorkflowContext must not be null");
    try {
      doExecute(context);
    } catch (TaskExecutionException e) {
      throw e;
    } catch (Exception e) {
      throw new TaskExecutionException("Task failed: " + e.getMessage(), e);
    }
  }

  protected abstract void doExecute(WorkflowContext context) throws TaskExecutionException;

  protected <T> T require(WorkflowContext context, String key, Class<T> type) {
    T value = context.getTyped(key, type);
    if (value == null) {
      throw new IllegalStateException("Required key missing: " + key);
    }
    return value;
  }

  protected <T> T getOrDefault(WorkflowContext context, String key, Class<T> type, T defaultValue) {
    T value = context.getTyped(key, type);
    return value != null ? value : defaultValue;
  }

  @Override
  public String getName() {
    return getClass().getSimpleName() + ":" + System.identityHashCode(this);
  }
}
