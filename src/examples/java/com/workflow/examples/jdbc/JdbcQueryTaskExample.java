package com.workflow.examples.jdbc;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.task.JdbcQueryTask;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Comprehensive examples demonstrating JdbcQueryTask usage with an in-memory H2 database.
 *
 * <p>This class shows examples from simple to complex:
 *
 * <ul>
 *   <li>Example 1: Simple SELECT query without parameters
 *   <li>Example 2: Parameterized SELECT query
 *   <li>Example 3: Complex query with multiple parameters and JOINs
 *   <li>Example 4: Query with aggregations and GROUP BY
 *   <li>Example 5: Dynamic query from context
 *   <li>Example 6: Query with date/time handling
 * </ul>
 */
@Slf4j
public class JdbcQueryTaskExample {

  public static final String USERNAME = "USERNAME";
  public static final String ACTIVE = "ACTIVE";
  public static final String DYNAMIC_SQL = "dynamicSql";
  public static final String DYNAMIC_PARAMS = "dynamicParams";
  public static final String RESULTS = "results";
  private final DataSource dataSource;

  public JdbcQueryTaskExample() throws SQLException {
    this.dataSource = createDataSource();
    initializeDatabase();
  }

  /** Creates an H2 in-memory database data source. */
  private DataSource createDataSource() {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:query_db;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  /** Initializes the database schema and populates sample data. */
  private void initializeDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      // Create users table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS users ("
              + "id INT PRIMARY KEY, "
              + "username VARCHAR(50) NOT NULL, "
              + "email VARCHAR(100), "
              + "age INT, "
              + "status VARCHAR(20), "
              + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
              + ")");

