package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/**
 * Executes SQL SELECT queries with type-safe result mapping using a custom row mapper.
 *
 * <p><b>Purpose:</b> Provides type-safe database query results by converting rows into domain
 * objects using a {@link RowMapper}, avoiding the need to work with generic {@code Map<String,
 * Object>} structures.
 *
 * <p><b>Type Safety Benefits:</b>
 *
 * <ul>
 *   <li><b>Domain Objects:</b> Maps rows directly to POJOs, DTOs, or records
 *   <li><b>Type Safety:</b> Compile-time type checking for result objects
 *   <li><b>Code Clarity:</b> More readable than extracting from maps
 *   <li><b>Reusability:</b> RowMapper can be reused across queries
 *   <li><b>IDE Support:</b> Better autocomplete and refactoring support
 * </ul>
 *
 * <p><b>Input Modes:</b>
 *
 * <p>This task supports two modes for providing inputs:
 *
 * <ol>
 *   <li><b>Direct Input Mode:</b> SQL, parameters, and row mapper provided directly via builder
 *       <ul>
 *         <li>Use {@link Builder#sql(String)} to provide SQL directly
 *         <li>Use {@link Builder#params(List)} to provide parameters directly
 *         <li>Use {@link Builder#rowMapper(RowMapper)} to provide mapper directly
 *         <li>Inputs are embedded in the task instance
 *       </ul>
 *   <li><b>Context Mode:</b> SQL, parameters, and mapper read from WorkflowContext
 *       <ul>
 *         <li>Use {@link Builder#readingSqlFrom(String)} to specify context key for SQL
 *         <li>Use {@link Builder#readingParamsFrom(String)} for parameters key
 *         <li>Use {@link Builder#readingRowMapperFrom(String)} for row mapper key
 *         <li>Allows dynamic queries and mapping based on workflow state
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
 *   <li><b>rowMapperKey</b> &mdash; {@link RowMapper}&lt;T&gt;: mapper function (required if not
 *       using direct mapper)
 *   <li><b>outputKey</b> &mdash; {@link List}&lt;T&gt;: typed results (written by task)
 * </ul>
 *
 * <p><b>RowMapper Interface:</b>
 *
 * <pre>{@code
 * @FunctionalInterface
 * public interface RowMapper<T> {
 *     T mapRow(ResultSet rs, int rowNum) throws SQLException;
 * }
 * }</pre>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Connection failures → {@link TaskExecutionException}
 *   <li>SQL errors → {@link TaskExecutionException}
 *   <li>Mapping errors → {@link TaskExecutionException}
 *   <li>Type conversion errors → {@link TaskExecutionException}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe provided the {@link DataSource} and {@link
 * RowMapper} are thread-safe.
 *
 * <p><b>Example Usage - Direct Input Mode (Map to POJO):</b>
 *
 * <pre>{@code
 * // Define domain object
 * public record User(Integer id, String name, String email, LocalDate createdAt) {}
 *
 * // Define row mapper
 * RowMapper<User> userMapper = (rs, rowNum) -> new User(
 *     rs.getInt("id"),
 *     rs.getString("name"),
 *     rs.getString("email"),
 *     rs.getDate("created_at").toLocalDate()
 * );
 *
 * JdbcTypedQueryTask<User> task = JdbcTypedQueryTask.<User>builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT id, name, email, created_at FROM users WHERE active = ?")
 *     .params(List.of(true))
 *     .rowMapper(userMapper)
 *     .writingResultsTo("users")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * task.execute(context);
 *
 * List<User> users = context.get("users");
 * for (User user : users) {
 *     System.out.println(user.name() + " - " + user.email());
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Context Mode:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("userQuery", "SELECT id, name, email, created_at FROM users WHERE active = ?");
 * context.put("userParams", List.of(true));
 * context.put("userMapper", userMapper);
 *
 * JdbcTypedQueryTask<User> task = JdbcTypedQueryTask.<User>builder()
 *     .dataSource(dataSource)
 *     .readingSqlFrom("userQuery")
 *     .readingParamsFrom("userParams")
 *     .readingRowMapperFrom("userMapper")
 *     .writingResultsTo("users")
 *     .build();
 *
 * task.execute(context);
 *
 * List<User> users = context.get("users");
 * }</pre>
 *
 * <p><b>Example Usage - Simple Scalar Mapping:</b>
 *
 * <pre>{@code
 * // Map to single column
 * RowMapper<String> emailMapper = (rs, rowNum) -> rs.getString("email");
 *
 * JdbcTypedQueryTask<String> task = JdbcTypedQueryTask.<String>builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT email FROM users WHERE department = ?")
 *     .params(List.of("Engineering"))
 *     .rowMapper(emailMapper)
 *     .writingResultsTo("emails")
 *     .build();
 *
 * task.execute(context);
 *
 * List<String> emails = context.get("emails");
 * }</pre>
 *
 * <p><b>Example Usage - Complex Mapping:</b>
 *
 * <pre>{@code
 * public record Order(
 *     Integer orderId,
 *     Customer customer,
 *     List<OrderItem> items,
 *     BigDecimal total
 * ) {}
 *
 * RowMapper<Order> orderMapper = (rs, rowNum) -> {
 *     Customer customer = new Customer(
 *         rs.getInt("customer_id"),
 *         rs.getString("customer_name")
 *     );
 *
 *     // Parse JSON array of items
 *     String itemsJson = rs.getString("items_json");
 *     List<OrderItem> items = parseOrderItems(itemsJson);
 *
 *     return new Order(
 *         rs.getInt("order_id"),
 *         customer,
 *         items,
 *         rs.getBigDecimal("total")
 *     );
 * };
 *
 * JdbcTypedQueryTask<Order> task = JdbcTypedQueryTask.<Order>builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT * FROM orders WHERE status = ?")
 *     .params(List.of("COMPLETED"))
 *     .rowMapper(orderMapper)
 *     .writingResultsTo("orders")
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - Mixed Mode (Direct SQL, Context Mapper):</b>
 *
 * <pre>{@code
 * // Fixed SQL, dynamic mapper from context
 * JdbcTypedQueryTask<User> task = JdbcTypedQueryTask.<User>builder()
 *     .dataSource(dataSource)
 *     .sql("SELECT * FROM users WHERE department = ?")
 *     .params(List.of("Engineering"))
 *     .readingRowMapperFrom("userMapper")
 *     .writingResultsTo("users")
 *     .build();
 *
 * context.put("userMapper", userMapper);
 * task.execute(context);
 * }</pre>
 *
 * @param <T> the type of objects returned by the query
 * @see RowMapper
 * @see JdbcQueryTask
 */
