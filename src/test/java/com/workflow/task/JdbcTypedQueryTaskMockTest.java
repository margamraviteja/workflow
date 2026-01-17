package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;

@ExtendWith(MockitoExtension.class)
class JdbcTypedQueryTaskMockTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  private void mockDatabase() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
  }

  @Test
  void testSuccessfulMapping() throws SQLException {
    mockDatabase();
    context.put("sql", "SELECT name FROM users");
    context.put("rowMapper", (JdbcTypedQueryTask.RowMapper<String>) (rs, _) -> rs.getString(1));

    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getString(1)).thenReturn("Alice", "Bob");

    JdbcTypedQueryTask<String> task =
        JdbcTypedQueryTask.<String>builder().dataSource(dataSource).build();

    task.execute(context);

    List<String> results = context.getTyped("results", new TypeReference<>() {});
    assertEquals(2, results.size());
    assertEquals("Alice", results.get(0));
    assertEquals("Bob", results.get(1));
  }

  @Test
  void testParameterBinding() throws SQLException {
    mockDatabase();
    List<Object> params = List.of("active", 101);
    context.put("sql", "SELECT * FROM items WHERE status = ? AND id = ?");
    context.put("params", params);
    context.put("rowMapper", (JdbcTypedQueryTask.RowMapper<Integer>) (rs, _) -> rs.getInt(1));

    JdbcTypedQueryTask<Integer> task =
        JdbcTypedQueryTask.<Integer>builder().dataSource(dataSource).build();

    task.execute(context);

    verify(statement).setObject(1, "active");
    verify(statement).setObject(2, 101);
  }

  @Test
  void testMapperExceptionWrapsInTaskException() throws SQLException {
    mockDatabase();
    context.put("sql", "SELECT 1");
    context.put(
        "rowMapper",
        (JdbcTypedQueryTask.RowMapper<String>)
            (_, _) -> {
              throw new SQLException("Mapping failed");
            });
    when(resultSet.next()).thenReturn(true);

    JdbcTypedQueryTask<String> task =
        JdbcTypedQueryTask.<String>builder().dataSource(dataSource).build();

    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  void testConnectionFailureThrowsException() throws SQLException {
    when(dataSource.getConnection()).thenThrow(new SQLException("Network error"));
    context.put("sql", "SELECT 1");
    context.put("rowMapper", (JdbcTypedQueryTask.RowMapper<Integer>) (_, _) -> 1);

    JdbcTypedQueryTask<Integer> task =
        JdbcTypedQueryTask.<Integer>builder().dataSource(dataSource).build();

    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }
}
