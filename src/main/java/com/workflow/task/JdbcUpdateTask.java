package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * A {@code Task} implementation that executes a JDBC update, insert, or delete statement using a
 * provided {@link DataSource} and writes the affected row count back into the {@link
 * WorkflowContext}.
 *
 * <p>This task is designed for executing SQL statements that modify data (INSERT, UPDATE, DELETE,
 * or DDL statements) and require knowing the number of rows affected. For queries that return
 * result sets, use {@code JdbcQueryTask} instead.
 *
 * <p><b>Input Modes:</b>
 *
 * <p>This task supports two modes for providing inputs:
 *
 * <ol>
 *   <li><b>Direct Input Mode:</b> SQL and parameters provided directly via builder methods
 *       <ul>
 *         <li>Use {@link Builder#sql(String)} to provide SQL directly
 *         <li>Use {@link Builder#params(List)} to provide parameters directly
 *         <li>Inputs are embedded in the task instance
 *       </ul>
 *   <li><b>Context Mode:</b> SQL and parameters read from WorkflowContext at execution time
 *       <ul>
 *         <li>Use {@link Builder#readingSqlFrom(String)} to specify context key for SQL
 *         <li>Use {@link Builder#readingParamsFrom(String)} to specify context key for parameters
 *         <li>Allows dynamic SQL and parameters based on workflow state
 *       </ul>
 * </ol>
 *
 * <p><b>Behavior summary</b>
 *
 * <ul>
 *   <li>Reads an SQL update statement (either directly or from context)
 *   <li>Reads positional parameters (either directly or from context, defaults to empty if not
 *       provided)
 *   <li>Executes {@link PreparedStatement#executeUpdate()} and retrieves the integer count of
 *       affected rows
 *   <li>Stores the {@link Integer} result into the {@link WorkflowContext} under {@code outputKey}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is immutable and thread-safe. The underlying {@link
 * DataSource} should be thread-safe as per JDBC specification.
 *
 * <p><b>Resource Management:</b> Connections and prepared statements are automatically closed using
 * try-with-resources, ensuring proper cleanup even in case of exceptions.
 *
 * <p><b>Example usage - Direct Input Mode</b>
 *
 * <pre>{@code
 * // SQL and parameters provided directly
 * JdbcUpdateTask task = JdbcUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .sql("UPDATE users SET status = ? WHERE id = ?")
 *     .params(Arrays.asList("INACTIVE", 101))
 *     .writingRowsAffectedTo("rowsAffected")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * task.execute(context);
 *
 * Integer count = (Integer) context.get("rowsAffected");
 * System.out.println("Updated " + count + " rows");
 * }</pre>
 *
 * <p><b>Example usage - Context Mode</b>
 *
 * <pre>{@code
 * // SQL and parameters from context
 * JdbcUpdateTask task = JdbcUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .readingSqlFrom("updateSql")
 *     .readingParamsFrom("updateParams")
 *     .writingRowsAffectedTo("rowsAffected")
 *     .build();
 *
 * context.put("updateSql", "UPDATE users SET status = ? WHERE id = ?");
 * context.put("updateParams", Arrays.asList("INACTIVE", 101));
 *
 * task.execute(context);
 * Integer count = (Integer) context.get("rowsAffected");
 * }</pre>
 *
 * <p><b>Example usage - Mixed Mode</b>
 *
 * <pre>{@code
 * // Fixed SQL, dynamic parameters
 * JdbcUpdateTask task = JdbcUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .sql("UPDATE users SET status = ? WHERE id = ?")
 *     .readingParamsFrom("updateParams")
 *     .writingRowsAffectedTo("rowsAffected")
 *     .build();
 *
 * context.put("updateParams", Arrays.asList("ACTIVE", userId));
 * task.execute(context);
 * }</pre>
 *
 * @see PreparedStatement#executeUpdate()
 * @see DataSource
 */
public class JdbcUpdateTask extends AbstractTask {

  private final DataSource dataSource;
  private final String sql;
  private final List<Object> params;
  private final String sqlKey;
  private final String paramsKey;
  private final String outputKey;

  /**
   * Private constructor used by the builder.
   *
   * @param builder the builder instance containing configuration
   */
  private JdbcUpdateTask(Builder builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.sql = builder.sql;
    this.params = builder.params != null ? Collections.unmodifiableList(builder.params) : null;
    this.sqlKey = builder.sqlKey;
    this.paramsKey = builder.paramsKey;
    this.outputKey = Objects.requireNonNull(builder.outputKey, "outputKey must not be null");
  }

  /**
   * Executes the configured SQL update statement using values from the provided {@link
   * WorkflowContext} or direct inputs.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>If direct SQL was provided, uses it; otherwise retrieves from context using {@code
   *       sqlKey}
   *   <li>If direct params were provided, uses them; otherwise retrieves from context using {@code
   *       paramsKey} (defaults to empty list if absent)
   *   <li>Binds parameters in order using {@link PreparedStatement#setObject(int, Object)}
   *   <li>Executes {@link PreparedStatement#executeUpdate()} and stores the affected row count
   *       under {@code outputKey} in the context
   * </ul>
   *
   * @param context the workflow context from which to read inputs and into which to write outputs
   * @throws TaskExecutionException if any error occurs while obtaining a connection, preparing or
   *     executing the statement, or processing the results
   */
  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    // Determine SQL source
    String effectiveSql = getSql(context);

    // Determine parameters source
    List<Object> effectiveParams = getQueryParams(context);

    // 1. Check if a transaction connection exists in the context
    Connection sharedConn = getConnection(context);

    // 2. Use the shared connection if available, otherwise get a new one from DataSource
    boolean isShared = sharedConn != null;
    Connection conn = null;

    try {
      conn = isShared ? sharedConn : dataSource.getConnection();
      if (!isShared) {
        conn.setAutoCommit(false);
      }

      try (PreparedStatement stmt = conn.prepareStatement(effectiveSql)) {
        for (int i = 0; i < effectiveParams.size(); i++) {
          stmt.setObject(i + 1, effectiveParams.get(i));
        }

        int rowsAffected = stmt.executeUpdate();
        context.put(outputKey, rowsAffected);
      }

      if (!isShared) {
        conn.commit();
      }

    } catch (Exception e) {
      if (!isShared) {
        rollback(conn);
      }
      throw new TaskExecutionException("Update execution failed: " + e.getMessage(), e);
    } finally {
      if (!isShared) {
        close(conn);
      }
    }
  }

  /**
   * Retrieves query parameters from either direct configuration or workflow context.
   *
   * @param context the workflow context to read parameters from if not configured directly
   * @return the list of query parameters (never null, may be empty)
   */
  @SuppressWarnings("unchecked")
  private List<Object> getQueryParams(WorkflowContext context) {
    List<Object> effectiveParams;
    if (params != null) {
      effectiveParams = params;
    } else if (paramsKey != null) {
      effectiveParams = getOrDefault(context, paramsKey, List.class, Collections.emptyList());
    } else {
      effectiveParams = Collections.emptyList();
    }
    return effectiveParams;
  }

  /**
   * Retrieves the SQL update statement from either direct configuration or workflow context.
   *
   * @param context the workflow context to read SQL from if not configured directly
   * @return the SQL update statement
   * @throws TaskExecutionException if SQL is not found in context when using context mode
   */
  private String getSql(WorkflowContext context) {
    String effectiveSql;
    if (sql != null) {
      effectiveSql = sql;
    } else {
      effectiveSql = require(context, sqlKey, String.class);
    }
    return effectiveSql;
  }

  /**
   * Create a new {@link Builder} to fluently configure and construct a {@link JdbcUpdateTask}.
   *
   * @return a fresh {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** A fluent builder for creating {@link JdbcUpdateTask} instances. */
  public static class Builder {
    private DataSource dataSource;
    private String sql;
    private List<Object> params;
    private String sqlKey;
    private String paramsKey;
    private String outputKey;

    /**
     * Set the {@link DataSource} that the task will use to obtain JDBC connections.
     *
     * @param ds the DataSource to use; must not be {@code null} when {@link #build()} is called
     * @return this builder instance
     */
    public Builder dataSource(DataSource ds) {
      this.dataSource = ds;
      return this;
    }

    /**
     * Provide the SQL statement directly (direct input mode).
     *
     * @param sql the SQL UPDATE/INSERT/DELETE statement
     * @return this builder instance
     */
    public Builder sql(String sql) {
      this.sql = sql;
      return this;
    }

    /**
     * Provide statement parameters directly (direct input mode).
     *
     * @param params the list of positional parameters
     * @return this builder instance
     */
    public Builder params(List<Object> params) {
      this.params = params != null ? new ArrayList<>(params) : null;
      return this;
    }

    /**
     * Configure the context key from which the SQL string will be read (context mode).
     *
     * @param key the context key containing the SQL {@link String}
     * @return this builder instance
     */
    public Builder readingSqlFrom(String key) {
      this.sqlKey = key;
      return this;
    }

    /**
     * Configure the context key from which the positional parameter {@link List} will be read
     * (context mode).
     *
     * @param key the context key containing the parameters {@link List}
     * @return this builder instance
     */
    public Builder readingParamsFrom(String key) {
      this.paramsKey = key;
      return this;
    }

    /**
     * Configure the context key where the number of affected rows (Integer) will be stored.
     *
     * @param key the context key where the row count will be written
     * @return this builder instance
     */
    public Builder writingRowsAffectedTo(String key) {
      this.outputKey = key;
      return this;
    }

    /**
     * Constructs a new {@link JdbcUpdateTask} instance with the configured properties.
     *
     * @return a new {@link JdbcUpdateTask}
     * @throws IllegalStateException if dataSource or outputKey is not set, or if neither SQL nor
     *     sqlKey is set
     */
    public JdbcUpdateTask build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if (sql == null && sqlKey == null) {
        throw new IllegalStateException("Either sql or sqlKey must be set");
      }
      if (outputKey == null) {
        throw new IllegalStateException("outputKey must be set");
      }
      return new JdbcUpdateTask(this);
    }
  }
}
