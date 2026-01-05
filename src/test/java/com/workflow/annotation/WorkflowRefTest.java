package com.workflow.annotation;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.annotation.java.JavaAnnotationWorkflowProcessor;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.CircularDependencyException;
import com.workflow.exception.WorkflowCompositionException;
import com.workflow.task.Task;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for workflow composition with @WorkflowRef annotation. */
@Slf4j
class WorkflowRefTest {
  private JavaAnnotationWorkflowProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new JavaAnnotationWorkflowProcessor();
  }

  /** Test basic composition of two simple workflows in sequence. */
  @Test
  void testBasicCompositionWithIncludeWorkflow() {
    Workflow workflow = processor.buildWorkflow(CompositeWorkflowDefinition.class);
    assertNotNull(workflow);
    assertEquals("CompositeWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(true, context.get("first.executed", Boolean.class));
    assertEquals(true, context.get("second.executed", Boolean.class));
  }

  /** Test composition with multiple included workflows in specific order. */
  @Test
  void testCompositionWithMultipleIncludedWorkflows() {
    Workflow workflow = processor.buildWorkflow(MultiLevelCompositeWorkflow.class);
    assertNotNull(workflow);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(true, context.get("validation.done", Boolean.class));
    assertEquals(true, context.get("processing.done", Boolean.class));
    assertEquals(true, context.get("output.done", Boolean.class));
  }

  /** Test circular dependency detection. */
  @Test
  void testCircularDependencyDetection() {
    assertThrows(
        CircularDependencyException.class,
        () -> processor.buildWorkflow(CircularDependencyWorkflowA.class));
  }

  /** Test that included workflow not found throws exception. */
  @Test
  void testIncludedWorkflowNotFound() {
    assertThrows(
        WorkflowCompositionException.class,
        () -> processor.buildWorkflow(InvalidIncludeWorkflow.class));
  }

  /** Test context sharing between parent and included workflows. */
  @Test
  void testContextSharingInIncludedWorkflows() {
    Workflow workflow = processor.buildWorkflow(ContextSharingWorkflow.class);

    WorkflowContext context = new WorkflowContext();
    context.put("initial.data", "test-value");

    WorkflowResult result = workflow.execute(context);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());

    // Verify that included workflow could access initial data
    assertEquals("test-value", context.get("initial.data", String.class));
    assertEquals(true, context.get("data.modified", Boolean.class));
  }

  // ============================================================================
  // Test Workflow Definitions
  // ============================================================================

  /** Simple workflow that can be included. */
  @WorkflowAnnotation(name = "FirstWorkflow")
  public static class FirstWorkflowDefinition {
    @TaskMethod(name = "FirstTask", order = 1)
    public Task createFirstTask() {
      return context -> context.put("first.executed", true);
    }
  }

  /** Another simple workflow that can be included. */
  @WorkflowAnnotation(name = "SecondWorkflow")
  public static class SecondWorkflowDefinition {
    @TaskMethod(name = "SecondTask", order = 1)
    public Task createSecondTask() {
      return context -> context.put("second.executed", true);
    }
  }

  /** Composite workflow that includes two other workflows. */
  @WorkflowAnnotation(name = "CompositeWorkflow")
  public static class CompositeWorkflowDefinition {
    @WorkflowRef(workflowClass = FirstWorkflowDefinition.class)
    private Workflow firstWorkflow;

    @WorkflowRef(workflowClass = SecondWorkflowDefinition.class)
    private Workflow secondWorkflow;

    @WorkflowMethod(order = 1)
    public Workflow createFirstWorkflow() {
      return SequentialWorkflow.builder().workflow(firstWorkflow).workflow(secondWorkflow).build();
    }
  }

  /** Validation workflow. */
  @WorkflowAnnotation(name = "ValidationWorkflow")
  public static class ValidationWorkflowDefinition {
    @TaskMethod(name = "ValidateData", order = 1)
    public Task createValidateTask() {
      return context -> context.put("validation.done", true);
    }
  }

  /** Processing workflow. */
  @WorkflowAnnotation(name = "ProcessingWorkflow")
  public static class ProcessingWorkflowDefinition {
    @TaskMethod(name = "ProcessData", order = 1)
    public Task createProcessTask() {
      return context -> context.put("processing.done", true);
    }
  }

  /** Output workflow. */
  @WorkflowAnnotation(name = "OutputWorkflow")
  public static class OutputWorkflowDefinition {
    @TaskMethod(name = "OutputData", order = 1)
    public Task createOutputTask() {
      return context -> context.put("output.done", true);
    }
  }

  /** Multi-level composite workflow that includes multiple workflows in order. */
  @WorkflowAnnotation(name = "MultiLevelComposite")
  public static class MultiLevelCompositeWorkflow {
    @WorkflowRef(
        workflowClass = ValidationWorkflowDefinition.class,
        description = "Validate input data")
    private Workflow validation;

    @WorkflowRef(
        workflowClass = ProcessingWorkflowDefinition.class,
        description = "Process validated data")
    private Workflow processing;

    @WorkflowRef(
        workflowClass = OutputWorkflowDefinition.class,
        description = "Output processed data")
    private Workflow output;

    @WorkflowMethod(order = 1)
    public Workflow createFirstWorkflow() {
      return SequentialWorkflow.builder()
          .workflow(validation)
          .workflow(processing)
          .workflow(output)
          .build();
    }
  }

  /** Circular dependency workflow A - includes B. */
  @WorkflowAnnotation(name = "CircularA")
  public static class CircularDependencyWorkflowA {
    @WorkflowRef(workflowClass = CircularDependencyWorkflowB.class)
    private Workflow workflowB;

    @WorkflowMethod(order = 1)
    public Workflow createFirstWorkflow() {
      return workflowB;
    }
  }

  /** Circular dependency workflow B - includes A. */
  @WorkflowAnnotation(name = "CircularB")
  public static class CircularDependencyWorkflowB {
    @WorkflowRef(workflowClass = CircularDependencyWorkflowA.class)
    private Workflow workflowA;

    @WorkflowMethod(order = 1)
    public Workflow createFirstWorkflow() {
      return workflowA;
    }
  }

  /** Invalid include workflow - references non-existent workflow class. */
  @WorkflowAnnotation(name = "InvalidInclude")
  public static class InvalidIncludeWorkflow {
    @WorkflowRef(workflowClass = NonExistentWorkflow.class)
    private Workflow nonExistent;

    @WorkflowMethod(order = 1)
    public Workflow createFirstWorkflow() {
      return nonExistent;
    }
  }

  /** Placeholder for non-existent workflow (should not be annotated). */
  public static class NonExistentWorkflow {
    // This class is deliberately not annotated with @WorkflowAnnotation
  }

  /** Context sharing workflow - verifies that included workflows share context. */
  @WorkflowAnnotation(name = "ContextSharingComposite")
  public static class ContextSharingWorkflow {
    @WorkflowRef(workflowClass = ContextModifyingWorkflow.class)
    private Workflow contextModifier;

    @WorkflowMethod(order = 1)
    public Workflow createFirstWorkflow() {
      return contextModifier;
    }
  }

  /** Workflow that modifies context. */
  @WorkflowAnnotation(name = "ContextModifying")
  public static class ContextModifyingWorkflow {
    @TaskMethod(name = "ModifyContext", order = 1)
    public Task createModifyTask() {
      return context -> {
        // Access data from parent workflow
        String initialData = (String) context.get("initial.data");
        assertNotNull(initialData);
        context.put("data.modified", true);
      };
    }
  }
}