      // Create orders table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS orders ("
              + "id INT PRIMARY KEY, "
              + "user_id INT, "
              + "product VARCHAR(100), "
              + "quantity INT, "
              + "price DECIMAL(10,2), "
              + "order_date TIMESTAMP, "
              + "FOREIGN KEY (user_id) REFERENCES users(id)"
              + ")");

      // Insert sample users
      stmt.execute(
          "INSERT INTO users VALUES (1, 'alice', 'alice@example.com', 28, 'ACTIVE', '2024-01-15 10:00:00')");
      stmt.execute(
          "INSERT INTO users VALUES (2, 'bob', 'bob@example.com', 35, 'ACTIVE', '2024-02-20 11:30:00')");
      stmt.execute(
          "INSERT INTO users VALUES (3, 'charlie', 'charlie@example.com', 42, 'INACTIVE', '2024-03-10 09:15:00')");
      stmt.execute(
          "INSERT INTO users VALUES (4, 'diana', 'diana@example.com', 25, 'ACTIVE', '2024-04-05 14:45:00')");
      stmt.execute(
          "INSERT INTO users VALUES (5, 'eve', 'eve@example.com', 31, 'INACTIVE', '2024-05-12 16:20:00')");

      // Insert sample orders
      stmt.execute(
          "INSERT INTO orders VALUES (101, 1, 'Laptop', 1, 999.99, '2024-06-01 10:00:00')");
      stmt.execute("INSERT INTO orders VALUES (102, 1, 'Mouse', 2, 25.50, '2024-06-02 11:00:00')");
      stmt.execute(
          "INSERT INTO orders VALUES (103, 2, 'Keyboard', 1, 75.00, '2024-06-03 12:00:00')");
      stmt.execute(
          "INSERT INTO orders VALUES (104, 2, 'Monitor', 2, 299.99, '2024-06-04 13:00:00')");
      stmt.execute(
          "INSERT INTO orders VALUES (105, 4, 'Headphones', 1, 150.00, '2024-06-05 14:00:00')");

      log.info("Database initialized with sample data");
    } catch (SQLException e) {
      log.error("Failed to initialize database", e);
      throw e;
    }
  }

  /**
   * Example 1: Simple SELECT query without parameters. Demonstrates basic query execution and
   * result retrieval.
   */
  public void example1SimpleQuery() {
    log.info("\n=== Example 1: Simple SELECT Query ===");

    // Create task with direct SQL (no parameters)
    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, username, email FROM users")
            .writingResultsTo("allUsers")
            .build();

    // Execute task
    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    // Retrieve and display results
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> users = (List<Map<String, Object>>) context.get("allUsers");
    log.info("Found {} users:", users.size());
    users.forEach(
        user ->
            log.info(
                "  User ID: {}, Username: {}, Email: {}",
                user.get("ID"),
                user.get(USERNAME),
                user.get("EMAIL")));
  }

  /** Example 2: Parameterized SELECT query. Demonstrates using parameters to filter results. */
  public void example2ParameterizedQuery() {
    log.info("\n=== Example 2: Parameterized Query ===");

    // Query active users
    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, username, email, status FROM users WHERE status = ?")
            .params(List.of(ACTIVE))
            .writingResultsTo("activeUsers")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> activeUsers = (List<Map<String, Object>>) context.get("activeUsers");
    log.info("Found {} active users:", activeUsers.size());
    activeUsers.forEach(
        user -> log.info("  {}: {} ({})", user.get("ID"), user.get(USERNAME), user.get("STATUS")));
  }

  /**
   * Example 3: Complex query with multiple parameters and JOINs. Demonstrates joining tables and
   * using multiple WHERE conditions.
   */
  public void example3ComplexJoinQuery() {
    log.info("\n=== Example 3: Complex JOIN Query ===");

    String sql =
        "SELECT u.username, u.email, o.product, o.quantity, o.price, o.order_date "
            + "FROM users u "
            + "INNER JOIN orders o ON u.id = o.user_id "
            + "WHERE u.status = ? AND o.price > ?";

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(Arrays.asList(ACTIVE, 50.0))
            .writingResultsTo("orderDetails")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> orders = (List<Map<String, Object>>) context.get("orderDetails");
    log.info("Found {} orders from active users with price > $50:", orders.size());
    orders.forEach(
        order ->
            log.info(
                "  {} ordered {} x{} for ${} on {}",
                order.get(USERNAME),
                order.get("PRODUCT"),
                order.get("QUANTITY"),
                order.get("PRICE"),
                order.get("ORDER_DATE")));
  }

  /**
   * Example 4: Query with aggregations and GROUP BY. Demonstrates using aggregate functions to
   * generate reports.
   */
  public void example4AggregationQuery() {
    log.info("\n=== Example 4: Aggregation Query ===");

    String sql =
        "SELECT u.username, "
            + "       COUNT(o.id) as order_count, "
            + "       SUM(o.quantity * o.price) as total_spent, "
            + "       AVG(o.price) as avg_price "
            + "FROM users u "
            + "LEFT JOIN orders o ON u.id = o.user_id "
            + "GROUP BY u.username "
            + "HAVING COUNT(o.id) > 0 "
            + "ORDER BY total_spent DESC";

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .writingResultsTo("userStats")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> stats = (List<Map<String, Object>>) context.get("userStats");
    log.info("User spending statistics:");
    stats.forEach(
        stat ->
            log.info(
                "  {}: {} orders, ${} total, ${} avg",
                stat.get(USERNAME),
                stat.get("ORDER_COUNT"),
                stat.get("TOTAL_SPENT"),
                stat.get("AVG_PRICE")));
  }

  /**
   * Example 5: Dynamic query from context. Demonstrates reading SQL and parameters from workflow
   * context at runtime.
   */
  @SuppressWarnings("unchecked")
  public void example5DynamicQuery() {
    log.info("\n=== Example 5: Dynamic Query from Context ===");

    // Task reads SQL and params from context
    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(DYNAMIC_SQL)
            .readingParamsFrom(DYNAMIC_PARAMS)
            .writingResultsTo(RESULTS)
            .build();

    WorkflowContext context = new WorkflowContext();

    // Scenario 1: Query users by age range
    context.put(DYNAMIC_SQL, "SELECT username, age FROM users WHERE age BETWEEN ? AND ?");
    context.put(DYNAMIC_PARAMS, Arrays.asList(25, 35));

    task.execute(context);

    List<Map<String, Object>> results = (List<Map<String, Object>>) context.get(RESULTS);
    log.info("Users aged 25-35:");
    results.forEach(user -> log.info("  {}: {} years old", user.get(USERNAME), user.get("AGE")));

    // Scenario 2: Change query dynamically for different criteria
    context.put(DYNAMIC_SQL, "SELECT username, email FROM users WHERE username LIKE ?");
    context.put(DYNAMIC_PARAMS, List.of("%a%"));

    task.execute(context);

    results = (List<Map<String, Object>>) context.get(RESULTS);

    log.info("\nUsers with 'a' in username:");
    results.forEach(user -> log.info("  {}: {}", user.get(USERNAME), user.get("EMAIL")));
  }

  /** Example 6: Query with date/time filtering. Demonstrates working with temporal data types. */
  public void example6DateTimeQuery() {
    log.info("\n=== Example 6: Date/Time Query ===");

    String sql =
        "SELECT username, created_at, "
            + "       DATEDIFF('DAY', created_at, CURRENT_TIMESTAMP) as days_since_creation "
            + "FROM users "
            + "WHERE created_at >= ? "
            + "ORDER BY created_at DESC";

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(List.of("2024-03-01"))
            .writingResultsTo("recentUsers")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> recentUsers = (List<Map<String, Object>>) context.get("recentUsers");
    log.info("Users created since March 2024:");
    recentUsers.forEach(
        user ->
            log.info(
                "  {} created on {} ({} days ago)",
                user.get(USERNAME),
                user.get("CREATED_AT"),
                user.get("DAYS_SINCE_CREATION")));
  }

  /**
   * Bonus: Using JdbcQueryTask within a Workflow. Demonstrates integration into a complete
   * workflow.
   */
  public void exampleBonusWorkflowIntegration() {
    log.info("\n=== Bonus: Workflow Integration ===");

    // Task 1: Query active users
    JdbcQueryTask queryActiveUsers =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users WHERE status = ?")
            .params(List.of(ACTIVE))
            .writingResultsTo("activeUserIds")
            .build();

    // Task 2: Use result from task 1 to query orders
    // (In real scenario, you'd use a custom task to extract IDs and build IN clause)
    JdbcQueryTask queryOrdersForActiveUsers =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM orders WHERE user_id IN (SELECT id FROM users WHERE status = ?)")
            .params(List.of(ACTIVE))
            .writingResultsTo("activeUserOrders")
            .build();

    // Create workflow
    Workflow workflow =
        SequentialWorkflow.builder()
            .name("UserOrdersReport")
            .task(queryActiveUsers)
            .task(queryOrdersForActiveUsers)
            .build();

    // Execute workflow
    WorkflowContext context = new WorkflowContext();
    var result = workflow.execute(context);

    log.info("Workflow execution status: {}", result.getStatus());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> activeUserIds =
        (List<Map<String, Object>>) context.get("activeUserIds");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> orders = (List<Map<String, Object>>) context.get("activeUserOrders");

    log.info("Found {} active users with {} total orders", activeUserIds.size(), orders.size());
  }

  /** Main method to run all examples. */
  public static void main(String[] args) throws SQLException {
    JdbcQueryTaskExample example = new JdbcQueryTaskExample();

    example.example1SimpleQuery();
    example.example2ParameterizedQuery();
    example.example3ComplexJoinQuery();
    example.example4AggregationQuery();
    example.example5DynamicQuery();
    example.example6DateTimeQuery();
    example.exampleBonusWorkflowIntegration();

    log.info("\n=== All examples completed successfully! ===");
  }
}
