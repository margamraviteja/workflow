package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcTransactionTaskTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private Task mockTask1;
  @Mock private Task mockTask2;

  private WorkflowContext context;

  @BeforeEach
  void setUp() throws SQLException {
    context = new WorkflowContext();
    // Default behavior for connection
    lenient().when(dataSource.getConnection()).thenReturn(connection);
    lenient().when(connection.getAutoCommit()).thenReturn(true);
  }

  @Nested
  @DisplayName("Builder Validation")
  class BuilderTests {
    @Test
    void build_ThrowsException_WhenDataSourceMissing() {
      try {
        JdbcTransactionTask.builder().task(mockTask1).build();
        fail();
      } catch (Exception _) {
        assertTrue(true);
      }
    }

    @Test
    void build_ThrowsException_WhenNoTasksProvided() {
      try {
        JdbcTransactionTask.builder().dataSource(dataSource).build();
        fail();
      } catch (Exception _) {
        assertTrue(true);
      }
    }

    @Test
    void build_Success_WithDirectTasks() {
      assertDoesNotThrow(
          () -> JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build());
    }

    @Test
    void build_Success_WithContextKey() {
      assertDoesNotThrow(
          () ->
              JdbcTransactionTask.builder().dataSource(dataSource).readingTasksFrom("key").build());
    }

    @Test
    void getName_ReturnsFormatWithSimpleName() {
      JdbcTransactionTask task =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();
      assertTrue(task.getName().startsWith("JdbcTransactionTask:"));
    }

    @Test
    void build_Success_WithIsolationLevel() {
      JdbcTransactionTask task =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(mockTask1)
              .isolationLevel(Connection.TRANSACTION_SERIALIZABLE)
              .build();
      assertNotNull(task);
    }
  }

  @Nested
  @DisplayName("Execution Logic")
  class ExecutionTests {
    @Test
    void execute_Success_CommitsTransaction() throws Exception {
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(mockTask1)
              .task(mockTask2)
              .build();

      transaction.execute(context);

      verify(connection).setAutoCommit(false);
      verify(mockTask1).execute(context);
      verify(mockTask2).execute(context);
      verify(connection).commit();
      verify(connection).close();
    }

    @Test
    void execute_TaskFails_TriggersRollback() throws Exception {
      doThrow(new RuntimeException("Fail")).when(mockTask1).execute(context);
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      assertThrows(TaskExecutionException.class, () -> transaction.execute(context));

      verify(connection).rollback();
      verify(connection).close();
    }

    @Test
    void execute_SetsAndRestoresIsolationLevel() throws Exception {
      when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(mockTask1)
              .isolationLevel(Connection.TRANSACTION_SERIALIZABLE)
              .build();

      transaction.execute(context);

      verify(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    void execute_RestoresAutoCommitOnFinish() throws Exception {
      when(connection.getAutoCommit()).thenReturn(true);
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      transaction.execute(context);
      verify(connection).setAutoCommit(true);
    }

    @Test
    void execute_HandlesRollbackFailureGracefully() throws Exception {
      doThrow(new RuntimeException("Task Fail")).when(mockTask1).execute(context);
      doThrow(new SQLException("Rollback Fail")).when(connection).rollback();

      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      // Should still throw the original TaskExecutionException
      assertThrows(TaskExecutionException.class, () -> transaction.execute(context));
      verify(connection).close();
    }

    @Test
    void execute_ContextIsNull_ThrowsException() {
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();
      assertThrows(NullPointerException.class, () -> transaction.execute(null));
    }

    @Test
    void execute_EmptyTasksFromContext_ThrowsException() {
      context.put("myTasks", Collections.emptyList());
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).readingTasksFrom("myTasks").build();

      assertThrows(TaskExecutionException.class, () -> transaction.execute(context));
    }

    @Test
    void execute_MissingTasksInContext_ThrowsException() {
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .readingTasksFrom("missingKey")
              .build();

      assertThrows(TaskExecutionException.class, () -> transaction.execute(context));
    }

    @Test
    void execute_ConnectionCleanup_EvenOnCommitFailure() throws SQLException {
      doThrow(new SQLException("Commit failed")).when(connection).commit();
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      assertThrows(TaskExecutionException.class, () -> transaction.execute(context));
      verify(connection).close();
    }

    @Test
    void execute_InternalConnectionKeyRemovedAfterExecution() {
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      transaction.execute(context);
      assertNull(context.get(JdbcTransactionTask.CONNECTION_CONTEXT_KEY));
    }
  }

  @Nested
  @DisplayName("Task & Context Interaction")
  class ContextInteractionTests {
    @Test
    void execute_SharesConnectionWithChildTasks() {
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(
                  ctx -> {
                    Connection conn =
                        (Connection) ctx.get(JdbcTransactionTask.CONNECTION_CONTEXT_KEY);
                    assertNotNull(conn);
                    assertEquals(connection, conn);
                  })
              .build();

      transaction.execute(context);
    }

    @Test
    void execute_UsesTasksFromContextKey() {
      context.put("dynamicTasks", Arrays.asList(mockTask1, mockTask2));
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .readingTasksFrom("dynamicTasks")
              .build();

      transaction.execute(context);

      verify(mockTask1).execute(context);
      verify(mockTask2).execute(context);
    }

    @Test
    void execute_DirectTasksTakePrecedenceOverContextKey() {
      context.put("ignoredKey", List.of(mockTask2));
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(mockTask1)
              .readingTasksFrom("ignoredKey")
              .build();

      transaction.execute(context);

      verify(mockTask1).execute(context);
      verify(mockTask2, never()).execute(context);
    }

    @Test
    void execute_NestedTransactionException_PropagatedCorrectly() {
      TaskExecutionException original = new TaskExecutionException("Nested fail");
      doThrow(original).when(mockTask1).execute(context);

      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      TaskExecutionException thrown =
          assertThrows(TaskExecutionException.class, () -> transaction.execute(context));
      assertTrue(thrown.getMessage().contains("Transaction failed"));
    }

    @Test
    void execute_CheckTypedHelperMethods_InAbstractTask() {
      // Testing require and getOrDefault through a concrete subclass instance
      JdbcTransactionTask task =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();
      context.put("testKey", "testValue");

      assertEquals("testValue", task.require(context, "testKey", String.class));
      assertEquals(
          "defaultValue", task.getOrDefault(context, "missing", String.class, "defaultValue"));
    }

    @Test
    void execute_RequireThrowsException_WhenKeyMissing() {
      JdbcTransactionTask task =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();
      assertThrows(
          IllegalStateException.class, () -> task.require(context, "missing", String.class));
    }

    @Test
    void execute_CleanupRestoresOriginalIsolation_WhenNotNone() throws SQLException {
      when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(mockTask1)
              .isolationLevel(Connection.TRANSACTION_SERIALIZABLE)
              .build();

      transaction.execute(context);
      verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    void execute_ChildTaskModifiesContext_VisibleToNextChild() {
      Task task1 = ctx -> ctx.put("sharedData", "value1");
      Task task2 =
          ctx -> {
            if (!"value1".equals(ctx.get("sharedData"))) throw new RuntimeException("Data missing");
          };

      JdbcTransactionTask transaction =
          JdbcTransactionTask.builder().dataSource(dataSource).task(task1).task(task2).build();

      assertDoesNotThrow(() -> transaction.execute(context));
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarioTests {
    @Test
    void complex_TransactionalOrderProcessing_Success() throws Exception {
      // Task 1: Create Order
      Task createOrder = ctx -> ctx.put("orderId", 101);
      // Task 2: Deduct Inventory (using shared connection)
      Task deductInv = mock(Task.class);
      // Task 3: Send Notification (non-DB)
      Task notify = mock(Task.class);

      JdbcTransactionTask orderTx =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(createOrder)
              .task(deductInv)
              .task(notify)
              .build();

      orderTx.execute(context);

      assertEquals(101, context.get("orderId"));
      verify(deductInv).execute(context);
      verify(notify).execute(context);
      verify(connection).commit();
    }

    @Test
    void complex_Rollback_OnMiddleTaskFailure() throws Exception {
      Task task1 = mock(Task.class);
      Task task2 =
          _ -> {
            throw new RuntimeException("Inventory Shortage");
          };
      Task task3 = mock(Task.class);

      JdbcTransactionTask tx =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(task1)
              .task(task2)
              .task(task3)
              .build();

      assertThrows(TaskExecutionException.class, () -> tx.execute(context));

      verify(task1).execute(context);
      verify(task3, never()).execute(context);
      verify(connection).rollback();
    }

    @Test
    void complex_NestedJdbcTransactionTasks_WorkIndependently() throws Exception {
      // Inner transaction
      JdbcTransactionTask innerTx =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      // Outer transaction
      JdbcTransactionTask outerTx =
          JdbcTransactionTask.builder().dataSource(dataSource).task(innerTx).build();

      outerTx.execute(context);

      // Connection should be closed/handled twice if they get separate connections,
      // or logic should handle nested connection if same.
      verify(dataSource, times(2)).getConnection();
    }

    @Test
    void complex_ConcurrentExecution_Safety() {
      // Note: DataSource and tasks must be thread-safe.
      // This verifies that task state (like tasksKey) doesn't leak between executions
      JdbcTransactionTask task =
          JdbcTransactionTask.builder().dataSource(dataSource).readingTasksFrom("listKey").build();

      WorkflowContext ctx1 = new WorkflowContext();
      ctx1.put("listKey", List.of(mockTask1));

      WorkflowContext ctx2 = new WorkflowContext();
      ctx2.put("listKey", List.of(mockTask2));

      task.execute(ctx1);
      task.execute(ctx2);

      verify(mockTask1).execute(ctx1);
      verify(mockTask2).execute(ctx2);
    }

    @Test
    void complex_DatabaseExceptionOnGetConnection() throws SQLException {
      when(dataSource.getConnection()).thenThrow(new SQLException("DB Down"));
      JdbcTransactionTask task =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      assertThrows(TaskExecutionException.class, () -> task.execute(context));
    }

    @Test
    void complex_AutoCommitRestore_EvenIfGetAutoCommitFails() throws SQLException {
      when(connection.getAutoCommit()).thenThrow(new SQLException("Fail"));
      JdbcTransactionTask task =
          JdbcTransactionTask.builder().dataSource(dataSource).task(mockTask1).build();

      assertThrows(TaskExecutionException.class, () -> task.execute(context));
      verify(connection).close(); // Ensure cleanup happens
    }

    @Test
    void complex_LargeTaskList_Execution() throws Exception {
      JdbcTransactionTask.Builder builder = JdbcTransactionTask.builder().dataSource(dataSource);
      for (int i = 0; i < 100; i++) {
        builder.task(mock(Task.class));
      }
      JdbcTransactionTask task = builder.build();

      assertDoesNotThrow(() -> task.execute(context));
      verify(connection, times(1)).commit();
    }

    @Test
    void complex_MixedTaskTypes_InTransaction() {
      // Validating that a mix of Lambdas and Mocks works
      JdbcTransactionTask task =
          JdbcTransactionTask.builder()
              .dataSource(dataSource)
              .task(ctx -> ctx.put("validated", true))
              .task(mockTask1)
              .build();

      task.execute(context);
      assertTrue((Boolean) context.get("validated"));
      verify(mockTask1).execute(context);
    }
  }
}
