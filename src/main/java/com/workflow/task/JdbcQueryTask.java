package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/**
 * Executes SQL SELECT queries using JDBC and returns results as a list of maps.
 *
 * <p><b>Purpose:</b> Provides database read operations for workflows. Executes parameterized SQL
 * SELECT queries and converts the ResultSet into a list of maps (column name → value) for easy
 * processing in workflows.
 *
 * <p><b>Query Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>SQL Support:</b> SELECT queries (any statement supported by {@link
 *       PreparedStatement#executeQuery()})
 *   <li><b>Parameterization:</b> Uses positional parameters (?) to prevent SQL injection
 *   <li><b>Parameter Binding:</b> Parameters bound in order using {@link
 *       PreparedStatement#setObject(int, Object)}
 *   <li><b>Result Format:</b> List of LinkedHashMap (preserves column order)
 *   <li><b>Column Names:</b> Uses column labels from ResultSetMetaData
 *   <li><b>Type Conversion:</b> Automatic conversion of JDBC types (Clob, Array) to Java types
 *   <li><b>Connection Pooling:</b> Connections obtained from DataSource and properly closed
 *   <li><b>Resource Cleanup:</b> Automatic cleanup via try-with-resources
 * </ul>
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
 * <p><b>Context Keys and Expected Types:</b>
 *
 * <ul>
 *   <li><b>sqlKey</b> &mdash; {@link String}: the SQL SELECT statement with '?' placeholders
 *       (required if not using direct SQL)
 *   <li><b>paramsKey</b> &mdash; {@link List}&lt;{@link Object}&gt;: positional parameters to bind
 *       to the {@link PreparedStatement} (optional, defaults to empty list)
 *   <li><b>outputKey</b> &mdash; {@link List}&lt;{@link Map}&lt;{@link String},{@link
 *       Object}&gt;&gt;: the task will write the query results here after execution
 *       <ul>
 *         <li>Each Map represents one row with column labels as keys
 *         <li>Maps are LinkedHashMap instances preserving column order
 *       </ul>
 * </ul>
 *
 * <p><b>Result Format:</b>
 *
 * <pre>{@code
 * List<Map<String,Object>> results = (List<Map<String,Object>>) context.get(outputKey);
 * // Each map is a LinkedHashMap preserving column order:
 * for (Map<String, Object> row : results) {
 *     Integer id = (Integer) row.get("id");           // Column: id
 *     String name = (String) row.get("name");         // Column: name
 *     Timestamp created = (Timestamp) row.get("created_at"); // Column: created_at
 * }
 * }</pre>
 *
 * <p><b>Type Conversions:</b>
 *
 * <ul>
 *   <li><b>Clob:</b> Converted to String via {@code clob.getSubString(1, clob.length())}
 *   <li><b>Array:</b> Converted to List via recursive conversion
 *   <li><b>Other Types:</b> Returned as-is from {@code ResultSet.getObject()}
 *   <li><b>NULL Values:</b> Represented as null in the map
 * </ul>
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li><b>SQL Injection Prevention:</b> Always use parameterized queries with '?' placeholders
 *   <li><b>Input Validation:</b> Validate and sanitize parameters before execution
 *   <li><b>Permission Control:</b> Ensure DataSource has appropriate database permissions
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Any exception during connection acquisition, statement preparation, parameter binding,
 *       execution, or result processing is wrapped in a {@link TaskExecutionException} and thrown
 *   <li>Connection failures → {@link TaskExecutionException}
 *   <li>SQL syntax errors → {@link TaskExecutionException}
 *   <li>Type conversion errors → {@link TaskExecutionException}
 *   <li>All exceptions include original cause for debugging
 * </ul>
 *
 * <p><b>Resource Management and Thread Safety:</b>
 *
 * <ul>
 *   <li>Connections, statements and result sets are closed using try-with-resources to avoid leaks
 *   <li>The task is safe to reuse across threads provided the supplied {@link DataSource} is
 *       thread-safe (typical for connection pools). The task itself holds only immutable
 *       configuration
 * </ul>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Entire ResultSet loaded into memory as List of Maps
 *   <li>Use connection pooling (e.g., HikariCP, Apache DBCP) for production
 *   <li>For large result sets, consider pagination with LIMIT/OFFSET or streaming via {@link
 *       JdbcStreamingQueryTask}
 *   <li>Index columns used in WHERE clauses for better query performance
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Fetch records from database tables
 *   <li>Execute complex SELECT queries with JOINs
 *   <li>Data extraction for ETL pipelines
 *   <li>Report generation from database
 *   <li>Lookup and validation operations
 *   <li>Data aggregation and analysis queries
 * </ul>
 *
 * <p><b>Example Usage - Direct Input Mode:</b>
 *
 * <pre>{@code
 * // SQL and parameters provided directly
 * JdbcQueryTask task = JdbcQueryTask.builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT id, name, email FROM users WHERE status = ? AND created_at > ?")
 *     .params(Arrays.asList("ACTIVE", LocalDate.of(2024, 1, 1)))
 *     .writingResultsTo("queryResults")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * task.execute(context);
 *
 * List<Map<String,Object>> rows = (List<Map<String,Object>>) context.get("queryResults");
 * for (Map<String,Object> row : rows) {
 *     Integer id = (Integer) row.get("id");
 *     String name = (String) row.get("name");
 *     String email = (String) row.get("email");
 *     System.out.println("User: " + name + " (" + email + ")");
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Context Mode:</b>
 *
 * <pre>{@code
 * // SQL and parameters from context (allows dynamic queries)
 * JdbcQueryTask task = JdbcQueryTask.builder()
 *     .dataSource(dataSource)
 *     .readingSqlFrom("userQuery")
 *     .readingParamsFrom("queryParams")
 *     .writingResultsTo("users")
 *     .build();
 *
 * context.put("userQuery", "SELECT * FROM users WHERE country = ?");
 * context.put("queryParams", Arrays.asList("USA"));
 *
 * task.execute(context);
 * List<Map<String, Object>> users = context.get("users");
 * }</pre>
 *
 * <p><b>Example Usage - Mixed Mode (Direct SQL, Context Parameters):</b>
 *
 * <pre>{@code
 * // SQL is fixed, but parameters come from context
 * JdbcQueryTask task = JdbcQueryTask.builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT * FROM orders WHERE user_id = ? AND status = ?")
 *     .readingParamsFrom("orderQueryParams")
 *     .writingResultsTo("userOrders")
 *     .build();
 *
 * context.put("orderQueryParams", Arrays.asList(userId, "COMPLETED"));
 * task.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - In Workflow with Processing:</b>
 *
 * <pre>{@code
 * Workflow reportWorkflow = SequentialWorkflow.builder()
 *     .name("UserReport")
 *     .task(context -> {
 *         // Prepare query parameters dynamically
 *         context.put("reportParams", List.of(LocalDate.now().minusDays(30)));
 *     })
 *     .task(JdbcQueryTask.builder()
 *         .dataSource(dataSource)
 *         .sql("SELECT * FROM user_stats WHERE date >= ?")
 *         .readingParamsFrom("reportParams")
 *         .writingResultsTo("reportData")
 *         .build())
 *     .task(context -> {
 *         // Process results
 *         List<Map<String, Object>> data = context.get("reportData");
 *         String report = generateReport(data);
 *         context.put("report", report);
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - Complex Query with JOINs:</b>
 *
 * <pre>{@code
 * JdbcQueryTask task = JdbcQueryTask.builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT u.id, u.name, o.order_id, o.total " +
 *          "FROM users u " +
 *          "LEFT JOIN orders o ON u.id = o.user_id " +
 *          "WHERE u.country = ? AND o.status = ?")
 *     .params(Arrays.asList("USA", "COMPLETED"))
 *     .writingResultsTo("userOrders")
 *     .build();
 *
 * task.execute(context);
 * List<Map<String, Object>> userOrders = context.get("userOrders");
 * }</pre>
 *
 * @see JdbcUpdateTask
 * @see JdbcBatchUpdateTask
 * @see JdbcStreamingQueryTask
 * @see javax.sql.DataSource
 * @see java.sql.PreparedStatement#executeQuery()
 */
