package com.workflow.examples.jdbc;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.task.JdbcTypedQueryTask;
import com.workflow.task.JdbcTypedQueryTask.RowMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Comprehensive examples demonstrating JdbcTypedQueryTask usage with an in-memory H2 database.
 *
 * <p>JdbcTypedQueryTask provides type-safe query results by mapping rows to domain objects (POJOs,
 * DTOs, or records) instead of generic Map structures. This offers:
 *
 * <ul>
 *   <li>Compile-time type safety
 *   <li>Better IDE support (autocomplete, refactoring)
 *   <li>Cleaner, more maintainable code
 *   <li>Reusable row mappers
 * </ul>
 *
 * <p>Examples from simple to complex:
 *
 * <ul>
 *   <li>Example 1: Simple mapping to a record
 *   <li>Example 2: Mapping to a POJO with complex fields
 *   <li>Example 3: Reusable row mappers
 *   <li>Example 4: Nested object mapping (composition)
 *   <li>Example 5: Dynamic mapper from context
 *   <li>Example 6: Mapper with error handling
 * </ul>
 */
@Slf4j
public class JdbcTypedQueryTaskExample {

  public static final String ID = "id";
  public static final String USERNAME = "username";
  public static final String EMAIL = "email";
  public static final String STATUS = "status";
  public static final String ACTIVE = "ACTIVE";
  public static final String ACTIVE_USERS = "activeUsers";
  public static final String NAME = "name";
  public static final String PRICE = "price";
  public static final String STOCK = "stock";
  public static final String CATEGORY = "category";
  private final DataSource dataSource;

  public JdbcTypedQueryTaskExample() throws SQLException {
    this.dataSource = createDataSource();
    initializeDatabase();
  }

  /** Domain model: Simple User record. */
  public record User(Integer id, String username, String email, String status) {}

  /** Domain model: Product POJO with various data types. */
  @Getter
  public static class Product {
    private final Integer id;
    private final String name;
    private final BigDecimal price;
    private final Integer stock;
    private final String category;

    public Product(Integer id, String name, BigDecimal price, Integer stock, String category) {
      this.id = id;
      this.name = name;
      this.price = price;
      this.stock = stock;
      this.category = category;
    }

    @Override
    public String toString() {
      return String.format(
          "Product{id=%d, name='%s', price=%s, stock=%d, category='%s'}",
          id, name, price, stock, category);
    }
  }

  /** Domain model: Order with date handling. */
  public record Order(
      Integer id,
      Integer userId,
      String product,
      Integer quantity,
      BigDecimal amount,
      LocalDate orderDate) {}

  /** Domain model: Customer with nested Address. */
  @Getter
  public static class Address {
    private final String street;
    private final String city;
    private final String zipCode;

    public Address(String street, String city, String zipCode) {
      this.street = street;
      this.city = city;
      this.zipCode = zipCode;
    }

    @Override
    public String toString() {
      return String.format("%s, %s %s", street, city, zipCode);
    }
  }

  @Getter
  public static class Customer {
    private final Integer id;
    private final String name;
    private final String email;
    private final Address address;

    public Customer(Integer id, String name, String email, Address address) {
      this.id = id;
      this.name = name;
      this.email = email;
      this.address = address;
    }

    @Override
    public String toString() {
      return String.format(
          "Customer{id=%d, name='%s', email='%s', address='%s'}", id, name, email, address);
    }
  }

  /** Domain model: Aggregated sales statistics. */
  public record SalesStats(
      String category, Long orderCount, BigDecimal totalRevenue, BigDecimal averageOrderValue) {}

  /** Creates an H2 in-memory database data source. */
  private DataSource createDataSource() {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:typed_db;DB_CLOSE_DELAY=-1");
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
              + "status VARCHAR(20)"
              + ")");