public class JdbcTypedQueryTask<T> extends AbstractTask {

  /**
   * Functional interface for mapping a database row to a typed object.
   *
   * @param <T> the type of object to map to
   */
  @FunctionalInterface
  public interface RowMapper<T> {
    /**
     * Maps a single row from the ResultSet to an object.
     *
     * @param rs the ResultSet positioned at the current row
     * @param rowNum the number of the current row (0-based)
     * @return the object for the current row
     * @throws SQLException if database access fails
     */
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
  }

  private final DataSource dataSource;
  private final String sql;
  private final List<Object> params;
  private final RowMapper<T> rowMapper;
  private final String sqlKey;
  private final String paramsKey;
  private final String rowMapperKey;
  private final String outputKey;

  /**
   * Private constructor used by the builder.
   *
   * @param builder the builder instance containing configuration
   */
  private JdbcTypedQueryTask(Builder<T> builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.sql = builder.sql;
    this.params = builder.params != null ? Collections.unmodifiableList(builder.params) : null;
    this.rowMapper = builder.rowMapper;
    this.sqlKey = builder.sqlKey;
    this.paramsKey = builder.paramsKey;
    this.rowMapperKey = builder.rowMapperKey;
    this.outputKey = Objects.requireNonNull(builder.outputKey, "outputKey must not be null");
  }