public class JdbcQueryTask extends AbstractTask {

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
  private JdbcQueryTask(Builder builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.sql = builder.sql;
    this.params = builder.params != null ? Collections.unmodifiableList(builder.params) : null;
    this.sqlKey = builder.sqlKey;
    this.paramsKey = builder.paramsKey;
    this.outputKey = Objects.requireNonNull(builder.outputKey, "outputKey must not be null");
  }

  /**
   * Executes the configured SQL query using values from the provided {@link WorkflowContext} or
   * direct inputs.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>If direct SQL was provided via {@link Builder#sql(String)}, uses it; otherwise retrieves
   *       SQL from context using {@code sqlKey}
   *   <li>If direct params were provided via {@link Builder#params(List)}, uses them; otherwise
   *       retrieves params from context using {@code paramsKey} (defaults to empty list if absent)
   *   <li>Binds parameters in order using {@link PreparedStatement#setObject(int, Object)}
   *   <li>Converts the {@link ResultSet} into a {@link List} of {@link LinkedHashMap
   *       LinkedHashMaps} to preserve column order and stores it under {@code outputKey} in the
   *       context
   * </ul>
   *
   * @param context the workflow context from which to read inputs and into which to write outputs
   * @throws TaskExecutionException if any error occurs while obtaining a connection, preparing or
   *     executing the statement, or processing the results
   */
  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    // Determine SQL source (direct or from context)
    String effectiveSql = getSql(context);

