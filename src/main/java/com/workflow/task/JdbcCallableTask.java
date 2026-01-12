package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/**
 * Executes database stored procedures or functions using JDBC {@link CallableStatement}.
 *
 * <p><b>Purpose:</b> Provides workflow integration with database stored procedures and functions,
 * supporting IN, OUT, and INOUT parameters, return values, and multiple result sets.
 *
 * <p><b>Callable Statement Semantics:</b>
 *
 * <ul>
 *   <li><b>Syntax:</b> Uses JDBC escape syntax: {@code {call procedure_name(?, ?, ?)}} or {@code {?
 *       = call function_name(?, ?)}}
 *   <li><b>Parameters:</b> Supports IN (input), OUT (output), and INOUT parameters
 *   <li><b>Return Values:</b> Functions can return values via registerOutParameter
 *   <li><b>Result Sets:</b> Procedures can return multiple result sets
 *   <li><b>Type Mapping:</b> JDBC types mapped to Java types automatically
 * </ul>
 *
 * <p><b>Input Modes:</b>
 *
 * <p>This task supports two modes for providing inputs:
 *
 * <ol>
 *   <li><b>Direct Input Mode:</b> Call statement and parameters provided directly via builder
 *       <ul>
 *         <li>Use {@link Builder#call(String)} to provide call statement directly
 *         <li>Use {@link Builder#inParameters(Map)} to provide IN parameters directly
 *         <li>Use {@link Builder#outParameters(Map)} to provide OUT parameter registrations
 *             directly
 *         <li>Inputs are embedded in the task instance
 *       </ul>
 *   <li><b>Context Mode:</b> Call and parameters read from WorkflowContext at execution time
 *       <ul>
 *         <li>Use {@link Builder#readingCallFrom(String)} to specify context key for call statement
 *         <li>Use {@link Builder#readingInParametersFrom(String)} for IN parameters key
 *         <li>Use {@link Builder#readingOutParametersFrom(String)} for OUT parameters key
 *         <li>Allows dynamic stored procedure calls based on workflow state
 *       </ul>
 * </ol>
 *
 * <p><b>Context Keys and Expected Types:</b>
 *
 * <ul>
 *   <li><b>callKey</b> &mdash; {@link String}: SQL call statement with JDBC escape syntax (required
 *       if not using direct call)
 *   <li><b>inParametersKey</b> &mdash; {@link Map}&lt;{@link Integer}, {@link Object}&gt;: IN
 *       parameters by position (optional, defaults to empty map)
 *   <li><b>outParametersKey</b> &mdash; {@link Map}&lt;{@link Integer}, {@link Integer}&gt;: OUT
 *       parameter positions to SQL type (optional, defaults to empty map)
 *   <li><b>resultSetKey</b> &mdash; {@link List}&lt;{@link List}&lt;{@link Map}&lt;{@link
 *       String},{@link Object}&gt;&gt;&gt;: all result sets (written by task)
 *   <li><b>outValuesKey</b> &mdash; {@link Map}&lt;{@link Integer}, {@link Object}&gt;: OUT
 *       parameter values (written by task)
 * </ul>
 *
 * <p><b>Parameter Types:</b>
 *
 * <ul>
 *   <li><b>IN:</b> Pass values via inParametersKey map (position → value)
 *   <li><b>OUT:</b> Register via outParametersKey map (position → SQL type from {@link Types})
 *   <li><b>INOUT:</b> Pass value via IN and register via OUT
 *   <li><b>Return Value:</b> Register position 1 as OUT parameter for functions
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Connection failures → {@link TaskExecutionException}
 *   <li>SQL syntax errors → {@link TaskExecutionException}
 *   <li>Parameter binding errors → {@link TaskExecutionException}
 *   <li>Stored procedure errors → {@link TaskExecutionException}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe provided the {@link DataSource} is thread-safe.
 *
 * <p><b>Example Usage - Direct Input Mode (Simple Procedure):</b>
 *
 * <pre>{@code
 * // Call: CREATE PROCEDURE update_inventory(IN product_id INT, IN quantity INT)
 *
 * Map<Integer, Object> inParams = new HashMap<>();
 * inParams.put(1, 101);  // product_id
 * inParams.put(2, 50);   // quantity
 *
 * JdbcCallableTask task = JdbcCallableTask.builder()
 *     .dataSource(dataSource)
 *     .call("{call update_inventory(?, ?)}")
 *     .inParameters(inParams)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * task.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Context Mode:</b>
 *
 * <pre>{@code
 * // Call SQL and parameters from context
 * WorkflowContext context = new WorkflowContext();
 * context.put("callSql", "{call update_inventory(?, ?)}");
 *
 * Map<Integer, Object> inParams = new HashMap<>();
 * inParams.put(1, 101);
 * inParams.put(2, 50);
 * context.put("inParams", inParams);
 *
 * JdbcCallableTask task = JdbcCallableTask.builder()
 *     .dataSource(dataSource)
 *     .readingCallFrom("callSql")
 *     .readingInParametersFrom("inParams")
 *     .build();
 *
 * task.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Function with Return Value (Direct Mode):</b>
 *
 * <pre>{@code
 * // Call: CREATE FUNCTION calculate_tax(amount DECIMAL) RETURNS DECIMAL
 *
 * Map<Integer, Object> inParams = new HashMap<>();
 * inParams.put(2, 100.00);  // amount (position 2, position 1 is return value)
 *
 * Map<Integer, Integer> outParams = new HashMap<>();
 * outParams.put(1, Types.DECIMAL);  // Register return value
 *
 * JdbcCallableTask task = JdbcCallableTask.builder()
 *     .dataSource(dataSource)
 *     .call("{? = call calculate_tax(?)}")
 *     .inParameters(inParams)
 *     .outParameters(outParams)
 *     .writingOutValuesTo("taxResult")
 *     .build();
 *
 * task.execute(context);
 *
 * Map<Integer, Object> outValues = context.get("taxResult");
 * BigDecimal tax = (BigDecimal) outValues.get(1);
 * }</pre>
 *
 * <p><b>Example Usage - Procedure with - OUT Parameters:</b>
 *
 * <pre>{@code
 * // Call: CREATE PROCEDURE get_user_stats(IN user_id INT, OUT total_orders INT, OUT total_spent DECIMAL)
 *
 * Map<Integer, Object> inParams = Map.of(1, 42);
 * Map<Integer, Integer> outParams = Map.of(
 *     2, Types.INTEGER,  // total_orders
 *     3, Types.DECIMAL   // total_spent
 * );
 *
 * JdbcCallableTask task = JdbcCallableTask.builder()
 *     .dataSource(dataSource)
 *     .call("{call get_user_stats(?, ?, ?)}")
 *     .inParameters(inParams)
 *     .outParameters(outParams)
 *     .writingOutValuesTo("userStats")
 *     .build();
 *
 * task.execute(context);
 *
 * Map<Integer, Object> stats = context.get("userStats");
 * Integer orders = (Integer) stats.get(2);
 * BigDecimal spent = (BigDecimal) stats.get(3);
 * }</pre>
 *
 * <p><b>Example Usage - Mixed Mode (Direct Call, Context Parameters):</b>
 *
 * <pre>{@code
 * // Fixed call statement, dynamic parameters from context
 * JdbcCallableTask task = JdbcCallableTask.builder()
 *     .dataSource(dataSource)
 *     .call("{call process_order(?, ?)}")
 *     .readingInParametersFrom("orderParams")
 *     .build();
 *
 * context.put("orderParams", Map.of(1, orderId, 2, userId));
 * task.execute(context);
 * }</pre>
 *
 * @see CallableStatement
 * @see Types
 * @see JdbcQueryTask
 */