      // Create products table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS products ("
              + "id INT PRIMARY KEY, "
              + "name VARCHAR(100) NOT NULL, "
              + "price DECIMAL(10,2), "
              + "stock INT, "
              + "category VARCHAR(50)"
              + ")");

      // Create orders table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS orders ("
              + "id INT PRIMARY KEY, "
              + "user_id INT, "
              + "product VARCHAR(100), "
              + "quantity INT, "
              + "amount DECIMAL(10,2), "
              + "order_date DATE"
              + ")");

      // Create customers table with address fields
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS customers ("
              + "id INT PRIMARY KEY, "
              + "name VARCHAR(100), "
              + "email VARCHAR(100), "
              + "street VARCHAR(200), "
              + "city VARCHAR(100), "
              + "zip_code VARCHAR(10)"
              + ")");

      // Insert users
      stmt.execute("INSERT INTO users VALUES (1, 'alice', 'alice@example.com', 'ACTIVE')");
      stmt.execute("INSERT INTO users VALUES (2, 'bob', 'bob@example.com', 'ACTIVE')");
      stmt.execute("INSERT INTO users VALUES (3, 'charlie', 'charlie@example.com', 'INACTIVE')");

      // Insert products
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99, 10, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (2, 'Mouse', 25.50, 50, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (3, 'Desk', 299.99, 5, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (4, 'Chair', 149.99, 15, 'Furniture')");

      // Insert orders
      stmt.execute("INSERT INTO orders VALUES (101, 1, 'Laptop', 1, 999.99, '2024-06-01')");
      stmt.execute("INSERT INTO orders VALUES (102, 1, 'Mouse', 2, 51.00, '2024-06-02')");
      stmt.execute("INSERT INTO orders VALUES (103, 2, 'Desk', 1, 299.99, '2024-06-03')");
      stmt.execute("INSERT INTO orders VALUES (104, 2, 'Chair', 2, 299.98, '2024-06-04')");

      // Insert customers
      stmt.execute(
          "INSERT INTO customers VALUES (1, 'John Doe', 'john@example.com', '123 Main St', 'New York', '10001')");
      stmt.execute(
          "INSERT INTO customers VALUES (2, 'Jane Smith', 'jane@example.com', '456 Oak Ave', 'Los Angeles', '90001')");
      stmt.execute(
          "INSERT INTO customers VALUES (3, 'Bob Johnson', 'bob@example.com', '789 Pine Rd', 'Chicago', '60601')");

      log.info("Database initialized with sample data");
    } catch (SQLException e) {
      log.error("Failed to initialize database", e);
      throw e;
    }
  }

  /**
   * Example 1: Simple mapping to a record. Demonstrates basic type-safe mapping using Java records.
   */
  public void example1SimpleRecordMapping() {
    log.info("\n=== Example 1: Simple Record Mapping ===");

    // Define row mapper for User record
    RowMapper<User> userMapper =
        (rs, _) ->
            new User(
                rs.getInt(ID), rs.getString(USERNAME), rs.getString(EMAIL), rs.getString(STATUS));

    JdbcTypedQueryTask<User> task =
        JdbcTypedQueryTask.<User>builder()
            .dataSource(dataSource)
            .sql("SELECT id, username, email, status FROM users WHERE status = ?")
            .params(List.of(ACTIVE))
            .rowMapper(userMapper)
            .writingResultsTo(ACTIVE_USERS)
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<User> activeUsers = (List<User>) context.get(ACTIVE_USERS);
    log.info("Found {} active users:", activeUsers.size());
    activeUsers.forEach(
        user ->
            log.info(
                "  User: id={}, username={}, email={}", user.id(), user.username(), user.email()));
  }

  /**
   * Example 2: Mapping to a POJO with complex fields. Demonstrates mapping with various data types
   * including BigDecimal.
   */
  public void example2POJOMapping() {
    log.info("\n=== Example 2: POJO Mapping with Complex Types ===");

    // Define row mapper for Product POJO
    RowMapper<Product> productMapper =
        (rs, _) ->
            new Product(
                rs.getInt(ID),
                rs.getString(NAME),
                rs.getBigDecimal(PRICE),
                rs.getInt(STOCK),
                rs.getString(CATEGORY));

    JdbcTypedQueryTask<Product> task =
        JdbcTypedQueryTask.<Product>builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, price, stock, category FROM products WHERE category = ?")
            .params(List.of("Electronics"))
            .rowMapper(productMapper)
            .writingResultsTo("electronicProducts")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Product> products = (List<Product>) context.get("electronicProducts");
    log.info("Found {} electronic products:", products.size());
    products.forEach(product -> log.info("  {}", product));
  }

  /**
   * Example 3: Reusable row mappers. Demonstrates creating and reusing mappers across multiple
   * queries.
   */
  public void example3ReusableMappers() {
    log.info("\n=== Example 3: Reusable Row Mappers ===");

    // Create reusable order mapper
    RowMapper<Order> orderMapper =
        (rs, _) ->
            new Order(
                rs.getInt(ID),
                rs.getInt("user_id"),
                rs.getString("product"),
                rs.getInt("quantity"),
                rs.getBigDecimal("amount"),
                rs.getDate("order_date").toLocalDate());

    // Query 1: Orders by user
    JdbcTypedQueryTask<Order> userOrdersTask =
        JdbcTypedQueryTask.<Order>builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM orders WHERE user_id = ?")
            .params(List.of(1))
            .rowMapper(orderMapper)
            .writingResultsTo("userOrders")
            .build();

    WorkflowContext context = new WorkflowContext();
    userOrdersTask.execute(context);

    @SuppressWarnings("unchecked")
    List<Order> userOrders = (List<Order>) context.get("userOrders");
    log.info("Orders for user 1:");
    userOrders.forEach(
        order ->
            log.info(
                "  Order {}: {} x{} = ${} on {}",
                order.id(),
                order.product(),
                order.quantity(),
                order.amount(),
                order.orderDate()));

    // Query 2: Recent orders (reusing same mapper)
    JdbcTypedQueryTask<Order> recentOrdersTask =
        JdbcTypedQueryTask.<Order>builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM orders WHERE order_date >= ? ORDER BY order_date DESC")
            .params(List.of(LocalDate.of(2024, 6, 2)))
            .rowMapper(orderMapper) // Reusing the same mapper!
            .writingResultsTo("recentOrders")
            .build();

    recentOrdersTask.execute(context);

    @SuppressWarnings("unchecked")
    List<Order> recentOrders = (List<Order>) context.get("recentOrders");
    log.info("\nRecent orders (since June 2):");
    recentOrders.forEach(
        order -> log.info("  Order {}: {} on {}", order.id(), order.product(), order.orderDate()));
  }

  /**
   * Example 4: Nested object mapping (composition). Demonstrates mapping rows to objects with
   * nested structures.
   */
  public void example4NestedObjectMapping() {
    log.info("\n=== Example 4: Nested Object Mapping ===");

    // Mapper with nested Address object
    RowMapper<Customer> customerMapper =
        (rs, _) -> {
          Address address =
              new Address(rs.getString("street"), rs.getString("city"), rs.getString("zip_code"));

          return new Customer(rs.getInt(ID), rs.getString(NAME), rs.getString(EMAIL), address);
        };

    JdbcTypedQueryTask<Customer> task =
        JdbcTypedQueryTask.<Customer>builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, email, street, city, zip_code FROM customers")
            .rowMapper(customerMapper)
            .writingResultsTo("customers")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Customer> customers = (List<Customer>) context.get("customers");
    log.info("Found {} customers:", customers.size());
    customers.forEach(customer -> log.info("  {}", customer));
  }

  /**
   * Example 5: Dynamic mapper from context. Demonstrates reading mapper from workflow context for
   * dynamic behavior.
   */
  public void example5DynamicMapper() {
    log.info("\n=== Example 5: Dynamic Mapper from Context ===");

    // Create different mappers for different scenarios
    RowMapper<User> basicMapper =
        (rs, _) ->
            new User(
                rs.getInt(ID), rs.getString(USERNAME), rs.getString(EMAIL), rs.getString(STATUS));

    JdbcTypedQueryTask<User> task =
        JdbcTypedQueryTask.<User>builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .readingRowMapperFrom("mapper")
            .writingResultsTo("results")
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("sql", "SELECT * FROM users WHERE status = ?");
    context.put("params", List.of(ACTIVE));
    context.put("mapper", basicMapper);

    task.execute(context);

    @SuppressWarnings("unchecked")
    List<User> results = (List<User>) context.get("results");
    log.info("Dynamic query results: {} users", results.size());
    results.forEach(user -> log.info("  {}", user));
  }

  /**
   * Example 6: Mapper with error handling and null safety. Demonstrates robust mapping with proper
   * error handling.
   */
  public void example6RobustMapper() {
    log.info("\n=== Example 6: Robust Mapper with Error Handling ===");

    // Mapper with null safety and error handling
    RowMapper<Product> robustMapper =
        (rs, rowNum) -> {
          try {
            Integer id = rs.getInt(ID);
            String name = rs.getString(NAME);

            // Handle potential nulls
            BigDecimal price = rs.getBigDecimal(PRICE);
            if (price == null) {
              price = BigDecimal.ZERO;
              log.warn("Null price for product {}, using 0.00", id);
            }

            int stock = rs.getInt(STOCK);
            if (rs.wasNull()) {
              stock = 0;
              log.warn("Null stock for product {}, using 0", id);
            }

            String category = rs.getString(CATEGORY);
            if (category == null) {
              category = "UNCATEGORIZED";
            }

            return new Product(id, name, price, stock, category);
          } catch (SQLException e) {
            log.error("Error mapping row {}: {}", rowNum, e.getMessage());
            throw e;
          }
        };

    JdbcTypedQueryTask<Product> task =
        JdbcTypedQueryTask.<Product>builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM products")
            .rowMapper(robustMapper)
            .writingResultsTo("allProducts")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<Product> products = (List<Product>) context.get("allProducts");
    log.info("Successfully mapped {} products with robust error handling", products.size());
  }

  /**
   * Example 7: Mapping aggregation results. Demonstrates mapping complex SQL aggregations to domain
   * objects.
   */
  public void example7AggregationMapping() {
    log.info("\n=== Example 7: Aggregation Mapping ===");

    // Mapper for aggregated sales statistics
    RowMapper<SalesStats> statsMapper =
        (rs, _) ->
            new SalesStats(
                rs.getString(CATEGORY),
                rs.getLong("order_count"),
                rs.getBigDecimal("total_revenue"),
                rs.getBigDecimal("avg_order_value"));

    String sql =
        "SELECT "
            + "  p.category, "
            + "  COUNT(o.id) as order_count, "
            + "  SUM(o.amount) as total_revenue, "
            + "  AVG(o.amount) as avg_order_value "
            + "FROM orders o "
            + "JOIN products p ON o.product = p.name "
            + "GROUP BY p.category "
            + "ORDER BY total_revenue DESC";

    JdbcTypedQueryTask<SalesStats> task =
        JdbcTypedQueryTask.<SalesStats>builder()
            .dataSource(dataSource)
            .sql(sql)
            .rowMapper(statsMapper)
            .writingResultsTo("salesStats")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    @SuppressWarnings("unchecked")
    List<SalesStats> stats = (List<SalesStats>) context.get("salesStats");
    log.info("Sales Statistics by Category:");
    stats.forEach(
        stat ->
            log.info(
                "  {}: {} orders, ${} total, ${} average",
                stat.category(),
                stat.orderCount(),
                stat.totalRevenue(),
                stat.averageOrderValue()));
  }

  /**
   * Bonus: Complete workflow with typed queries. Demonstrates building a complete workflow with
   * type-safe queries.
   */
  public void exampleBonusCompleteWorkflow() {
    log.info("\n=== Bonus: Complete Workflow with Typed Queries ===");

    // Mapper definitions
    RowMapper<User> userMapper =
        (rs, _) ->
            new User(
                rs.getInt(ID), rs.getString(USERNAME),
                rs.getString(EMAIL), rs.getString(STATUS));

    RowMapper<Order> orderMapper =
        (rs, _) ->
            new Order(
                rs.getInt(ID),
                rs.getInt("user_id"),
                rs.getString("product"),
                rs.getInt("quantity"),
                rs.getBigDecimal("amount"),
                rs.getDate("order_date").toLocalDate());

    // Task 1: Get active users
    JdbcTypedQueryTask<User> getActiveUsers =
        JdbcTypedQueryTask.<User>builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM users WHERE status = ?")
            .params(List.of(ACTIVE))
            .rowMapper(userMapper)
            .writingResultsTo(ACTIVE_USERS)
            .build();

    // Task 2: Get orders for active users
    JdbcTypedQueryTask<Order> getOrders =
        JdbcTypedQueryTask.<Order>builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM orders WHERE user_id IN (SELECT id FROM users WHERE status = ?)")
            .params(List.of(ACTIVE))
            .rowMapper(orderMapper)
            .writingResultsTo("activeUserOrders")
            .build();

    // Create workflow
    Workflow workflow =
        SequentialWorkflow.builder()
            .name("ActiveUserOrderReport")
            .task(getActiveUsers)
            .task(getOrders)
            .build();

    // Execute workflow
    WorkflowContext context = new WorkflowContext();
    var result = workflow.execute(context);

    log.info("Workflow execution status: {}", result.getStatus());

    @SuppressWarnings("unchecked")
    List<User> activeUsers = (List<User>) context.get(ACTIVE_USERS);

    @SuppressWarnings("unchecked")
    List<Order> orders = (List<Order>) context.get("activeUserOrders");

    log.info("\nActive User Order Report:");
    log.info("  Active users: {}", activeUsers.size());
    log.info("  Total orders: {}", orders.size());

    BigDecimal totalRevenue =
        orders.stream().map(Order::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

    log.info("  Total revenue: ${}", totalRevenue);

    log.info("\nOrders by user:");
    activeUsers.forEach(
        user -> {
          List<Order> userOrders =
              orders.stream().filter(o -> o.userId().equals(user.id())).toList();
          BigDecimal userTotal =
              userOrders.stream().map(Order::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
          log.info("  {}: {} orders, ${} total", user.username(), userOrders.size(), userTotal);
        });
  }

  /** Main method to run all examples. */
  static void main() throws SQLException {
    JdbcTypedQueryTaskExample example = new JdbcTypedQueryTaskExample();

    example.example1SimpleRecordMapping();
    example.example2POJOMapping();
    example.example3ReusableMappers();
    example.example4NestedObjectMapping();
    example.example5DynamicMapper();
    example.example6RobustMapper();
    example.example7AggregationMapping();
    example.exampleBonusCompleteWorkflow();

    log.info("\n=== All examples completed successfully! ===");
  }
}
