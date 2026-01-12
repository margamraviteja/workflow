package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcQueryTaskTest {

  private DataSource dataSource;
  private Connection connection;
  private PreparedStatement statement;
  private ResultSet resultSet;
  private ResultSetMetaData metaData;
  private WorkflowContext context;

  private static final String SQL_KEY = "query.sql";
  private static final String PARAMS_KEY = "query.params";
  private static final String OUTPUT_KEY = "query.results";

  @BeforeEach
  void setUp() throws Exception {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    statement = mock(PreparedStatement.class);
    resultSet = mock(ResultSet.class);
    metaData = mock(ResultSetMetaData.class);
    context = new WorkflowContext();

    // Standard mock behavior
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metaData);
  }

  // ========== Context Mode Tests  ==========

  @Test
  void testSuccessfulQueryExecution() throws Exception {
    // Arrange
    String sql = "SELECT id, name FROM users WHERE type = ?";
    List<Object> params = List.of("ADMIN");
    context.put(SQL_KEY, sql);
    context.put(PARAMS_KEY, params);

    // Mock Result Set: 1 row with 2 columns
    when(metaData.getColumnCount()).thenReturn(2);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");

    when(resultSet.next()).thenReturn(true, false); // Return one row, then stop
    when(resultSet.getObject(1)).thenReturn(101);
    when(resultSet.getObject(2)).thenReturn("Alice");

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(SQL_KEY)
            .readingParamsFrom(PARAMS_KEY)
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped(OUTPUT_KEY, new TypeReference<>() {});
    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals(101, results.getFirst().get("id"));
    assertEquals("Alice", results.getFirst().get("name"));

    // Verify JDBC interactions
    verify(connection).prepareStatement(sql);
    verify(statement).setObject(1, "ADMIN");
    verify(statement).executeQuery();
  }

  @Test
  void testMissingSqlThrowsException() {
    // Arrange: No SQL in context
    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(SQL_KEY)
            .readingParamsFrom(PARAMS_KEY)
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act & Assert
    assertThrows(
        TaskExecutionException.class,
        () -> task.execute(context),
        "Should throw if required SQL key is missing");
  }

  @Test
  void testEmptyParametersFallback() throws Exception {
    // Arrange: SQL present but no params key in context
    context.put(SQL_KEY, "SELECT * FROM logs");
    when(resultSet.next()).thenReturn(false); // Empty result set

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(SQL_KEY)
            .readingParamsFrom(PARAMS_KEY)
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert: Task should succeed using empty list for params
    verify(statement, never()).setObject(anyInt(), any());
    assertTrue(context.getTyped(OUTPUT_KEY, List.class).isEmpty());
  }

  @Test
  void testSqlExceptionWrapping() throws Exception {
    // Arrange
    context.put(SQL_KEY, "SELECT * FROM invalid_table");
    when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("DB Down"));

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(SQL_KEY)
            .readingParamsFrom(PARAMS_KEY)
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act & Assert
    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(ex.getMessage().contains("Query failed"));
    assertEquals("DB Down", ex.getCause().getMessage());
  }

  @Test
  void testResourceCleanup() throws Exception {
    // Arrange
    context.put(SQL_KEY, "SELECT 1");
    when(resultSet.next()).thenReturn(false);

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(SQL_KEY)
            .readingParamsFrom(PARAMS_KEY)
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert: Ensure try-with-resources closed everything
    verify(resultSet).close();
    verify(statement).close();
    verify(connection).close();
  }

  // ========== Direct Input Mode Tests ==========

  @Test
  void testDirectInputMode_WithParameters() throws Exception {
    // Arrange - SQL and parameters provided directly
    String sql = "SELECT id, name, email FROM users WHERE status = ? AND dept = ?";
    List<Object> params = Arrays.asList("ACTIVE", "Engineering");

    when(metaData.getColumnCount()).thenReturn(3);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");
    when(metaData.getColumnLabel(3)).thenReturn("email");

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(1);
    when(resultSet.getObject(2)).thenReturn("John");
    when(resultSet.getObject(3)).thenReturn("john@example.com");

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(params)
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped(OUTPUT_KEY, new TypeReference<>() {});
    assertNotNull(results);
    assertEquals(1, results.size());

    Map<String, Object> row = results.getFirst();
    assertEquals(1, row.get("id"));
    assertEquals("John", row.get("name"));
    assertEquals("john@example.com", row.get("email"));

    // Verify JDBC interactions
    verify(connection).prepareStatement(sql);
    verify(statement).setObject(1, "ACTIVE");
    verify(statement).setObject(2, "Engineering");
    verify(statement).executeQuery();
  }

  @Test
  void testDirectInputMode_NoParameters() throws Exception {
    // Arrange - SQL without parameters
    String sql = "SELECT COUNT(*) as total FROM users";

    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("total");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(42);

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(Collections.emptyList())
            .writingResultsTo("count")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped("count", new TypeReference<>() {});
    assertEquals(1, results.size());
    assertEquals(42, results.getFirst().get("total"));

    verify(statement, never()).setObject(anyInt(), any());
  }

  @Test
  void testDirectInputMode_EmptyResults() throws Exception {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";
    when(resultSet.next()).thenReturn(false); // No results

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(List.of(999))
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped(OUTPUT_KEY, new TypeReference<>() {});
    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void testDirectInputMode_MultipleRows() throws Exception {
    // Arrange
    String sql = "SELECT id, name FROM users ORDER BY id";

    when(metaData.getColumnCount()).thenReturn(2);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");

    when(resultSet.next()).thenReturn(true, true, true, false);

    // Row 1
    when(resultSet.getObject(1)).thenReturn(1, 2, 3);
    when(resultSet.getObject(2)).thenReturn("Alice", "Bob", "Charlie");

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql)
            .params(Collections.emptyList())
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped(OUTPUT_KEY, new TypeReference<>() {});
    assertEquals(3, results.size());
    assertEquals(1, results.get(0).get("id"));
    assertEquals("Alice", results.get(0).get("name"));
    assertEquals(2, results.get(1).get("id"));
    assertEquals("Bob", results.get(1).get("name"));
    assertEquals(3, results.get(2).get("id"));
    assertEquals("Charlie", results.get(2).get("name"));
  }

  // ========== Mixed Mode Tests ==========

  @Test
  void testMixedMode_DirectSqlContextParams() throws Exception {
    // Arrange - SQL is direct, parameters from context
    String sql = "SELECT * FROM orders WHERE user_id = ? AND status = ?";
    context.put(PARAMS_KEY, List.of(123, "COMPLETED"));

    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("order_id");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(456);

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(sql) // Direct SQL
            .readingParamsFrom(PARAMS_KEY) // Context parameters
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped(OUTPUT_KEY, new TypeReference<>() {});
    assertEquals(1, results.size());
    assertEquals(456, results.getFirst().get("order_id"));

    verify(statement).setObject(1, 123);
    verify(statement).setObject(2, "COMPLETED");
  }

  @Test
  void testMixedMode_DirectParamsContextSql() throws Exception {
    // Arrange - Parameters are direct, SQL from context
    List<Object> params = List.of("ACTIVE");
    context.put(SQL_KEY, "SELECT * FROM users WHERE status = ?");

    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(1);

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom(SQL_KEY) // Context SQL
            .params(params) // Direct parameters
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    List<Map<String, Object>> results = context.getTyped(OUTPUT_KEY, new TypeReference<>() {});
    assertEquals(1, results.size());

    verify(statement).setObject(1, "ACTIVE");
  }

  // ========== Builder Validation Tests (NEW!) ==========

  @Test
  void testBuilder_MissingDataSource() {
    try {
      JdbcQueryTask.builder().sql("SELECT 1").writingResultsTo(OUTPUT_KEY).build();
      fail();
    } catch (Exception _) {
      assertTrue(true, "Should throw when dataSource is missing");
    }
  }

  @Test
  void testBuilder_MissingSqlAndSqlKey() {
    try {
      JdbcQueryTask.builder().dataSource(dataSource).writingResultsTo(OUTPUT_KEY).build();
    } catch (Exception _) {
      assertTrue(true, "Should throw when neither sql nor sqlKey is provided");
    }
  }

  @Test
  void testBuilder_MissingOutputKey() {
    try {
      JdbcQueryTask.builder().dataSource(dataSource).sql("SELECT 1").build();
    } catch (Exception _) {
      assertTrue(true, "Should throw when outputKey is missing");
    }
  }

  @Test
  void testBuilder_DirectModeTakesPrecedence() throws Exception {
    // Arrange - Both direct and context values provided
    String directSql = "SELECT 'direct' as source";
    String contextSql = "SELECT 'context' as source";

    context.put(SQL_KEY, contextSql);
    context.put(PARAMS_KEY, List.of("context"));

    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("source");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn("direct");

    JdbcQueryTask task =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(directSql) // Direct takes precedence
            .params(List.of("direct")) // Direct takes precedence
            .readingSqlFrom(SQL_KEY) // Ignored
            .readingParamsFrom(PARAMS_KEY) // Ignored
            .writingResultsTo(OUTPUT_KEY)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(connection).prepareStatement(directSql); // Direct SQL used
    verify(statement).setObject(1, "direct"); // Direct params used
  }
}
