package com.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForEachWorkflowTest {

  private Workflow childWorkflow;
  private WorkflowContext context;
  private ForEachWorkflow.ForEachWorkflowBuilder builder;

  @BeforeEach
  void setUp() {
    childWorkflow = mock(Workflow.class);
    context = new WorkflowContext();
    builder =
        ForEachWorkflow.builder()
            .name("TestForEach")
            .itemsKey("items")
            .itemVariable("current")
            .workflow(childWorkflow);
  }

  @Test
  @DisplayName("Should iterate over all items and return SUCCESS")
  void execute_SuccessPath() {
    // Arrange
    List<String> data = List.of("A", "B", "C");
    context.put("items", data);

    when(childWorkflow.execute(any()))
        .thenReturn(WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build());

    // Act
    ForEachWorkflow forEach = builder.indexVariable("idx").build();
    WorkflowResult result = forEach.execute(context);

    // Assert
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    verify(childWorkflow, times(3)).execute(context);
    // Verify last item and index are left in context (standard behavior for your implementation)
    assertEquals("C", context.get("current"));
    assertEquals(2, context.get("idx"));
  }

  @Test
  @DisplayName("Should support arrays as input items")
  void execute_ArraySupport() {
    // Arrange
    String[] data = {"One", "Two"};
    context.put("items", data);
    when(childWorkflow.execute(any()))
        .thenReturn(WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build());

    // Act
    WorkflowResult result = builder.build().execute(context);

    // Assert
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    verify(childWorkflow, times(2)).execute(context);
  }

  @Test
  @DisplayName("Should stop execution and return failure when child workflow fails (Fail-Fast)")
  void execute_FailFast() {
    // Arrange
    context.put("items", List.of("SuccessItem", "FailItem", "NeverReachedItem"));

    WorkflowResult successRes = WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
    WorkflowResult failRes =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new RuntimeException("Boom"))
            .build();

    // Succeed on first call, fail on second
    when(childWorkflow.execute(context)).thenReturn(successRes).thenReturn(failRes);

    // Act
    WorkflowResult result = builder.build().execute(context);

    // Assert
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertEquals("Boom", result.getError().getMessage());
    verify(childWorkflow, times(2)).execute(context); // Third item never processed
  }

  @Test
  @DisplayName("Should return SUCCESS immediately if items key is missing or null")
  void execute_NullItems() {
    // Act
    WorkflowResult result = builder.build().execute(context);

    // Assert
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    verify(childWorkflow, never()).execute(any());
  }

  @Test
  @DisplayName("Should return SUCCESS immediately if collection is empty")
  void execute_EmptyCollection() {
    // Arrange
    context.put("items", List.of());

    // Act
    WorkflowResult result = builder.build().execute(context);

    // Assert
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    verify(childWorkflow, never()).execute(any());
  }

  @Test
  @DisplayName("Should return FAILURE if the item source is not a collection or array")
  void execute_InvalidType() {
    // Arrange
    context.put("items", "I am a String, not a List");

    // Act
    WorkflowResult result = builder.build().execute(context);

    // Assert
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertInstanceOf(IllegalArgumentException.class, result.getError());
  }

  @Test
  @DisplayName("Should throw exception during construction if required fields are blank")
  void constructor_Validation() {
    // Scenario 1: Missing all required fields
    ForEachWorkflow.ForEachWorkflowBuilder emptyBuilder = ForEachWorkflow.builder();
    assertThrows(NullPointerException.class, emptyBuilder::build);

    // Scenario 2: Blank itemsKey
    ForEachWorkflow.ForEachWorkflowBuilder blankKeyBuilder =
        ForEachWorkflow.builder().itemsKey("").itemVariable("v").workflow(childWorkflow);

    assertThrows(IllegalArgumentException.class, blankKeyBuilder::build);
  }
}
