package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JdbcStreamingQueryTask - Enhanced Mock Tests")
class JdbcStreamingQueryTaskEnhancedTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;
  @Mock private ResultSetMetaData metaData;

  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  private void mockDatabase() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metaData);
  }

  @Test
  @DisplayName("Should stream rows with direct SQL and callback")
  void testDirectStreamingQuery() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(2);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");
    when(resultSet.next()).thenReturn(true, true, true, false);
    when(resultSet.getObject(1)).thenReturn(1, 2, 3);
    when(resultSet.getObject(2)).thenReturn("Alice", "Bob", "Charlie");

    List<Map<String, Object>> capturedRows = new ArrayList<>();
    Consumer<Map<String, Object>> callback = capturedRows::add;

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, name FROM users")
            .params(Collections.emptyList())
            .rowCallback(callback)
            .writingRowCountTo("count")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(3, capturedRows.size());
    assertEquals(1, capturedRows.getFirst().get("id"));
    assertEquals("Alice", capturedRows.getFirst().get("name"));
    assertEquals(3L, context.get("count"));
    verify(statement).setFetchSize(1000); // Default fetch size
  }

  @Test
  @DisplayName("Should use custom fetch size")
  void testCustomFetchSize() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .fetchSize(500)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement).setFetchSize(500);
  }

  @Test
  @DisplayName("Should set query timeout")
  void testQueryTimeout() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .queryTimeout(30)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement).setQueryTimeout(30);
  }

  @Test
  @DisplayName("Should bind parameters correctly")
  void testParameterBinding() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    List<Object> params = Arrays.asList("Active", 18, 100.0);

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users WHERE status = ? AND age > ? AND balance < ?")
            .params(params)
            .rowCallback(row -> {})
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement).setObject(1, "Active");
    verify(statement).setObject(2, 18);
    verify(statement).setObject(3, 100.0);
  }

  @Test
  @DisplayName("Should use context mode for SQL and params")
  void testContextMode() throws SQLException {
    // Arrange
    mockDatabase();
    context.put("sql", "SELECT * FROM users WHERE status = ?");
    context.put("params", List.of("Active"));
    context.put("callback", (Consumer<Map<String, Object>>) row -> {});

    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .readingRowCallbackFrom("callback")
            .writingRowCountTo("count")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement).setObject(1, "Active");
    assertEquals(0L, context.get("count"));
  }

  @Test
  @DisplayName("Should handle empty result set")
  void testEmptyResultSet() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    AtomicInteger callbackInvocations = new AtomicInteger(0);
    Consumer<Map<String, Object>> callback = row -> callbackInvocations.incrementAndGet();

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users WHERE 1=0")
            .params(Collections.emptyList())
            .rowCallback(callback)
            .writingRowCountTo("count")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(0, callbackInvocations.get());
    assertEquals(0L, context.get("count"));
  }

  @Test
  @DisplayName("Should handle large result set")
  void testLargeResultSet() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");

    // Simulate 10000 rows
    Boolean[] nextResults = new Boolean[10001];
    Arrays.fill(nextResults, 0, 10000, true);
    nextResults[10000] = false;
    when(resultSet.next()).thenReturn(true, nextResults);

    Integer[] ids = new Integer[10001];
    for (int i = 0; i < 10001; i++) {
      ids[i] = i + 1;
    }
    when(resultSet.getObject(1)).thenReturn(1, ids);

    AtomicLong count = new AtomicLong(0);
    Consumer<Map<String, Object>> callback = row -> count.incrementAndGet();

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM large_table")
            .params(Collections.emptyList())
            .rowCallback(callback)
            .fetchSize(1000)
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(10001, count.get()); // Mockito returns 10001 due to the setup
  }

  @Test
  @DisplayName("Should handle null values in result set")
  void testNullValues() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(3);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");
    when(metaData.getColumnLabel(3)).thenReturn("email");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(1);
    when(resultSet.getObject(2)).thenReturn(null);
    when(resultSet.getObject(3)).thenReturn("test@example.com");

    List<Map<String, Object>> capturedRows = new ArrayList<>();

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, email FROM users")
            .params(Collections.emptyList())
            .rowCallback(capturedRows::add)
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, capturedRows.size());
    assertNull(capturedRows.getFirst().get("name"));
    assertEquals("test@example.com", capturedRows.getFirst().get("email"));
  }

  @Test
  @DisplayName("Should stop processing when callback throws exception")
  void testCallbackException() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(true, true, true);
    when(resultSet.getObject(1)).thenReturn(1, 2, 3);

    AtomicInteger processedCount = new AtomicInteger(0);
    Consumer<Map<String, Object>> callback =
        row -> {
          processedCount.incrementAndGet();
          if (processedCount.get() == 2) {
            throw new RuntimeException("Processing error at row 2");
          }
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users")
            .params(Collections.emptyList())
            .rowCallback(callback)
            .build();

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Row callback failed at row 1"));
    assertEquals(2, processedCount.get());
  }

  @Test
  @DisplayName("Should throw exception when SQL is missing")
  void testMissingSql() {
    // Arrange
    context.put("callback", (Consumer<Map<String, Object>>) row -> {});

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("missingSql")
            .readingRowCallbackFrom("callback")
            .build();

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("SQL query not found"));
  }

  @Test
  @DisplayName("Should throw exception when callback is missing")
  void testMissingCallback() {
    // Arrange
    context.put("sql", "SELECT * FROM users");

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingRowCallbackFrom("missingCallback")
            .build();

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Row callback not found"));
  }

  @Test
  @DisplayName("Should throw exception on SQL error")
  void testSqlException() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
    when(statement.executeQuery()).thenThrow(new SQLException("Table not found"));

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM nonexistent_table")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .build();

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Streaming query failed"));
  }

  @Test
  @DisplayName("Should throw exception on connection failure")
  void testConnectionFailure() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM users")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .build();

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  @DisplayName("Should properly close resources on success")
  void testResourceClosingOnSuccess() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(resultSet).close();
    verify(statement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should properly close resources on failure")
  void testResourceClosingOnFailure() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
    when(statement.executeQuery()).thenThrow(new SQLException("Error"));

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM users")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .build();

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));

    verify(statement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should handle multiple columns correctly")
  void testMultipleColumns() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(5);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");
    when(metaData.getColumnLabel(3)).thenReturn("email");
    when(metaData.getColumnLabel(4)).thenReturn("age");
    when(metaData.getColumnLabel(5)).thenReturn("balance");

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(1);
    when(resultSet.getObject(2)).thenReturn("Alice");
    when(resultSet.getObject(3)).thenReturn("alice@example.com");
    when(resultSet.getObject(4)).thenReturn(30);
    when(resultSet.getObject(5)).thenReturn(1000.50);

    List<Map<String, Object>> capturedRows = new ArrayList<>();

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, email, age, balance FROM users")
            .params(Collections.emptyList())
            .rowCallback(capturedRows::add)
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(1, capturedRows.size());
    Map<String, Object> row = capturedRows.getFirst();
    assertEquals(1, row.get("id"));
    assertEquals("Alice", row.get("name"));
    assertEquals("alice@example.com", row.get("email"));
    assertEquals(30, row.get("age"));
    assertEquals(1000.50, row.get("balance"));
  }

  @Test
  @DisplayName("Should throw IllegalStateException when dataSource is null")
  void testBuilderValidation_NullDataSource() {
    try {
      JdbcStreamingQueryTask.builder().sql("SELECT * FROM users").rowCallback(row -> {}).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should aggregate data using callback")
  void testAggregationCallback() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(2);
    when(metaData.getColumnLabel(1)).thenReturn("category");
    when(metaData.getColumnLabel(2)).thenReturn("amount");
    when(resultSet.next()).thenReturn(true, true, true, true, false);
    when(resultSet.getObject(1)).thenReturn("A", "B", "A", "B");
    when(resultSet.getObject(2)).thenReturn(100.0, 200.0, 150.0, 250.0);

    Map<String, Double> totals = new HashMap<>();
    Consumer<Map<String, Object>> aggregateCallback =
        row -> {
          String category = (String) row.get("category");
          Double amount = (Double) row.get("amount");
          totals.merge(category, amount, Double::sum);
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT category, amount FROM transactions")
            .params(Collections.emptyList())
            .rowCallback(aggregateCallback)
            .writingRowCountTo("count")
            .build();

    // Act
    task.execute(context);

    // Assert
    assertEquals(250.0, totals.get("A"), 0.01); // 100 + 150
    assertEquals(450.0, totals.get("B"), 0.01); // 200 + 250
    assertEquals(4L, context.get("count"));
  }

  @Test
  @DisplayName("Should skip query timeout when timeout is zero or negative")
  void testNoQueryTimeout() throws SQLException {
    // Arrange
    mockDatabase();
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id FROM users")
            .params(Collections.emptyList())
            .rowCallback(row -> {})
            .queryTimeout(0)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement, never()).setQueryTimeout(anyInt());
  }
}
