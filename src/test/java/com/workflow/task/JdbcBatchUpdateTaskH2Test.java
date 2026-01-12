package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

@DisplayName("JdbcBatchUpdateTask - H2 Integration Tests")
class JdbcBatchUpdateTaskH2Test {

  private static DataSource dataSource;
  private WorkflowContext context;

  @BeforeAll
  static void setupDatabase() throws Exception {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:batch_test;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    dataSource = ds;

    // Create test tables
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), age INT)");
      stmt.execute(
          "CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))");
      stmt.execute(
          "CREATE TABLE logs (id INT AUTO_INCREMENT PRIMARY KEY, level VARCHAR(20), message"
              + " VARCHAR(255))");
      stmt.execute(
          "CREATE TABLE orders (id INT PRIMARY KEY, user_id INT, total DECIMAL(10,2), status"
              + " VARCHAR(20))");
    }
  }

  @AfterAll
  static void cleanupDatabase() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS orders");
      stmt.execute("DROP TABLE IF EXISTS logs");
      stmt.execute("DROP TABLE IF EXISTS products");
      stmt.execute("DROP TABLE IF EXISTS users");
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    context = new WorkflowContext();

    // Clear tables before each test
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM users");
      stmt.execute("DELETE FROM products");
      stmt.execute("DELETE FROM logs");
      stmt.execute("DELETE FROM orders");
    }
  }

  @Test
  @DisplayName("Should insert multiple rows in batch")
  void testBatchInsert() throws Exception {
    // Arrange
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList(1, "Alice", "alice@example.com", 30),
            Arrays.asList(2, "Bob", "bob@example.com", 25),
            Arrays.asList(3, "Charlie", "charlie@example.com", 35));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name, email, age) VALUES (?, ?, ?, ?)")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertEquals(3, results.length);
    assertEquals(1, results[0]);
    assertEquals(1, results[1]);
    assertEquals(1, results[2]);

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
      assertTrue(rs.next());
      assertEquals(3, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should update multiple rows in batch")
  void testBatchUpdate() throws Exception {
    // Setup initial data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@old.com', 30)");
      stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@old.com', 25)");
      stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@old.com', 35)");
    }

    // Arrange
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList("alice@new.com", 1),
            Arrays.asList("bob@new.com", 2),
            Arrays.asList("charlie@new.com", 3));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET email = ? WHERE id = ?")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertArrayEquals(new int[] {1, 1, 1}, results);

    // Verify all emails updated
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT email FROM users ORDER BY id")) {
      assertTrue(rs.next());
      assertEquals("alice@new.com", rs.getString(1));
      assertTrue(rs.next());
      assertEquals("bob@new.com", rs.getString(1));
      assertTrue(rs.next());
      assertEquals("charlie@new.com", rs.getString(1));
    }
  }

  @Test
  @DisplayName("Should delete multiple rows in batch")
  void testBatchDelete() throws Exception {
    // Setup initial data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 30)");
      stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 25)");
      stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', 35)");
      stmt.execute("INSERT INTO users VALUES (4, 'David', 'david@example.com', 28)");
    }

    // Arrange
    List<List<Object>> batchData = Arrays.asList(List.of(1), List.of(3));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("DELETE FROM users WHERE id = ?")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertArrayEquals(new int[] {1, 1}, results);

    // Verify remaining users
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1)); // Only Bob and David remain
    }
  }

  @Test
  @DisplayName("Should handle empty batch")
  void testEmptyBatch() throws Exception {
    // Arrange
    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name, email, age) VALUES (?, ?, ?, ?)")
            .batchParams(Collections.emptyList())
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertEquals(0, results.length);

    // Verify no data inserted
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
      assertTrue(rs.next());
      assertEquals(0, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should handle batch with null values")
  void testBatchWithNullValues() throws Exception {
    // Arrange
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList(1, "Alice", null, 30), Arrays.asList(2, null, "bob@example.com", 25));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name, email, age) VALUES (?, ?, ?, ?)")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertArrayEquals(new int[] {1, 1}, results);

    // Verify null values
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet rs1 = stmt.executeQuery("SELECT email FROM users WHERE id = 1");
      assertTrue(rs1.next());
      assertNull(rs1.getString(1));

      ResultSet rs2 = stmt.executeQuery("SELECT name FROM users WHERE id = 2");
      assertTrue(rs2.next());
      assertNull(rs2.getString(1));
    }
  }

  @Test
  @DisplayName("Should handle large batch efficiently")
  void testLargeBatch() throws Exception {
    // Arrange - create 1000 row batch
    List<List<Object>> largeBatch = new java.util.ArrayList<>();
    for (int i = 1; i <= 1000; i++) {
      largeBatch.add(Arrays.asList(i, "User" + i, "user" + i + "@example.com", 20 + (i % 50)));
    }

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name, email, age) VALUES (?, ?, ?, ?)")
            .batchParams(largeBatch)
            .writingBatchResultsTo("results")
            .build();

    // Act
    long startTime = System.currentTimeMillis();
    task.execute(context);
    long endTime = System.currentTimeMillis();

    // Assert
    int[] results = (int[]) context.get("results");
    assertEquals(1000, results.length);

    // Verify all inserted
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
      assertTrue(rs.next());
      assertEquals(1000, rs.getInt(1));
    }

    // Performance check - should be reasonably fast (< 2 seconds)
    assertTrue(
        (endTime - startTime) < 2000,
        "Batch of 1000 should complete in under 2 seconds, took: " + (endTime - startTime) + "ms");
  }

  @Test
  @DisplayName("Should use context mode for SQL and params")
  void testContextMode() throws Exception {
    // Arrange
    context.put("batchSql", "INSERT INTO products (id, name, price) VALUES (?, ?, ?)");
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList(1, "Laptop", 999.99),
            Arrays.asList(2, "Mouse", 29.99),
            Arrays.asList(3, "Keyboard", 79.99));
    context.put("batchData", batchData);

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("batchSql")
            .readingBatchParamsFrom("batchData")
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertArrayEquals(new int[] {1, 1, 1}, results);

    // Verify data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products")) {
      assertTrue(rs.next());
      assertEquals(3, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should handle mixed success - some updates match, some don't")
  void testMixedUpdateResults() throws Exception {
    // Setup initial data - only ids 1 and 2 exist
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 30)");
      stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 25)");
    }

    // Arrange - try to update ids 1, 2, and 999 (doesn't exist)
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList("Alice Updated", 1),
            Arrays.asList("Bob Updated", 2),
            Arrays.asList("Nobody", 999));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertEquals(3, results.length);
    assertEquals(1, results[0]); // Alice updated
    assertEquals(1, results[1]); // Bob updated
    assertEquals(0, results[2]); // Nobody updated (id 999 doesn't exist)
  }

  @Test
  @DisplayName("Should handle decimal values correctly")
  void testDecimalValues() throws Exception {
    // Arrange
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList(1, "Product A", 99.99),
            Arrays.asList(2, "Product B", 149.50),
            Arrays.asList(3, "Product C", 1299.99));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO products (id, name, price) VALUES (?, ?, ?)")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT price FROM products ORDER BY id")) {
      assertTrue(rs.next());
      assertEquals(99.99, rs.getDouble(1), 0.01);
      assertTrue(rs.next());
      assertEquals(149.50, rs.getDouble(1), 0.01);
      assertTrue(rs.next());
      assertEquals(1299.99, rs.getDouble(1), 0.01);
    }
  }

  @Test
  @DisplayName("Should handle auto-increment columns")
  void testAutoIncrementBatch() throws Exception {
    // Arrange
    List<List<Object>> batchData =
        Arrays.asList(
            Arrays.asList("INFO", "Application started"),
            Arrays.asList("DEBUG", "Processing request"),
            Arrays.asList("ERROR", "Connection failed"),
            Arrays.asList("WARN", "Memory usage high"));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO logs (level, message) VALUES (?, ?)")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertArrayEquals(new int[] {1, 1, 1, 1}, results);

    // Verify auto-increment worked
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT id, level FROM logs ORDER BY id")) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt("id"));
      assertEquals("INFO", rs.getString("level"));

      assertTrue(rs.next());
      assertEquals(2, rs.getInt("id"));
      assertEquals("DEBUG", rs.getString("level"));

      assertTrue(rs.next());
      assertEquals(3, rs.getInt("id"));
      assertEquals("ERROR", rs.getString("level"));

      assertTrue(rs.next());
      assertEquals(4, rs.getInt("id"));
      assertEquals("WARN", rs.getString("level"));
    }
  }

  @Test
  @DisplayName("Should perform ETL-like operation with batch")
  void testETLScenario() throws Exception {
    // Scenario: Load orders from external source (simulated)
    List<List<Object>> orderData =
        Arrays.asList(
            Arrays.asList(1001, 1, 149.99, "PENDING"),
            Arrays.asList(1002, 2, 299.50, "PENDING"),
            Arrays.asList(1003, 1, 79.99, "PENDING"),
            Arrays.asList(1004, 3, 499.99, "PENDING"),
            Arrays.asList(1005, 2, 199.00, "PENDING"));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO orders (id, user_id, total, status) VALUES (?, ?, ?, ?)")
            .batchParams(orderData)
            .writingBatchResultsTo("loadResults")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("loadResults");
    assertEquals(5, results.length);

    // Verify total count and sum
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet rs1 = stmt.executeQuery("SELECT COUNT(*) FROM orders");
      assertTrue(rs1.next());
      assertEquals(5, rs1.getInt(1));

      ResultSet rs2 = stmt.executeQuery("SELECT SUM(total) FROM orders");
      assertTrue(rs2.next());
      assertEquals(1228.47, rs2.getDouble(1), 0.01); // 149.99 + 299.50 + 79.99 + 499.99 + 199.00
    }
  }

  @Test
  @DisplayName("Should handle single batch parameter")
  void testSingleBatchEntry() {
    // Arrange
    List<List<Object>> batchData =
        Collections.singletonList(Arrays.asList(1, "Alice", "alice@example.com", 30));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name, email, age) VALUES (?, ?, ?, ?)")
            .batchParams(batchData)
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertEquals(1, results.length);
    assertEquals(1, results[0]);
  }
}
