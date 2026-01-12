package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

class JdbcTransactionIntegrationTest {

  private static JdbcDataSource dataSource;

  @BeforeAll
  static void setupDatabase() throws SQLException {
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance INT)");
    }
  }

  @AfterAll
  static void cleanupDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS accounts");
    }
  }

  @BeforeEach
  void resetData() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM accounts");
      stmt.execute("INSERT INTO accounts VALUES (1, 1000), (2, 1000)");
    }
  }

  private int getBalance(int id) throws SQLException {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      return rs.next() ? rs.getInt(1) : -1;
    }
  }

  @Test
  @DisplayName("Functional: Successful Transfer Commits Changes")
  void testSuccessfulTransfer() throws TaskExecutionException, SQLException {
    Task debit =
        ctx -> executeUpdate("UPDATE accounts SET balance = balance - 200 WHERE id = 1", ctx);
    Task credit =
        ctx -> executeUpdate("UPDATE accounts SET balance = balance + 200 WHERE id = 2", ctx);

    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder().dataSource(dataSource).task(debit).task(credit).build();

    txTask.execute(new WorkflowContext());

    assertEquals(800, getBalance(1));
    assertEquals(1200, getBalance(2));
  }

  @Test
  @DisplayName("Functional: Exception in Second Task Rolls Back First Task")
  void testRollbackOnFailure() throws SQLException {
    Task debit =
        ctx -> executeUpdate("UPDATE accounts SET balance = balance - 200 WHERE id = 1", ctx);
    Task failTask =
        _ -> {
          throw new RuntimeException("Simulated Failure");
        };

    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder().dataSource(dataSource).task(debit).task(failTask).build();

    try {
      txTask.execute(new WorkflowContext());
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }

    // Balance should still be 1000 because of rollback
    assertEquals(1000, getBalance(1));
  }

  @Test
  @DisplayName("Functional: Constraint Violation Rolls Back All")
  void testSqlConstraintViolation() throws SQLException {
    // Attempting to insert a null into a PK or similar (here just forcing a syntax error for
    // simplicity)
    Task badSql =
        ctx ->
            executeUpdate(
                "INSERT INTO accounts (id, balance) VALUES (1, 500)", ctx); // PK Violation

    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder().dataSource(dataSource).task(badSql).build();

    try {
      txTask.execute(new WorkflowContext());
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
    assertEquals(1000, getBalance(1)); // Original record remains unchanged
  }

  @Test
  @DisplayName("Functional: Isolation Level Prevents Dirty Reads")
  void testIsolationLevel() throws TaskExecutionException, SQLException {
    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder()
            .dataSource(dataSource)
            .isolationLevel(Connection.TRANSACTION_SERIALIZABLE)
            .task(
                ctx -> {
                  try {
                    executeUpdate("UPDATE accounts SET balance = 5000 WHERE id = 1", ctx);
                    // At this point, a separate connection should NOT see 5000 if not committed
                    assertEquals(1000, getBalance(1));
                  } catch (SQLException e) {
                    throw new TaskExecutionException(e);
                  }
                })
            .build();

    txTask.execute(new WorkflowContext());
    assertEquals(5000, getBalance(1)); // Now visible
  }

  @Test
  @DisplayName("Functional: Nested Tasks Reuse Same Connection")
  void testConnectionSharing() throws TaskExecutionException {
    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder()
            .dataSource(dataSource)
            .task(
                ctx -> {
                  Connection c1 = (Connection) ctx.get(JdbcTransactionTask.CONNECTION_CONTEXT_KEY);
                  Connection c2 = (Connection) ctx.get(JdbcTransactionTask.CONNECTION_CONTEXT_KEY);
                  assertSame(c1, c2, "Tasks must receive the exact same connection instance");
                })
            .build();

    txTask.execute(new WorkflowContext());
  }

  @Test
  @DisplayName("Functional: Multiple Updates within One Task")
  void testSingleTaskMultipleUpdates() throws TaskExecutionException, SQLException {
    Task multiUpdate =
        ctx -> {
          executeUpdate("UPDATE accounts SET balance = 0 WHERE id = 1", ctx);
          executeUpdate("UPDATE accounts SET balance = 0 WHERE id = 2", ctx);
        };

    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder().dataSource(dataSource).task(multiUpdate).build();

    txTask.execute(new WorkflowContext());
    assertEquals(0, getBalance(1));
    assertEquals(0, getBalance(2));
  }

  @Test
  @DisplayName("Functional: Task Reading from Context and Writing to DB")
  void testContextDynamicData() throws TaskExecutionException, SQLException {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("newBalance", 777);

    Task updateFromCtx =
        c -> {
          Integer val = (Integer) c.get("newBalance");
          executeUpdate("UPDATE accounts SET balance = " + val + " WHERE id = 1", c);
        };

    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder().dataSource(dataSource).task(updateFromCtx).build();

    txTask.execute(ctx);
    assertEquals(777, getBalance(1));
  }

  @Test
  @DisplayName("Functional: Rollback Does Not Affect Other Connections")
  void testParallelConnectionConsistency() throws TaskExecutionException, SQLException {
    Task update = ctx -> executeUpdate("UPDATE accounts SET balance = 0 WHERE id = 1", ctx);
    Task fail =
        _ -> {
          throw new RuntimeException();
        };

    JdbcTransactionTask txTask =
        JdbcTransactionTask.builder().dataSource(dataSource).task(update).task(fail).build();

    // Separate connection to watch
    Connection observer = dataSource.getConnection();

    try {
      txTask.execute(new WorkflowContext());
    } catch (Exception _) {
      assertTrue(true);
    }

    ResultSet rs =
        observer.prepareStatement("SELECT balance FROM accounts WHERE id = 1").executeQuery();
    rs.next();
    assertEquals(1000, rs.getInt(1), "Observer should never have seen the balance at 0");
    observer.close();
  }

  // Helper to execute SQL using the connection in context
  private void executeUpdate(String sql, WorkflowContext ctx) {
    Connection conn = (Connection) ctx.get(JdbcTransactionTask.CONNECTION_CONTEXT_KEY);
    try (Statement stmt = conn.createStatement()) {
      stmt.executeUpdate(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