public class JdbcCallableTask extends AbstractTask {

  private final DataSource dataSource;
  private final String call;
  private final Map<Integer, Object> inParameters;
  private final Map<Integer, Integer> outParameters;
  private final String callKey;
  private final String inParametersKey;
  private final String outParametersKey;
  private final String resultSetKey;
  private final String outValuesKey;

  /**
   * Private constructor used by the builder.
   *
   * @param builder the builder instance containing configuration
   */
  private JdbcCallableTask(Builder builder) {
    this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
    this.call = builder.call;
    this.inParameters =
        builder.inParameters != null ? Collections.unmodifiableMap(builder.inParameters) : null;
    this.outParameters =
        builder.outParameters != null ? Collections.unmodifiableMap(builder.outParameters) : null;
    this.callKey = builder.callKey;
    this.inParametersKey = builder.inParametersKey;
    this.outParametersKey = builder.outParametersKey;
    this.resultSetKey = builder.resultSetKey;
    this.outValuesKey = builder.outValuesKey;
  }

  /**
   * Executes the stored procedure or function using values from the provided {@link
   * WorkflowContext} or direct inputs.
   *
   * <p>Implementation details:
   *
   * <ul>
   *   <li>If direct call was provided, uses it; otherwise retrieves from context
   *   <li>If direct parameters were provided, uses them; otherwise retrieves from context
   *   <li>Binds IN parameters using {@link CallableStatement#setObject}
   *   <li>Registers OUT parameters using {@link CallableStatement#registerOutParameter}
   *   <li>Processes result sets and OUT parameter values
   *   <li>Stores results in context at configured keys
   * </ul>
   *
   * @param context the workflow context from which to read inputs and into which to write outputs
   * @throws TaskExecutionException if any error occurs during execution
   */
  @Override
  protected void doExecute(WorkflowContext context) {
    // Determine call statement source (direct or from context)
    String effectiveCall = getCallableSql(context);

    // Determine IN parameters source (direct or from context)
    Map<Integer, Object> effectiveInParams = getInParameters(context);

    // Determine OUT parameters source (direct or from context)
    Map<Integer, Integer> effectiveOutParams = getOutParameters(context);

    try (Connection conn = dataSource.getConnection();
        CallableStatement stmt = conn.prepareCall(effectiveCall)) {

      // Bind IN parameters
      for (Map.Entry<Integer, Object> entry : effectiveInParams.entrySet()) {
        stmt.setObject(entry.getKey(), entry.getValue());
      }

      // Register OUT parameters
      for (Map.Entry<Integer, Integer> entry : effectiveOutParams.entrySet()) {
        stmt.registerOutParameter(entry.getKey(), entry.getValue());
      }

      // Execute
      boolean hasResultSet = stmt.execute();

      // Get OUT parameter values
      if (!effectiveOutParams.isEmpty() && outValuesKey != null) {
        Map<Integer, Object> outValues = new HashMap<>();
        for (Integer position : effectiveOutParams.keySet()) {
          Object value = stmt.getObject(position);
          outValues.put(position, value);
        }
        context.put(outValuesKey, outValues);
      }

      // Process result sets
      List<List<Map<String, Object>>> allResultSets = new ArrayList<>();
      while (hasResultSet) {
        try (ResultSet rs = stmt.getResultSet()) {
          List<Map<String, Object>> resultSet = convertResultSetToList(rs);
          allResultSets.add(resultSet);
        }
        hasResultSet = stmt.getMoreResults();
      }

      if (!allResultSets.isEmpty() && resultSetKey != null) {
        context.put(resultSetKey, allResultSets);
      }

    } catch (SQLException e) {
      throw new TaskExecutionException(
          "Failed to execute callable statement: " + e.getMessage(), e);
    }
  }

