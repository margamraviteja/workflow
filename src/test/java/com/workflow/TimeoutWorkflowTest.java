package com.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskTimeoutException;
import com.workflow.helper.Workflows;
import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeoutWorkflowTest {

  @Mock private Workflow mockInnerWorkflow;

  @Mock private WorkflowContext mockContext;

  @Mock private AbstractWorkflow.ExecutionContext mockExecContext;

  @Mock private WorkflowResult mockSuccessResult;

  @Mock private WorkflowResult mockFailureResult;

  @BeforeEach
  void setUp() {
    lenient().when(mockInnerWorkflow.getName()).thenReturn("InnerTask");
  }

  @Test
  void testExecute_SuccessWithinTimeout() {
    // Arrange
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder()
            .name("TestTimeout")
            .workflow(mockInnerWorkflow)
            .timeoutMs(500)
            .build();

    when(mockInnerWorkflow.execute(mockContext)).thenReturn(mockSuccessResult);
    when(mockSuccessResult.getStatus()).thenReturn(WorkflowStatus.SUCCESS);

    // Act
    WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

    // Assert
    assertEquals(mockSuccessResult, result);
    verify(mockInnerWorkflow, times(1)).execute(mockContext);
  }

  @Test
  void testExecute_TimesOut() {
    // Arrange
    long timeout = 100;
    TimeoutWorkflow timeoutWorkflow =
        Workflows.timeout("TestTimeout").workflow(mockInnerWorkflow).timeoutMs(timeout).build();

    // Simulate a long-running task
    when(mockInnerWorkflow.execute(mockContext))
        .thenAnswer(
            _ -> {
              new ThreadSleepingSleeper().sleep(Duration.ofMillis(500));
              return mockSuccessResult;
            });

    // Set up the expected failure response from the execution context
    when(mockExecContext.failure(any(TaskTimeoutException.class))).thenReturn(mockFailureResult);

    // Act
    WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

    // Assert
    assertEquals(mockFailureResult, result);
    verify(mockExecContext).failure(argThat(TaskTimeoutException.class::isInstance));
  }

  @Test
  void testExecute_ZeroTimeout_BypassesAsync() {
    // Arrange
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder()
            .workflow(mockInnerWorkflow)
            .timeoutMs(0) // Should skip the CompletableFuture logic
            .build();

    when(mockInnerWorkflow.execute(mockContext)).thenReturn(mockSuccessResult);

    // Act
    WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

    // Assert
    assertEquals(mockSuccessResult, result);
    verify(mockInnerWorkflow).execute(mockContext);
  }

  @Test
  void testExecute_InnerWorkflowThrowsException() {
    // Arrange
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().workflow(mockInnerWorkflow).timeoutMs(500).build();

    RuntimeException innerException = new RuntimeException("Crash");
    when(mockInnerWorkflow.execute(mockContext)).thenThrow(innerException);
    when(mockExecContext.failure(any())).thenReturn(mockFailureResult);

    // Act
    WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

    // Assert
    assertEquals(mockFailureResult, result);
    // It catches ExecutionException because CompletableFuture wraps the inner exception
    verify(mockExecContext).failure(innerException);
  }

  @Test
  void testExecute_WithSpecificExecutor() {
    // Arrange: Use a specific executor for this test
    try (ExecutorService testExecutor = Executors.newSingleThreadExecutor()) {

      TimeoutWorkflow timeoutWorkflow =
          TimeoutWorkflow.builder()
              .workflow(mockInnerWorkflow)
              .timeoutMs(50)
              .executor(testExecutor) // Injecting the executor
              .build();

      // Simulate work that definitely exceeds the 50ms timeout
      when(mockInnerWorkflow.execute(mockContext))
          .thenAnswer(
              _ -> {
                new ThreadSleepingSleeper().sleep(Duration.ofMillis(200));
                return mockSuccessResult;
              });

      when(mockExecContext.failure(any(TaskTimeoutException.class))).thenReturn(mockFailureResult);

      // Act
      WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

      // Assert
      assertEquals(mockFailureResult, result, "Should return failure result on timeout");
      verify(mockExecContext).failure(any(TaskTimeoutException.class));
    }
  }

  @Test
  void testGetSubWorkflows() {
    TimeoutWorkflow timeoutWorkflow = TimeoutWorkflow.builder().workflow(mockInnerWorkflow).build();

    assertNotNull(timeoutWorkflow.getSubWorkflows());
    assertEquals(1, timeoutWorkflow.getSubWorkflows().size());
    assertEquals(mockInnerWorkflow, timeoutWorkflow.getSubWorkflows().getFirst());
  }

  @Test
  void testGetName_withProvidedName() {
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().name("MyTimeout").workflow(mockInnerWorkflow).build();
    assertEquals("MyTimeout", timeoutWorkflow.getName());
  }

  @Test
  void testGetName_withNullName() {
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().name(null).workflow(mockInnerWorkflow).build();
    assertNotNull(timeoutWorkflow.getName());
    assertTrue(timeoutWorkflow.getName().contains("TimeoutWorkflow"));
  }

  @Test
  void testGetWorkflowType() {
    TimeoutWorkflow timeoutWorkflow = TimeoutWorkflow.builder().workflow(mockInnerWorkflow).build();
    assertEquals("[Timeout]", timeoutWorkflow.getWorkflowType());
  }

  @Test
  void testBuilder_nullWorkflow_throwsException() {
    try {
      TimeoutWorkflow.builder().workflow(null).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void testBuilder_nullExecutor_throwsException() {
    try {
      TimeoutWorkflow.builder().workflow(mockInnerWorkflow).executor(null).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void testExecute_negativeTimeout_bypassesAsync() {
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().workflow(mockInnerWorkflow).timeoutMs(-100).build();

    when(mockInnerWorkflow.execute(mockContext)).thenReturn(mockSuccessResult);

    WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

    assertEquals(mockSuccessResult, result);
    verify(mockInnerWorkflow).execute(mockContext);
  }

  @Test
  void testExecute_executionExceptionWithCause() {
    // Arrange
    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().workflow(mockInnerWorkflow).timeoutMs(500).build();

    RuntimeException cause = new RuntimeException("Root cause");
    when(mockInnerWorkflow.execute(mockContext)).thenThrow(cause);
    when(mockExecContext.failure(cause)).thenReturn(mockFailureResult);

    // Act
    WorkflowResult result = timeoutWorkflow.doExecute(mockContext, mockExecContext);

    // Assert
    assertEquals(mockFailureResult, result);
    verify(mockExecContext).failure(cause);
  }

  @Test
  void testImplementsWorkflowContainer() {
    TimeoutWorkflow timeoutWorkflow = TimeoutWorkflow.builder().workflow(mockInnerWorkflow).build();

    assertInstanceOf(WorkflowContainer.class, timeoutWorkflow);
  }

  @Test
  void testBuilder_withAllParameters() {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    TimeoutWorkflow workflow =
        TimeoutWorkflow.builder()
            .name("CompleteWorkflow")
            .workflow(mockInnerWorkflow)
            .timeoutMs(1000)
            .executor(executor)
            .build();

    assertEquals("CompleteWorkflow", workflow.getName());
    assertEquals(1, workflow.getSubWorkflows().size());

    executor.shutdown();
  }

  @Test
  void testExecute_withRealWorkflow_success() {
    Workflow innerWorkflow = SequentialWorkflow.builder().name("RealInner").build();

    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().workflow(innerWorkflow).timeoutMs(1000).build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = timeoutWorkflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testExecute_withRealWorkflow_timeout() {
    Workflow slowWorkflow =
        SequentialWorkflow.builder()
            .name("SlowWorkflow")
            .task(
                _ -> {
                  try {
                    new ThreadSleepingSleeper().sleep(Duration.ofMillis(500));
                  } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                  }
                })
            .build();

    TimeoutWorkflow timeoutWorkflow =
        TimeoutWorkflow.builder().workflow(slowWorkflow).timeoutMs(50).build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = timeoutWorkflow.execute(context);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertInstanceOf(TaskTimeoutException.class, result.getError());
  }

  @Test
  void testDefaultExecutor_isUsedWhenNotSpecified() {
    TimeoutWorkflow workflow =
        TimeoutWorkflow.builder().workflow(mockInnerWorkflow).timeoutMs(100).build();

    when(mockInnerWorkflow.execute(any())).thenReturn(mockSuccessResult);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    // Verify it executes successfully with default executor
    assertNotNull(result);
  }
}