  /**
   * Executes the typed query using values from the provided {@link WorkflowContext} or direct
   * inputs.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>If direct SQL was provided, uses it; otherwise retrieves from context
   *   <li>If direct params were provided, uses them; otherwise retrieves from context
   *   <li>If direct row mapper was provided, uses it; otherwise retrieves from context
   *   <li>Executes query and maps each row using the provided mapper
   *   <li>Stores typed results in context
   * </ul>
   *
   * @param context the workflow context from which to read inputs and into which to write outputs
   * @throws TaskExecutionException if any error occurs during execution
   */
  @Override
  protected void doExecute(WorkflowContext context) {
    // Determine SQL source
    String effectiveSql = getSql(context);

    // Determine parameters source
    List<Object> effectiveParams = getQueryParams(context);

    // Determine row mapper source
    RowMapper<T> effectiveMapper = getRowMapper(context);

    // 1. Check if a transaction connection exists in the context
    Connection sharedConn = (Connection) context.get(JdbcTransactionTask.CONNECTION_CONTEXT_KEY);

    // 2. Use the shared connection if available, otherwise get a new one from DataSource
    boolean isShared = sharedConn != null;
    Connection conn = null;

    List<T> results = new ArrayList<>();
    try {
      conn = isShared ? sharedConn : dataSource.getConnection();
      if (!isShared) {
        conn.setReadOnly(true);
      }

      try (PreparedStatement stmt = conn.prepareStatement(effectiveSql)) {
        // Bind parameters
        for (int i = 0; i < effectiveParams.size(); i++) {
          stmt.setObject(i + 1, effectiveParams.get(i));
        }

        // Execute and map results
        try (ResultSet rs = stmt.executeQuery()) {
          int rowNum = 0;
          while (rs.next()) {
            T mappedRow = mapRow(effectiveMapper, rs, rowNum);
            results.add(mappedRow);
            rowNum++;
          }
        }
      }

      // Store results
      context.put(outputKey, results);

    } catch (SQLException e) {
      throw new TaskExecutionException("Typed query execution failed: " + e.getMessage(), e);
    } finally {
      if (!isShared) {
        close(conn);
      }
    }
  }

