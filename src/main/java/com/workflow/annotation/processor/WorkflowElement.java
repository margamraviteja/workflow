package com.workflow.annotation.processor;

import com.workflow.Workflow;
import com.workflow.task.Task;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Represents metadata about a workflow or task method extracted from annotations. This is used
 * during workflow construction to maintain order and metadata.
 */
public record WorkflowElement(
    String name, int order, Method method, ElementType type, Object metadata)
    implements Comparable<WorkflowElement> {

  public enum ElementType {
    WORKFLOW,
    TASK
  }

  @Override
  public int compareTo(WorkflowElement other) {
    return Integer.compare(this.order, other.order);
  }

  /**
   * Invokes the method and returns the result as a Workflow.
   *
   * @param instance the instance to invoke the method on
   * @return the Workflow instance
   * @throws InvocationTargetException if invocation fails
   * @throws IllegalAccessException if the method is inaccessible
   * @throws IllegalStateException if the result is not a Workflow
   */
  public Workflow invokeAsWorkflow(Object instance)
      throws InvocationTargetException, IllegalAccessException {
    Object result = method.invoke(instance);
    if (result instanceof Workflow workflow) {
      return workflow;
    }
    throw new IllegalStateException(
        "Method " + method.getName() + " did not return a Workflow instance");
  }

  /**
   * Invokes the method and returns the result as a Task.
   *
   * @param instance the instance to invoke the method on
   * @return the Task instance
   * @throws InvocationTargetException if invocation fails
   * @throws IllegalAccessException if the method is inaccessible
   * @throws IllegalStateException if the result is not a Task
   */
  public Task invokeAsTask(Object instance)
      throws InvocationTargetException, IllegalAccessException {
    Object result = method.invoke(instance);
    if (result instanceof Task task) {
      return task;
    }
    throw new IllegalStateException(
        "Method " + method.getName() + " did not return a Task instance");
  }
}
