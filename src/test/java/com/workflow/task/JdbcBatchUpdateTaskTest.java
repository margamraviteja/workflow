package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import java.sql.*;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcBatchUpdateTaskTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;

  private JdbcBatchUpdateTask task;

  @BeforeEach
  void setUp() throws Exception {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);

    task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingBatchParamsFrom("params")
            .writingBatchResultsTo("results")
            .build();
  }

  @Test
  void execute_ShouldInvokeJdbcBatchMethods() throws Exception {
    // Arrange
    WorkflowContext context = new WorkflowContext();
    context.put("sql", "INSERT INTO test (val) VALUES (?)");
    context.put("params", List.of(List.of("A"), List.of("B")));

    int[] expectedCounts = {1, 1};
    when(statement.executeBatch()).thenReturn(expectedCounts);

    // Act
    task.execute(context);

    // Assert
    verify(statement, times(2)).addBatch();
    verify(statement).executeBatch();

    int[] actualResults = (int[]) context.get("results");
    assertArrayEquals(expectedCounts, actualResults);
  }
}