  /**
   * Retrieves the row mapper from either direct configuration or workflow context.
   *
   * @param context the workflow context to read row mapper from if not configured directly
   * @return the row mapper function
   * @throws TaskExecutionException if row mapper is not found in context when using context mode
   */
  @SuppressWarnings("unchecked")
  private RowMapper<T> getRowMapper(WorkflowContext context) {
    RowMapper<T> effectiveMapper;
    if (rowMapper != null) {
      effectiveMapper = rowMapper;
    } else {
      effectiveMapper = (RowMapper<T>) context.get(rowMapperKey);
      if (effectiveMapper == null) {
        throw new TaskExecutionException("Row mapper not found in context at key: " + rowMapperKey);
      }
    }
    return effectiveMapper;
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
    } else {
      effectiveParams = (List<Object>) context.get(paramsKey, Collections.emptyList());
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
  private String getSql(WorkflowContext context) {
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
   * Maps a single database row to a typed object using the provided row mapper.
   *
   * @param rowMapper the mapper function to apply
   * @param rs the ResultSet positioned at the current row
   * @param rowNum the row number (0-based)
   * @param <T> the type of object to map to
   * @return the mapped object
   * @throws TaskExecutionException if mapping fails
   */
  private static <T> T mapRow(RowMapper<T> rowMapper, ResultSet rs, int rowNum) {
    try {
      return rowMapper.mapRow(rs, rowNum);
    } catch (SQLException e) {
      throw new TaskExecutionException("Failed to map row " + rowNum + ": " + e.getMessage(), e);
    }
  }

  /**
   * Create a new {@link Builder} to fluently configure and construct a {@link JdbcTypedQueryTask}.
   *
   * <p>The builder supports both direct input mode and context mode:
   *
   * <ul>
   *   <li><b>Direct Mode:</b> Use {@link Builder#sql(String)}, {@link Builder#params(List)}, and
   *       {@link Builder#rowMapper(RowMapper)} to provide inputs directly
   *   <li><b>Context Mode:</b> Use {@link Builder#readingSqlFrom(String)}, {@link
   *       Builder#readingParamsFrom(String)}, and {@link Builder#readingRowMapperFrom(String)} to
   *       read from context
   *   <li><b>Mixed Mode:</b> Combine direct and context inputs as needed
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Direct mode
   * JdbcTypedQueryTask<User> task = JdbcTypedQueryTask.<User>builder()
   *     .dataSource(dataSource)
   *     .sql("SELECT * FROM users")
   *     .params(List.of())
   *     .rowMapper((rs, rowNum) -> new User(rs.getInt("id"), rs.getString("name")))
   *     .writingResultsTo("users")
   *     .build();
   *
   * // Context mode
   * JdbcTypedQueryTask<User> task = JdbcTypedQueryTask.<User>builder()
   *     .dataSource(dataSource)
   *     .readingSqlFrom("sql")
   *     .readingParamsFrom("params")
   *     .readingRowMapperFrom("mapper")
   *     .writingResultsTo("users")
   *     .build();
   * }</pre>
   *
   * @param <T> the type of objects returned by the query
   * @return a fresh {@link Builder} instance
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * A fluent builder for creating {@link JdbcTypedQueryTask} instances.
   *
   * <p>The builder supports multiple configuration modes:
   *
   * <ul>
   *   <li><b>Direct Input:</b> Provide SQL, parameters, and row mapper directly
   *   <li><b>Context Input:</b> Read SQL, parameters, and row mapper from context
   *   <li><b>Mixed Input:</b> Combine direct and context inputs as needed
   * </ul>
   */
  public static class Builder<T> {
    private DataSource dataSource;
    private String sql;
    private List<Object> params;
    private RowMapper<T> rowMapper;
    private String sqlKey = "sql";
    private String paramsKey = "params";
    private String rowMapperKey = "rowMapper";
    private String outputKey = "results";

    /**
     * Set the {@link DataSource} that the task will use to obtain JDBC connections.
     *
     * @param dataSource the DataSource to use; must not be {@code null} when {@link #build()} is
     *     called
     * @return this builder instance
     */
    public Builder<T> dataSource(DataSource dataSource) {
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
    public Builder<T> sql(String sql) {
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
    public Builder<T> params(List<Object> params) {
      this.params = params != null ? new ArrayList<>(params) : null;
      return this;
    }

    /**
     * Provide row mapper directly (direct input mode).
     *
     * <p>This is mutually exclusive with {@link #readingRowMapperFrom(String)}. If both are set,
     * the direct mapper takes precedence.
     *
     * @param rowMapper the mapper function to convert rows to objects
     * @return this builder instance
     */
    public Builder<T> rowMapper(RowMapper<T> rowMapper) {
      this.rowMapper = rowMapper;
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
    public Builder<T> readingSqlFrom(String sqlKey) {
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
    public Builder<T> readingParamsFrom(String paramsKey) {
      this.paramsKey = paramsKey;
      return this;
    }

    /**
     * Configure the context key from which the row mapper will be read (context mode).
     *
     * <p>This is mutually exclusive with {@link #rowMapper(RowMapper)}. If both are set, the direct
     * mapper takes precedence.
     *
     * @param rowMapperKey the context key containing the row mapper; default is "rowMapper"
     * @return this builder instance
     */
    public Builder<T> readingRowMapperFrom(String rowMapperKey) {
      this.rowMapperKey = rowMapperKey;
      return this;
    }

    /**
     * Configure the context key where the results will be written.
     *
     * @param outputKey the context key for results; default is "results"
     * @return this builder instance
     */
    public Builder<T> writingResultsTo(String outputKey) {
      this.outputKey = outputKey;
      return this;
    }

    /**
     * Build a new {@link JdbcTypedQueryTask} with the configured values.
     *
     * <p>Validates that required configuration is present:
     *
     * <ul>
     *   <li>dataSource must be set
     *   <li>Either SQL or sqlKey must be set
     *   <li>Either rowMapper or rowMapperKey must be set
     *   <li>outputKey must be set
     * </ul>
     *
     * @return a new {@link JdbcTypedQueryTask} instance
     * @throws IllegalStateException if required fields are not set
     */
    public JdbcTypedQueryTask<T> build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if (sql == null && sqlKey == null) {
        throw new IllegalStateException("Either sql or sqlKey must be set");
      }
      if (rowMapper == null && rowMapperKey == null) {
        throw new IllegalStateException("Either rowMapper or rowMapperKey must be set");
      }
      if (outputKey == null) {
        throw new IllegalStateException("outputKey must be set");
      }
      return new JdbcTypedQueryTask<>(this);
    }
  }
}
