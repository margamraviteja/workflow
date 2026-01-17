package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

class JdbcQueryTaskIntegrationTest {

  private static JdbcDataSource dataSource;
  private WorkflowContext context;

  @BeforeAll
  static void setupDatabase() throws Exception {
    // Initialize H2 In-Memory Database
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    // Create schema and seed data
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE employees (id INT PRIMARY KEY, name VARCHAR(255), department VARCHAR(255))");
      stmt.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering')");
      stmt.execute("INSERT INTO employees VALUES (2, 'Bob', 'Design')");
      stmt.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering')");

      stmt.execute(
          "CREATE TABLE salaries (emp_id INT, amount DECIMAL, bonus DECIMAL, PRIMARY KEY(emp_id))");
      stmt.execute("INSERT INTO salaries VALUES (1, 90000.00, 5000.00)");
      stmt.execute("INSERT INTO salaries VALUES (2, 85000.00, 3000.00)");
      stmt.execute("INSERT INTO salaries VALUES (3, 95000.00, 7000.00)");
    }
  }

  @AfterAll
  static void cleanupDatabase() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop tables
      stmt.execute("DROP TABLE IF EXISTS salaries");
      stmt.execute("DROP TABLE IF EXISTS employees");
    }
  }

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  @Test
  void testExecuteWithRealDatabase() {
    // Arrange
    context.put(
        "input.sql", "SELECT name, department FROM employees WHERE department = ? ORDER BY id ASC");
    context.put("input.params", List.of("Engineering"));

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("input.sql")
            .readingParamsFrom("input.params")
            .writingResultsTo("output.results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results =
        context.getTyped("output.results", new TypeReference<>() {});

    assertNotNull(results);
    assertEquals(2, results.size(), "Should find two engineers");

    // Verify First Row
    Map<String, Object> alice = results.getFirst();
    assertEquals("Alice", alice.get("NAME"));
    assertEquals("Engineering", alice.get("DEPARTMENT"));

    // Verify Second Row
    Map<String, Object> charlie = results.get(1);
    assertEquals("Charlie", charlie.get("NAME"));
  }

  @Test
  void testColumnOrderPreservation() {
    // Arrange
    context.put("sql", "SELECT department, name, id FROM employees WHERE id = 1");

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped("results", new TypeReference<>() {});
    Map<String, Object> row = results.getFirst();

    // Verify that it is a LinkedHashMap and preserves the SELECT order
    Object[] keys = row.keySet().toArray();
    assertEquals("DEPARTMENT", keys[0]);
    assertEquals("NAME", keys[1]);
    assertEquals("ID", keys[2]);
  }

  @Test
  void testJoinAndAggregation() {
    // Calculate total compensation per department using a JOIN and GROUP BY
    String complexSql =
        "SELECT e.department, SUM(s.amount + s.bonus) AS total_comp "
            + "FROM employees e "
            + "JOIN salaries s ON e.id = s.emp_id "
            + "GROUP BY e.department "
            + "ORDER BY total_comp DESC";

    context.put("sql.complex", complexSql);
    context.put("sql.params", List.of()); // No params for this query

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql.complex")
            .readingParamsFrom("sql.params")
            .writingResultsTo("comp.stats")
            .build();

    task.execute(context);

    List<Map<String, Object>> stats = context.getTyped("comp.stats", new TypeReference<>() {});

    // Engineering: (90k+5k) + (95k+7k) = 197,000
    Map<String, Object> engineering =
        stats.stream()
            .filter(m -> m.get("DEPARTMENT").equals("Engineering"))
            .findFirst()
            .orElseThrow();

    assertEquals(197000.0, ((Number) engineering.get("TOTAL_COMP")).doubleValue());
  }

  @Test
  void testSubqueryAndCaseStatement() {
    String sql =
        "SELECT name, "
            + "CASE WHEN (SELECT amount FROM salaries WHERE emp_id = e.id) > 90000 "
            + "THEN 'High' ELSE 'Standard' END AS salary_tier "
            + "FROM employees e "
            + "WHERE e.id IN (SELECT emp_id FROM salaries WHERE amount > ?)";

    context.put("sql.subquery", sql);
    context.put("params", List.of(80000)); // Threshold

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql.subquery")
            .readingParamsFrom("params")
            .writingResultsTo("tier.results")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("tier.results", new TypeReference<>() {});

    // Charlie has 95k, should be High. Alice has 90k, should be Standard.
    Map<String, Object> charlie =
        results.stream()
            .filter(r -> r.get("NAME").equals("Charlie"))
            .findFirst()
            .orElseGet(LinkedHashMap::new);
    assertEquals("High", charlie.get("SALARY_TIER"));
  }

  @Test
  void testLeftJoinAndNullHandling() throws SQLException {
    // Insert an employee without a salary record
    try (Connection conn = dataSource.getConnection();
        Statement s = conn.createStatement()) {
      s.execute("INSERT INTO employees VALUES (4, 'Dan', 'HR')");
    }

    String sql =
        "SELECT UPPER(e.name) as name_upper, s.amount "
            + "FROM employees e "
            + "LEFT JOIN salaries s ON e.id = s.emp_id "
            + "WHERE e.name = ?";

    context.put("sql", sql);
    context.put("params", List.of("Dan"));

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("out")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("out", new TypeReference<>() {});
    Map<String, Object> dan = results.getFirst();

    assertEquals("DAN", dan.get("NAME_UPPER"));
    assertNull(dan.get("AMOUNT"), "Salary should be null for Dan");
  }

  @Test
  void testPaginationWithOffsetAndFetch() {
    // We have 3 employees (Alice, Bob, Charlie).
    // Let's skip the first one and take the next two.
    String sql = "SELECT name FROM employees ORDER BY name ASC LIMIT ? OFFSET ?";

    context.put("sql", sql);
    context.put("params", List.of(2, 1)); // Limit 2, Offset 1

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("paged.results")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("paged.results", new TypeReference<>() {});

    // Ordered: Alice (0), Bob (1), Charlie (2)
    // Offset 1 starts at Bob. Limit 2 gets Bob and Charlie.
    assertEquals(2, results.size());
    assertEquals("Bob", results.get(0).get("NAME"));
    assertEquals("Charlie", results.get(1).get("NAME"));
  }

  @Test
  void testJsonAggregation() {
    // 1. Use double quotes if you want to preserve case in column names,
    // or expect UPPERCASE keys in the Map.
    // CAST the JSON result to VARCHAR
    String sql =
        "SELECT department, CAST(JSON_ARRAYAGG(name) AS VARCHAR) AS member_list "
            + "FROM employees GROUP BY department";

    context.put("sql", sql);
    context.put("params", List.of());

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("json.out")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("json.out", new TypeReference<>() {});

    // 2. Search case-insensitively for the "Engineering" department row
    Map<String, Object> engineering =
        results.stream()
            .filter(r -> r.get("DEPARTMENT").toString().equalsIgnoreCase("Engineering"))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Department 'Engineering' not found in results: " + results));

    // 3. Robustly convert the result to String
    Object rawJson = engineering.get("MEMBER_LIST");
    assertNotNull(rawJson, "JSON member_list should not be null");

    String members;
    // Check if it's a byte array and convert if necessary
    if (rawJson instanceof byte[]) {
      members = new String((byte[]) rawJson, java.nio.charset.StandardCharsets.UTF_8);
    } else {
      members = rawJson.toString();
    }

    assertTrue(members.contains("Alice"));
    assertTrue(members.contains("Charlie"));
  }

  @Test
  void testSqlArrayAggregation() {
    String sql =
        "SELECT department, ARRAY_AGG(name) AS name_array " + "FROM employees GROUP BY department";

    context.put("sql", sql);
    context.put("params", List.of());

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("results")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("results", new TypeReference<>() {});
    Map<String, Object> engineering =
        results.stream()
            .filter(r -> r.get("DEPARTMENT").toString().equalsIgnoreCase("Engineering"))
            .findFirst()
            .orElseGet(LinkedHashMap::new);

    List<?> nameList = (List<?>) engineering.get("NAME_ARRAY");

    assertTrue(nameList.contains("Alice"));
    assertTrue(nameList.contains("Charlie"));
  }

  @Test
  void testDateFilteringAndReturnTypes() throws Exception {
    // Add a created_at column for this test
    try (Connection conn = dataSource.getConnection();
        Statement s = conn.createStatement()) {
      s.execute("ALTER TABLE employees ADD COLUMN joined_date DATE");
      s.execute("UPDATE employees SET joined_date = '2024-01-01' WHERE id = 1");
    }

    String sql = "SELECT name, joined_date FROM employees WHERE joined_date >= ?";
    context.put("sql", sql);
    // Passing a String or java.sql.Date both work with setObject()
    context.put("params", List.of("2024-01-01"));

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("date.results")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("date.results", new TypeReference<>() {});
    Map<String, Object> row = results.getFirst();

    // Verify type preservation
    assertInstanceOf(java.sql.Date.class, row.get("JOINED_DATE"));
    assertEquals("Alice", row.get("NAME"));
  }

  @Test
  void testEmptyResultSet() {
    context.put("sql", "SELECT * FROM employees WHERE id = 999"); // Non-existent ID
    context.put("params", List.of());

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("empty.out")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("empty.out", new TypeReference<>() {});

    assertNotNull(results, "The output list should be initialized, not null");
    assertTrue(results.isEmpty(), "The list should be empty for no matches");
  }

  @Test
  void testMixedParameterTypes() {
    String sql = "SELECT name FROM employees WHERE id > ? AND department = ?";
    context.put("sql", sql);
    context.put("params", List.of(1, "Engineering")); // 1 (Integer), "Engineering" (String)

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("mixed.out")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("mixed.out", new TypeReference<>() {});

    // Should only find Charlie (id=3), as Alice is id=1
    assertEquals(1, results.size());
    assertEquals("Charlie", results.getFirst().get("NAME"));
  }

  @Test
  void testArrayAutoConversion() {
    // With the new convertJdbcValue logic, this should just work!
    context.put("sql", "SELECT ARRAY_AGG(name) AS names FROM employees");
    context.put("params", List.of());

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("out")
            .build();
    task.execute(context);

    List<Map<String, Object>> results = context.getTyped("out", new TypeReference<>() {});
    Object names = results.getFirst().get("NAMES");

    // It is now a java.util.List, not a JdbcArray or Object[]
    assertInstanceOf(java.util.List.class, names);
    assertTrue(((List<?>) names).contains("Alice"));
  }
}