  /**
   * Retrieves OUT parameter registrations from either direct configuration or workflow context.
   *
   * @param context the workflow context to read OUT parameters from if not configured directly
   * @return map of parameter positions to SQL types (never null, may be empty)
   */
  @SuppressWarnings("unchecked")
  private Map<Integer, Integer> getOutParameters(WorkflowContext context) {
    Map<Integer, Integer> effectiveOutParams;
    if (outParameters != null) {
      effectiveOutParams = outParameters;
    } else {
      effectiveOutParams =
          (Map<Integer, Integer>) context.get(outParametersKey, Collections.emptyMap());
    }
    return effectiveOutParams;
  }

  /**
   * Retrieves IN parameter values from either direct configuration or workflow context.
   *
   * @param context the workflow context to read IN parameters from if not configured directly
   * @return map of parameter positions to values (never null, may be empty)
   */
  @SuppressWarnings("unchecked")
  private Map<Integer, Object> getInParameters(WorkflowContext context) {
    Map<Integer, Object> effectiveInParams;
    if (inParameters != null) {
      effectiveInParams = inParameters;
    } else {
      effectiveInParams =
          (Map<Integer, Object>) context.get(inParametersKey, Collections.emptyMap());
    }
    return effectiveInParams;
  }

  /**
   * Retrieves the callable statement (procedure/function call) from either direct configuration or
   * workflow context.
   *
   * @param context the workflow context to read call statement from if not configured directly
   * @return the callable statement string
   * @throws TaskExecutionException if call statement is not found in context when using context
   *     mode
   */
  private String getCallableSql(WorkflowContext context) {
    String effectiveCall;
    if (call != null) {
      effectiveCall = call;
    } else {
      effectiveCall = context.getTyped(callKey, String.class);
      if (effectiveCall == null) {
        throw new TaskExecutionException("Call statement not found in context at key: " + callKey);
      }
    }
    return effectiveCall;
  }

