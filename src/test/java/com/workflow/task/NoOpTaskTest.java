package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import org.junit.jupiter.api.Test;

class NoOpTaskTest {

  @Test
  void getName_returnsNoOpTask() {
    NoOpTask t = new NoOpTask();
    // getName now returns "NoOpTask:hashcode" per AbstractTask contract
    String name = t.getName();
    assertTrue(name.startsWith("NoOpTask:"));
  }

  @Test
  void execute_withMockContext_doesNotInteractWithContext() {
    WorkflowContext ctx = mock(WorkflowContext.class);
    NoOpTask t = new NoOpTask();

    // Should not throw and should not call any methods on the context
    t.execute(ctx);

    verifyNoInteractions(ctx);
  }

  @Test
  void execute_withNullContext_throwsNullPointerException() {
    NoOpTask t = new NoOpTask();

    // AbstractTask checks context with Objects.requireNonNull which throws NullPointerException
    assertThrows(NullPointerException.class, () -> t.execute(null));
  }

  @Test
  void execute_multipleTimes_isIdempotent_andDoesNotInteract() {
    WorkflowContext ctx = mock(WorkflowContext.class);
    NoOpTask t = new NoOpTask();

    t.execute(ctx);
    t.execute(ctx);
    t.execute(ctx);

    verifyNoInteractions(ctx);
  }

  @Test
  void execute_withContextThatWouldThrowIfCalled_stillDoesNotInvokeAnyMethod() {
    // Create a mock that would throw if any method is invoked to ensure NoOpTask truly does nothing
    WorkflowContext ctx = mock(WorkflowContext.class);
    doThrow(new RuntimeException("should not be called")).when(ctx).toString();

    NoOpTask t = new NoOpTask();

    // execute should not trigger any interaction (including toString invocation by our test)
    assertDoesNotThrow(() -> t.execute(ctx));

    // Explicitly verify no interactions with the mock
    verifyNoInteractions(ctx);
  }
}
