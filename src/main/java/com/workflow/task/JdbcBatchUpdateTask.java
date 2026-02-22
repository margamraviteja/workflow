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
import lombok.extern.slf4j.Slf4j;

/**
 * Executes multiple SQL statements in a batch for efficient bulk INSERT, UPDATE, or DELETE
 * operations.
 *
 * <p><b>Purpose:</b> Provides high-performance bulk database write operations for workflows.
 * Executes the same SQL statement multiple times with different parameter sets using JDBC batch
 * processing, returning the affected row count for each statement.
 *
 * <p><b>Batch Processing Semantics:</b>
 *
 * <ul>
 *   <li><b>SQL Template:</b> Single SQL statement with '?' placeholders reused for all parameter
 *       sets
 *   <li><b>Parameter Sets:</b> List of parameter lists, each representing one execution
 *   <li><b>Execution:</b> Uses {@link PreparedStatement#addBatch()} and {@link
 *       PreparedStatement#executeBatch()}
 *   <li><b>Return Value:</b> int[] array with affected row count for each statement
 *   <li><b>Performance:</b> Significantly faster than individual statements (reduces network
 *       round-trips)
 *   <li><b>Transaction:</b> All statements execute within same transaction (database dependent)
 *   <li><b>Atomicity:</b> Rollback behavior depends on database and transaction configuration
 *   <li><b>Empty Batch:</b> Returns empty int[] array if no parameters provided
 * </ul>
 *
 * <p><b>Input Modes:</b>
 *
 * <p>This task supports two modes for providing inputs:
 *
 * <ol>
 *   <li><b>Direct Input Mode:</b> SQL and batch parameters provided directly via builder methods
 *       <ul>
 *         <li>Use {@link Builder#sql(String)} to provide SQL directly
 *         <li>Use {@link Builder#batchParams(List)} to provide batch parameters directly
 *         <li>Inputs are embedded in the task instance
 *       </ul>
 *   <li><b>Context Mode:</b> SQL and batch parameters read from WorkflowContext at execution time
 *       <ul>
 *         <li>Use {@link Builder#readingSqlFrom(String)} to specify context key for SQL
 *         <li>Use {@link Builder#readingBatchParamsFrom(String)} to specify context key for batch
 *             parameters
 *         <li>Allows dynamic SQL and parameters based on workflow state
 *       </ul>
 * </ol>
 *
 * <p><b>Context Keys and Expected Types:</b>
 *
 * <ul>
 *   <li><b>sqlKey</b> &mdash; {@link String}: SQL template with '?' placeholders (required if not
 *       using direct SQL)
 *   <li><b>batchParamsKey</b> &mdash; {@link List}&lt;{@link List}&lt;{@link Object}&gt;&gt;: batch
 *       parameter sets (optional, defaults to empty list)
 *       <ul>
 *         <li>Outer List: Collection of parameter sets
 *         <li>Inner List: Parameters for one statement execution
 *       </ul>
 *   <li><b>outputKey</b> &mdash; {@code int[]}: array of affected row counts (written by task)
 * </ul>
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li><b>SQL Injection Prevention:</b> Always use parameterized statements with '?' placeholders
 *   <li><b>Data Validation:</b> Validate all parameter sets before execution
 *   <li><b>Batch Size Limits:</b> Some databases have maximum batch size limits
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Connection failures → {@link TaskExecutionException}
 *   <li>SQL syntax errors → {@link TaskExecutionException}
 *   <li>Constraint violations → {@link TaskExecutionException} (may rollback entire batch)
 *   <li>Parameter binding errors → {@link TaskExecutionException}
 *   <li>Partial failures depend on database driver and transaction settings
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. Multiple threads can safely execute batch
 * updates concurrently provided the {@link DataSource} is thread-safe (typical for connection
 * pools).
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li><b>Optimal Batch Size:</b> Typically 1000-5000 rows per batch (database dependent)
 *   <li><b>Memory Usage:</b> All parameter sets held in memory during execution
 *   <li><b>Network Efficiency:</b> 10-100x faster than individual statements
 *   <li><b>Transaction Size:</b> Large batches may exceed transaction log capacity
 *   <li><b>Connection Pooling:</b> Essential for production use (HikariCP, Apache DBCP)
 *   <li><b>Database Tuning:</b> Disable constraints/indexes during large bulk loads
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Bulk data imports and ETL operations
 *   <li>Batch insert of records from external sources
 *   <li>Mass updates based on computed values
 *   <li>Data migration between systems
 *   <li>Scheduled batch processing jobs
 *   <li>Log and event batch insertion
 * </ul>
 *
 * <p><b>Example Usage - Direct Input Mode:</b>
 *
 * <pre>{@code
 * // SQL and batch parameters provided directly
 * List<List<Object>> batchData = Arrays.asList(
 *     Arrays.asList("INFO", "Application started", Timestamp.valueOf(LocalDateTime.now())),
 *     Arrays.asList("DEBUG", "Processing request", Timestamp.valueOf(LocalDateTime.now())),
 *     Arrays.asList("ERROR", "Connection failed", Timestamp.valueOf(LocalDateTime.now()))
 * );
 *
 * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .sql("INSERT INTO logs (level, message, timestamp) VALUES (?, ?, ?)")
 *     .batchParams(batchData)
 *     .writingBatchResultsTo("batchResults")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * task.execute(context);
 *
 * int[] results = (int[]) context.get("batchResults");
 * int totalInserted = Arrays.stream(results).sum();
 * System.out.println("Inserted " + totalInserted + " log entries");
 * }</pre>
 *
 * <p><b>Example Usage - Context Mode:</b>
 *
 * <pre>{@code
 * // SQL and batch parameters from context
 * WorkflowContext context = new WorkflowContext();
 * context.put("batchSql", "INSERT INTO logs (level, message, timestamp) VALUES (?, ?, ?)");
 *
 * List<List<Object>> batchData = Arrays.asList(
 *     Arrays.asList("INFO", "Application started", Timestamp.valueOf(LocalDateTime.now())),
 *     Arrays.asList("DEBUG", "Processing request", Timestamp.valueOf(LocalDateTime.now()))
 * );
 * context.put("batchData", batchData);
 *
 * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .readingSqlFrom("batchSql")
 *     .readingBatchParamsFrom("batchData")
 *     .writingBatchResultsTo("batchResults")
 *     .build();
 *
 * task.execute(context);
 *
 * int[] results = (int[]) context.get("batchResults");
 * }</pre>
 *
 * <p><b>Example Usage - Mixed Mode (Direct SQL, Context Parameters):</b>
 *
 * <pre>{@code
 * // SQL is fixed, but batch parameters come from context
 * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .sql("UPDATE products SET price = ?, updated_at = ? WHERE id = ?")
 *     .readingBatchParamsFrom("priceUpdates")
 *     .writingBatchResultsTo("updateResults")
 *     .build();
 *
 * context.put("priceUpdates", generatePriceUpdates());
 * task.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Bulk Update Prices:</b>
 *
 * <pre>{@code
 * List<Product> products = getProductsToUpdate();
 *
 * List<List<Object>> updates = products.stream()
 *     .map(p -> Arrays.asList(p.getNewPrice(), p.getUpdatedAt(), p.getId()))
 *     .collect(Collectors.toList());
 *
 * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .sql("UPDATE products SET price = ?, updated_at = ? WHERE id = ?")
 *     .batchParams(updates)
 *     .writingBatchResultsTo("updateResults")
 *     .build();
 *
 * task.execute(context);
 * int[] counts = (int[]) context.get("updateResults");
 * }</pre>
 *
 * <p><b>Example Usage - ETL Pipeline with Batch Load:</b>
 *
 * <pre>{@code
 * Workflow etlPipeline = SequentialWorkflow.builder()
 *     .name("DataMigration")
 *     // Extract
 *     .task(JdbcQueryTask.builder()
 *         .dataSource(sourceDs)
 *         .sql("SELECT * FROM legacy_users")
 *         .params(Collections.emptyList())
 *         .writingResultsTo("sourceData")
 *         .build())
 *     // Transform
 *     .task(context -> {
 *         List<Map<String, Object>> sourceData = context.get("sourceData");
 *         List<List<Object>> transformed = sourceData.stream()
 *             .map(row -> Arrays.asList(
 *                 row.get("name"),
 *                 row.get("email"),
 *                 "ACTIVE",
 *                 Timestamp.valueOf(LocalDateTime.now())
 *             ))
 *             .collect(Collectors.toList());
 *         context.put("transformedData", transformed);
 *     })
 *     // Load
 *     .task(JdbcBatchUpdateTask.builder()
 *         .dataSource(targetDs)
 *         .sql("INSERT INTO users (name, email, status, created_at) VALUES (?, ?, ?, ?)")
 *         .readingBatchParamsFrom("transformedData")
 *         .writingBatchResultsTo("loadResults")
 *         .build())
 *     .task(context -> {
 *         int[] results = context.get("loadResults");
 *         System.out.println("Migrated " + Arrays.stream(results).sum() + " records");
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - Handling Empty Batch:</b>
 *
 * <pre>{@code
 * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
 *     .dataSource(dataSource)
 *     .sql("INSERT INTO items (name) VALUES (?)")
 *     .batchParams(Collections.emptyList()) // Empty batch
 *     .writingBatchResultsTo("results")
 *     .build();
 *
 * task.execute(context);
 * int[] results = (int[]) context.get("results"); // Returns empty array: int[0]
 * }</pre>
 *
 * @see JdbcQueryTask
 * @see JdbcUpdateTask
 * @see javax.sql.DataSource
 * @see java.sql.PreparedStatement#executeBatch()
 */
