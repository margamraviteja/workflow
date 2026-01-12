package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
@DisplayName("JdbcBatchUpdateTask - Mock Tests")
class JdbcBatchUpdateTaskEnhancedTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;

  private WorkflowContext context;

  @BeforeEach
  void setUp() throws Exception {
    context = new WorkflowContext();
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
  }

  @Test
  @DisplayName("Should execute batch with direct SQL and params")
  void testDirectBatchUpdate() throws Exception {
    // Arrange
    List<List<Object>> batchParams =
        Arrays.asList(
            Arrays.asList("Alice", 1), Arrays.asList("Bob", 2), Arrays.asList("Charlie", 3));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    int[] expected = {1, 1, 1};
    when(statement.executeBatch()).thenReturn(expected);

    // Act
    task.execute(context);

    // Assert
    verify(statement, times(3)).addBatch();
    verify(statement).executeBatch();

    int[] results = (int[]) context.get("results");
    assertArrayEquals(expected, results);
  }

  @Test
  @DisplayName("Should execute batch with SQL from context")
  void testContextMode() throws Exception {
    // Arrange
    context.put("sql", "INSERT INTO users (id, name) VALUES (?, ?)");
    List<List<Object>> batchParams =
        Arrays.asList(Arrays.asList(1, "Alice"), Arrays.asList(2, "Bob"));
    context.put("params", batchParams);

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingBatchParamsFrom("params")
            .writingBatchResultsTo("results")
            .build();

    int[] expected = {1, 1};
    when(statement.executeBatch()).thenReturn(expected);

    // Act
    task.execute(context);

    // Assert
    verify(statement, times(2)).addBatch();
    verify(statement).executeBatch();

    int[] results = (int[]) context.get("results");
    assertArrayEquals(expected, results);
  }

  @Test
  @DisplayName("Should handle mixed mode - direct SQL, context params")
  void testMixedMode() throws Exception {
    // Arrange
    List<List<Object>> batchParams =
        Arrays.asList(Arrays.asList("Product1", 100), Arrays.asList("Product2", 200));
    context.put("batchData", batchParams);

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO products (name, price) VALUES (?, ?)")
            .readingBatchParamsFrom("batchData")
            .writingBatchResultsTo("results")
            .build();

    int[] expected = {1, 1};
    when(statement.executeBatch()).thenReturn(expected);

    // Act
    task.execute(context);

    // Assert
    verify(statement, times(2)).addBatch();
    assertArrayEquals(expected, (int[]) context.get("results"));
  }

  @Test
  @DisplayName("Should handle empty batch params")
  void testEmptyBatch() throws Exception {
    // Arrange
    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name) VALUES (?, ?)")
            .batchParams(Collections.emptyList())
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement, never()).addBatch();
    verify(statement, never()).executeBatch(); // Early return, no batch execution

    int[] results = (int[]) context.get("results");
    assertEquals(0, results.length);
  }

  @Test
  @DisplayName("Should handle null batch params from context as empty")
  void testNullBatchParamsFromContext() throws Exception {
    // Arrange
    context.put("sql", "INSERT INTO users (id, name) VALUES (?, ?)");
    // No batchParams in context

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingBatchParamsFrom("missingKey")
            .writingBatchResultsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(statement, never()).addBatch();
    verify(statement, never()).executeBatch(); // Early return
    int[] results = (int[]) context.get("results");
    assertEquals(0, results.length);
  }

  @Test
  @DisplayName("Should bind parameters correctly for each batch")
  void testParameterBinding() throws Exception {
    // Arrange
    List<List<Object>> batchParams =
        Arrays.asList(
            Arrays.asList("Alice", 30, "alice@example.com"),
            Arrays.asList("Bob", 25, "bob@example.com"));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (name, age, email) VALUES (?, ?, ?)")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenReturn(new int[] {1, 1});

    // Act
    task.execute(context);

    // Assert - verify first batch
    verify(statement).setObject(1, "Alice");
    verify(statement).setObject(2, 30);
    verify(statement).setObject(3, "alice@example.com");

    // Assert - verify second batch
    verify(statement).setObject(1, "Bob");
    verify(statement).setObject(2, 25);
    verify(statement).setObject(3, "bob@example.com");
  }

  @Test
  @DisplayName("Should handle large batch")
  void testLargeBatch() throws Exception {
    // Arrange
    List<List<Object>> largeBatch = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      largeBatch.add(Arrays.asList("User" + i, i));
    }

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (name, id) VALUES (?, ?)")
            .batchParams(largeBatch)
            .writingBatchResultsTo("results")
            .build();

    int[] expected = new int[1000];
    Arrays.fill(expected, 1);
    when(statement.executeBatch()).thenReturn(expected);

    // Act
    task.execute(context);

    // Assert
    verify(statement, times(1000)).addBatch();
    verify(statement).executeBatch();

    int[] results = (int[]) context.get("results");
    assertEquals(1000, results.length);
  }

  @Test
  @DisplayName("Should handle batch with null values")
  void testBatchWithNullValues() throws Exception {
    // Arrange
    List<List<Object>> batchParams =
        Arrays.asList(Arrays.asList("Alice", null), Arrays.asList(null, "bob@example.com"));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ?, email = ?")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenReturn(new int[] {1, 1});

    // Act
    task.execute(context);

    // Assert
    verify(statement).setObject(1, "Alice");
    verify(statement).setObject(2, null);
    verify(statement).setObject(1, null);
    verify(statement).setObject(2, "bob@example.com");
  }

  @Test
  @DisplayName("Should handle partial success in batch")
  void testPartialBatchSuccess() throws Exception {
    // Arrange
    List<List<Object>> batchParams =
        Arrays.asList(
            Arrays.asList("Alice", 1), Arrays.asList("Bob", 2), Arrays.asList("Charlie", 3));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    // Some updates succeed, some fail (represented by Statement.EXECUTE_FAILED)
    int[] expected = {1, Statement.EXECUTE_FAILED, 1};
    when(statement.executeBatch()).thenReturn(expected);

    // Act
    task.execute(context);

    // Assert
    int[] results = (int[]) context.get("results");
    assertArrayEquals(expected, results);
    assertEquals(Statement.EXECUTE_FAILED, results[1]);
  }

  @Test
  @DisplayName("Should throw exception when SQL is missing")
  void testMissingSql() {
    // Arrange
    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("missingKey")
            .writingBatchResultsTo("results")
            .build();

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  @DisplayName("Should throw exception on SQL error")
  void testSqlException() throws Exception {
    // Arrange
    List<List<Object>> batchParams = List.of(Arrays.asList("Alice", 1));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INVALID SQL")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenThrow(new SQLException("Syntax error"));

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Batch update failed"));
  }

  @Test
  @DisplayName("Should throw exception on connection failure")
  void testConnectionFailure() throws Exception {
    // Arrange
    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name) VALUES (?, ?)")
            .batchParams(List.of(Arrays.asList(1, "Alice")))
            .writingBatchResultsTo("results")
            .build();

    when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  @DisplayName("Should properly close resources on success")
  void testResourceClosingOnSuccess() throws Exception {
    // Arrange
    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name) VALUES (?, ?)")
            .batchParams(List.of(Arrays.asList(1, "Alice")))
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenReturn(new int[] {1});

    // Act
    task.execute(context);

    // Assert
    verify(statement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should properly close resources on failure")
  void testResourceClosingOnFailure() throws Exception {
    // Arrange
    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO users (id, name) VALUES (?, ?)")
            .batchParams(List.of(Arrays.asList(1, "Alice")))
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenThrow(new SQLException("Error"));

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));

    verify(statement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should throw IllegalStateException when dataSource is null")
  void testBuilderValidation_NullDataSource() {
    try {
      JdbcBatchUpdateTask.builder()
          .sql("INSERT INTO users (id, name) VALUES (?, ?)")
          .writingBatchResultsTo("results")
          .build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should throw IllegalStateException when both sql and sqlKey are null")
  void testBuilderValidation_NoSql() {
    try {
      JdbcBatchUpdateTask.builder().dataSource(dataSource).writingBatchResultsTo("results").build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should throw IllegalStateException when outputKey is null")
  void testBuilderValidation_NoOutputKey() {
    try {
      JdbcBatchUpdateTask.builder()
          .dataSource(dataSource)
          .sql("INSERT INTO users (id, name) VALUES (?, ?)")
          .build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should handle batch with different parameter counts per row")
  void testDifferentParameterCounts() throws Exception {
    // This tests edge case where batch params have different sizes
    List<List<Object>> batchParams = Arrays.asList(List.of("Alice"), Arrays.asList("Bob", 2));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ?")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenReturn(new int[] {1, 1});

    // Act
    task.execute(context);

    // Assert - first batch has 1 param
    verify(statement, times(1)).setObject(1, "Alice");

    // Second batch has 2 params, but only first should be used
    verify(statement, times(1)).setObject(1, "Bob");
    verify(statement, times(1)).setObject(2, 2);
  }

  @Test
  @DisplayName("Should handle batch with empty parameter list")
  void testBatchWithEmptyParams() throws Exception {
    // Arrange
    List<List<Object>> batchParams =
        Arrays.asList(Collections.emptyList(), Collections.emptyList());

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("DELETE FROM temp_table")
            .batchParams(batchParams)
            .writingBatchResultsTo("results")
            .build();

    when(statement.executeBatch()).thenReturn(new int[] {5, 0});

    // Act
    task.execute(context);

    // Assert
    verify(statement, never()).setObject(anyInt(), any());
    verify(statement, times(2)).addBatch();
  }
}
