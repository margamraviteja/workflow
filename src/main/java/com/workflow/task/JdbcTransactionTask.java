package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes multiple tasks within a single database transaction with automatic commit or rollback.
 *
 * <p><b>Purpose:</b> Provides ACID transaction management for workflow tasks that perform database
 * operations. Ensures all operations succeed together or fail together, maintaining data
 * consistency.
 *
 * <p><b>Transaction Semantics:</b>
 *
 * <ul>
 *   <li><b>Atomicity:</b> All tasks execute within one transaction - all succeed or all rollback
 *   <li><b>Auto-commit:</b> Connection auto-commit is disabled during transaction
 *   <li><b>Commit:</b> Transaction committed automatically on successful completion
 *   <li><b>Rollback:</b> Transaction rolled back automatically on any task failure
 *   <li><b>Isolation:</b> Uses database's default transaction isolation level (can be configured)
 *   <li><b>Connection Sharing:</b> Same connection used for all tasks in transaction
 * </ul>
 *
 * <p><b>Input Modes:</b>
 *
 * <p>This task supports two modes for providing the transaction tasks:
 *
 * <ol>
 *   <li><b>Direct Input Mode:</b> Tasks provided directly via builder
 *       <ul>
 *         <li>Use {@link Builder#task(Task)} to add tasks directly
 *         <li>Tasks are embedded in the task instance
 *       </ul>
 *   <li><b>Context Mode:</b> Tasks read from WorkflowContext at execution time
 *       <ul>
 *         <li>Use {@link Builder#readingTasksFrom(String)} to specify context key
 *         <li>Allows dynamic transaction composition based on workflow state
 *       </ul>
 * </ol>
 *
 * <p><b>Context Keys and Expected Types:</b>
 *
 * <ul>
 *   <li><b>tasksKey</b> &mdash; {@link List}&lt;{@link Task}&gt;: tasks to execute in transaction
 *       (required if not using direct tasks)
 *   <li><b>Connection Management:</b> A special context key "_jdbc_transaction_connection" is used
 *       internally to share the connection across tasks
 * </ul>
 *
 * <p><b>Isolation Levels:</b>
 *
 * <ul>
 *   <li><b>READ_UNCOMMITTED:</b> Lowest isolation, allows dirty reads
 *   <li><b>READ_COMMITTED:</b> Prevents dirty reads
 *   <li><b>REPEATABLE_READ:</b> Prevents dirty and non-repeatable reads
 *   <li><b>SERIALIZABLE:</b> Highest isolation, fully isolated transactions
 *   <li><b>DEFAULT:</b> Uses database's default isolation level
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Any task failure → automatic rollback → {@link TaskExecutionException}
 *   <li>Connection failures → {@link TaskExecutionException}
 *   <li>Commit failures → {@link TaskExecutionException}
 *   <li>Rollback failures → logged but original exception propagated
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe provided the {@link DataSource} and nested
 * tasks are thread-safe.
 *
 * <p><b>Best Practices:</b>
 *
 * <ul>
 *   <li>Keep transactions short to minimize lock contention
 *   <li>Use appropriate isolation level for your use case
 *   <li>Avoid long-running operations inside transactions
 *   <li>Be aware of deadlock potential with multiple transactions
 *   <li>Use connection pooling for better resource management
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * Task debit = ctx -> executeUpdate("UPDATE accounts SET balance = balance - 200 WHERE id = 1", ctx);
 * Task credit = ctx -> executeUpdate("UPDATE accounts SET balance = balance + 200 WHERE id = 2", ctx);
 *
 * JdbcTransactionTask txTask = JdbcTransactionTask.builder().dataSource(dataSource).task(debit).task(credit).build();
 * txTask.execute(new WorkflowContext());
 * // Both updates committed together, or both rolled back on failure
 * }</pre>
 *
 * <p><b>Note:</b> Nested tasks should be designed to work with the shared connection from the
 * context.
 *
 * @see JdbcUpdateTask
 * @see JdbcBatchUpdateTask
 * @see JdbcQueryTask
 */
@Slf4j
public class JdbcTransactionTask extends AbstractTask {

  public static final String CONNECTION_CONTEXT_KEY = "_jdbc_transaction_connection";

  private final DataSource dataSource;
  private final List<Task> tasks;
  private final String tasksKey;
  private final int isolationLevel;

  /**
   * Private constructor used by the builder.
   *
   * @param builder the builder instance containing configuration
   */
  private JdbcTransactionTask(Builder builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.tasks = builder.tasks != null ? Collections.unmodifiableList(builder.tasks) : null;
    this.tasksKey = builder.tasksKey;
    this.isolationLevel = builder.isolationLevel;
  }

  /**
   * Executes all tasks within a single database transaction.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>Obtains connection from DataSource
   *   <li>Disables auto-commit
   *   <li>Sets configured isolation level if not default
   *   <li>Stores connection in context for nested tasks to use
   *   <li>Executes all tasks sequentially
   *   <li>Commits transaction on success
   *   <li>Rolls back transaction on any failure
   *   <li>Removes connection from context and closes it
   * </ul>
   *
   * @param context the workflow context
   * @throws TaskExecutionException if any task fails or transaction cannot be committed
   */
  @Override
  @SuppressWarnings("unchecked")
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    // Determine tasks source (direct or from context)
    List<Task> effectiveTasks;
    if (tasks != null) {
      effectiveTasks = tasks;
    } else if (tasksKey != null) {
      effectiveTasks = (List<Task>) context.get(tasksKey);
      if (effectiveTasks == null || effectiveTasks.isEmpty()) {
        throw new TaskExecutionException("No tasks found in context at key: " + tasksKey);
      }
    } else {
      throw new TaskExecutionException("No tasks configured for transaction");
    }

    Connection connection = null;
    boolean originalAutoCommit = true;
    int originalIsolationLevel = Connection.TRANSACTION_NONE;

    try {
      // Get connection and configure transaction
      connection = dataSource.getConnection();
      originalAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);

      originalIsolationLevel = connection.getTransactionIsolation();

      // Set isolation level if specified
      int transactionIsolationLevel = getIsolationLevel();
      if (transactionIsolationLevel != Connection.TRANSACTION_NONE) {
        connection.setTransactionIsolation(transactionIsolationLevel);
      }

      // Store connection in context for nested tasks
      context.put(CONNECTION_CONTEXT_KEY, connection);

      // Execute all tasks
      for (Task task : effectiveTasks) {
        task.execute(context);
      }

      // Commit transaction
      connection.commit();

    } catch (Exception e) {
      // Rollback on any error
      rollback(connection);
      throw new TaskExecutionException(
          "Transaction failed and was rolled back: " + e.getMessage(), e);

    } finally {
      // Cleanup: remove connection from context and restore settings
      context.remove(CONNECTION_CONTEXT_KEY);
      cleanup(connection, originalIsolationLevel, originalAutoCommit);
    }
  }

  private int getIsolationLevel() {
    switch (isolationLevel) {
      case Connection.TRANSACTION_READ_UNCOMMITTED,
      Connection.TRANSACTION_READ_COMMITTED,
      Connection.TRANSACTION_REPEATABLE_READ,
      Connection.TRANSACTION_SERIALIZABLE:
        break; // valid levels
      default:
        log.warn(
            "Invalid isolation level specified: {}. Defaulting to database default.",
            isolationLevel);
        return Connection.TRANSACTION_NONE;
    }
    return isolationLevel;
  }

  /**
   * Cleans up connection resources by restoring original settings and closing the connection.
   *
   * <p>Restores the original transaction isolation level and auto-commit setting before closing the
   * connection. Logs warnings if cleanup operations fail.
   *
   * @param connection the connection to clean up (maybe null)
   * @param originalIsolationLevel the original isolation level to restore
   * @param originalAutoCommit the original auto-commit setting to restore
   */
  private void cleanup(
      Connection connection, int originalIsolationLevel, boolean originalAutoCommit) {
    if (connection != null) {
      try {
        // Restore original isolation level if changed
        if (isolationLevel != Connection.TRANSACTION_NONE
            && originalIsolationLevel != Connection.TRANSACTION_NONE) {
          connection.setTransactionIsolation(originalIsolationLevel);
        }
        // Restore auto-commit
        connection.setAutoCommit(originalAutoCommit);
        connection.close();
      } catch (Exception e) {
        log.warn("Failed to cleanup connection: {}", e.getMessage());
      }
    }
  }

  /**
   * Create a new {@link Builder} to fluently configure and construct a {@link JdbcTransactionTask}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Direct mode
   * JdbcTransactionTask transaction = JdbcTransactionTask.builder()
   *     .dataSource(dataSource)
   *     .task(updateTask1)
   *     .task(updateTask2)
   *     .isolationLevel(Connection.TRANSACTION_SERIALIZABLE)
   *     .build();
   *
   * // Context mode
   * JdbcTransactionTask transaction = JdbcTransactionTask.builder()
   *     .dataSource(dataSource)
   *     .readingTasksFrom("transactionTasks")
   *     .build();
   * }</pre>
   *
   * @return a fresh {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** A fluent builder for creating {@link JdbcTransactionTask} instances. */
  public static class Builder {
    private DataSource dataSource;
    private List<Task> tasks;
    private String tasksKey;
    private int isolationLevel = Connection.TRANSACTION_NONE;

    /**
     * Set the {@link DataSource} that the task will use to obtain JDBC connections.
     *
     * @param dataSource the DataSource to use; must not be {@code null} when {@link #build()} is
     *     called
     * @return this builder instance
     */
    public Builder dataSource(DataSource dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    /**
     * Add a task to execute within the transaction (direct input mode).
     *
     * <p>This is mutually exclusive with {@link #readingTasksFrom(String)}. If both are used, the
     * direct tasks take precedence.
     *
     * @param task the task to add
     * @return this builder instance
     */
    public Builder task(Task task) {
      if (this.tasks == null) {
        this.tasks = new ArrayList<>();
      }
      this.tasks.add(task);
      return this;
    }

    /**
     * Configure the context key from which the task list will be read (context mode).
     *
     * <p>This is mutually exclusive with {@link #task(Task)}. If both are used, the direct tasks
     * take precedence.
     *
     * @param tasksKey the context key containing the {@link List} of {@link Task}s
     * @return this builder instance
     */
    public Builder readingTasksFrom(String tasksKey) {
      this.tasksKey = tasksKey;
      return this;
    }

    /**
     * Set the transaction isolation level.
     *
     * <p>Available levels from {@link Connection}:
     *
     * <ul>
     *   <li>{@link Connection#TRANSACTION_READ_UNCOMMITTED}
     *   <li>{@link Connection#TRANSACTION_READ_COMMITTED}
     *   <li>{@link Connection#TRANSACTION_REPEATABLE_READ}
     *   <li>{@link Connection#TRANSACTION_SERIALIZABLE}
     *   <li>{@link Connection#TRANSACTION_NONE} (default - use database default)
     * </ul>
     *
     * @param isolationLevel the isolation level; default is TRANSACTION_NONE (database default)
     * @return this builder instance
     */
    public Builder isolationLevel(int isolationLevel) {
      this.isolationLevel = isolationLevel;
      return this;
    }

    /**
     * Build a new {@link JdbcTransactionTask} with the configured values.
     *
     * <p>Validates that required configuration is present:
     *
     * <ul>
     *   <li>dataSource must be set
     *   <li>Either tasks or tasksKey must be set
     * </ul>
     *
     * @return a new {@link JdbcTransactionTask} instance
     * @throws IllegalStateException if dataSource is not set, or if neither tasks nor tasksKey is
     *     set
     */
    public JdbcTransactionTask build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if ((tasks == null || tasks.isEmpty()) && tasksKey == null) {
        throw new IllegalStateException("Either tasks or tasksKey must be set");
      }
      return new JdbcTransactionTask(this);
    }
  }
}
