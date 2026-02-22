package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * Executes SQL SELECT queries and processes results in a streaming fashion without loading all rows
 * into memory.
 *
 * <p><b>Purpose:</b> Efficiently processes large database result sets by consuming rows one at a
 * time using a callback function, avoiding OutOfMemoryError issues that can occur with {@link
 * JdbcQueryTask}.
 *
 * <p><b>Streaming Semantics:</b>
 *
 * <ul>
 *   <li><b>Row-by-Row Processing:</b> Callback invoked for each row as it's fetched
 *   <li><b>Memory Efficiency:</b> Only one row held in memory at a time
 *   <li><b>Early Termination:</b> Can stop processing by throwing exception from callback
 *   <li><b>Fetch Size:</b> Configurable fetch size for driver optimization
 *   <li><b>Forward-Only Cursor:</b> ResultSet configured for forward-only traversal
 *   <li><b>Read-Only:</b> ResultSet is read-only for better performance
 * </ul>
 *
 * <p><b>Input Modes:</b>
 *
 * <p>This task supports two modes for providing inputs:
 *
 * <ol>
 *   <li><b>Direct Input Mode:</b> SQL, parameters, and callback provided directly via builder
 *       <ul>
 *         <li>Use {@link Builder#sql(String)} to provide SQL directly
 *         <li>Use {@link Builder#params(List)} to provide parameters directly
 *         <li>Use {@link Builder#rowCallback(Consumer)} to provide callback directly
 *         <li>Inputs are embedded in the task instance
 *       </ul>
 *   <li><b>Context Mode:</b> SQL, parameters, and callback read from WorkflowContext
 *       <ul>
 *         <li>Use {@link Builder#readingSqlFrom(String)} to specify context key for SQL
 *         <li>Use {@link Builder#readingParamsFrom(String)} for parameters key
 *         <li>Use {@link Builder#readingRowCallbackFrom(String)} for callback key
 *         <li>Allows dynamic queries and processing based on workflow state
 *       </ul>
 * </ol>
 *
 * <p><b>Context Keys and Expected Types:</b>
 *
 * <ul>
 *   <li><b>sqlKey</b> &mdash; {@link String}: the SQL SELECT statement (required if not using
 *       direct SQL)
 *   <li><b>paramsKey</b> &mdash; {@link List}&lt;{@link Object}&gt;: query parameters (optional,
 *       defaults to empty list)
 *   <li><b>rowCallbackKey</b> &mdash; {@link Consumer}&lt;{@link Map}&lt;{@link String},{@link
 *       Object}&gt;&gt;: callback for each row (required if not using direct callback)
 *   <li><b>rowCountKey</b> &mdash; {@link Long}: total rows processed (written by task)
 * </ul>
 *
 * <p><b>Performance Optimization:</b>
 *
 * <ul>
 *   <li><b>Fetch Size:</b> Set to optimize network round-trips (default: 1000)
 *   <li><b>Forward-Only:</b> ResultSet type for maximum performance
 *   <li><b>Read-Only:</b> Concurrency mode for better efficiency
 *   <li><b>Statement Timeout:</b> Configurable timeout to prevent long-running queries
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Connection failures → {@link TaskExecutionException}
 *   <li>SQL errors → {@link TaskExecutionException}
 *   <li>Callback exceptions → {@link TaskExecutionException} (processing stops)
 *   <li>Timeout → {@link TaskExecutionException}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe provided the {@link DataSource} and callback
 * are thread-safe.
 *
 * <p><b>Example Usage - Direct Input Mode:</b>
 *
 * <pre>{@code
 * // Define callback to process each row
 * Consumer<Map<String, Object>> printCallback = row -> {
 *     Integer id = (Integer) row.get("id");
 *     String name = (String) row.get("name");
 *     System.out.println("User ID: " + id + ", Name: " + name);
 * };
 *
 * JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT id, name, email FROM users WHERE active = ?")
 *     .params(List.of(true))
 *     .rowCallback(printCallback)
 *     .writingRowCountTo("processedCount")
 *     .fetchSize(500)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * task.execute(context);
 *
 * Long totalProcessed = context.get("processedCount");
 * System.out.println("Processed " + totalProcessed + " rows");
 * }</pre>
 *
 * <p><b>Example Usage - Context Mode:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("querySql", "SELECT id, name, email FROM users WHERE active = ?");
 * context.put("queryParams", List.of(true));
 *
 * Consumer<Map<String, Object>> rowCallback = row -> {
 *     // Process row
 *     System.out.println("User: " + row.get("name"));
 * };
 * context.put("rowCallback", rowCallback);
 *
 * JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
 *     .dataSource(dataSource)
 *     .readingSqlFrom("querySql")
 *     .readingParamsFrom("queryParams")
 *     .readingRowCallbackFrom("rowCallback")
 *     .writingRowCountTo("processedCount")
 *     .fetchSize(500)
 *     .build();
 *
 * task.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Export to CSV (Direct Mode):</b>
 *
 * <pre>{@code
 * // Create CSV writer
 * try (CSVWriter writer = new CSVWriter(new FileWriter("orders.csv"))) {
 *     Consumer<Map<String, Object>> csvCallback = row -> {
 *         String[] values = row.values().stream()
 *             .map(String::valueOf)
 *             .toArray(String[]::new);
 *         writer.writeNext(values);
 *     };
 *
 *     JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
 *         .dataSource(dataSource)
 *         .sql("SELECT * FROM orders WHERE date >= ?")
 *         .params(List.of(LocalDate.now().minusDays(30)))
 *         .rowCallback(csvCallback)
 *         .fetchSize(1000)
 *         .build();
 *
 *     task.execute(context);
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Aggregation:</b>
 *
 * <pre>{@code
 * // Aggregate in memory with direct input mode
 * Map<String, Double> categoryTotals = new HashMap<>();
 * Consumer<Map<String, Object>> aggregateCallback = row -> {
 *     String category = (String) row.get("category");
 *     Double amount = (Double) row.get("amount");
 *     categoryTotals.merge(category, amount, Double::sum);
 * };
 *
 * JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT category, amount FROM transactions WHERE year = ?")
 *     .params(List.of(2026))
 *     .rowCallback(aggregateCallback)
 *     .build();
 *
 * task.execute(context);
 * // categoryTotals now contains aggregated data
 * }</pre>
 *
 * <p><b>Example Usage - Mixed Mode (Direct SQL, Context Callback):</b>
 *
 * <pre>{@code
 * // Fixed SQL, dynamic callback from context
 * JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT * FROM logs WHERE date = ?")
 *     .params(List.of(LocalDate.now()))
 *     .readingRowCallbackFrom("logProcessor")
 *     .writingRowCountTo("logsProcessed")
 *     .build();
 *
 * context.put("logProcessor", (Consumer<Map<String, Object>>) row -> {
 *     // Dynamic log processing
 *     processLog(row);
 * });
 *
 * task.execute(context);
 * }</pre>
 *
 * @see JdbcQueryTask
 * @see Consumer
 */
public class JdbcStreamingQueryTask extends AbstractTask {

  private final DataSource dataSource;
  private final String sql;
  private final List<Object> params;
  private final Consumer<Map<String, Object>> rowCallback;
  private final String sqlKey;
  private final String paramsKey;
  private final String rowCallbackKey;
  private final String rowCountKey;
  private final int fetchSize;
  private final int queryTimeout;

  /**
   * Private constructor used by the builder.
   *
   * @param builder the builder instance containing configuration
   */
  private JdbcStreamingQueryTask(Builder builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.sql = builder.sql;
    this.params = builder.params != null ? Collections.unmodifiableList(builder.params) : null;
    this.rowCallback = builder.rowCallback;
    this.sqlKey = builder.sqlKey;
    this.paramsKey = builder.paramsKey;
    this.rowCallbackKey = builder.rowCallbackKey;
    this.rowCountKey = builder.rowCountKey;
    this.fetchSize = builder.fetchSize;
    this.queryTimeout = builder.queryTimeout;
  }

  /**
   * Executes the streaming query using values from the provided {@link WorkflowContext} or direct
   * inputs.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>If direct SQL was provided, uses it; otherwise retrieves from context
   *   <li>If direct params were provided, uses them; otherwise retrieves from context
   *   <li>If direct callback was provided, uses it; otherwise retrieves from context
   *   <li>Configures statement with fetch size and timeout
   *   <li>Processes each row through the callback
   *   <li>Stores total row count in context
   * </ul>
   *
   * @param context the workflow context from which to read inputs and into which to write outputs
   * @throws TaskExecutionException if any error occurs during execution
   */
  @Override
  protected void doExecute(WorkflowContext context) {
    // Determine SQL source
    String effectiveSql = getSqlQuery(context);

    // Determine parameters source
    List<Object> effectiveParams = getQueryParams(context);

    // Determine row callback source
    Consumer<Map<String, Object>> effectiveCallback = getRowCallback(context);

    // 1. Check if a transaction connection exists in the context
    Connection sharedConn = getConnection(context);

    // 2. Use the shared connection if available, otherwise get a new one from DataSource
    boolean isShared = sharedConn != null;
    Connection conn = null;

    long rowCount = 0;
    try {
      conn = isShared ? sharedConn : dataSource.getConnection();
      if (!isShared) {
        conn.setReadOnly(true);
      }

      try (PreparedStatement stmt =
          conn.prepareStatement(
              effectiveSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
        // Configure statement
        stmt.setFetchSize(fetchSize);
        if (queryTimeout > 0) {
          stmt.setQueryTimeout(queryTimeout);
        }

        // Bind parameters
        for (int i = 0; i < effectiveParams.size(); i++) {
          stmt.setObject(i + 1, effectiveParams.get(i));
        }

        // Execute and stream results
        try (ResultSet rs = stmt.executeQuery()) {
          ResultSetMetaData metaData = rs.getMetaData();
          int columnCount = metaData.getColumnCount();

          while (rs.next()) {
            // Convert row to map
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
              String columnName = metaData.getColumnLabel(i);
              Object value = rs.getObject(i);
              row.put(columnName, value);
            }

            // Invoke callback
            callRowCallback(effectiveCallback, row, rowCount);
            rowCount++;
          }
        }
      }

      // Store row count
      if (rowCountKey != null) {
        context.put(rowCountKey, rowCount);
      }

    } catch (SQLException e) {
      throw new TaskExecutionException(
          "Streaming query failed after processing " + rowCount + " rows: " + e.getMessage(), e);
    } finally {
      if (!isShared) {
        close(conn);
      }
    }
  }

  /**
   * Retrieves the row callback from either direct configuration or workflow context.
   *
   * @param context the workflow context to read callback from if not configured directly
   * @return the row callback function
   * @throws TaskExecutionException if callback is not found in context when using context mode
   */
  @SuppressWarnings("unchecked")
  private Consumer<Map<String, Object>> getRowCallback(WorkflowContext context) {
    Consumer<Map<String, Object>> effectiveCallback;
    if (rowCallback != null) {
      effectiveCallback = rowCallback;
    } else {
      effectiveCallback = (Consumer<Map<String, Object>>) context.get(rowCallbackKey);
      if (effectiveCallback == null) {
        throw new TaskExecutionException(
            "Row callback not found in context at key: " + rowCallbackKey);
      }
    }
    return effectiveCallback;
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
      effectiveParams = (List<Object>) context.get(paramsKey, Collections.emptyList());
    } else {
      effectiveParams = Collections.emptyList();
    }
    return effectiveParams;
  }

  /**
   * Retrieves the SQL query string from either direct configuration or workflow context.
   *
   * @param context the workflow context to read SQL from if not configured directly
   * @return the SQL query string
   * @throws TaskExecutionException if SQL is not found in context when using context mode
   */
  private String getSqlQuery(WorkflowContext context) {
    String effectiveSql;
    if (sql != null) {
      effectiveSql = sql;
    } else {
      effectiveSql = context.getTyped(sqlKey, String.class);
      if (effectiveSql == null) {
        throw new TaskExecutionException("SQL query not found in context at key: " + sqlKey);
      }
    }
    return effectiveSql;
  }

  /**
   * Invokes the row callback with the current row data.
   *
   * @param rowCallback the callback function to invoke
   * @param row the row data as a map
   * @param rowCount the current row count (0-based)
   * @throws TaskExecutionException if callback execution fails
   */
  private static void callRowCallback(
      Consumer<Map<String, Object>> rowCallback, Map<String, Object> row, long rowCount) {
    try {
      rowCallback.accept(row);
    } catch (Exception e) {
      throw new TaskExecutionException(
          "Row callback failed at row " + rowCount + ": " + e.getMessage(), e);
    }
  }

  /**
   * Create a new {@link Builder} to fluently configure and construct a {@link
   * JdbcStreamingQueryTask}.
   *
   * <p>The builder supports both direct input mode and context mode:
   *
   * <ul>
   *   <li><b>Direct Mode:</b> Use {@link Builder#sql(String)}, {@link Builder#params(List)}, and
   *       {@link Builder#rowCallback(Consumer)} to provide inputs directly
   *   <li><b>Context Mode:</b> Use {@link Builder#readingSqlFrom(String)}, {@link
   *       Builder#readingParamsFrom(String)}, and {@link Builder#readingRowCallbackFrom(String)} to
   *       read from context
   *   <li><b>Mixed Mode:</b> Combine direct and context inputs as needed
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Direct mode
   * JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
   *     .dataSource(dataSource)
   *     .sql("SELECT * FROM users")
   *     .params(List.of())
   *     .rowCallback(row -> System.out.println(row))
   *     .build();
   *
   * // Context mode
   * JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
   *     .dataSource(dataSource)
   *     .readingSqlFrom("sql")
   *     .readingParamsFrom("params")
   *     .readingRowCallbackFrom("callback")
   *     .build();
   * }</pre>
   *
   * @return a fresh {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A fluent builder for creating {@link JdbcStreamingQueryTask} instances.
   *
   * <p>The builder supports multiple configuration modes:
   *
   * <ul>
   *   <li><b>Direct Input:</b> Provide SQL, parameters, and callback directly
   *   <li><b>Context Input:</b> Read SQL, parameters, and callback from context
   *   <li><b>Mixed Input:</b> Combine direct and context inputs as needed
   * </ul>
   */
  public static class Builder {
    private DataSource dataSource;
    private String sql;
    private List<Object> params;
    private Consumer<Map<String, Object>> rowCallback;
    private String sqlKey = "sql";
    private String paramsKey = "params";
    private String rowCallbackKey = "rowCallback";
    private String rowCountKey = "rowCount";
    private int fetchSize = 1000;
    private int queryTimeout = 0;

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
     * Provide the SQL query directly (direct input mode).
     *
     * <p>This is mutually exclusive with {@link #readingSqlFrom(String)}. If both are set, the
     * direct SQL takes precedence.
     *
     * @param sql the SQL SELECT query
     * @return this builder instance
     */
    public Builder sql(String sql) {
      this.sql = sql;
      return this;
    }

    /**
     * Provide query parameters directly (direct input mode).
     *
     * <p>This is mutually exclusive with {@link #readingParamsFrom(String)}. If both are set, the
     * direct params take precedence.
     *
     * @param params the list of positional parameters
     * @return this builder instance
     */
    public Builder params(List<Object> params) {
      this.params = params != null ? new ArrayList<>(params) : null;
      return this;
    }

    /**
     * Provide row callback directly (direct input mode).
     *
     * <p>This is mutually exclusive with {@link #readingRowCallbackFrom(String)}. If both are set,
     * the direct callback takes precedence.
     *
     * @param rowCallback the consumer to process each row
     * @return this builder instance
     */
    public Builder rowCallback(Consumer<Map<String, Object>> rowCallback) {
      this.rowCallback = rowCallback;
      return this;
    }

    /**
     * Configure the context key from which the SQL query will be read (context mode).
     *
     * <p>This is mutually exclusive with {@link #sql(String)}. If both are set, the direct SQL
     * takes precedence.
     *
     * @param sqlKey the context key containing the SQL {@link String}; default is "sql"
     * @return this builder instance
     */
    public Builder readingSqlFrom(String sqlKey) {
      this.sqlKey = sqlKey;
      return this;
    }

    /**
     * Configure the context key from which parameters will be read (context mode).
     *
     * <p>This is mutually exclusive with {@link #params(List)}. If both are set, the direct params
     * take precedence.
     *
     * @param paramsKey the context key containing parameters; default is "params"
     * @return this builder instance
     */
    public Builder readingParamsFrom(String paramsKey) {
      this.paramsKey = paramsKey;
      return this;
    }

    /**
     * Configure the context key from which the row callback will be read (context mode).
     *
     * <p>This is mutually exclusive with {@link #rowCallback(Consumer)}. If both are set, the
     * direct callback takes precedence.
     *
     * @param rowCallbackKey the context key containing the callback; default is "rowCallback"
     * @return this builder instance
     */
    public Builder readingRowCallbackFrom(String rowCallbackKey) {
      this.rowCallbackKey = rowCallbackKey;
      return this;
    }

    /**
     * Configure the context key where the row count will be written.
     *
     * @param rowCountKey the context key for row count; default is "rowCount"
     * @return this builder instance
     */
    public Builder writingRowCountTo(String rowCountKey) {
      this.rowCountKey = rowCountKey;
      return this;
    }

    /**
     * Set the fetch size hint for the driver.
     *
     * @param fetchSize the fetch size (default: 1000)
     * @return this builder instance
     */
    public Builder fetchSize(int fetchSize) {
      this.fetchSize = fetchSize;
      return this;
    }

    /**
     * Set the query timeout in seconds.
     *
     * @param queryTimeout the timeout in seconds; 0 means no timeout (default: 0)
     * @return this builder instance
     */
    public Builder queryTimeout(int queryTimeout) {
      this.queryTimeout = queryTimeout;
      return this;
    }

    /**
     * Build a new {@link JdbcStreamingQueryTask} with the configured values.
     *
     * <p>Validates that required configuration is present:
     *
     * <ul>
     *   <li>dataSource must be set
     *   <li>Either SQL or sqlKey must be set
     *   <li>Either rowCallback or rowCallbackKey must be set
     * </ul>
     *
     * @return a new {@link JdbcStreamingQueryTask} instance
     * @throws IllegalStateException if required fields are not set
     */
    public JdbcStreamingQueryTask build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if (sql == null && sqlKey == null) {
        throw new IllegalStateException("Either sql or sqlKey must be set");
      }
      if (rowCallback == null && rowCallbackKey == null) {
        throw new IllegalStateException("Either rowCallback or rowCallbackKey must be set");
      }
      return new JdbcStreamingQueryTask(this);
    }
  }
}
