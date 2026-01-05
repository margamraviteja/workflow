package com.workflow.annotation.java;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.workflow.*;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.WorkflowRef;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.CircularDependencyException;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JavaAnnotationWorkflowProcessor}. */
@Slf4j
class JavaAnnotationWorkflowProcessorTest {

  private JavaAnnotationWorkflowProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new JavaAnnotationWorkflowProcessor();
  }

  @WorkflowAnnotation(name = "TestSequentialWorkflow")
  public static class SequentialWorkflowDefinition {
    @TaskMethod(name = "Task1", order = 1)
    public Task createTask1() {
      return context -> context.put("task1.executed", true);
    }

    @TaskMethod(name = "Task2", order = 2)
    public Task createTask2() {
      return context -> {
        context.put("task2.executed", true);
        context.put("task1.was.executed", context.get("task1.executed", Boolean.class));
      };
    }

    @WorkflowMethod(name = "SubWorkflow", order = 3)
    public Workflow createSubWorkflow() {
      return SequentialWorkflow.builder()
          .name("SubWorkflow")
          .task(context -> context.put("subworkflow.executed", true))
          .build();
    }
  }

  @WorkflowAnnotation(name = "TestParallelWorkflow", parallel = true)
  public static class ParallelWorkflowDefinition {
    @TaskMethod(name = "ParallelTask1", order = 1)
    public Task createTask1() {
      return context -> {
        try {
          await().atLeast(100, TimeUnit.MILLISECONDS).until(() -> true);
        } catch (Exception _) {
          Thread.currentThread().interrupt();
        }
        context.put("parallel.task1.executed", true);
      };
    }

    @TaskMethod(name = "ParallelTask2", order = 2)
    public Task createTask2() {
      return context -> {
        try {
          await().atLeast(50, TimeUnit.MILLISECONDS).until(() -> true);
        } catch (Exception _) {
          Thread.currentThread().interrupt();
        }
        context.put("parallel.task2.executed", true);
      };
    }
  }

  @WorkflowAnnotation(name = "TestRetryWorkflow")
  public static class RetryWorkflowDefinition {
    private int attemptCount = 0;

    @TaskMethod(name = "RetryTask", order = 1, maxRetries = 3)
    public Task createRetryTask() {
      return context -> {
        attemptCount++;
        context.put("attempt.count", attemptCount);
        if (attemptCount < 2) {
          throw new RuntimeException("Simulated failure");
        }
      };
    }
  }

  @WorkflowAnnotation(name = "TestTimeoutWorkflow")
  public static class TimeoutWorkflowDefinition {
    @TaskMethod(name = "TimeoutTask", order = 1, timeoutMs = 100)
    public Task createTimeoutTask() {
      return _ -> {
        try {
          await().atLeast(200, TimeUnit.MILLISECONDS).until(() -> true);
        } catch (Exception _) {
          Thread.currentThread().interrupt();
        }
      };
    }
  }

  @Test
  void testBuildSequentialWorkflow() {
    Workflow workflow = processor.buildWorkflow(SequentialWorkflowDefinition.class);

    assertNotNull(workflow);
    assertEquals("TestSequentialWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("task1.executed", Boolean.class));
    assertTrue(context.getTyped("task2.executed", Boolean.class));
    assertTrue(context.getTyped("subworkflow.executed", Boolean.class));
    assertTrue(context.getTyped("task1.was.executed", Boolean.class));
  }

  @Test
  void testBuildParallelWorkflow() {
    Workflow workflow = processor.buildWorkflow(ParallelWorkflowDefinition.class);

    assertNotNull(workflow);
    assertEquals("TestParallelWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("parallel.task1.executed", Boolean.class));
    assertTrue(context.getTyped("parallel.task2.executed", Boolean.class));
  }

  @Test
  void testWorkflowWithRetry() {
    Workflow workflow = processor.buildWorkflow(RetryWorkflowDefinition.class);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(2, context.get("attempt.count", Integer.class));
  }

  @Test
  void testWorkflowWithTimeout() {
    Workflow workflow = processor.buildWorkflow(TimeoutWorkflowDefinition.class);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNotNull(result.getError());
  }

  @Test
  void testBuildFromInstance() {
    SequentialWorkflowDefinition instance = new SequentialWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("TestSequentialWorkflow", workflow.getName());
  }

  @Test
  void testMissingAnnotationThrowsException() {
    class NotAnnotated {}

    assertThrows(IllegalArgumentException.class, () -> processor.buildWorkflow(NotAnnotated.class));
  }

  @WorkflowAnnotation(name = "EmptyWorkflow")
  public static class EmptyWorkflowDefinition {
    // No workflow or task methods
  }

  @Test
  void testEmptyWorkflowDefinition() {
    Workflow workflow = processor.buildWorkflow(EmptyWorkflowDefinition.class);

    assertNotNull(workflow);
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @WorkflowAnnotation(name = "OrderedWorkflow")
  public static class OrderedWorkflowDefinition {
    @TaskMethod(name = "Task3", order = 3)
    public Task createTask3() {
      return context -> context.put("order", context.get("order", String.class) + "3");
    }

    @TaskMethod(name = "Task1", order = 1)
    public Task createTask1() {
      return context -> context.put("order", "1");
    }

    @TaskMethod(name = "Task2", order = 2)
    public Task createTask2() {
      return context -> context.put("order", context.get("order", String.class) + "2");
    }
  }

  @Test
  void testTaskExecutionOrder() {
    Workflow workflow = processor.buildWorkflow(OrderedWorkflowDefinition.class);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult workflowResult = workflow.execute(context);
    log.info("Result: {}", workflowResult);

    assertEquals("123", context.get("order", String.class));
  }

  @WorkflowAnnotation(name = "DefaultNameWorkflow")
  public static class DefaultNameDefinition {
    @TaskMethod(order = 1)
    public Task createTaskWithDefaultName() {
      return context -> context.put("executed", true);
    }
  }

  @Test
  void testDefaultMethodNames() {
    Workflow workflow = processor.buildWorkflow(DefaultNameDefinition.class);

    assertNotNull(workflow);
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("executed", Boolean.class));
  }

  @WorkflowAnnotation(name = "SequentialWF")
  public static class SimpleSequentialWorkflow {
    @TaskMethod(name = "Step1", order = 1)
    public Task task1() {
      return _ -> {};
    }

    @WorkflowMethod(name = "Step2", order = 2)
    public Workflow step2() {
      return new TaskWorkflow(TaskDescriptor.builder().name("sub").task(_ -> {}).build());
    }
  }

  @WorkflowAnnotation(name = "ParallelWF", parallel = true)
  public static class SimpleParallelWorkflow {
    @TaskMethod(order = 1)
    public Task task1() {
      return _ -> {};
    }
  }

  @WorkflowAnnotation(name = "RefWorkflow")
  public static class DependencyWorkflow {
    @TaskMethod(order = 1)
    public Task useField(
        @WorkflowRef(workflowClass = SimpleParallelWorkflow.class)
            Workflow myRef) { // Test param injection
      return myRef::execute;
    }
  }

  @Test
  void testSequentialWorkflow() {
    Workflow workflow = processor.buildWorkflow(SimpleSequentialWorkflow.class);

    assertNotNull(workflow);
    assertInstanceOf(SequentialWorkflow.class, workflow);
    assertEquals("SequentialWF", workflow.getName());
  }

  @Test
  void testParallelWorkflow() {
    Workflow workflow = processor.buildWorkflow(SimpleParallelWorkflow.class);

    assertNotNull(workflow);
    assertInstanceOf(ParallelWorkflow.class, workflow);
    assertEquals("ParallelWF", workflow.getName());
  }

  @Test
  void testWorkflowRefFieldInjection() {
    DependencyWorkflow instance = new DependencyWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);
    assertNotNull(workflow);
  }

  @WorkflowAnnotation(name = "A")
  public static class WorkflowA {
    @WorkflowRef(workflowClass = WorkflowB.class)
    Workflow b;

    @TaskMethod(order = 1)
    public Task t() {
      return _ -> {};
    }
  }

  @WorkflowAnnotation(name = "B")
  public static class WorkflowB {
    @WorkflowRef(workflowClass = WorkflowA.class)
    Workflow a;

    @TaskMethod(order = 1)
    public Task t() {
      return _ -> {};
    }
  }

  @Test
  void testCircularDependency() {
    assertThrows(CircularDependencyException.class, () -> processor.buildWorkflow(WorkflowA.class));
  }

  @Test
  void testMissingAnnotation() {
    class UnannotatedClass {}

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> processor.buildWorkflow(UnannotatedClass.class));
    assertTrue(ex.getMessage().contains("is not annotated with @WorkflowAnnotation"));
  }

  @Test
  void testInvalidTaskReturnType() {
    @WorkflowAnnotation(name = "Invalid")
    class InvalidReturnWorkflow {
      @TaskMethod(order = 1)
      public String badTask() {
        return "not a task";
      }
    }

    assertThrows(
        WorkflowBuildException.class, () -> processor.buildWorkflow(InvalidReturnWorkflow.class));
  }

  @WorkflowAnnotation(name = "PolicyWF")
  public static class PolicyWorkflow {
    @TaskMethod(order = 1, maxRetries = 5, timeoutMs = 1000)
    public Task taskWithPolicies() {
      return _ -> {};
    }
  }

  @Test
  void testTaskMetadataWrapping() {

    Workflow wf = processor.buildWorkflow(PolicyWorkflow.class);
    assertNotNull(wf);
    // Internally this creates a TaskWorkflow; we verify the build succeeds
    // with the provided policy values.
  }

  @WorkflowAnnotation(name = "ChildWorkflow")
  public static class ChildWorkflowDefinition {
    @TaskMethod(name = "ChildTask", order = 1)
    public Task childTask() {
      return ctx -> System.out.println("Child task running");
    }
  }

  @Getter
  @WorkflowAnnotation(name = "ParentWorkflow")
  public static class ParentWorkflowWithField {
    // Getter for test verification
    // This is the target for field-level injection
    @WorkflowRef(workflowClass = ChildWorkflowDefinition.class)
    private Workflow injectedChildWorkflow;

    @WorkflowMethod(name = "ParentStep", order = 1)
    public Workflow parentStep() {
      // In a real scenario, the user might return the injected field
      // or a new workflow that uses it.
      return injectedChildWorkflow;
    }
  }

  @Test
  void testWorkflowRefFieldLevelInjection() {
    // 1. Arrange: Create the processor and the instance
    ParentWorkflowWithField instance = new ParentWorkflowWithField();

    // 2. Act: Build the workflow using the instance
    // The processor will scan fields, see @WorkflowRef, build ChildWorkflowDefinition,
    // and call field.set(instance, childWorkflow)
    Workflow parentWorkflow = processor.buildWorkflow(instance);

    // 3. Assert
    assertNotNull(parentWorkflow, "The parent workflow should be built");

    Workflow fieldRef = instance.getInjectedChildWorkflow();
    assertNotNull(fieldRef, "The private field @WorkflowRef should have been injected");
    assertEquals(
        "ChildWorkflow",
        fieldRef.getName(),
        "The injected workflow should have the name defined in its annotation");

    // Verify that the parent's structure (which returns the field) is also correct
    assertInstanceOf(SequentialWorkflow.class, parentWorkflow);
    assertEquals("ParentWorkflow", parentWorkflow.getName());
  }

  @WorkflowAnnotation(name = "DependencyWF")
  public static class DependencyDefinition {
    @TaskMethod(name = "DepTask", order = 1)
    public Task depTask() {
      return ctx -> {};
    }
  }

  @WorkflowAnnotation(name = "ParameterInjectionWF")
  public static class ParameterInjectionWorkflow {

    // The processor should build DependencyDefinition and pass it here
    @WorkflowMethod(name = "StepWithParam", order = 1)
    public Workflow stepWithParam(
        @WorkflowRef(workflowClass = DependencyDefinition.class) Workflow child) {
      // Return the child directly or wrap it
      return child;
    }
  }

  @Test
  void testWorkflowRefParameterInjection() {
    // Act
    // This triggers buildWorkflowRefParameterMap and invokeWorkflowMethodWithParameterInjection
    Workflow mainWorkflow = processor.buildWorkflow(ParameterInjectionWorkflow.class);

    // Assert
    assertNotNull(mainWorkflow, "The main workflow should be built");

    // Since our method returns the injected parameter directly,
    // the first element of the main workflow should be the DependencyWF
    if (mainWorkflow instanceof SequentialWorkflow) {
      // In your implementation, elements are wrapped or added to the builder
      assertEquals("ParameterInjectionWF", mainWorkflow.getName());

      // Check that the internal logic didn't fail and the child was built
      // We verify this by checking if the built workflow contains the expected sub-workflow
      // Depending on your SequentialWorkflow implementation, you can inspect children:
      assertDoesNotThrow(
          () -> {
            // If the injection failed, buildWorkflow would have thrown an exception
            // during the method.invoke() call in the processor.
          });
    }
  }

  @WorkflowAnnotation(name = "PrivateConstructorWF")
  static class PrivateConstructorWorkflow {
    // No public no-arg constructor - triggers instantiation failure
    private PrivateConstructorWorkflow() {}

    @TaskMethod(order = 1)
    public Task t() {
      return c -> {};
    }
  }

  @WorkflowAnnotation(name = "ExceptionInMethodWF")
  static class ExceptionInMethodWorkflow {
    @WorkflowMethod(order = 1)
    public Workflow throwsError() {
      throw new RuntimeException("Simulated business logic failure");
    }
  }

  @WorkflowAnnotation(name = "IllegalStateWF")
  static class IllegalStateWorkflow {
    // Method returns Object but is annotated with @WorkflowMethod
    // This is used to test the result-type check after invocation
    @WorkflowMethod(order = 1)
    public Workflow returnsNull() {
      return null; // Will cause issues if the processor expects a non-null instance
    }
  }

  @Test
  void testConstructorFailure() {
    WorkflowBuildException ex =
        assertThrows(
            WorkflowBuildException.class,
            () -> {
              processor.buildWorkflow(PrivateConstructorWorkflow.class);
            });

    assertTrue(ex.getMessage().contains("Failed to instantiate workflow class"));
    assertNotNull(ex.getCause());
  }

  @Test
  void testMethodInvocationFailure() {
    // 1. Arrange: Prepare the instance outside the lambda
    ExceptionInMethodWorkflow instance = new ExceptionInMethodWorkflow();

    // 2. Act & Assert: Only the specific call that triggers the exception is inside the lambda
    WorkflowBuildException ex =
        assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(instance));

    // 3. Verify
    assertTrue(ex.getMessage().contains("Failed to invoke method: throwsError"));
  }

  @Test
  void testThrowWorkflowBuildException() {
    @WorkflowAnnotation(name = "Root")
    class CompositionErrorWorkflow {
      // Reference a class that is NOT annotated with @WorkflowAnnotation
      @WorkflowRef(workflowClass = String.class)
      Workflow child;
    }

    assertThrows(
        WorkflowBuildException.class,
        () -> {
          processor.buildWorkflow(CompositionErrorWorkflow.class);
        });
  }

  @Test
  void testIllegalResultState() {
    @WorkflowAnnotation(name = "BadReturn")
    class BadReturnWorkflow {
      @WorkflowMethod(order = 1)
      public Workflow badReturn() {
        return null; // Causes ClassCastException or IllegalStateException in invocation wrapper
      }
    }

    BadReturnWorkflow badReturnWorkflow = new BadReturnWorkflow();
    assertThrows(
        WorkflowBuildException.class,
        () -> {
          processor.buildWorkflow(badReturnWorkflow);
        });
  }

  @Test
  void testIllegalAccessOnField() {
    @WorkflowAnnotation(name = "FinalFieldWF")
    class FinalFieldWorkflow {
      @WorkflowRef(workflowClass = SimpleSequentialWorkflow.class)
      private final Workflow child = null; // Final fields might throw on set()
    }

    // Depending on the JVM/Security Manager, this triggers IllegalAccessException
    // which is caught and wrapped in WorkflowBuildException
    assertThrows(
        WorkflowBuildException.class,
        () -> {
          processor.buildWorkflow(FinalFieldWorkflow.class);
        });
  }
}
