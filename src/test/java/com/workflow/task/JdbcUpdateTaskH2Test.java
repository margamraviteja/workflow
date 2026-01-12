package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

@DisplayName("JdbcUpdateTask - H2 Integration Tests")
class JdbcUpdateTaskH2Test {

  private static DataSource dataSource;
  private WorkflowContext context;

  @BeforeAll
  static void setupDatabase() throws Exception {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:update_test;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    dataSource = ds;

    // Create test tables
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), status"
              + " VARCHAR(20), age INT)");
      stmt.execute(
          "CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2),"
              + " quantity INT)");
      stmt.execute(
          "CREATE TABLE audit_log (id INT AUTO_INCREMENT PRIMARY KEY, message VARCHAR(255))");
    }
  }

  @AfterAll
  static void cleanupDatabase() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS audit_log");
      stmt.execute("DROP TABLE IF EXISTS products");
      stmt.execute("DROP TABLE IF EXISTS users");
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    context = new WorkflowContext();

    // Clear and seed data before each test
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM users");
      stmt.execute("DELETE FROM products");
      stmt.execute("DELETE FROM audit_log");

      // Seed users
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 'ACTIVE', 30)");
      stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 'ACTIVE', 25)");
      stmt.execute(
          "INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', 'INACTIVE', 35)");

      // Seed products
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99, 10)");
      stmt.execute("INSERT INTO products VALUES (2, 'Mouse', 29.99, 50)");
      stmt.execute("INSERT INTO products VALUES (3, 'Keyboard', 79.99, 30)");
    }
  }

  @Test
  @DisplayName("Should update single row")
  void testUpdateSingleRow() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .params(Arrays.asList("Alice Smith", 1))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("updated"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
      assertTrue(rs.next());
      assertEquals("Alice Smith", rs.getString("name"));
    }
  }

  @Test
  @DisplayName("Should update multiple rows")
  void testUpdateMultipleRows() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET status = ? WHERE status = ?")
            .params(Arrays.asList("SUSPENDED", "ACTIVE"))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(2, context.get("updated")); // Alice and Bob

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE status = 'SUSPENDED'")) {
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should insert new row")
  void testInsert() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name, email, status, age) VALUES (?, ?, ?, ?, ?)")
            .params(Arrays.asList(4, "David", "david@example.com", "ACTIVE", 28))
            .writingRowsAffectedTo("inserted")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("inserted"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 4")) {
      assertTrue(rs.next());
      assertEquals("David", rs.getString("name"));
      assertEquals("david@example.com", rs.getString("email"));
      assertEquals(28, rs.getInt("age"));
    }
  }

  @Test
  @DisplayName("Should delete rows")
  void testDelete() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("DELETE FROM users WHERE status = ?")
            .params(List.of("INACTIVE"))
            .writingRowsAffectedTo("deleted")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("deleted"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1)); // Only Alice and Bob remain
    }
  }

  @Test
  @DisplayName("Should return 0 when no rows match")
  void testUpdateNoMatch() {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .params(Arrays.asList("Nobody", 999))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(0, context.get("updated"));
  }

  @Test
  @DisplayName("Should update with null value")
  void testUpdateWithNull() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET email = ? WHERE id = ?")
            .params(Arrays.asList(null, 1))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("updated"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT email FROM users WHERE id = 1")) {
      assertTrue(rs.next());
      assertNull(rs.getString("email"));
    }
  }

  @Test
  @DisplayName("Should handle complex WHERE clause")
  void testComplexWhereClause() {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET status = ? WHERE age > ? AND status = ?")
            .params(Arrays.asList("SENIOR", 30, "ACTIVE"))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert - No users with age > 30 and ACTIVE status initially
    assertEquals(0, context.get("updated"));
  }

  @Test
  @DisplayName("Should update decimal values")
  void testUpdateDecimal() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE products SET price = ? WHERE id = ?")
            .params(Arrays.asList(899.99, 1))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("updated"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT price FROM products WHERE id = 1")) {
      assertTrue(rs.next());
      assertEquals(899.99, rs.getDouble("price"), 0.01);
    }
  }

  @Test
  @DisplayName("Should use context mode for SQL and params")
  void testContextMode() throws Exception {
    // Arrange
    context.put("updateSql", "UPDATE users SET status = ? WHERE id = ?");
    context.put("updateParams", Arrays.asList("PENDING", 2));

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("updateSql")
            .readingParamsFrom("updateParams")
            .writingRowsAffectedTo("result")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("result"));

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT status FROM users WHERE id = 2")) {
      assertTrue(rs.next());
      assertEquals("PENDING", rs.getString("status"));
    }
  }

  @Test
  @DisplayName("Should handle auto-increment insert")
  void testAutoIncrementInsert() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO audit_log (message) VALUES (?)")
            .params(List.of("User logged in"))
            .writingRowsAffectedTo("inserted")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("inserted"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery("SELECT COUNT(*) FROM audit_log WHERE message = 'User logged in'")) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should perform multiple sequential updates")
  void testSequentialUpdates() throws Exception {
    // Arrange
    JdbcUpdateTask task1 =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE products SET quantity = quantity - ? WHERE id = ?")
            .params(Arrays.asList(5, 1))
            .writingRowsAffectedTo("update1")
            .build();

    JdbcUpdateTask task2 =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE products SET quantity = quantity - ? WHERE id = ?")
            .params(Arrays.asList(10, 2))
            .writingRowsAffectedTo("update2")
            .build();

    // Act
    task1.execute(context);
    task2.execute(context);

    // Assert
    assertEquals(1, context.get("update1"));
    assertEquals(1, context.get("update2"));

    // Verify database state
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet rs1 = stmt.executeQuery("SELECT quantity FROM products WHERE id = 1");
      assertTrue(rs1.next());
      assertEquals(5, rs1.getInt(1)); // 10 - 5

      ResultSet rs2 = stmt.executeQuery("SELECT quantity FROM products WHERE id = 2");
      assertTrue(rs2.next());
      assertEquals(40, rs2.getInt(1)); // 50 - 10
    }
  }

  @Test
  @DisplayName("Should handle empty string parameter")
  void testEmptyStringParameter() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .params(Arrays.asList("", 1))
            .writingRowsAffectedTo("updated")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, context.get("updated"));

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
      assertTrue(rs.next());
      assertEquals("", rs.getString("name"));
    }
  }
}
