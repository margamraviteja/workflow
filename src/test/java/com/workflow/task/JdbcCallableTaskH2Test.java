package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.context.WorkflowContext;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

@DisplayName("JdbcCallableTask - H2 Integration Tests")
class JdbcCallableTaskH2Test {

  private static DataSource dataSource;
  private WorkflowContext context;

  @BeforeAll
  static void setupDatabase() throws Exception {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:callable_test;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    dataSource = ds;

    // Create test tables
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), balance"
              + " DECIMAL(10,2))");
      stmt.execute(
          "CREATE TABLE orders (id INT PRIMARY KEY, user_id INT, total DECIMAL(10,2), status"
              + " VARCHAR(20))");
      stmt.execute(
          "CREATE TABLE audit_log (id INT AUTO_INCREMENT PRIMARY KEY, message VARCHAR(255),"
              + " timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

      // Create stored procedures and functions
      createStoredProcedures(stmt);
    }
  }

  private static void createStoredProcedures(Statement stmt) throws SQLException {
    // 1. Simple procedure
    // Note: H2 expects just the method body or a very specific signature format.
    stmt.execute(
        "CREATE ALIAS update_user_balance AS $$ "
            + "void updateBalance(Connection conn, int userId, Double amount) throws SQLException {"
            + "  PreparedStatement ps = conn.prepareStatement(\"UPDATE users SET balance = ? WHERE id = ?\");"
            + "  if (amount == null) {"
            + "    ps.setNull(1, java.sql.Types.DECIMAL);"
            + "  } else {"
            + "    ps.setDouble(1, amount);"
            + "  }"
            + "  ps.setInt(2, userId);"
            + "  ps.executeUpdate();"
            + "} $$");

    // 2. Procedure with result set
    stmt.execute(
        "CREATE ALIAS get_user_count AS $$ "
            + "java.sql.ResultSet getUserCount(Connection conn) throws SQLException {"
            + "  return conn.createStatement().executeQuery(\"SELECT COUNT(*) as count FROM users\");"
            + "} $$");

    // 3. Get active users
    stmt.execute(
        "CREATE ALIAS get_active_users AS $$ "
            + "java.sql.ResultSet getActiveUsers(Connection conn) throws SQLException {"
            + "  return conn.createStatement().executeQuery(\"SELECT id, name, email FROM users WHERE balance > 0 ORDER BY id\");"
            + "} $$");

    // 4. Get user details
    stmt.execute(
        "CREATE ALIAS get_user_details AS $$ "
            + "java.sql.ResultSet getUserDetails(Connection conn, int userId) throws SQLException {"
            + "  PreparedStatement ps = conn.prepareStatement(\"SELECT * FROM users WHERE id = ?\");"
            + "  ps.setInt(1, userId);"
            + "  return ps.executeQuery();"
            + "} $$");

    // 5. Audit log
    stmt.execute(
        "CREATE ALIAS add_audit_log AS $$ "
            + "void addLog(Connection conn, String message) throws SQLException {"
            + "  PreparedStatement ps = conn.prepareStatement(\"INSERT INTO audit_log (message) VALUES (?)\");"
            + "  ps.setString(1, message);"
            + "  ps.executeUpdate();"
            + "} $$");
  }

  @AfterAll
  static void cleanupDatabase() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop stored procedures/functions
      stmt.execute("DROP ALIAS IF EXISTS update_user_balance");
      stmt.execute("DROP ALIAS IF EXISTS get_user_count");
      stmt.execute("DROP ALIAS IF EXISTS get_active_users");
      stmt.execute("DROP ALIAS IF EXISTS get_user_details");
      stmt.execute("DROP ALIAS IF EXISTS add_audit_log");
      stmt.execute("DROP ALIAS IF EXISTS test_types");

      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS audit_log");
      stmt.execute("DROP TABLE IF EXISTS orders");
      stmt.execute("DROP TABLE IF EXISTS users");
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    context = new WorkflowContext();

    // Clear and seed data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM users");
      stmt.execute("DELETE FROM orders");
      stmt.execute("DELETE FROM audit_log");

      // Seed users
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 1000.00)");
      stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 500.00)");
      stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', 0.00)");

      // Seed orders
      stmt.execute("INSERT INTO orders VALUES (1, 1, 150.00, 'COMPLETED')");
      stmt.execute("INSERT INTO orders VALUES (2, 1, 75.50, 'PENDING')");
      stmt.execute("INSERT INTO orders VALUES (3, 2, 200.00, 'COMPLETED')");
    }
  }

  @Test
  @DisplayName("Should execute simple procedure with IN parameters")
  void testSimpleProcedureWithInParams() throws Exception {
    // Arrange
    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, 1);
    inParams.put(2, 1500.00);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call update_user_balance(?, ?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert - verify balance updated
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE id = 1");
        ResultSet rs = ps.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(1500.00, rs.getDouble("balance"), 0.01);
    }
  }

  @Test
  @DisplayName("Should execute procedure that returns result set")
  void testProcedureReturningResultSet() throws Exception {
    // Arrange
    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_active_users()}")
            .writingResultSetsTo("users")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results = context.getTyped("users", new TypeReference<>() {});
    assertNotNull(results);
    assertEquals(1, results.size()); // One result set

    List<Map<String, Object>> userList = results.getFirst();
    assertEquals(2, userList.size()); // Alice and Bob have balance > 0

    assertEquals(1, userList.get(0).get("ID"));
    assertEquals("Alice", userList.get(0).get("NAME"));
    assertEquals(2, userList.get(1).get("ID"));
    assertEquals("Bob", userList.get(1).get("NAME"));
  }

  @Test
  @DisplayName("Should execute procedure with parameter returning result set")
  void testProcedureWithParamReturningResultSet() throws Exception {
    // Arrange
    Map<Integer, Object> inParams = Map.of(1, 1);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_user_details(?)}")
            .inParameters(inParams)
            .writingResultSetsTo("userDetails")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results =
        context.getTyped("userDetails", new TypeReference<>() {});
    assertEquals(1, results.size());

    List<Map<String, Object>> userList = results.getFirst();
    assertEquals(1, userList.size());

    Map<String, Object> user = userList.getFirst();
    assertEquals(1, user.get("ID"));
    assertEquals("Alice", user.get("NAME"));
    assertEquals("alice@example.com", user.get("EMAIL"));
    assertEquals(1000.00, ((Number) user.get("BALANCE")).doubleValue(), 0.01);
  }

  @Test
  @DisplayName("Should use context mode for call and parameters")
  void testContextMode() throws Exception {
    // Arrange
    context.put("procCall", "{call update_user_balance(?, ?)}");

    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, 2);
    inParams.put(2, 750.00);
    context.put("inParams", inParams);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .readingCallFrom("procCall")
            .readingInParametersFrom("inParams")
            .build();

    // Act
    task.execute(context);

    // Assert
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE id = 2");
        ResultSet rs = ps.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(750.00, rs.getDouble("balance"), 0.01);
    }
  }

  @Test
  @DisplayName("Should execute audit log procedure")
  void testAuditLogProcedure() throws Exception {
    // Arrange
    Map<Integer, Object> inParams = Map.of(1, "User login successful");

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call add_audit_log(?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT message FROM audit_log")) {
      assertTrue(rs.next());
      assertEquals("User login successful", rs.getString("message"));
    }
  }

  @Test
  @DisplayName("Should handle procedure with no parameters")
  void testProcedureWithNoParams() throws Exception {
    // Arrange
    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_active_users()}")
            .writingResultSetsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results = context.getTyped("results", new TypeReference<>() {});
    assertNotNull(results);
    assertFalse(results.isEmpty());
  }

  @Test
  @DisplayName("Should handle procedure that returns empty result set")
  void testProcedureReturningEmptyResultSet() throws Exception {
    // Clear all users
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM users");
    }

    // Arrange
    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_active_users()}")
            .writingResultSetsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results = context.getTyped("results", new TypeReference<>() {});
    assertEquals(1, results.size());
    assertEquals(0, results.getFirst().size());
  }

  @Test
  @DisplayName("Should execute multiple procedures sequentially")
  void testMultipleProceduresSequentially() throws Exception {
    // Arrange
    JdbcCallableTask task1 =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call add_audit_log(?)}")
            .inParameters(Map.of(1, "First log"))
            .build();

    JdbcCallableTask task2 =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call add_audit_log(?)}")
            .inParameters(Map.of(1, "Second log"))
            .build();

    JdbcCallableTask task3 =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call add_audit_log(?)}")
            .inParameters(Map.of(1, "Third log"))
            .build();

    // Act
    task1.execute(context);
    task2.execute(context);
    task3.execute(context);

    // Assert
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM audit_log")) {
      assertTrue(rs.next());
      assertEquals(3, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should handle null parameter values")
  void testNullParameterValues() throws Exception {
    // 1. Ensure user exists specifically for this test
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "INSERT INTO users (id, name, email, balance) VALUES (99, 'NullTest', 'null@test.com', 100.00)");
    }

    // 2. Arrange - update balance to null
    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, 99); // Use the ID we just created
    inParams.put(2, null);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call update_user_balance(?, ?)}")
            .inParameters(inParams)
            .build();

    // 3. Act
    task.execute(context);

    // 4. Assert
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE id = 99");
        ResultSet rs = ps.executeQuery()) {
      assertTrue(rs.next(), "User 99 should exist");
      assertNull(rs.getObject("balance"), "Balance should have been updated to null");
    }
  }

  @Test
  @DisplayName("Should handle different data types")
  void testDifferentDataTypes() throws Exception {
    // Create procedure that handles different types
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE ALIAS test_types AS $$ "
              + "void testTypes(Connection conn, int intVal, double doubleVal, String stringVal) throws SQLException {"
              + "  PreparedStatement ps = conn.prepareStatement(\"INSERT INTO audit_log (message) VALUES (?)\");"
              + "  ps.setString(1, \"Int: \" + intVal + \", Double: \" + doubleVal + \", String: \" + stringVal);"
              + "  ps.executeUpdate();"
              + "} $$");
    }

    // Arrange
    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, 42);
    inParams.put(2, 3.14159);
    inParams.put(3, "TestString");

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call test_types(?, ?, ?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT message FROM audit_log")) {
      assertTrue(rs.next());
      String message = rs.getString(1);
      assertTrue(message.contains("42"));
      assertTrue(message.contains("3.14159"));
      assertTrue(message.contains("TestString"));
    }
  }

  @Test
  @DisplayName("Should work within workflow - realistic scenario")
  void testRealisticWorkflowScenario() throws Exception {
    // Scenario: Update user balance and log the action

    // Step 1: Update balance
    JdbcCallableTask updateTask =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call update_user_balance(?, ?)}")
            .inParameters(Map.of(1, 1, 2, 2000.00))
            .build();

    // Step 2: Log the action
    JdbcCallableTask logTask =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call add_audit_log(?)}")
            .inParameters(Map.of(1, "Updated user 1 balance to 2000.00"))
            .build();

    // Step 3: Get updated user details
    JdbcCallableTask getUserTask =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_user_details(?)}")
            .inParameters(Map.of(1, 1))
            .writingResultSetsTo("updatedUser")
            .build();

    // Act
    updateTask.execute(context);
    logTask.execute(context);
    getUserTask.execute(context);

    // Assert
    List<List<Map<String, Object>>> results =
        context.getTyped("updatedUser", new TypeReference<>() {});
    Map<String, Object> user = results.getFirst().getFirst();
    assertEquals(2000.00, ((Number) user.get("BALANCE")).doubleValue(), 0.01);

    // Verify audit log
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM audit_log")) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test
  @DisplayName("Should handle procedure execution with large string parameter")
  void testLargeStringParameter() throws Exception {
    // Arrange
    String largeMessage = "A".repeat(250); // Large but within VARCHAR(255)
    Map<Integer, Object> inParams = Map.of(1, largeMessage);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call add_audit_log(?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT message FROM audit_log")) {
      assertTrue(rs.next());
      assertEquals(largeMessage, rs.getString(1));
    }
  }
}
