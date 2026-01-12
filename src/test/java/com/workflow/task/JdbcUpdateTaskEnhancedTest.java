package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
@DisplayName("JdbcUpdateTask - Mock Tests")
class JdbcUpdateTaskEnhancedTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement preparedStatement;

  private WorkflowContext context;

  @BeforeEach
  void setUp() throws Exception {
    context = new WorkflowContext();
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
  }

  @Test
  @DisplayName("Should execute update with direct SQL and params")
  void testDirectSqlAndParams() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .params(Arrays.asList("Alice", 1))
            .writingRowsAffectedTo("result")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(connection).prepareStatement("UPDATE users SET name = ? WHERE id = ?");
    verify(preparedStatement).setObject(1, "Alice");
    verify(preparedStatement).setObject(2, 1);
    verify(preparedStatement).executeUpdate();
    assertEquals(1, context.get("result"));
  }

  @Test
  @DisplayName("Should execute update with SQL from context")
  void testSqlFromContext() throws Exception {
    // Arrange
    context.put("sql", "UPDATE users SET status = ? WHERE id = ?");
    context.put("params", Arrays.asList("ACTIVE", 42));

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingRowsAffectedTo("rowsAffected")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(connection).prepareStatement("UPDATE users SET status = ? WHERE id = ?");
    verify(preparedStatement).setObject(1, "ACTIVE");
    verify(preparedStatement).setObject(2, 42);
    assertEquals(1, context.get("rowsAffected"));
  }

  @Test
  @DisplayName("Should execute update with mixed mode - direct SQL, context params")
  void testMixedMode() throws Exception {
    // Arrange
    context.put("updateParams", Arrays.asList("INACTIVE", 99));

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET status = ? WHERE id = ?")
            .readingParamsFrom("updateParams")
            .writingRowsAffectedTo("affected")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(preparedStatement).setObject(1, "INACTIVE");
    verify(preparedStatement).setObject(2, 99);
    assertEquals(1, context.get("affected"));
  }

  @Test
  @DisplayName("Should handle empty parameters")
  void testEmptyParameters() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("DELETE FROM logs WHERE created_at < NOW()")
            .params(Collections.emptyList())
            .writingRowsAffectedTo("deleted")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(5);

    // Act
    task.execute(context);

    // Assert
    verify(preparedStatement, never()).setObject(anyInt(), any());
    assertEquals(5, context.get("deleted"));
  }

  @Test
  @DisplayName("Should handle null parameters from context as empty list")
  void testNullParametersFromContext() throws Exception {
    // Arrange
    context.put("sql", "TRUNCATE TABLE temp_table");
    // No params in context

    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params") // This key doesn't exist
            .writingRowsAffectedTo("result")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(0);

    // Act
    task.execute(context);

    // Assert
    verify(preparedStatement, never()).setObject(anyInt(), any());
    assertEquals(0, context.get("result"));
  }

  @Test
  @DisplayName("Should return zero when no rows affected")
  void testNoRowsAffected() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ? WHERE id = ?")
            .params(Arrays.asList("NonExistent", 99999))
            .writingRowsAffectedTo("count")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(0);

    // Act
    task.execute(context);

    // Assert
    assertEquals(0, context.get("count"));
  }

  @Test
  @DisplayName("Should handle multiple parameters correctly")
  void testMultipleParameters() throws Exception {
    // Arrange
    List<Object> params = Arrays.asList("John", "Doe", 30, "john@example.com", 123);
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql(
                "INSERT INTO users (first_name, last_name, age, email, dept_id) VALUES (?, ?, ?, ?,"
                    + " ?)")
            .params(params)
            .writingRowsAffectedTo("inserted")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(preparedStatement).setObject(1, "John");
    verify(preparedStatement).setObject(2, "Doe");
    verify(preparedStatement).setObject(3, 30);
    verify(preparedStatement).setObject(4, "john@example.com");
    verify(preparedStatement).setObject(5, 123);
  }

  @Test
  @DisplayName("Should throw exception when SQL is missing from context")
  void testMissingSqlFromContext() {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("missingKey")
            .writingRowsAffectedTo("result")
            .build();

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  @DisplayName("Should throw exception on SQL error")
  void testSqlException() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INVALID SQL SYNTAX")
            .params(Collections.emptyList())
            .writingRowsAffectedTo("result")
            .build();

    when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Syntax error"));

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Update execution failed"));
  }

  @Test
  @DisplayName("Should throw exception on connection failure")
  void testConnectionFailure() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ?")
            .params(List.of("Test"))
            .writingRowsAffectedTo("result")
            .build();

    when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Update execution failed"));
  }

  @Test
  @DisplayName("Should properly close resources on success")
  void testResourceClosingOnSuccess() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ?")
            .params(List.of("Test"))
            .writingRowsAffectedTo("result")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should properly close resources on failure")
  void testResourceClosingOnFailure() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET name = ?")
            .params(List.of("Test"))
            .writingRowsAffectedTo("result")
            .build();

    when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Error"));

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));

    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should throw IllegalStateException when dataSource is null")
  void testBuilderValidation_NullDataSource() {
    try {
      JdbcUpdateTask.builder()
          .sql("UPDATE users SET name = ?")
          .writingRowsAffectedTo("result")
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
      JdbcUpdateTask.builder().dataSource(dataSource).writingRowsAffectedTo("result").build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should throw IllegalStateException when outputKey is null")
  void testBuilderValidation_NoOutputKey() {
    try {
      JdbcUpdateTask.builder().dataSource(dataSource).sql("UPDATE users SET name = ?").build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should handle batch of multiple updates sequentially")
  void testMultipleUpdatesInSequence() throws Exception {
    // Arrange
    JdbcUpdateTask task1 =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET status = ? WHERE id = ?")
            .params(Arrays.asList("ACTIVE", 1))
            .writingRowsAffectedTo("result1")
            .build();

    JdbcUpdateTask task2 =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET status = ? WHERE id = ?")
            .params(Arrays.asList("INACTIVE", 2))
            .writingRowsAffectedTo("result2")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task1.execute(context);
    task2.execute(context);

    // Assert
    assertEquals(1, context.get("result1"));
    assertEquals(1, context.get("result2"));
    verify(preparedStatement, times(2)).executeUpdate();
  }

  @Test
  @DisplayName("Should handle null values in parameters")
  void testNullParameterValues() throws Exception {
    // Arrange
    JdbcUpdateTask task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE users SET middle_name = ? WHERE id = ?")
            .params(Arrays.asList(null, 1))
            .writingRowsAffectedTo("result")
            .build();

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(preparedStatement).setObject(1, null);
    verify(preparedStatement).setObject(2, 1);
  }
}
