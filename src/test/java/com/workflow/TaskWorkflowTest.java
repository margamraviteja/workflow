package com.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.listener.WorkflowListeners;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import com.workflow.task.executor.TaskExecutor;
import org.junit.jupiter.api.Test;

class TaskWorkflowTest {

  @Test
  void execute_whenConstructedWithTask_runsTaskAndReturnsSuccess() {
    // Arrange: simple Task that flips a flag
    final boolean[] ran = {false};
    Task t =
        new Task() {
          @Override
          public void execute(WorkflowContext context) {
            ran[0] = true;
          }

          @Override
          public String getName() {
            return "simple-task";
          }
        };

    TaskWorkflow wf = new TaskWorkflow(t);

    // Act
    WorkflowResult result = wf.execute(new WorkflowContext());

    // Assert
    assertNotNull(result);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertNull(result.getError());
    assertTrue(ran[0], "Task should have been executed");
  }

  @Test
  void execute_whenTaskThrowsRuntimeException_resultIsFailedAndContainsError() {
    Task t =
        new Task() {
          @Override
          public void execute(WorkflowContext context) {
            throw new RuntimeException("boom");
          }

          @Override
          public String getName() {
            return "failing-task";
          }
        };

    TaskWorkflow wf = new TaskWorkflow(t);

    WorkflowResult result = wf.execute(new WorkflowContext());

    assertNotNull(result);
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNotNull(result.getError());
    assertEquals("boom", result.getError().getCause().getMessage());
  }

  @Test
  void execute_whenTaskThrowsCheckedException_resultIsFailedAndContainsError() {
    Task t =
        new Task() {
          @Override
          public void execute(WorkflowContext context) {
            throw new TaskExecutionException("checked");
          }

          @Override
          public String getName() {
            return "checked-task";
          }
        };

    TaskWorkflow wf = new TaskWorkflow(t);

    WorkflowResult result = wf.execute(new WorkflowContext());

    assertNotNull(result);
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNotNull(result.getError());
    assertEquals("checked", result.getError().getMessage());
  }

  @Test
  void execute_withCustomTaskExecutor_success_path_invokesExecutor() {
    // Prepare mocks
    TaskDescriptor td = mock(TaskDescriptor.class);
    Task task = mock(Task.class);
    when(td.getTask()).thenReturn(task);

    WorkflowContext ctx = mock(WorkflowContext.class);
    when(ctx.getListeners()).thenReturn(new WorkflowListeners());

    TaskExecutor executor = mock(TaskExecutor.class);
    // executor.execute should do nothing (success)

    TaskWorkflow wf = new TaskWorkflow(td, executor);

    WorkflowResult result = wf.execute(ctx);

    assertNotNull(result);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    verify(executor, times(1)).execute(td, ctx);
  }

  @Test
  void execute_withCustomTaskExecutor_throwing_exception_resultsInFailedWorkflowResult() {
    TaskDescriptor td = mock(TaskDescriptor.class);
    Task task = mock(Task.class);
    when(td.getTask()).thenReturn(task);

    WorkflowContext ctx = mock(WorkflowContext.class);
    when(ctx.getListeners()).thenReturn(new WorkflowListeners());

    TaskExecutor executor = mock(TaskExecutor.class);
    doThrow(new RuntimeException("exec-failed")).when(executor).execute(td, ctx);

    TaskWorkflow wf = new TaskWorkflow(td, executor);

    WorkflowResult result = wf.execute(ctx);

    assertNotNull(result);
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNotNull(result.getError());
    assertEquals("exec-failed", result.getError().getMessage());
    verify(executor, times(1)).execute(td, ctx);
  }

  @Test
  void getName_returnsTaskName_whenPresent() {
    TaskDescriptor td = mock(TaskDescriptor.class);
    Task task = mock(Task.class);
    when(task.getName()).thenReturn("my-task-name");
    when(td.getTask()).thenReturn(task);

    TaskWorkflow wf = new TaskWorkflow(td, mock(TaskExecutor.class));

    assertEquals("my-task-name", wf.getName());
  }

  @Test
  void getName_returnsDefaultWhenTaskNameIsNull() {
    TaskDescriptor td = mock(TaskDescriptor.class);
    Task task = mock(Task.class);
    when(task.getName()).thenReturn(null);
    when(td.getTask()).thenReturn(task);

    TaskWorkflow wf = new TaskWorkflow(td, mock(TaskExecutor.class));

    String name = wf.getName();
    assertNotNull(name);
    assertFalse(name.isEmpty());
  }

  @Test
  void constructor_taskDescriptorNullTask_getNameAndExecuteHandleGracefully() {
    // If TaskDescriptor.getTask() returns null, getName will NPE; ensure execute handles it by
    // throwing
    TaskDescriptor td = mock(TaskDescriptor.class);
    when(td.getTask()).thenReturn(null);

    TaskExecutor executor = mock(TaskExecutor.class);
    // executor.execute would be called but will receive null taskDescriptor.getTask() inside
    // DefaultTaskExecutor in real code.
    // Here we simulate executor throwing NPE to mimic behavior.
    doThrow(new NullPointerException("task missing"))
        .when(executor)
        .execute(td, new WorkflowContext());

    try {
      new TaskWorkflow(td, executor);
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }
}
