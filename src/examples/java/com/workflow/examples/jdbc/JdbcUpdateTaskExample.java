package com.workflow.examples.jdbc;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.task.JdbcQueryTask;
import com.workflow.task.JdbcUpdateTask;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Comprehensive examples demonstrating JdbcUpdateTask usage with an in-memory H2 database.
 *
 * <p>This class shows examples from simple to complex:
 *
 * <ul>
 *   <li>Example 1: Simple INSERT statement
 *   <li>Example 2: Simple UPDATE statement
 *   <li>Example 3: Simple DELETE statement
 *   <li>Example 4: INSERT with multiple parameters
 *   <li>Example 5: UPDATE with complex conditions
 *   <li>Example 6: Dynamic SQL from context
 *   <li>Example 7: DDL operations (CREATE, ALTER, DROP)
 * </ul>
 */
@Slf4j
public class JdbcUpdateTaskExample {

  public static final String ELECTRONICS = "Electronics";
  public static final String UPDATE_SQL = "updateSql";
  public static final String UPDATE_PARAMS = "updateParams";
  public static final String ROWS_AFFECTED = "rowsAffected";
  public static final String RESULT = "result";
  private final DataSource dataSource;

  public JdbcUpdateTaskExample() throws SQLException {
    this.dataSource = createDataSource();
    initializeDatabase();
  }

  /** Creates an H2 in-memory database data source. */
  private DataSource createDataSource() {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:update_db;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  /** Initializes the database schema and populates sample data. */
  private void initializeDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      // Create products table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS products ("
              + "id INT PRIMARY KEY AUTO_INCREMENT, "
              + "name VARCHAR(100) NOT NULL, "
              + "price DECIMAL(10,2), "
              + "stock INT, "
              + "category VARCHAR(50), "
              + "status VARCHAR(20) DEFAULT 'ACTIVE'"
              + ")");

