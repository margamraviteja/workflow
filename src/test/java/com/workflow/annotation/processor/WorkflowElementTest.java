package com.workflow.annotation.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.task.Task;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class WorkflowElementTest {

  @Test
  void shouldCreateWorkflowElement() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element =
        new WorkflowElement("testWorkflow", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertEquals("testWorkflow", element.name());
    assertEquals(1, element.order());
    assertEquals(method, element.method());
    assertEquals(WorkflowElement.ElementType.WORKFLOW, element.type());
    assertNull(element.metadata());
  }

  @Test
  void shouldCreateTaskElement() {
    Method method = getTestMethod("getTask");
    TaskMethod taskMethod = method.getAnnotation(TaskMethod.class);
    WorkflowElement element =
        new WorkflowElement("testTask", 2, method, WorkflowElement.ElementType.TASK, taskMethod);

    assertEquals("testTask", element.name());
    assertEquals(2, element.order());
    assertEquals(method, element.method());
    assertEquals(WorkflowElement.ElementType.TASK, element.type());
    assertNotNull(element.metadata());
  }

  @Test
  void shouldCompareByOrder() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element1 =
        new WorkflowElement("first", 1, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element2 =
        new WorkflowElement("second", 2, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element3 =
        new WorkflowElement("third", 3, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertTrue(element1.compareTo(element2) < 0);
    assertTrue(element2.compareTo(element3) < 0);
    assertTrue(element3.compareTo(element1) > 0);
  }

  @Test
  void shouldInvokeAsWorkflow() throws InvocationTargetException, IllegalAccessException {
    TestWorkflowClass instance = new TestWorkflowClass();
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element =
        new WorkflowElement("testWorkflow", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    Workflow workflow = element.invokeAsWorkflow(instance);
    assertNotNull(workflow);
    assertEquals("testWorkflow", workflow.getName());
  }

  @Test
  void shouldInvokeAsTask() throws InvocationTargetException, IllegalAccessException {
    TestWorkflowClass instance = new TestWorkflowClass();
    Method method = getTestMethod("getTask");
    WorkflowElement element =
        new WorkflowElement("testTask", 1, method, WorkflowElement.ElementType.TASK, null);

    Task task = element.invokeAsTask(instance);
    assertNotNull(task);
  }

  @Test
  void shouldThrowExceptionWhenInvokingAsWorkflowReturnsNonWorkflow() {
    TestWorkflowClass instance = new TestWorkflowClass();
    Method method = getTestMethod("getString");
    WorkflowElement element =
        new WorkflowElement(
            "invalidWorkflow", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertThrows(IllegalStateException.class, () -> element.invokeAsWorkflow(instance));
  }

  @Test
  void shouldThrowExceptionWhenInvokingAsTaskReturnsNonTask() {
    TestWorkflowClass instance = new TestWorkflowClass();
    Method method = getTestMethod("getString");
    WorkflowElement element =
        new WorkflowElement("invalidTask", 1, method, WorkflowElement.ElementType.TASK, null);

    assertThrows(IllegalStateException.class, () -> element.invokeAsTask(instance));
  }

  @Test
  void shouldHandleMethodThatThrowsException() {
    TestWorkflowClass instance = new TestWorkflowClass();
    Method method = getTestMethod("throwException");
    WorkflowElement element =
        new WorkflowElement(
            "throwingMethod", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertThrows(InvocationTargetException.class, () -> element.invokeAsWorkflow(instance));
  }

  @Test
  void shouldSortElementsByOrder() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element1 =
        new WorkflowElement("first", 3, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element2 =
        new WorkflowElement("second", 1, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element3 =
        new WorkflowElement("third", 2, method, WorkflowElement.ElementType.WORKFLOW, null);

    java.util.List<WorkflowElement> elements = new java.util.ArrayList<>();
    elements.add(element1);
    elements.add(element2);
    elements.add(element3);
    elements.sort(WorkflowElement::compareTo);

    assertEquals("second", elements.get(0).name());
    assertEquals("third", elements.get(1).name());
    assertEquals("first", elements.get(2).name());
  }

  @Test
  void shouldHandleNegativeOrder() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element1 =
        new WorkflowElement("negative", -1, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element2 =
        new WorkflowElement("zero", 0, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element3 =
        new WorkflowElement("positive", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertTrue(element1.compareTo(element2) < 0);
    assertTrue(element2.compareTo(element3) < 0);
  }

  @Test
  void shouldHandleEqualOrders() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element1 =
        new WorkflowElement("first", 1, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element2 =
        new WorkflowElement("second", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertEquals(0, element1.compareTo(element2));
  }

  @Test
  void shouldSupportRecordEquality() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element1 =
        new WorkflowElement("test", 1, method, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element2 =
        new WorkflowElement("test", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    assertEquals(element1, element2);
    assertEquals(element1.hashCode(), element2.hashCode());
  }

  @Test
  void shouldNotEqualWithDifferentFields() {
    Method method1 = getTestMethod("getWorkflow");
    Method method2 = getTestMethod("getTask");

    WorkflowElement element1 =
        new WorkflowElement("test", 1, method1, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element2 =
        new WorkflowElement("different", 1, method1, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element3 =
        new WorkflowElement("test", 2, method1, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element4 =
        new WorkflowElement("test", 1, method2, WorkflowElement.ElementType.WORKFLOW, null);
    WorkflowElement element5 =
        new WorkflowElement("test", 1, method1, WorkflowElement.ElementType.TASK, null);

    assertNotEquals(element1, element2);
    assertNotEquals(element1, element3);
    assertNotEquals(element1, element4);
    assertNotEquals(element1, element5);
  }

  @Test
  void shouldHaveWorkingToString() {
    Method method = getTestMethod("getWorkflow");
    WorkflowElement element =
        new WorkflowElement("testWorkflow", 1, method, WorkflowElement.ElementType.WORKFLOW, null);

    String toString = element.toString();
    assertTrue(toString.contains("testWorkflow"));
    assertTrue(toString.contains("WORKFLOW"));
  }

  @Test
  void shouldHandleWorkflowMethodAnnotation() {
    Method method = getTestMethod("getWorkflow");
    WorkflowMethod workflowMethod = method.getAnnotation(WorkflowMethod.class);
    WorkflowElement element =
        new WorkflowElement(
            "testWorkflow", 1, method, WorkflowElement.ElementType.WORKFLOW, workflowMethod);

    assertNotNull(element.metadata());
    assertInstanceOf(WorkflowMethod.class, element.metadata());
  }

  @Test
  void shouldHandleTaskMethodAnnotation() {
    Method method = getTestMethod("getTask");
    TaskMethod taskMethod = method.getAnnotation(TaskMethod.class);
    WorkflowElement element =
        new WorkflowElement("testTask", 1, method, WorkflowElement.ElementType.TASK, taskMethod);

    assertNotNull(element.metadata());
    assertInstanceOf(TaskMethod.class, element.metadata());
  }

  private Method getTestMethod(String methodName) {
    try {
      return TestWorkflowClass.class.getMethod(methodName);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  // Test class
  public static class TestWorkflowClass {
    @WorkflowMethod
    public Workflow getWorkflow() {
      return SequentialWorkflow.builder().name("testWorkflow").build();
    }

    @TaskMethod
    public Task getTask() {
      return _ -> CompletableFuture.completedFuture("result");
    }

    public String getString() {
      return "not a workflow or task";
    }

    public Workflow throwException() {
      throw new RuntimeException("Test exception");
    }
  }
}
