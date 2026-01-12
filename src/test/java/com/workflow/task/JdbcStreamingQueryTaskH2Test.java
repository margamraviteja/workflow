package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

class JdbcStreamingQueryTaskH2Test {

  private static JdbcDataSource ds;

  @BeforeAll
  static void initDb() throws SQLException {
    ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:newtestdb;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(255), price DOUBLE)");
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 1200.00)");
      stmt.execute("INSERT INTO products VALUES (2, 'Mouse', 25.00)");
      stmt.execute("INSERT INTO products VALUES (3, 'Keyboard', 75.00)");
    }
  }

  @AfterAll
  static void cleanupDb() throws SQLException {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS products");
    }
  }

  @Test
  void testStreamAllRows() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("sql", "SELECT * FROM products ORDER BY id");

    List<String> names = new ArrayList<>();
    ctx.put(
        "rowCallback",
        (java.util.function.Consumer<Map<String, Object>>)
            row -> names.add((String) row.get("NAME")));

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder().dataSource(ds).writingRowCountTo("total").build();

    task.execute(ctx);

    assertEquals(List.of("Laptop", "Mouse", "Keyboard"), names);
    assertEquals(3L, ctx.get("total"));
  }

  @Test
  void testWithParameters() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("sql", "SELECT name FROM products WHERE price > ?");
    ctx.put("params", List.of(50.00));

    AtomicInteger count = new AtomicInteger();
    ctx.put("rowCallback", (Consumer<Map<String, Object>>) _ -> count.incrementAndGet());

    JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder().dataSource(ds).build();
    task.execute(ctx);

    assertEquals(2, count.get());
  }

  @Test
  void testEmptyResultSet() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("sql", "SELECT * FROM products WHERE id = 999");
    ctx.put("rowCallback", (Consumer<Map<String, Object>>) _ -> fail("Should not be called"));

    JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder().dataSource(ds).build();
    task.execute(ctx);

    assertEquals(0L, ctx.get("rowCount"));
  }

  @Test
  void testQueryTimeoutConfiguration() {
    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder().dataSource(ds).queryTimeout(5).build();

    WorkflowContext ctx = new WorkflowContext();
    ctx.put("sql", "SELECT 1");
    ctx.put("rowCallback", (Consumer<Map<String, Object>>) _ -> {});

    // Should execute without error
    assertDoesNotThrow(() -> task.execute(ctx));
  }
}
