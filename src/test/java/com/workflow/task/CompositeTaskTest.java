package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class CompositeTaskTest {

  private final WorkflowContext context = mock(WorkflowContext.class);

  @AfterEach
  void tearDown() {
    Mockito.validateMockitoUsage();
  }

  @Test
  void getName_reflectsNumberOfTasks() {
    CompositeTask ct0 = new CompositeTask(Collections.emptyList());
    assertEquals("CompositeTask[0]", ct0.getName());

    CompositeTask ct3 =
        new CompositeTask(Arrays.asList(mock(Task.class), mock(Task.class), mock(Task.class)));
    assertEquals("CompositeTask[3]", ct3.getName());
  }

  @Test
  void execute_withEmptyList_doesNothing() {
    CompositeTask ct = new CompositeTask(Collections.emptyList());
    // should not throw
    ct.execute(context);
    assertTrue(true);
  }

  @Test
  void execute_singleTask_invokesTask() {
    Task t = mock(Task.class);
    CompositeTask ct = new CompositeTask(Collections.singletonList(t));

    ct.execute(context);

    verify(t, times(1)).execute(context);
  }

  @Test
  void execute_multipleTasks_invokesAllInOrder() {
    Task t1 = mock(Task.class);
    Task t2 = mock(Task.class);
    Task t3 = mock(Task.class);

    List<Task> tasks = Arrays.asList(t1, t2, t3);
    CompositeTask ct = new CompositeTask(tasks);

    ct.execute(context);

    InOrder inOrder = inOrder(t1, t2, t3);
    inOrder.verify(t1).execute(context);
    inOrder.verify(t2).execute(context);
    inOrder.verify(t3).execute(context);

    verifyNoMoreInteractions(t1, t2, t3);
  }

  @Test
  void execute_whenTaskThrowsTaskExecutionException_propagatesAndStops() {
    Task t1 = mock(Task.class);
    Task t2 = mock(Task.class);

    doThrow(new TaskExecutionException("fail")).when(t1).execute(context);

    CompositeTask ct = new CompositeTask(Arrays.asList(t1, t2));

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> ct.execute(context));
    assertEquals("fail", ex.getMessage());

    verify(t1, times(1)).execute(context);
    verify(t2, never()).execute(context);
  }

  @Test
  void execute_whenTaskThrowsRuntimeException_propagatesAndStops() {
    Task t1 = mock(Task.class);
    Task t2 = mock(Task.class);

    doThrow(new RuntimeException("runtime")).when(t1).execute(context);

    CompositeTask ct = new CompositeTask(Arrays.asList(t1, t2));

    // AbstractTask wraps runtime exceptions in TaskExecutionException
    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> ct.execute(context));
    assertTrue(ex.getMessage().contains("runtime"));

    verify(t1, times(1)).execute(context);
    verify(t2, never()).execute(context);
  }

  @Test
  void execute_withNullTaskInList_throwsTaskExecutionExceptionAndStops() {
    Task t1 = mock(Task.class);
    Task t3 = mock(Task.class);
    // second element is null
    CompositeTask ct = new CompositeTask(Arrays.asList(t1, null, t3));

    // NullPointerException is wrapped by AbstractTask in TaskExecutionException
    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> ct.execute(context));
    assertNotNull(ex);
    assertInstanceOf(NullPointerException.class, ex.getCause());

    // first should have been invoked, second causes NPE, third should not be invoked
    verify(t1, times(1)).execute(context);
    verifyNoInteractions(t3);
  }
}
