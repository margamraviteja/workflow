package com.workflow.annotation.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.TaskWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.WorkflowRef;
import com.workflow.context.WorkflowContext;
import com.workflow.helper.WorkflowResults;
import com.workflow.task.Task;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AnnotationWorkflowProcessorTest {

  private TestAnnotationWorkflowProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new TestAnnotationWorkflowProcessor();
  }

  @Test
  void shouldThrowExceptionWhenBuildingNonAnnotatedClass() {
    assertThrows(
        IllegalArgumentException.class, () -> processor.buildWorkflow(NonAnnotatedClass.class));
  }

  @Test
  void shouldBuildWorkflowFromAnnotatedClass() {
    Workflow workflow = processor.buildWorkflow(ValidWorkflowClass.class);
    assertNotNull(workflow);
  }

  @Test
  void shouldBuildWorkflowFromAnnotatedInstance() {
    ValidWorkflowClass instance = new ValidWorkflowClass();
    Workflow workflow = processor.buildWorkflow(instance);
    assertNotNull(workflow);
  }

  @Test
  void shouldThrowExceptionWhenBuildingNonAnnotatedInstance() {
    NonAnnotatedClass instance = new NonAnnotatedClass();
    assertThrows(IllegalArgumentException.class, () -> processor.buildWorkflow(instance));
  }

  @Test
  void shouldExtractWorkflowRefs() {
    List<WorkflowRefMetadata> refs = processor.extractWorkflowRefs(WorkflowWithRefs.class);
    assertEquals(1, refs.size());
    assertEquals("refWorkflow", refs.getFirst().name());
  }

  @Test
  void shouldThrowExceptionForInvalidWorkflowRefFieldType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> processor.extractWorkflowRefs(InvalidWorkflowRefType.class));
  }

  @Test
  void shouldValidateWorkflowMethod() throws NoSuchMethodException {
    Method method = ValidWorkflowMethods.class.getDeclaredMethod("validWorkflowMethod");
    assertDoesNotThrow(() -> processor.validateWorkflowMethod(method));
  }

  @Test
  void shouldThrowExceptionForInvalidWorkflowMethodReturnType() throws NoSuchMethodException {
    Method method = InvalidWorkflowMethods.class.getDeclaredMethod("invalidReturnType");
    assertThrows(IllegalArgumentException.class, () -> processor.validateWorkflowMethod(method));
  }

  @Test
  void shouldValidateTaskMethod() throws NoSuchMethodException {
    Method method = ValidTaskMethods.class.getDeclaredMethod("validTaskMethod");
    assertDoesNotThrow(() -> processor.validateTaskMethod(method));
  }

  @Test
  void shouldThrowExceptionForInvalidTaskMethodReturnType() throws NoSuchMethodException {
    Method method = InvalidTaskMethods.class.getDeclaredMethod("invalidReturnType");
    assertThrows(IllegalArgumentException.class, () -> processor.validateTaskMethod(method));
  }

  @Test
  void shouldExtractWorkflowElements() {
    List<WorkflowElement> elements = processor.extractWorkflowElements(WorkflowWithElements.class);
    assertEquals(2, elements.size());
    assertEquals("task1", elements.get(0).name());
    assertEquals("workflow1", elements.get(1).name());
  }

  @Test
  void shouldSortWorkflowElementsByOrder() {
    List<WorkflowElement> elements =
        processor.extractWorkflowElements(WorkflowWithOrderedElements.class);
    assertEquals(3, elements.size());
    assertEquals("first", elements.get(0).name());
    assertEquals("second", elements.get(1).name());
    assertEquals("third", elements.get(2).name());
  }

  @Test
  void shouldDetectCircularDependency() {
    try {
      processor.validateNoDependencies(CircularWorkflowA.class, new HashSet<>());
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void shouldNotThrowExceptionForNonCircularDependencies() {
    assertDoesNotThrow(
        () -> processor.validateNoDependencies(WorkflowWithRefs.class, new HashSet<>()));
  }

  @Test
  void shouldBuildWorkflowRefParameterMap() throws NoSuchMethodException {
    Method method =
        WorkflowWithRefParameters.class.getDeclaredMethod("methodWithRefs", Workflow.class);
    Map<String, Workflow> parameterMap = processor.buildWorkflowRefParameterMap(method);
    assertEquals(1, parameterMap.size());
    assertTrue(parameterMap.containsKey("arg0"));
  }

  @Test
  void shouldThrowExceptionForInvalidWorkflowRefParameterType() throws NoSuchMethodException {
    Method method =
        InvalidWorkflowRefParameters.class.getDeclaredMethod("methodWithInvalidRef", String.class);
    assertThrows(
        IllegalArgumentException.class, () -> processor.buildWorkflowRefParameterMap(method));
  }

  @Test
  void shouldWrapTaskAsWorkflow() throws NoSuchMethodException {
    Task task = _ -> CompletableFuture.completedFuture("result");
    Method method = WorkflowWithElements.class.getDeclaredMethod("task1");
    TaskMethod taskMethod = method.getAnnotation(TaskMethod.class);
    WorkflowElement element =
        new WorkflowElement("task1", 0, method, WorkflowElement.ElementType.TASK, taskMethod);

    Workflow workflow = processor.wrapTaskAsWorkflow(task, element);
    assertNotNull(workflow);
  }

  @Test
  void shouldWrapTaskAsWorkflowWithRetryPolicy() throws NoSuchMethodException {
    Task task = _ -> CompletableFuture.completedFuture("result");
    Method method = WorkflowWithRetryTask.class.getDeclaredMethod("taskWithRetry");
    TaskMethod taskMethod = method.getAnnotation(TaskMethod.class);
    WorkflowElement element =
        new WorkflowElement(
            "taskWithRetry", 0, method, WorkflowElement.ElementType.TASK, taskMethod);

    Workflow workflow = processor.wrapTaskAsWorkflow(task, element);
    assertNotNull(workflow);
  }

  @Test
  void shouldWrapTaskAsWorkflowWithTimeoutPolicy() throws NoSuchMethodException {
    Task task = _ -> CompletableFuture.completedFuture("result");
    Method method = WorkflowWithTimeoutTask.class.getDeclaredMethod("taskWithTimeout");
    TaskMethod taskMethod = method.getAnnotation(TaskMethod.class);
    WorkflowElement element =
        new WorkflowElement(
            "taskWithTimeout", 0, method, WorkflowElement.ElementType.TASK, taskMethod);

    Workflow workflow = processor.wrapTaskAsWorkflow(task, element);
    assertNotNull(workflow);
  }

  // Test classes
  static class NonAnnotatedClass {}

  @WorkflowAnnotation(name = "validWorkflow")
  static class ValidWorkflowClass {}

  @WorkflowAnnotation(name = "workflowWithRefs")
  static class WorkflowWithRefs {
    @WorkflowRef(workflowClass = ValidWorkflowClass.class)
    private Workflow refWorkflow;
  }

  static class InvalidWorkflowRefType {
    @WorkflowRef(workflowClass = ValidWorkflowClass.class)
    private String invalidField;
  }

  static class ValidWorkflowMethods {
    @WorkflowMethod
    Workflow validWorkflowMethod() {
      return null;
    }
  }

  static class InvalidWorkflowMethods {
    @WorkflowMethod
    String invalidReturnType() {
      return "invalid";
    }
  }

  static class ValidTaskMethods {
    @TaskMethod
    Task validTaskMethod() {
      return null;
    }
  }

  static class InvalidTaskMethods {
    @TaskMethod
    String invalidReturnType() {
      return "invalid";
    }
  }

  @WorkflowAnnotation(name = "workflowWithElements")
  static class WorkflowWithElements {
    @TaskMethod(order = 1)
    Task task1() {
      return _ -> CompletableFuture.completedFuture("task1");
    }

    @WorkflowMethod(order = 2)
    Workflow workflow1() {
      return new TaskWorkflow(_ -> CompletableFuture.completedFuture("workflow1"));
    }
  }

  @WorkflowAnnotation(name = "workflowWithOrderedElements")
  static class WorkflowWithOrderedElements {
    @TaskMethod(name = "second", order = 2)
    Task task2() {
      return _ -> CompletableFuture.completedFuture("task2");
    }

    @TaskMethod(name = "first", order = 1)
    Task task1() {
      return _ -> CompletableFuture.completedFuture("task1");
    }

    @TaskMethod(name = "third", order = 3)
    Task task3() {
      return _ -> CompletableFuture.completedFuture("task3");
    }
  }

  @WorkflowAnnotation(name = "circularA")
  static class CircularWorkflowA {
    @WorkflowRef(workflowClass = CircularWorkflowB.class)
    private Workflow refB;
  }

  @WorkflowAnnotation(name = "circularB")
  static class CircularWorkflowB {
    @WorkflowRef(workflowClass = CircularWorkflowA.class)
    private Workflow refA;
  }

  static class WorkflowWithRefParameters {
    void methodWithRefs(@WorkflowRef(workflowClass = ValidWorkflowClass.class) Workflow workflow) {
      // no-op
    }
  }

  static class InvalidWorkflowRefParameters {
    void methodWithInvalidRef(
        @WorkflowRef(workflowClass = ValidWorkflowClass.class) String invalid) {
      // no-op
    }
  }

  @WorkflowAnnotation(name = "workflowWithRetryTask")
  static class WorkflowWithRetryTask {
    @TaskMethod(maxRetries = 3)
    Task taskWithRetry() {
      return _ -> CompletableFuture.completedFuture("retry");
    }
  }

  @WorkflowAnnotation(name = "workflowWithTimeoutTask")
  static class WorkflowWithTimeoutTask {
    @TaskMethod(timeoutMs = 5000)
    Task taskWithTimeout() {
      return _ -> CompletableFuture.completedFuture("timeout");
    }
  }

  // Test implementation of the abstract class
  static class TestAnnotationWorkflowProcessor extends AnnotationWorkflowProcessor {
    private static final Logger logger =
        LoggerFactory.getLogger(TestAnnotationWorkflowProcessor.class);

    @Override
    protected Logger getLogger() {
      return logger;
    }

    @Override
    protected Workflow buildWorkflow(Object instance, WorkflowAnnotation annotation) {
      return new Workflow() {
        @Override
        public WorkflowResult execute(WorkflowContext context) {
          CompletableFuture.completedFuture("test");
          return WorkflowResults.success(Instant.now(), Instant.now());
        }

        @Override
        public String getName() {
          return annotation.name();
        }
      };
    }
  }
}
