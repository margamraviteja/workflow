package com.workflow.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcUpdateTaskTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement preparedStatement;

  private JdbcUpdateTask task;
  private WorkflowContext context;

  @BeforeEach
  void setUp() throws Exception {
    task =
        JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingRowsAffectedTo("result")
            .build();

    context = new WorkflowContext();

    // Setup mock behavior
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
  }

  @Test
  void execute_ShouldBindParametersAndStoreResult() throws Exception {
    // Arrange
    String sql = "UPDATE users SET name = ? WHERE id = ?";
    List<Object> params = List.of("John Doe", 1);
    context.put("sql", sql);
    context.put("params", params);

    when(preparedStatement.executeUpdate()).thenReturn(1);

    // Act
    task.execute(context);

    // Assert
    verify(connection).prepareStatement(sql);
    verify(preparedStatement).setObject(1, "John Doe");
    verify(preparedStatement).setObject(2, 1);
    verify(preparedStatement).executeUpdate();

    assertEquals(1, context.get("result"), "Rows affected should be 1");
  }
}
