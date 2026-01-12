package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.*;
import java.sql.Date;
import java.util.*;
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
@DisplayName("JdbcCallableTask - Enhanced Mock Tests")
class JdbcCallableTaskEnhancedTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private CallableStatement callableStatement;
  @Mock private ResultSet resultSet;
  @Mock private ResultSetMetaData metaData;

  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  @Test
  @DisplayName("Should execute procedure with IN parameters only")
  void testProcedureWithInParametersOnly() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);

    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, 100);
    inParams.put(2, "Active");

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call update_status(?, ?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, 100);
    verify(callableStatement).setObject(2, "Active");
    verify(callableStatement).execute();
    verify(callableStatement, never()).registerOutParameter(anyInt(), anyInt());
  }

  @Test
  @DisplayName("Should execute procedure with OUT parameters only")
  void testProcedureWithOutParametersOnly() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(1)).thenReturn(42);

    Map<Integer, Integer> outParams = new HashMap<>();
    outParams.put(1, Types.INTEGER);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_count(?)}")
            .outParameters(outParams)
            .writingOutValuesTo("outResults")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).registerOutParameter(1, Types.INTEGER);
    verify(callableStatement).execute();

    Map<Integer, Object> outResults = context.getTyped("outResults", new TypeReference<>() {});
    assertEquals(42, outResults.get(1));
  }

  @Test
  @DisplayName("Should execute procedure with both IN and OUT parameters")
  void testProcedureWithInAndOutParameters() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(2)).thenReturn(150);
    when(callableStatement.getObject(3)).thenReturn("SUCCESS");

    Map<Integer, Object> inParams = Map.of(1, 100);
    Map<Integer, Integer> outParams = new HashMap<>();
    outParams.put(2, Types.INTEGER);
    outParams.put(3, Types.VARCHAR);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call process_order(?, ?, ?)}")
            .inParameters(inParams)
            .outParameters(outParams)
            .writingOutValuesTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, 100);
    verify(callableStatement).registerOutParameter(2, Types.INTEGER);
    verify(callableStatement).registerOutParameter(3, Types.VARCHAR);

    Map<Integer, Object> results = context.getTyped("results", new TypeReference<>() {});
    assertEquals(150, results.get(2));
    assertEquals("SUCCESS", results.get(3));
  }

  @Test
  @DisplayName("Should execute function with return value")
  void testFunctionWithReturnValue() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(1)).thenReturn(250.50);

    Map<Integer, Object> inParams = Map.of(2, 200.00);
    Map<Integer, Integer> outParams = Map.of(1, Types.DECIMAL);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{? = call calculate_total(?)}")
            .inParameters(inParams)
            .outParameters(outParams)
            .writingOutValuesTo("result")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(2, 200.00);
    verify(callableStatement).registerOutParameter(1, Types.DECIMAL);

    Map<Integer, Object> result = context.getTyped("result", new TypeReference<>() {});
    assertEquals(250.50, result.get(1));
  }

  @Test
  @DisplayName("Should process single result set")
  void testSingleResultSet() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(true);
    when(callableStatement.getResultSet()).thenReturn(resultSet);
    when(callableStatement.getMoreResults()).thenReturn(false);

    when(resultSet.getMetaData()).thenReturn(metaData);
    when(metaData.getColumnCount()).thenReturn(2);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(metaData.getColumnLabel(2)).thenReturn("name");

    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getObject(1)).thenReturn(1, 2);
    when(resultSet.getObject(2)).thenReturn("Alice", "Bob");

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_users()}")
            .writingResultSetsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results = context.getTyped("results", new TypeReference<>() {});
    assertEquals(1, results.size());
    assertEquals(2, results.getFirst().size());
    assertEquals("Alice", results.getFirst().get(0).get("name"));
    assertEquals("Bob", results.getFirst().get(1).get("name"));
  }

  @Test
  @DisplayName("Should process multiple result sets")
  void testMultipleResultSets() throws Exception {
    // Arrange
    ResultSet resultSet2 = mock(ResultSet.class);
    ResultSetMetaData metaData2 = mock(ResultSetMetaData.class);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(true);

    // First result set
    when(callableStatement.getResultSet()).thenReturn(resultSet, resultSet2);
    when(callableStatement.getMoreResults()).thenReturn(true, false);

    // Setup first result set
    when(resultSet.getMetaData()).thenReturn(metaData);
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("user_id");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(1);

    // Setup second result set
    when(resultSet2.getMetaData()).thenReturn(metaData2);
    when(metaData2.getColumnCount()).thenReturn(1);
    when(metaData2.getColumnLabel(1)).thenReturn("order_id");
    when(resultSet2.next()).thenReturn(true, false);
    when(resultSet2.getObject(1)).thenReturn(101);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_user_and_orders()}")
            .writingResultSetsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results = context.getTyped("results", new TypeReference<>() {});
    assertEquals(2, results.size());
    assertEquals(1, results.get(0).getFirst().get("user_id"));
    assertEquals(101, results.get(1).getFirst().get("order_id"));
  }

  @Test
  @DisplayName("Should use context mode for call and parameters")
  void testContextMode() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(2)).thenReturn(100);

    context.put("callSql", "{call my_procedure(?, ?)}");
    Map<Integer, Object> inParams = Map.of(1, "test");
    context.put("inParams", inParams);
    Map<Integer, Integer> outParams = Map.of(2, Types.INTEGER);
    context.put("outParams", outParams);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .readingCallFrom("callSql")
            .readingInParametersFrom("inParams")
            .readingOutParametersFrom("outParams")
            .writingOutValuesTo("outResults")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, "test");
    verify(callableStatement).registerOutParameter(2, Types.INTEGER);
    Map<Integer, Object> outResults = context.getTyped("outResults", new TypeReference<>() {});
    assertEquals(100, outResults.get(2));
  }

  @Test
  @DisplayName("Should handle null IN parameter values")
  void testNullInParameterValues() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);

    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, null);
    inParams.put(2, "value");

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call my_proc(?, ?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, null);
    verify(callableStatement).setObject(2, "value");
  }

  @Test
  @DisplayName("Should handle null OUT parameter return values")
  void testNullOutParameterValues() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(1)).thenReturn(null);

    Map<Integer, Integer> outParams = Map.of(1, Types.VARCHAR);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_optional_value(?)}")
            .outParameters(outParams)
            .writingOutValuesTo("result")
            .build();

    // Act
    task.execute(context);

    // Assert
    Map<Integer, Object> result = context.getTyped("result", new TypeReference<>() {});
    assertNull(result.get(1));
  }

  @Test
  @DisplayName("Should handle empty parameters")
  void testEmptyParameters() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);

    JdbcCallableTask task =
        JdbcCallableTask.builder().dataSource(dataSource).call("{call no_params_proc()}").build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).execute();
    verify(callableStatement, never()).setObject(anyInt(), any());
    verify(callableStatement, never()).registerOutParameter(anyInt(), anyInt());
  }

  @Test
  @DisplayName("Should handle empty result set")
  void testEmptyResultSet() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(true);
    when(callableStatement.getResultSet()).thenReturn(resultSet);
    when(callableStatement.getMoreResults()).thenReturn(false);

    when(resultSet.getMetaData()).thenReturn(metaData);
    when(metaData.getColumnCount()).thenReturn(1);
    when(metaData.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(false);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_empty_set()}")
            .writingResultSetsTo("results")
            .build();

    // Act
    task.execute(context);

    // Assert
    List<List<Map<String, Object>>> results = context.getTyped("results", new TypeReference<>() {});
    assertEquals(1, results.size());
    assertEquals(0, results.getFirst().size());
  }

  @Test
  @DisplayName("Should throw exception when call SQL is missing from context")
  void testMissingCallSql() {
    // Arrange
    JdbcCallableTask task =
        JdbcCallableTask.builder().dataSource(dataSource).readingCallFrom("missingKey").build();

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Call statement not found"));
  }

  @Test
  @DisplayName("Should throw exception on SQL error")
  void testSqlException() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenThrow(new SQLException("Procedure not found"));

    JdbcCallableTask task =
        JdbcCallableTask.builder().dataSource(dataSource).call("{call nonexistent_proc()}").build();

    // Act & Assert
    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(exception.getMessage().contains("Failed to execute callable statement"));
  }

  @Test
  @DisplayName("Should throw exception on connection failure")
  void testConnectionFailure() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

    JdbcCallableTask task =
        JdbcCallableTask.builder().dataSource(dataSource).call("{call my_proc()}").build();

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));
  }

  @Test
  @DisplayName("Should properly close resources on success")
  void testResourceClosingOnSuccess() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);

    JdbcCallableTask task =
        JdbcCallableTask.builder().dataSource(dataSource).call("{call my_proc()}").build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should properly close resources on failure")
  void testResourceClosingOnFailure() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenThrow(new SQLException("Error"));

    JdbcCallableTask task =
        JdbcCallableTask.builder().dataSource(dataSource).call("{call my_proc()}").build();

    // Act & Assert
    assertThrows(TaskExecutionException.class, () -> task.execute(context));

    verify(callableStatement).close();
    verify(connection).close();
  }

  @Test
  @DisplayName("Should throw IllegalStateException when dataSource is null")
  void testBuilderValidation_NullDataSource() {
    try {
      JdbcCallableTask.builder().call("{call my_proc()}").build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  @DisplayName("Should handle complex data types")
  void testComplexDataTypes() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);

    Date date = new Date(System.currentTimeMillis());
    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, date);
    inParams.put(2, 123.45);
    inParams.put(3, true);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call complex_proc(?, ?, ?)}")
            .inParameters(inParams)
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, date);
    verify(callableStatement).setObject(2, 123.45);
    verify(callableStatement).setObject(3, true);
  }

  @Test
  @DisplayName("Should support INOUT parameters")
  void testInOutParameters() throws Exception {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareCall(anyString())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenReturn(false);
    when(callableStatement.getObject(1)).thenReturn(200);

    // INOUT parameter: set as IN and register as OUT
    Map<Integer, Object> inParams = Map.of(1, 100);
    Map<Integer, Integer> outParams = Map.of(1, Types.INTEGER);

    JdbcCallableTask task =
        JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call increment_value(?)}")
            .inParameters(inParams)
            .outParameters(outParams)
            .writingOutValuesTo("result")
            .build();

    // Act
    task.execute(context);

    // Assert
    verify(callableStatement).setObject(1, 100);
    verify(callableStatement).registerOutParameter(1, Types.INTEGER);

    Map<Integer, Object> result = context.getTyped("result", new TypeReference<>() {});
    assertEquals(200, result.get(1));
  }
}
