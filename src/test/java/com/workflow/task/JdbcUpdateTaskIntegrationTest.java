package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcUpdateTaskIntegrationTest {

  private static JdbcDataSource dataSource;
  private JdbcUpdateTask updateTask;

  @BeforeAll
  static void setupDatabase() throws Exception {
    // Initialize H2 DataSource
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    // Create a table and insert dummy data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(255), stock INT)");
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 10)");
    }
  }

  @AfterAll
  static void cleanupDatabase() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS products");
    }
  }

  @BeforeEach
  void setup() {
    updateTask =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("update_sql")
            .readingParamsFrom("update_params")
            .writingRowsAffectedTo("rows_count")
            .build();
  }

  @Test
  void execute_ShouldActuallyUpdateDatabase() throws Exception {
    // Arrange
    WorkflowContext context = new WorkflowContext();
    context.put("update_sql", "UPDATE products SET stock = ? WHERE name = ?");
    context.put("update_params", List.of(50, "Laptop"));

    // Act
    updateTask.execute(context);

    // Assert affected rows in context
    assertEquals(1, context.get("rows_count"), "Context should record 1 row affected");

    // Verify state in the actual database
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT stock FROM products WHERE id = 1")) {

      rs.next();
      assertEquals(50, rs.getInt("stock"), "Database stock should be updated to 50");
    }
  }

  @Test
  void execute_WhenNoRowsMatch_ShouldReturnZeroAffected() {
    // Arrange: SQL that targets a non-existent ID
    WorkflowContext context = new WorkflowContext();
    context.put("update_sql", "UPDATE products SET stock = 100 WHERE id = 999");
    context.put("update_params", List.of()); // Empty list or no params

    // Act
    updateTask.execute(context);

    // Assert
    assertEquals(
        0, context.get("rows_count"), "Should record 0 rows affected for non-matching criteria");
  }

  @Test
  void execute_WithEmptyParams_ShouldExecuteSuccessfully() throws Exception {
    // Arrange: Use a literal SQL statement with no placeholders
    WorkflowContext context = new WorkflowContext();
    context.put("update_sql", "DELETE FROM products WHERE id = 1");
    // "update_params" is missing, should trigger Collections.emptyList()

    // Act
    updateTask.execute(context);

    // Assert
    assertEquals(1, context.get("rows_count"));

    // Verify deletion
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products WHERE id = 1")) {
      rs.next();
      assertEquals(0, rs.getInt(1));
    }
  }

  @Test
  void execute_WithInvalidSql_ShouldThrowTaskExecutionException() {
    // Arrange: Table "non_existent" does not exist
    WorkflowContext context = new WorkflowContext();
    context.put("update_sql", "UPDATE non_existent SET stock = 5");
    context.put("update_params", List.of());

    // Act & Assert
    assertThrows(
        TaskExecutionException.class,
        () -> updateTask.execute(context),
        "Should wrap SQLException in TaskExecutionException");
  }

  @Test
  void execute_WithMixedParameterTypes_ShouldUpdateCorrectly() throws Exception {
    // Arrange
    WorkflowContext context = new WorkflowContext();
    // Inserting a new row to test multiple types (Int, String, Int)
    context.put("update_sql", "INSERT INTO products (id, name, stock) VALUES (?, ?, ?)");
    context.put("update_params", List.of(2, "Smartphone", 25));

    // Act
    updateTask.execute(context);

    // Assert
    assertEquals(1, context.get("rows_count"));

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("SELECT name FROM products WHERE id = ?")) {
      stmt.setInt(1, 2);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      assertEquals("Smartphone", rs.getString("name"));
    }
  }

  @Test
  void execute_MultiRowUpdate_ShouldReturnCorrectCount() throws Exception {
    // Arrange: Add more data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO products(id, name, stock) VALUES (3, 'Tablet', 10)");
      stmt.execute("INSERT INTO products(id, name, stock) VALUES (4, 'Monitor', 10)");
    }

    WorkflowContext context = new WorkflowContext();
    // Update all items where stock is 10
    context.put("update_sql", "UPDATE products SET stock = 0 WHERE stock = ?");
    context.put("update_params", List.of(10));

    // Act
    updateTask.execute(context);

    // Assert: We had Tablet(10), and Monitor(10)
    assertEquals(2, context.get("rows_count"), "Three rows should have been updated");
  }
}