      // Create customers table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS customers ("
              + "id INT PRIMARY KEY AUTO_INCREMENT, "
              + "name VARCHAR(100) NOT NULL, "
              + "email VARCHAR(100) UNIQUE, "
              + "loyalty_points INT DEFAULT 0, "
              + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
              + ")");

      // Insert sample products
      stmt.execute(
          "INSERT INTO products (name, price, stock, category) VALUES ('Laptop', 999.99, 10, 'Electronics')");
      stmt.execute(
          "INSERT INTO products (name, price, stock, category) VALUES ('Mouse', 25.50, 50, 'Electronics')");
      stmt.execute(
          "INSERT INTO products (name, price, stock, category) VALUES ('Desk', 299.99, 5, 'Furniture')");
      stmt.execute(
          "INSERT INTO products (name, price, stock, category) VALUES ('Chair', 149.99, 15, 'Furniture')");

      // Insert sample customers
      stmt.execute(
          "INSERT INTO customers (name, email, loyalty_points) VALUES ('Alice Johnson', 'alice@example.com', 100)");
      stmt.execute(
          "INSERT INTO customers (name, email, loyalty_points) VALUES ('Bob Smith', 'bob@example.com', 250)");

      log.info("Database initialized with sample data");
    } catch (SQLException e) {
      log.error("Failed to initialize database", e);
      throw e;
    }
  }

  /** Example 1: Simple INSERT statement. Demonstrates inserting a single row with parameters. */
  public void example1SimpleInsert() {
    log.info("\n=== Example 1: Simple INSERT ===");

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO products (name, price, stock, category) VALUES (?, ?, ?, ?)")
            .params(Arrays.asList("Monitor", 299.99, 8, ELECTRONICS))
            .writingRowsAffectedTo("insertedRows")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    Integer rowsAffected = (Integer) context.get("insertedRows");
    log.info("Inserted {} row(s)", rowsAffected);

    // Verify insertion
    verifyProducts("Monitor");
  }

  /** Example 2: Simple UPDATE statement. Demonstrates updating rows based on a condition. */
  public void example2SimpleUpdate() {
    log.info("\n=== Example 2: Simple UPDATE ===");

    // Update price of a specific product
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE products SET price = ? WHERE name = ?")
            .params(Arrays.asList(899.99, "Laptop"))
            .writingRowsAffectedTo("updatedRows")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    Integer rowsAffected = (Integer) context.get("updatedRows");
    log.info("Updated {} row(s)", rowsAffected);

    // Verify update
    verifyProducts("Laptop");
  }

  /** Example 3: Simple DELETE statement. Demonstrates deleting rows based on a condition. */
  public void example3SimpleDelete() {
    log.info("\n=== Example 3: Simple DELETE ===");

    // First, insert a product to delete
    JdbcUpdateTask insertTask =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO products (name, price, stock, category) VALUES (?, ?, ?, ?)")
            .params(Arrays.asList("Old Product", 10.00, 1, "Obsolete"))
            .writingRowsAffectedTo("output")
            .build();
    insertTask.execute(new WorkflowContext());

    // Now delete it
    JdbcUpdateTask deleteTask =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("DELETE FROM products WHERE category = ?")
            .params(List.of("Obsolete"))
            .writingRowsAffectedTo("deletedRows")
            .build();

    WorkflowContext context = new WorkflowContext();
    deleteTask.execute(context);

    Integer rowsAffected = (Integer) context.get("deletedRows");
    log.info("Deleted {} row(s)", rowsAffected);
  }

  /**
   * Example 4: INSERT with multiple parameters. Demonstrates inserting a customer with all fields.
   */
  public void example4InsertWithMultipleParams() {
    log.info("\n=== Example 4: INSERT with Multiple Parameters ===");

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO customers (name, email, loyalty_points) VALUES (?, ?, ?)")
            .params(Arrays.asList("Charlie Brown", "charlie@example.com", 150))
            .writingRowsAffectedTo("insertedCustomers")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    Integer rowsAffected = (Integer) context.get("insertedCustomers");
    log.info("Inserted {} customer(s)", rowsAffected);

    // Verify insertion
    verifyCustomers("Charlie Brown");
  }

  /**
   * Example 5: UPDATE with complex conditions. Demonstrates updating multiple columns with multiple
   * WHERE conditions.
   */
  public void example5ComplexUpdate() {
    log.info("\n=== Example 5: Complex UPDATE ===");

    // Update stock and status for products in a specific category with low stock
    String sql =
        "UPDATE products SET stock = stock + ?, status = ? " + "WHERE category = ? AND stock < ?";

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(Arrays.asList(20, "RESTOCKED", ELECTRONICS, 15))
            .writingRowsAffectedTo("updatedProducts")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    Integer rowsAffected = (Integer) context.get("updatedProducts");
    log.info("Updated {} product(s)", rowsAffected);

    // Verify updates
    verifyProductsByCategory(ELECTRONICS);
  }

  /**
   * Example 6: Dynamic SQL from context. Demonstrates reading SQL and parameters from workflow
   * context at runtime.
   */
  public void example6DynamicUpdate() {
    log.info("\n=== Example 6: Dynamic UPDATE from Context ===");

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(UPDATE_SQL)
            .readingParamsFrom(UPDATE_PARAMS)
            .writingRowsAffectedTo(ROWS_AFFECTED)
            .build();

    WorkflowContext context = new WorkflowContext();

    // Scenario 1: Increase loyalty points
    context.put(
        UPDATE_SQL, "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE email = ?");
    context.put(UPDATE_PARAMS, Arrays.asList(50, "alice@example.com"));

    task.execute(context);
    log.info("Scenario 1 - Updated {} row(s)", context.get(ROWS_AFFECTED));
    verifyCustomers("Alice Johnson");

    // Scenario 2: Update product price by percentage
    context.put(UPDATE_SQL, "UPDATE products SET price = price * ? WHERE category = ?");
    context.put(UPDATE_PARAMS, Arrays.asList(1.10, "Furniture")); // 10% increase

    task.execute(context);
    log.info("Scenario 2 - Updated {} row(s)", context.get(ROWS_AFFECTED));
    verifyProductsByCategory("Furniture");
  }

  /**
   * Example 7: DDL operations (CREATE, ALTER, DROP). Demonstrates using JdbcUpdateTask for schema
   * modifications.
   */
  public void example7DDLOperations() {
    log.info("\n=== Example 7: DDL Operations ===");

    WorkflowContext context = new WorkflowContext();

    // Create a temporary table
    JdbcUpdateTask createTable =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql(
                "CREATE TABLE temp_inventory (id INT PRIMARY KEY, item_name VARCHAR(100), quantity INT)")
            .writingRowsAffectedTo("createResult")
            .build();

    createTable.execute(context);
    log.info("Created temp_inventory table (result: {})", context.get("createResult"));

    // Alter table to add a column
    JdbcUpdateTask alterTable =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("ALTER TABLE temp_inventory ADD COLUMN location VARCHAR(50)")
            .writingRowsAffectedTo("alterResult")
            .build();

    alterTable.execute(context);
    log.info("Altered temp_inventory table (result: {})", context.get("alterResult"));

    // Insert data into temp table
    JdbcUpdateTask insertData =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO temp_inventory VALUES (?, ?, ?, ?)")
            .params(Arrays.asList(1, "Widget", 100, "Warehouse A"))
            .writingRowsAffectedTo("insertResult")
            .build();

    insertData.execute(context);
    log.info("Inserted {} row(s) into temp_inventory", context.get("insertResult"));

    // Drop the temporary table
    JdbcUpdateTask dropTable =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("DROP TABLE temp_inventory")
            .writingRowsAffectedTo("dropResult")
            .build();

    dropTable.execute(context);
    log.info("Dropped temp_inventory table (result: {})", context.get("dropResult"));
  }

  /**
   * Bonus: Using JdbcUpdateTask in a Workflow with conditional logic. Demonstrates integration into
   * a complete workflow with query and update.
   */
  public void exampleBonusWorkflowIntegration() {
    log.info("\n=== Bonus: Workflow Integration ===");

    // Task 1: Check current stock levels
    JdbcQueryTask checkStock =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT name, stock FROM products WHERE stock < ?")
            .params(List.of(10))
            .writingResultsTo("lowStockProducts")
            .build();

    // Task 2: Update status of low stock products
    JdbcUpdateTask updateStatus =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE products SET status = ? WHERE stock < ?")
            .params(Arrays.asList("LOW_STOCK", 10))
            .writingRowsAffectedTo("updatedCount")
            .build();

    // Task 3: Reorder products (increase stock)
    JdbcUpdateTask reorderProducts =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE products SET stock = stock + ? WHERE stock < ?")
            .params(Arrays.asList(25, 10))
            .writingRowsAffectedTo("reorderedCount")
            .build();

    // Create workflow
    Workflow workflow =
        SequentialWorkflow.builder()
            .name("InventoryManagement")
            .task(checkStock)
            .task(updateStatus)
            .task(reorderProducts)
            .build();

    // Execute workflow
    WorkflowContext context = new WorkflowContext();
    var result = workflow.execute(context);

    log.info("Workflow execution status: {}", result.getStatus());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lowStock =
        (List<Map<String, Object>>) context.get("lowStockProducts");
    Integer updatedCount = (Integer) context.get("updatedCount");
    Integer reorderedCount = (Integer) context.get("reorderedCount");

    log.info("Found {} low stock products", lowStock.size());
    log.info("Updated status for {} products", updatedCount);
    log.info("Reordered {} products", reorderedCount);

    // Verify final state
    verifyAllProducts();
  }

  // Helper methods to verify changes
  private void verifyProducts(String productName) {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM products WHERE name = ?")
            .params(Collections.singletonList(productName))
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Verification - Product '{}': {}", productName, result);
  }

  private void verifyProductsByCategory(String category) {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT name, price, stock, status FROM products WHERE category = ?")
            .params(Collections.singletonList(category))
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Verification - Products in '{}' category:", category);
    result.forEach(
        p ->
            log.info(
                "  {}: ${}, Stock: {}, Status: {}",
                p.get("NAME"),
                p.get("PRICE"),
                p.get("STOCK"),
                p.get("STATUS")));
  }

  private void verifyCustomers(String customerName) {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM customers WHERE name = ?")
            .params(Collections.singletonList(customerName))
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Verification - Customer '{}': {}", customerName, result);
  }

  private void verifyAllProducts() {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT name, price, stock, status FROM products ORDER BY name")
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("All products:");
    result.forEach(
        p ->
            log.info(
                "  {}: ${}, Stock: {}, Status: {}",
                p.get("NAME"),
                p.get("PRICE"),
                p.get("STOCK"),
                p.get("STATUS")));
  }

  /** Main method to run all examples. */
  public static void main(String[] args) throws SQLException {
    JdbcUpdateTaskExample example = new JdbcUpdateTaskExample();

    example.example1SimpleInsert();
    example.example2SimpleUpdate();
    example.example3SimpleDelete();
    example.example4InsertWithMultipleParams();
    example.example5ComplexUpdate();
    example.example6DynamicUpdate();
    example.example7DDLOperations();
    example.exampleBonusWorkflowIntegration();

    log.info("\n=== All examples completed successfully! ===");
  }
}