@Slf4j
public class JdbcBatchUpdateTask extends AbstractTask {

  private final DataSource dataSource;
  private final String sql;
  private final List<List<Object>> batchParams;
  private final String sqlKey;
  private final String batchParamsKey;
  private final String outputKey;

  /**
   * Private constructor used by the builder.
   *
   * @param builder the builder instance containing configuration
   */
  private JdbcBatchUpdateTask(Builder builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.sql = builder.sql;
    this.batchParams =
        builder.batchParams != null ? Collections.unmodifiableList(builder.batchParams) : null;
    this.sqlKey = builder.sqlKey;
    this.batchParamsKey = builder.batchParamsKey;
    this.outputKey = Objects.requireNonNull(builder.outputKey, "outputKey must not be null");
  }

  /**
   * Executes the configured SQL batch update using values from the provided {@link WorkflowContext}
   * or direct inputs.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>If direct SQL was provided via {@link Builder#sql(String)}, uses it; otherwise retrieves
   *       SQL from context using {@code sqlKey}
   *   <li>If direct batch params were provided via {@link Builder#batchParams(List)}, uses them;
   *       otherwise retrieves from context using {@code batchParamsKey} (defaults to empty list if
   *       absent)
   *   <li>If batch parameters are empty, writes an empty {@code int[]} array to {@code outputKey}
   *       and returns
   *   <li>For each parameter set, binds parameters using {@link PreparedStatement#setObject(int,
   *       Object)} and adds to batch via {@link PreparedStatement#addBatch()}
   *   <li>Executes {@link PreparedStatement#executeBatch()} and stores the resulting {@code int[]}
   *       under {@code outputKey} in the context
   * </ul>
   *
   * @param context the workflow context from which to read inputs and into which to write outputs
   * @throws TaskExecutionException if any error occurs while obtaining a connection, preparing or
   *     executing the batch, or processing the results
   */
  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    // Determine SQL source (direct or from context)
    String effectiveSql = getSql(context);

