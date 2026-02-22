package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.exception.TaskValidationException;
import java.sql.Connection;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public abstract class AbstractTask implements Task {

  @Override
  public final void execute(WorkflowContext context) throws TaskExecutionException {
    Objects.requireNonNull(context, "WorkflowContext must not be null");

    try {
      // Validate required keys
      validateRequiredKeys(context);

      // Pre-execution logic
      beforeExecute(context);

      // Delegate to subclass implementation
      doExecute(context);
    } catch (TaskExecutionException e) {
      throw e;
    } catch (Exception e) {
      throw new TaskExecutionException("Task failed: " + e.getMessage(), e);
    } finally {
      // Post-execution logic (always runs)
      afterExecute(context);
    }
  }

  private void validateRequiredKeys(WorkflowContext context) {
    List<String> missingKeys = new ArrayList<>();
    for (String key : getRequiredKeys()) {
      // Check if context already has the key
      if (!context.containsKey(key)) {
        missingKeys.add(String.format("Task [%s] requires missing key: %s", getName(), key));
      }
    }
    if (!missingKeys.isEmpty()) {
      throw new TaskValidationException("Missing required keys: " + String.join(", ", missingKeys));
    }
  }

  protected abstract void doExecute(WorkflowContext context) throws TaskExecutionException;

  /**
   * Retrieves a database connection from the context if available. This is useful for tasks that
   * may be executed within a {@link JdbcTransactionTask} which provides a connection in the
   * context.
   *
   * @param context the workflow context to check for a connection
   * @return the database connection if present, or null if not available
   */
  protected Connection getConnection(WorkflowContext context) {
    return context.getTyped(JdbcTransactionTask.CONNECTION_CONTEXT_KEY, Connection.class);
  }

  /**
   * Attempts to roll back the transaction on the given connection.
   *
   * <p>Logs a warning if rollback fails but does not throw an exception, allowing the original
   * exception to be propagated.
   *
   * @param connection the database connection to rollback (maybe null)
   */
  protected void rollback(Connection connection) {
    if (connection != null) {
      try {
        connection.rollback();
      } catch (Exception ex) {
        log.warn("Failed to rollback transaction: {}", ex.getMessage());
      }
    }
  }

  /**
   * Attempts to close the given database connection.
   *
   * <p>Logs a warning if closing fails but does not throw an exception, allowing any original
   * exception to be propagated.
   *
   * @param connection the database connection to close (maybe null)
   */
  protected void close(Connection connection) {
    if (connection != null) {
      try {
        connection.close();
      } catch (Exception ex) {
        log.warn("Failed to close connection: {}", ex.getMessage());
      }
    }
  }

  /**
   * Hook called before {@link #doExecute(WorkflowContext)}. Useful for logging, metrics, or setup.
   */
  protected void beforeExecute(WorkflowContext context) {
    // Default: No-op
  }

  /**
   * Hook called after {@link #doExecute(WorkflowContext)}, regardless of success or failure. Useful
   * for cleanup or performance tracking.
   */
  protected void afterExecute(WorkflowContext context) {
    // Default: No-op
  }

  /**
   * Defines the keys that must be present in the context before execution. Subclasses can override
   * to specify their own required keys.
   */
  public Set<String> getRequiredKeys() {
    return Collections.emptySet();
  }

  protected <T> T require(WorkflowContext context, String key, Class<T> type) {
    T value = context.getTyped(key, type);
    if (value == null) {
      throw new TaskValidationException("Required key missing: " + key);
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
