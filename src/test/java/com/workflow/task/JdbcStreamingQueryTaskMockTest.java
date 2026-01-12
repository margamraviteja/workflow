package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcStreamingQueryTaskMockTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;
  @Mock private ResultSetMetaData metaData;

  private JdbcStreamingQueryTask task;
  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
    task = JdbcStreamingQueryTask.builder().dataSource(dataSource).build();
  }

  private void mockDatabase() throws SQLException {
    // Arrange mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metaData);
  }

  @Test
  void testSuccessfulExecution() throws SQLException {
    mockDatabase();
    context.put("sql", "SELECT * FROM users");
    List<Map<String, Object>> capturedRows = new ArrayList<>();
    context.put("rowCallback", (Consumer<Map<String, Object>>) capturedRows::add);

    // Mock 2 rows
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("name");
    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getObject(1)).thenReturn("Alice", "Bob");

    task.execute(context);

    assertEquals(2, capturedRows.size());
    assertEquals("Alice", capturedRows.getFirst().get("name"));
    assertEquals(2L, context.get("rowCount"));
    verify(statement).setFetchSize(1000);
  }

  @Test
  void testMissingSqlThrowsException() {
    context.put("rowCallback", (Consumer<Map<String, Object>>) row -> {});
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  void testMissingCallbackThrowsException() {
    context.put("sql", "SELECT 1");
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  void testSqlExceptionWrapsInTaskException() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    context.put("sql", "SELECT * FROM error");
    context.put("rowCallback", (Consumer<Map<String, Object>>) row -> {});
    when(statement.executeQuery()).thenThrow(new SQLException("DB Down"));

    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  void testCallbackExceptionStopsProcessing() throws SQLException {
    mockDatabase();
    context.put("sql", "SELECT * FROM users");
    context.put(
        "rowCallback",
        (Consumer<Map<String, Object>>)
            row -> {
              throw new RuntimeException("Callback Crash");
            });

    when(resultSet.next()).thenReturn(true);
    when(metaData.getColumnCount()).thenReturn(1);

    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }
}
