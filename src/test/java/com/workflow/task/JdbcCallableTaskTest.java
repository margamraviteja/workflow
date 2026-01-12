package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcCallableTaskTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private CallableStatement callableStatement;

  private JdbcCallableTask task;
  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();

    task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .readingCallFrom("callSql")
            .readingInParametersFrom("inParams")
            .readingOutParametersFrom("outParams")
            .writingOutValuesTo("outResults")
            .writingResultSetsTo("results")
            .build();
  }

  @Test
  void testExecute_SuccessfulProcedureCallWithOutParams() throws SQLException {
    // Arrange mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);

    // Arrange
    context.put("callSql", "{call my_proc(?, ?)}");

    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, "input_val");
    context.put("inParams", inParams);

    Map<Integer, Integer> outConfig = new HashMap<>();
    outConfig.put(2, Types.INTEGER);
    context.put("outParams", outConfig);

    when(callableStatement.execute()).thenReturn(false); // No result sets
    when(callableStatement.getObject(2)).thenReturn(100);

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, "input_val");
    verify(callableStatement).registerOutParameter(2, Types.INTEGER);

    Map<Integer, Object> outResults = context.getTyped("outResults", new TypeReference<>() {});
    assertEquals(100, outResults.get(2));
  }

  @Test
  void testExecute_ThrowsExceptionOnSqlError() throws SQLException {
    // Arrange mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);

    context.put("callSql", "{call error_proc()}");
    when(connection.prepareCall(anyString())).thenThrow(new SQLException("DB Connection Lost"));

    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  void testMissingSql() {
    // No callSql put into context
    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(ex.getMessage().contains("Call statement not found"));
  }

  @Test
  void testMultipleResultSets() throws SQLException {
    // Arrange mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);

    context.put("callSql", "{call get_multiple_reports()}");

    // Mock first ResultSet
    ResultSet rs1 = mock(ResultSet.class);
    ResultSetMetaData meta1 = mock(ResultSetMetaData.class);
    when(meta1.getColumnCount()).thenReturn(1);
    when(meta1.getColumnLabel(1)).thenReturn("first_col");
    when(rs1.getMetaData()).thenReturn(meta1);
    when(rs1.next()).thenReturn(true, false); // One row
    when(rs1.getObject(1)).thenReturn("val1");

    // Mock second ResultSet
    ResultSet rs2 = mock(ResultSet.class);
    ResultSetMetaData meta2 = mock(ResultSetMetaData.class);
    when(meta2.getColumnCount()).thenReturn(1);
    when(meta2.getColumnLabel(1)).thenReturn("second_col");
    when(rs2.getMetaData()).thenReturn(meta2);
    when(rs2.next()).thenReturn(true, false); // One row
    when(rs2.getObject(1)).thenReturn("val2");

    // Set up the execution flow
    when(callableStatement.execute()).thenReturn(true); // Has first result set
    when(callableStatement.getResultSet()).thenReturn(rs1, rs2);
    when(callableStatement.getMoreResults())
        .thenReturn(true, false); // More results for rs2, then stop

    task.execute(context);

    List<List<Map<String, Object>>> allResults =
        context.getTyped("results", new TypeReference<>() {});
    assertEquals(2, allResults.size(), "Should contain two distinct result sets");
    assertEquals("val1", allResults.get(0).getFirst().get("first_col"));
    assertEquals("val2", allResults.get(1).getFirst().get("second_col"));
  }

  @Test
  void testInOutParameters() throws SQLException {
    // Arrange mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);

    context.put("callSql", "{call increment_counter(?)}");

    // Parameter 1 is both IN and OUT
    context.put("inParams", Map.of(1, 10));
    context.put("outParams", Map.of(1, Types.INTEGER));

    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(1)).thenReturn(11);

    task.execute(context);

    verify(callableStatement).setObject(1, 10);
    verify(callableStatement).registerOutParameter(1, Types.INTEGER);

    Map<Integer, Object> outValues = context.getTyped("outValues", new TypeReference<>() {});
    assertNull(outValues);
  }

  @Test
  void testSqlExceptionHandling() throws SQLException {
    // Arrange mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);

    context.put("callSql", "{call bad_syntax}");
    when(callableStatement.execute()).thenThrow(new SQLException("Syntax Error", "42000", 1064));

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));

    assertInstanceOf(SQLException.class, ex.getCause());
    assertTrue(ex.getMessage().contains("Syntax Error"));
  }
}