    // Determine batch parameters source (direct or from context)
    List<List<Object>> effectiveBatchParams = getBatchQueryParams(context);

    if (effectiveBatchParams.isEmpty()) {
      context.put(outputKey, new int[0]);
      return;
    }

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
        for (List<Object> rowParams : effectiveBatchParams) {
          for (int i = 0; i < rowParams.size(); i++) {
            stmt.setObject(i + 1, rowParams.get(i));
          }
          stmt.addBatch();
        }

        int[] updateCounts = stmt.executeBatch();
        context.put(outputKey, updateCounts);
      }

      if (!isShared) {
        conn.commit();
      }

    } catch (Exception e) {
      if (!isShared) {
        rollback(conn);
      }
      throw new TaskExecutionException("Batch update failed", e);
    } finally {
      if (!isShared) {
        close(conn);
      }
    }
  }

  /**
   * Retrieves batch parameters from either direct configuration or workflow context.
   *
   * @param context the workflow context to read batch parameters from if not configured directly
   * @return the list of parameter sets for batch execution (never null, may be empty)
   */
  @SuppressWarnings("unchecked")
  private List<List<Object>> getBatchQueryParams(WorkflowContext context) {
    List<List<Object>> effectiveBatchParams;
    if (batchParams != null) {
      effectiveBatchParams = batchParams;
    } else if (batchParamsKey != null) {
      effectiveBatchParams =
          getOrDefault(context, batchParamsKey, List.class, Collections.emptyList());
    } else {
      effectiveBatchParams = Collections.emptyList();
    }
    return effectiveBatchParams;
  }

  /**
   * Retrieves the SQL statement from either direct configuration or workflow context.
   *
   * @param context the workflow context to read SQL from if not configured directly
   * @return the SQL statement
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
   * Create a new {@link Builder} to fluently configure and construct a {@link JdbcBatchUpdateTask}.
   *
   * <p>The builder supports both direct input mode and context mode:
   *
   * <ul>
   *   <li><b>Direct Mode:</b> Use {@link Builder#sql(String)} and {@link Builder#batchParams(List)}
   *       to provide inputs directly
   *   <li><b>Context Mode:</b> Use {@link Builder#readingSqlFrom(String)} and {@link
   *       Builder#readingBatchParamsFrom(String)} to read from context
   *   <li><b>Mixed Mode:</b> Combine direct and context inputs (e.g., fixed SQL with dynamic batch
   *       parameters)
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Direct mode
   * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
   *     .dataSource(dataSource)
   *     .sql("INSERT INTO logs (level, message) VALUES (?, ?)")
   *     .batchParams(List.of(
   *         List.of("INFO", "Started"),
   *         List.of("DEBUG", "Processing")
   *     ))
   *     .writingBatchResultsTo("batchResults")
   *     .build();
   *
   * // Context mode
   * JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
   *     .dataSource(dataSource)
   *     .readingSqlFrom("batchSql")
   *     .readingBatchParamsFrom("batchData")
   *     .writingBatchResultsTo("batchResults")
   *     .build();
   * }</pre>
   *
   * @return a fresh {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A fluent builder for creating {@link JdbcBatchUpdateTask} instances.
   *
   * <p>The builder supports multiple configuration modes:
   *
   * <ul>
   *   <li><b>Direct Input:</b> Provide SQL and batch parameters directly via {@link #sql(String)}
   *       and {@link #batchParams(List)}
   *   <li><b>Context Input:</b> Read SQL and batch parameters from context via {@link
   *       #readingSqlFrom(String)} and {@link #readingBatchParamsFrom(String)}
   *   <li><b>Mixed Input:</b> Combine direct and context inputs as needed
   * </ul>
   */
  public static class Builder {
    private DataSource dataSource;
    private String sql;
    private List<List<Object>> batchParams;
    private String sqlKey;
    private String batchParamsKey;
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
     * Provide the SQL template directly (direct input mode).
     *
     * <p>When using this method, the SQL is embedded in the task instance and does not need to be
     * provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingSqlFrom(String)}. If both are set, the
     * direct SQL takes precedence.
     *
     * @param sql the SQL template with '?' placeholders for parameters
     * @return this builder instance
     */
    public Builder sql(String sql) {
      this.sql = sql;
      return this;
    }

    /**
     * Provide batch parameters directly (direct input mode).
     *
     * <p>When using this method, the batch parameters are embedded in the task instance and do not
     * need to be provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingBatchParamsFrom(String)}. If both are set,
     * the direct batch params take precedence.
     *
     * @param batchParams the list of parameter sets, where each inner list represents parameters
     *     for one batch execution
     * @return this builder instance
     */
    public Builder batchParams(List<List<Object>> batchParams) {
      this.batchParams = batchParams != null ? new ArrayList<>(batchParams) : null;
      return this;
    }

    /**
     * Configure the context key from which the SQL template string will be read (context mode).
     *
     * <p>At execution time, the task will read the SQL from the context using this key.
     *
     * <p>This is mutually exclusive with {@link #sql(String)}. If both are set, the direct SQL
     * takes precedence.
     *
     * @param key the context key containing the SQL {@link String} template with '?' placeholders;
     *     must not be {@code null} when {@link #build()} is called if not using direct SQL
     * @return this builder instance
     */
    public Builder readingSqlFrom(String key) {
      this.sqlKey = key;
      return this;
    }

    /**
     * Configure the context key from which the batch parameters will be read (context mode).
     *
     * <p>At execution time, the task will read the batch parameters from the context using this
     * key.
     *
     * <p>This is mutually exclusive with {@link #batchParams(List)}. If both are set, the direct
     * batch params take precedence.
     *
     * @param key the context key containing the batch parameters ({@link List}&lt;{@link
     *     List}&lt;{@link Object}&gt;&gt;); must not be {@code null} when {@link #build()} is
     *     called if not using direct batch params
     * @return this builder instance
     */
    public Builder readingBatchParamsFrom(String key) {
      this.batchParamsKey = key;
      return this;
    }

    /**
     * Configure the context key where the batch results will be written.
     *
     * @param key the context key to write the {@code int[]} array of update counts to; must not be
     *     {@code null} when {@link #build()} is called
     * @return this builder instance
     */
    public Builder writingBatchResultsTo(String key) {
      this.outputKey = key;
      return this;
    }

    /**
     * Build a new {@link JdbcBatchUpdateTask} with the configured values.
     *
     * <p>Validates that required configuration is present:
     *
     * <ul>
     *   <li>dataSource must be set
     *   <li>Either SQL or sqlKey must be set
     *   <li>Either batchParams or batchParamsKey should be set (batchParamsKey is optional)
     *   <li>outputKey must be set
     * </ul>
     *
     * @return a new {@link JdbcBatchUpdateTask} instance
     * @throws IllegalStateException if dataSource or outputKey is not set, or if neither SQL nor
     *     sqlKey is set
     */
    public JdbcBatchUpdateTask build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if (sql == null && sqlKey == null) {
        throw new IllegalStateException("Either sql or sqlKey must be set");
      }
      if (outputKey == null) {
        throw new IllegalStateException("outputKey must be set");
      }
      return new JdbcBatchUpdateTask(this);
    }
  }
}
