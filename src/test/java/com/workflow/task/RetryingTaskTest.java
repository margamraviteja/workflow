package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.sleeper.Sleeper;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryingTaskTest {

  private Task mockDelegate;
  private RetryPolicy mockPolicy;
  private RetryPolicy.BackoffStrategy mockBackoff;
  private Sleeper mockSleeper;
  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    mockDelegate = mock(Task.class);
    mockPolicy = mock(RetryPolicy.class);
    mockBackoff = mock(RetryPolicy.BackoffStrategy.class);
    mockSleeper = mock(Sleeper.class);
    context = new WorkflowContext();

    // Standard mock behavior
    when(mockPolicy.backoff()).thenReturn(mockBackoff);
  }

  @Test
  void execute_SucceedsOnFirstAttempt_NoRetries() {
    RetryingTask retryingTask = new RetryingTask(mockDelegate, mockPolicy, mockSleeper);

    retryingTask.execute(context);

    verify(mockDelegate, times(1)).execute(context);
    verifyNoInteractions(mockPolicy, mockSleeper);
  }

  @Test
  void execute_RetriesTwiceThenSucceeds() throws Exception {
    RetryingTask retryingTask = new RetryingTask(mockDelegate, mockPolicy, mockSleeper);

    // Fail twice, then succeed
    doThrow(new RuntimeException("Transient Error"))
        .doThrow(new RuntimeException("Transient Error"))
        .doNothing()
        .when(mockDelegate)
        .execute(context);

    // Policy says "Yes" for first two attempts
    when(mockPolicy.shouldRetry(eq(1), any())).thenReturn(true);
    when(mockPolicy.shouldRetry(eq(2), any())).thenReturn(true);

    // Backoff values
    when(mockBackoff.computeDelayMs(1)).thenReturn(100L);
    when(mockBackoff.computeDelayMs(2)).thenReturn(200L);

    retryingTask.execute(context);

    verify(mockDelegate, times(3)).execute(context);
    verify(mockSleeper).sleep(Duration.ofMillis(100));
    verify(mockSleeper).sleep(Duration.ofMillis(200));
  }

  @Test
  void execute_FailsWhenPolicyExhausted() {
    RetryingTask retryingTask = new RetryingTask(mockDelegate, mockPolicy, mockSleeper);

    doThrow(new RuntimeException("Persistent Error")).when(mockDelegate).execute(context);
    when(mockPolicy.shouldRetry(anyInt(), any())).thenReturn(false);

    assertThrows(TaskExecutionException.class, () -> retryingTask.execute(context));
    verify(mockDelegate, times(1)).execute(context);
  }

  @Test
  void execute_AbortsOnInterruptDuringSleep() throws Exception {
    RetryingTask retryingTask = new RetryingTask(mockDelegate, mockPolicy, mockSleeper);

    doThrow(new RuntimeException("Retry Me")).when(mockDelegate).execute(context);
    when(mockPolicy.shouldRetry(anyInt(), any())).thenReturn(true);
    when(mockBackoff.computeDelayMs(anyInt())).thenReturn(1000L);

    // Simulate interruption
    doThrow(new InterruptedException()).when(mockSleeper).sleep(any());

    assertThrows(TaskExecutionException.class, () -> retryingTask.execute(context));
    assertTrue(Thread.currentThread().isInterrupted(), "Thread interrupt flag should be set");
  }

  @Test
  void execute_ValidatesSpecificExceptionTypes() {
    // Using real factory methods to test integration
    RetryPolicy selectivePolicy = RetryPolicy.limitedRetries(3, IOException.class);
    RetryingTask retryingTask = new RetryingTask(mockDelegate, selectivePolicy, mockSleeper);

    // Throw a non-retryable exception
    doThrow(new NullPointerException()).when(mockDelegate).execute(context);

    assertThrows(TaskExecutionException.class, () -> retryingTask.execute(context));
    verify(mockDelegate, times(1)).execute(context); // Should not retry NPE
  }

  @Test
  void getName_IncludesDelegateName() {
    when(mockDelegate.getName()).thenReturn("OrderProcessor");
    RetryingTask retryingTask = new RetryingTask(mockDelegate, mockPolicy);

    assertEquals("RetryingTask(OrderProcessor)", retryingTask.getName());
  }
}
