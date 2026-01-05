package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ConditionalTaskTest {

  @Test
  void execute_whenConditionTrue_executesInnerTask() {
    @SuppressWarnings("unchecked")
    Predicate<WorkflowContext> condition = mock(Predicate.class);
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    when(condition.test(ctx)).thenReturn(true);

    ConditionalTask ct = new ConditionalTask(condition, inner);
    ct.execute(ctx);

    verify(condition, times(1)).test(ctx);
    verify(inner, times(1)).execute(ctx);
  }

  @Test
  void execute_whenConditionFalse_doesNotExecuteInnerTask() {
    @SuppressWarnings("unchecked")
    Predicate<WorkflowContext> condition = mock(Predicate.class);
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    when(condition.test(ctx)).thenReturn(false);

    ConditionalTask ct = new ConditionalTask(condition, inner);
    ct.execute(ctx);

    verify(condition, times(1)).test(ctx);
    verify(inner, never()).execute(any());
  }

  @Test
  void execute_whenInnerThrowsTaskExecutionException_propagates() {
    Predicate<WorkflowContext> condition = _ -> true;
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    doThrow(new TaskExecutionException("inner failed")).when(inner).execute(ctx);

    ConditionalTask ct = new ConditionalTask(condition, inner);

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> ct.execute(ctx));
    assertEquals("inner failed", ex.getMessage());

    verify(inner, times(1)).execute(ctx);
  }

  @Test
  void execute_whenInnerThrowsRuntimeException_propagates() {
    Predicate<WorkflowContext> condition = _ -> true;
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    doThrow(new RuntimeException("runtime")).when(inner).execute(ctx);

    ConditionalTask ct = new ConditionalTask(condition, inner);

    // AbstractTask wraps runtime exceptions in TaskExecutionException
    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> ct.execute(ctx));
    assertTrue(ex.getMessage().contains("runtime"));

    verify(inner, times(1)).execute(ctx);
  }

  @Test
  void execute_whenConditionThrowsRuntimeException_innerNotInvoked_andExceptionPropagates() {
    @SuppressWarnings("unchecked")
    Predicate<WorkflowContext> condition = mock(Predicate.class);
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    when(condition.test(ctx)).thenThrow(new RuntimeException("predicate failed"));

    ConditionalTask ct = new ConditionalTask(condition, inner);

    // AbstractTask wraps runtime exceptions in TaskExecutionException
    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> ct.execute(ctx));
    assertTrue(ex.getMessage().contains("predicate failed"));

    verify(condition, times(1)).test(ctx);
    verify(inner, never()).execute(any());
  }

  @Test
  void getName_returnsFormattedNameUsingInnerName() {
    Predicate<WorkflowContext> condition = _ -> true;
    Task inner = mock(Task.class);
    when(inner.getName()).thenReturn("InnerTask");

    ConditionalTask ct = new ConditionalTask(condition, inner);

    assertEquals("ConditionalTask(InnerTask)", ct.getName());
    verify(inner, times(1)).getName();
  }

  @Test
  void getName_whenInnerIsNull_throwsNullPointerException() {
    Predicate<WorkflowContext> condition = _ -> true;
    ConditionalTask ct = new ConditionalTask(condition, null);

    assertThrows(NullPointerException.class, ct::getName);
  }

  @Test
  void execute_whenConditionIsNull_throwsTaskExecutionException() {
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    ConditionalTask ct = new ConditionalTask(null, inner);

    // Will throw NPE when trying to invoke null condition, wrapped by AbstractTask
    assertThrows(TaskExecutionException.class, () -> ct.execute(ctx));
    verify(inner, never()).execute(any());
  }

  @Test
  void execute_order_verification_conditionThenInner() {
    @SuppressWarnings("unchecked")
    Predicate<WorkflowContext> condition = mock(Predicate.class);
    WorkflowContext ctx = mock(WorkflowContext.class);
    Task inner = mock(Task.class);

    when(condition.test(ctx)).thenReturn(true);

    ConditionalTask ct = new ConditionalTask(condition, inner);
    ct.execute(ctx);

    InOrder inOrder = inOrder(condition, inner);
    inOrder.verify(condition).test(ctx);
    inOrder.verify(inner).execute(ctx);
  }
}
