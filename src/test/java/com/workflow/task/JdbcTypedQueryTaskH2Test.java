package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.sql.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import tools.jackson.core.type.TypeReference;

class JdbcTypedQueryTaskH2Test {

  private static JdbcDataSource ds;

  // Domain Object for Testing
  record Employee(int id, String name, double salary) {}

  @BeforeAll
  static void initDb() throws SQLException {
    ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:typed_test;DB_CLOSE_DELAY=-1");
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE employees (id INT, name VARCHAR(50), salary DOUBLE)");
      stmt.execute("INSERT INTO employees VALUES (1, 'John', 50000), (2, 'Jane', 60000)");
    }
  }

  @AfterAll
  static void cleanupDb() throws SQLException {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS employees");
    }
  }

  @Test
  void testMapToDomainObject() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("query", "SELECT * FROM employees WHERE salary > ?");
    ctx.put("args", List.of(55000.0));

    JdbcTypedQueryTask.RowMapper<Employee> mapper =
        (rs, _) -> new Employee(rs.getInt("id"), rs.getString("name"), rs.getDouble("salary"));
    ctx.put("mapper", mapper);

    JdbcTypedQueryTask<Employee> task =
        JdbcTypedQueryTask.<Employee>builder()
            .dataSource(ds)
            .readingSqlFrom("query")
            .readingParamsFrom("args")
            .readingRowMapperFrom("mapper")
            .writingResultsTo("empList")
            .build();

    task.execute(ctx);

    List<Employee> results = ctx.getTyped("empList", new TypeReference<>() {});
    assertEquals(1, results.size());
    assertEquals("Jane", results.getFirst().name());
  }

  @Test
  void testEmptyResultReturnsEmptyList() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("sql", "SELECT * FROM employees WHERE id = 99");
    ctx.put("mapper", (JdbcTypedQueryTask.RowMapper<Integer>) (rs, _) -> rs.getInt(1));

    JdbcTypedQueryTask<Employee> task =
        JdbcTypedQueryTask.<Employee>builder()
            .dataSource(ds)
            .readingSqlFrom("sql")
            .readingRowMapperFrom("mapper")
            .writingResultsTo("results")
            .build();
    task.execute(ctx);

    List<Integer> results = ctx.getTyped("results", new TypeReference<>() {});
    assertTrue(results.isEmpty());
  }

  @Test
  void testNullHandling() throws SQLException {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO employees (id, name, salary) VALUES (3, null, 0)");
    }

    WorkflowContext ctx = new WorkflowContext();
    ctx.put("empQuery", "SELECT name FROM employees WHERE id = 3");
    ctx.put("empMapper", (JdbcTypedQueryTask.RowMapper<String>) (rs, _) -> rs.getString("name"));

    JdbcTypedQueryTask<Employee> task =
        JdbcTypedQueryTask.<Employee>builder()
            .dataSource(ds)
            .readingSqlFrom("empQuery")
            .readingRowMapperFrom("empMapper")
            .writingResultsTo("empResults")
            .build();
    task.execute(ctx);

    List<String> results = ctx.getTyped("empResults", new TypeReference<>() {});
    assertNull(results.getFirst());
  }
}
