package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.sql.*;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcBatchUpdateIntegrationTest {

  private static JdbcDataSource ds;
  private JdbcBatchUpdateTask batchTask;

  @BeforeAll
  static void setupDatabase() throws Exception {
    ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:batch_db;DB_CLOSE_DELAY=-1");

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE departments (id INT PRIMARY KEY, name VARCHAR(50))");
      stmt.execute("CREATE TABLE employees (id INT PRIMARY KEY, dept_id INT, name VARCHAR(50))");

      stmt.execute("INSERT INTO departments VALUES (1, 'IT'), (2, 'HR')");
      stmt.execute(
          "INSERT INTO employees VALUES (101, 1, 'Alice'), (102, 1, 'Bob'), (103, 2, 'Charlie')");
    }
  }

  @AfterAll
  static void cleanupDatabase() throws Exception {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS employees");
      stmt.execute("DROP TABLE IF EXISTS departments");
    }
  }

  @BeforeEach
  void setup() {
    batchTask =
        JdbcBatchUpdateTask.builder()
            .dataSource(ds)
            .readingSqlFrom("sql")
            .readingBatchParamsFrom("batch_data")
            .writingBatchResultsTo("out")
            .build();
  }

  @Test
  void execute_ComplexBatchDeleteAcrossTables() throws Exception {
    // Scenario: Delete employees from different departments by ID
    WorkflowContext context = new WorkflowContext();
    context.put("sql", "DELETE FROM employees WHERE id = ? AND dept_id = ?");

    // Batch parameters: [EmployeeID, DeptID]
    context.put("batch_data", List.of(List.of(101, 1), List.of(103, 2)));

    // Act
    batchTask.execute(context);

    // Assert
    int[] results = (int[]) context.get("out");
    assertEquals(2, results.length);
    assertEquals(1, results[0]); // One row for Alice
    assertEquals(1, results[1]); // One row for Charlie

    // Verify remaining data
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM employees");
      rs.next();
      assertEquals(1, rs.getInt(1), "Only Bob should remain");
    }
  }

  @Test
  void execute_EmptyBatch_ShouldReturnEmptyArray() {
    WorkflowContext context = new WorkflowContext();
    context.put("sql", "UPDATE employees SET name = 'Unknown'");
    context.put("batch_data", List.of()); // Empty list

    batchTask.execute(context);

    int[] results = (int[]) context.get("out");
    assertEquals(0, results.length);
  }

  @Test
  void execute_BatchInsertWithMixedTypes() throws Exception {
    WorkflowContext context = new WorkflowContext();
    context.put("sql", "INSERT INTO employees (id, dept_id, name) VALUES (?, ?, ?)");
    context.put("batch_data", List.of(List.of(201, 1, "Dave"), List.of(202, 2, "Eve")));

    batchTask.execute(context);

    int[] results = (int[]) context.get("out");
    assertEquals(2, results.length);

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM employees WHERE id > 200");
      rs.next();
      assertEquals(2, rs.getInt(1));
    }
  }
}
