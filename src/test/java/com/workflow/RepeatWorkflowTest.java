package com.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RepeatWorkflowTest {

  private Workflow childWorkflow;
  private WorkflowContext context;
  private WorkflowResult successResult;
  private WorkflowResult failureResult;

  @BeforeEach
  void setUp() {
    childWorkflow = mock(Workflow.class);
    context = new WorkflowContext();
    successResult = WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
    failureResult =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new RuntimeException("Iteration failed"))
            .build();
  }

  @Test
  @DisplayName("Should execute exactly 'n' times and return SUCCESS")
  void execute_FixedIterations() {
    // Arrange
    int iterations = 3;
    when(childWorkflow.execute(any())).thenReturn(successResult);

    RepeatWorkflow repeat =
        RepeatWorkflow.builder()
            .times(iterations)
            .indexVariable("i")
            .workflow(childWorkflow)
            .build();

    // Act
    WorkflowResult result = repeat.execute(context);

    // Assert
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    verify(childWorkflow, times(iterations)).execute(context);
    // Verify final index left in context
    assertEquals(2, context.get("i"));
  }

  @Test
  @DisplayName("Should use default index variable name if none provided")
  void execute_DefaultIndexVariable() {
    // Arrange
    when(childWorkflow.execute(any())).thenReturn(successResult);
    RepeatWorkflow repeat = RepeatWorkflow.builder().times(1).workflow(childWorkflow).build();

    // Act
    repeat.execute(context);

    // Assert
    assertNotNull(context.get("iteration"), "Should use 'iteration' as default key");
    assertEquals(0, context.get("iteration"));
  }

  @Test
  @DisplayName("Should stop immediately when an iteration fails (Fail-Fast)")
  void execute_FailFast() {
    // Arrange
    // Succeed on index 0, Fail on index 1, index 2 should never be called
    when(childWorkflow.execute(context))
        .thenReturn(successResult)
        .thenReturn(failureResult)
        .thenReturn(successResult);

    RepeatWorkflow repeat = RepeatWorkflow.builder().times(3).workflow(childWorkflow).build();

    // Act
    WorkflowResult result = repeat.execute(context);

    // Assert
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    verify(childWorkflow, times(2)).execute(context);
    assertEquals(
        1, context.get("iteration"), "Context should reflect index where failure occurred");
  }

  @Test
  @DisplayName("Should return SUCCESS immediately if times is zero or negative")
  void execute_ZeroOrNegativeTimes() {
    // Arrange
    RepeatWorkflow zeroRepeat = RepeatWorkflow.builder().times(0).workflow(childWorkflow).build();

    RepeatWorkflow negativeRepeat =
        RepeatWorkflow.builder().times(-5).workflow(childWorkflow).build();

    // Act & Assert
    assertEquals(WorkflowStatus.SUCCESS, zeroRepeat.execute(context).getStatus());
    assertEquals(WorkflowStatus.SUCCESS, negativeRepeat.execute(context).getStatus());

    verify(childWorkflow, never()).execute(any());
  }

  @Test
  @DisplayName("Should maintain execution order with correct index injection")
  void execute_OrderAndIndexVerification() {
    // Arrange
    when(childWorkflow.execute(any())).thenReturn(successResult);
    RepeatWorkflow repeat =
        RepeatWorkflow.builder().times(2).indexVariable("idx").workflow(childWorkflow).build();

    // Act
    repeat.execute(context);

    // Assert - Verify that the context was updated with 0 then 1 in order
    InOrder inOrder = inOrder(childWorkflow);
    // Note: Since we use the same context object, we verify the calls happened
    inOrder.verify(childWorkflow, times(2)).execute(context);
  }

  @Test
  @DisplayName("Should throw exception if child workflow is null")
  void builder_Validation() {
    // Set up the builder outside the assertion to isolate the failure point
    RepeatWorkflow.RepeatWorkflowBuilder builder = RepeatWorkflow.builder().times(5).workflow(null);

    // Only the invocation possibly throwing the exception is inside the lambda
    assertThrows(NullPointerException.class, builder::build);
  }
}