    // Determine parameters source (direct or from context)
    List<Object> effectiveParams = getQueryParams(context);

    List<Map<String, Object>> results = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(effectiveSql)) {

      for (int i = 0; i < effectiveParams.size(); i++) {
        stmt.setObject(i + 1, effectiveParams.get(i));
      }

      try (ResultSet rs = stmt.executeQuery()) {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= columnCount; i++) {
            Object value = rs.getObject(i);
            String columnLabel = metaData.getColumnLabel(i);
            row.put(columnLabel, convertJdbcValue(value));
          }
          results.add(row);
        }
      }

      // Save to the specific output key
      context.put(outputKey, results);

    } catch (Exception e) {
      throw new TaskExecutionException("Query failed: " + e.getMessage(), e);
    }
  }

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
   * Converts JDBC-specific objects into standard Java types so they remain accessible after the
   * Connection is closed.
   *
   * <p>This method handles special JDBC types that may become invalid after the connection is
   * closed:
   *
   * <ul>
   *   <li>Clob → String
   *   <li>Array → List (with recursive conversion for nested arrays)
   *   <li>Other types → returned as-is
   * </ul>
   *
   * @param value the JDBC value to convert
   * @return the converted Java value, or null if input is null
   * @throws Exception if conversion fails
   */
  private Object convertJdbcValue(Object value) throws Exception {
    if (value == null) {
      return null;
    }
    switch (value) {
      case Array sqlArray -> {
        Object[] array = (Object[]) sqlArray.getArray();
        List<Object> list = new ArrayList<>();
        for (Object item : array) {
          list.add(convertJdbcValue(item)); // Recursive for nested arrays
        }
        return list;
      }
      case Clob clob -> {
        return clob.getSubString(1, (int) clob.length());
      }
      default -> {
        // Return other types as-is
        return value;
      }
    }
  }

  /**
   * Create a new {@link Builder} to fluently configure and construct a {@link JdbcQueryTask}.
   *
   * <p>The builder supports both direct input mode and context mode:
   *
   * <ul>
   *   <li><b>Direct Mode:</b> Use {@link Builder#sql(String)} and {@link Builder#params(List)} to
   *       provide inputs directly
   *   <li><b>Context Mode:</b> Use {@link Builder#readingSqlFrom(String)} and {@link
   *       Builder#readingParamsFrom(String)} to read from context
   *   <li><b>Mixed Mode:</b> Combine direct and context inputs (e.g., fixed SQL with dynamic
   *       parameters)
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Direct mode
   * JdbcQueryTask task = JdbcQueryTask.builder()
   *     .dataSource(dataSource)
   *     .sql("SELECT * FROM users WHERE status = ?")
   *     .params(List.of("ACTIVE"))
   *     .writingResultsTo("queryResults")
   *     .build();
   *
   * // Context mode
   * JdbcQueryTask task = JdbcQueryTask.builder()
   *     .dataSource(dataSource)
   *     .readingSqlFrom("sql")
   *     .readingParamsFrom("params")
   *     .writingResultsTo("queryResults")
   *     .build();
   * }</pre>
   *
   * @return a fresh {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A fluent builder for creating {@link JdbcQueryTask} instances.
   *
   * <p>The builder supports multiple configuration modes:
   *
   * <ul>
   *   <li><b>Direct Input:</b> Provide SQL and parameters directly via {@link #sql(String)} and
   *       {@link #params(List)}
   *   <li><b>Context Input:</b> Read SQL and parameters from context via {@link
   *       #readingSqlFrom(String)} and {@link #readingParamsFrom(String)}
   *   <li><b>Mixed Input:</b> Combine direct and context inputs as needed
   * </ul>
   */
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
     * Provide the SQL query directly (direct input mode).
     *
     * <p>When using this method, the SQL is embedded in the task instance and does not need to be
     * provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingSqlFrom(String)}. If both are set, the
     * direct SQL takes precedence.
     *
     * @param sql the SQL SELECT query with '?' placeholders for parameters
     * @return this builder instance
     */
    public Builder sql(String sql) {
      this.sql = sql;
      return this;
    }

    /**
     * Provide query parameters directly (direct input mode).
     *
     * <p>When using this method, the parameters are embedded in the task instance and do not need
     * to be provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingParamsFrom(String)}. If both are set, the
     * direct params take precedence.
     *
     * @param params the list of positional parameters to bind to the SQL query
     * @return this builder instance
     */
    public Builder params(List<Object> params) {
      this.params = params != null ? new ArrayList<>(params) : null;
      return this;
    }

    /**
     * Configure the context key from which the SQL string will be read (context mode).
     *
     * <p>At execution time, the task will read the SQL from the context using this key.
     *
     * <p>This is mutually exclusive with {@link #sql(String)}. If both are set, the direct SQL
     * takes precedence.
     *
     * @param key the context key containing the SQL {@link String}; must not be {@code null} when
     *     {@link #build()} is called if not using direct SQL
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
     * <p>At execution time, the task will read the parameters from the context using this key.
     *
     * <p>This is mutually exclusive with {@link #params(List)}. If both are set, the direct params
     * take precedence.
     *
     * @param key the context key containing the parameters {@link List}; must not be {@code null}
     *     when {@link #build()} is called if not using direct params
     * @return this builder instance
     */
    public Builder readingParamsFrom(String key) {
      this.paramsKey = key;
      return this;
    }

    /**
     * Configure the context key where the query results will be written.
     *
     * @param key the context key to write the {@link List}&lt;{@link Map}&lt;{@link String}, {@link
     *     Object}&gt;&gt; results to; must not be {@code null} when {@link #build()} is called
     * @return this builder instance
     */
    public Builder writingResultsTo(String key) {
      this.outputKey = key;
      return this;
    }

    /**
     * Build a new {@link JdbcQueryTask} with the configured values.
     *
     * <p>Validates that required configuration is present:
     *
     * <ul>
     *   <li>dataSource must be set
     *   <li>Either SQL or sqlKey must be set
     *   <li>Either params or paramsKey should be set (paramsKey is optional)
     *   <li>outputKey must be set
     * </ul>
     *
     * @return a new {@link JdbcQueryTask} instance
     * @throws IllegalStateException if dataSource or outputKey is not set, or if neither SQL nor
     *     sqlKey is set
     */
    public JdbcQueryTask build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if (sql == null && sqlKey == null) {
        throw new IllegalStateException("Either sql or sqlKey must be set");
      }
      if (outputKey == null) {
        throw new IllegalStateException("outputKey must be set");
      }
      return new JdbcQueryTask(this);
    }
  }
}