  /**
   * Converts a ResultSet to a list of maps.
   *
   * @param rs the ResultSet to convert
   * @return list of maps representing rows
   * @throws SQLException if database access fails
   */
  private List<Map<String, Object>> convertResultSetToList(ResultSet rs) throws SQLException {
    List<Map<String, Object>> results = new ArrayList<>();
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();

    while (rs.next()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        String columnName = metaData.getColumnLabel(i);
        Object value = rs.getObject(i);
        row.put(columnName, value);
      }
      results.add(row);
    }

    return results;
  }

  /**
   * Create a new {@link Builder} to fluently configure and construct a {@link JdbcCallableTask}.
   *
   * <p>The builder supports both direct input mode and context mode:
   *
   * <ul>
   *   <li><b>Direct Mode:</b> Use {@link Builder#call(String)}, {@link Builder#inParameters(Map)},
   *       and {@link Builder#outParameters(Map)} to provide inputs directly
   *   <li><b>Context Mode:</b> Use {@link Builder#readingCallFrom(String)}, {@link
   *       Builder#readingInParametersFrom(String)}, and {@link
   *       Builder#readingOutParametersFrom(String)} to read from context
   *   <li><b>Mixed Mode:</b> Combine direct and context inputs as needed
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Direct mode
   * JdbcCallableTask task = JdbcCallableTask.builder()
   *     .dataSource(dataSource)
   *     .call("{call update_inventory(?, ?)}")
   *     .inParameters(Map.of(1, 101, 2, 50))
   *     .build();
   *
   * // Context mode
   * JdbcCallableTask task = JdbcCallableTask.builder()
   *     .dataSource(dataSource)
   *     .readingCallFrom("callSql")
   *     .readingInParametersFrom("inParams")
   *     .readingOutParametersFrom("outParams")
   *     .writingOutValuesTo("outValues")
   *     .build();
   * }</pre>
   *
   * @return a fresh {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A fluent builder for creating {@link JdbcCallableTask} instances.
   *
   * <p>The builder supports multiple configuration modes:
   *
   * <ul>
   *   <li><b>Direct Input:</b> Provide call statement and parameters directly
   *   <li><b>Context Input:</b> Read call statement and parameters from context
   *   <li><b>Mixed Input:</b> Combine direct and context inputs as needed
   * </ul>
   */
  public static class Builder {
    private DataSource dataSource;
    private String call;
    private Map<Integer, Object> inParameters;
    private Map<Integer, Integer> outParameters;
    private String callKey = "callSql";
    private String inParametersKey = "inParams";
    private String outParametersKey = "outParams";
    private String resultSetKey = "resultSets";
    private String outValuesKey = "outValues";

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
     * Provide the call statement directly (direct input mode).
     *
     * <p>When using this method, the call statement is embedded in the task instance and does not
     * need to be provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingCallFrom(String)}. If both are set, the
     * direct call takes precedence.
     *
     * @param call the SQL call statement using JDBC escape syntax
     * @return this builder instance
     */
    public Builder call(String call) {
      this.call = call;
      return this;
    }

    /**
     * Provide IN parameters directly (direct input mode).
     *
     * <p>When using this method, the IN parameters are embedded in the task instance and do not
     * need to be provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingInParametersFrom(String)}. If both are set,
     * the direct parameters take precedence.
     *
     * @param inParameters map of parameter position to value
     * @return this builder instance
     */
    public Builder inParameters(Map<Integer, Object> inParameters) {
      this.inParameters = inParameters != null ? new HashMap<>(inParameters) : null;
      return this;
    }

    /**
     * Provide OUT parameter registrations directly (direct input mode).
     *
     * <p>When using this method, the OUT parameter types are embedded in the task instance and do
     * not need to be provided in the context at execution time.
     *
     * <p>This is mutually exclusive with {@link #readingOutParametersFrom(String)}. If both are
     * set, the direct parameters take precedence.
     *
     * @param outParameters map of parameter position to SQL type (from {@link Types})
     * @return this builder instance
     */
    public Builder outParameters(Map<Integer, Integer> outParameters) {
      this.outParameters = outParameters != null ? new HashMap<>(outParameters) : null;
      return this;
    }

    /**
     * Configure the context key from which the call statement will be read (context mode).
     *
     * <p>At execution time, the task will read the call statement from the context using this key.
     *
     * <p>This is mutually exclusive with {@link #call(String)}. If both are set, the direct call
     * takes precedence.
     *
     * @param callKey the context key containing the call statement; default is "callSql"
     * @return this builder instance
     */
    public Builder readingCallFrom(String callKey) {
      this.callKey = callKey;
      return this;
    }

    /**
     * Configure the context key from which IN parameters will be read (context mode).
     *
     * <p>At execution time, the task will read the IN parameters from the context using this key.
     *
     * <p>This is mutually exclusive with {@link #inParameters(Map)}. If both are set, the direct
     * parameters take precedence.
     *
     * @param inParametersKey the context key containing IN parameters; default is "inParams"
     * @return this builder instance
     */
    public Builder readingInParametersFrom(String inParametersKey) {
      this.inParametersKey = inParametersKey;
      return this;
    }

    /**
     * Configure the context key from which OUT parameter types will be read (context mode).
     *
     * <p>At execution time, the task will read the OUT parameter types from the context using this
     * key.
     *
     * <p>This is mutually exclusive with {@link #outParameters(Map)}. If both are set, the direct
     * parameters take precedence.
     *
     * @param outParametersKey the context key containing OUT parameter types; default is
     *     "outParams"
     * @return this builder instance
     */
    public Builder readingOutParametersFrom(String outParametersKey) {
      this.outParametersKey = outParametersKey;
      return this;
    }

    /**
     * Configure the context key where result sets will be written.
     *
     * @param resultSetKey the context key for result sets; default is "resultSets"
     * @return this builder instance
     */
    public Builder writingResultSetsTo(String resultSetKey) {
      this.resultSetKey = resultSetKey;
      return this;
    }

    /**
     * Configure the context key where OUT parameter values will be written.
     *
     * @param outValuesKey the context key for OUT values; default is "outValues"
     * @return this builder instance
     */
    public Builder writingOutValuesTo(String outValuesKey) {
      this.outValuesKey = outValuesKey;
      return this;
    }

    /**
     * Build a new {@link JdbcCallableTask} with the configured values.
     *
     * <p>Validates that required configuration is present:
     *
     * <ul>
     *   <li>dataSource must be set
     *   <li>Either call or callKey must be set
     * </ul>
     *
     * @return a new {@link JdbcCallableTask} instance
     * @throws IllegalStateException if dataSource is not set, or if neither call nor callKey is set
     */
    public JdbcCallableTask build() {
      if (dataSource == null) {
        throw new IllegalStateException("dataSource must be set");
      }
      if (call == null && callKey == null) {
        throw new IllegalStateException("Either call or callKey must be set");
      }
      return new JdbcCallableTask(this);
    }
  }
}
