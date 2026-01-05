package com.workflow.annotation.java;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.WorkflowRef;
import com.workflow.annotation.processor.AnnotationWorkflowProcessor;
import com.workflow.annotation.processor.WorkflowElement;
import com.workflow.annotation.processor.WorkflowElement.ElementType;
import com.workflow.annotation.processor.WorkflowRefMetadata;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.task.Task;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Pure Java-based annotation processor that scans classes for workflow annotations and constructs
 * workflows without requiring Spring framework dependencies.
 *
 * <p>This processor uses reflection to discover {@link WorkflowAnnotation}, {@link WorkflowMethod},
 * {@link TaskMethod}, and {@link WorkflowRef} annotations and builds the corresponding workflow
 * structure with support for workflow composition.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @WorkflowAnnotation(name = "MyWorkflow", parallel = false)
 * public class MyWorkflowDefinition {
 *   @WorkflowMethod(name = "Step1", order = 1)
 *   public Workflow step1() { return ...; }
 *
 *   @TaskMethod(name = "Step2", order = 2)
 *   public Task step2() { return ...; }
 * }
 *
 * // Build the workflow
 * JavaAnnotationWorkflowProcessor processor = new JavaAnnotationWorkflowProcessor();
 * Workflow workflow = processor.buildWorkflow(MyWorkflowDefinition.class);
 * }</pre>
 */
@Slf4j
public class JavaAnnotationWorkflowProcessor extends AnnotationWorkflowProcessor {

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected Workflow buildWorkflow(Object instance, WorkflowAnnotation annotation) {
    getLogger().info("Building workflow from class: {}", instance.getClass().getName());

    List<WorkflowElement> elements = extractWorkflowElements(instance.getClass());
    List<WorkflowRefMetadata> workflowRefMetadata = extractWorkflowRefs(instance.getClass());
    List<Workflow> workflows = new ArrayList<>();

    // Validate for circular dependencies
    validateNoDependencies(instance.getClass(), new HashSet<>());

    // Process workflow refs first
    for (WorkflowRefMetadata metadata : workflowRefMetadata) {
      Workflow workflowRef = buildWorkflow(metadata);
      try {
        // Inject the built workflow back into the field
        metadata.field().setAccessible(true);
        metadata.field().set(instance, workflowRef);
      } catch (IllegalAccessException e) {
        throw new WorkflowBuildException(e.getMessage(), e);
      }
    }

    // Process regular workflow/task elements
    for (WorkflowElement element : elements) {
      try {
        Workflow workflow;
        if (element.type() == ElementType.WORKFLOW) {
          workflow = invokeWorkflowMethodWithParameterInjection(element, instance);
        } else {
          Task task = invokeTaskMethodWithParameterInjection(element, instance);
          workflow = wrapTaskAsWorkflow(task, element);
        }
        workflows.add(workflow);
      } catch (Exception e) {
        throw new WorkflowBuildException(
            "Failed to invoke method: " + element.method().getName(), e);
      }
    }

    String workflowName =
        annotation.name().isEmpty() ? instance.getClass().getSimpleName() : annotation.name();

    if (annotation.parallel()) {
      ParallelWorkflow.ParallelWorkflowBuilder builder =
          ParallelWorkflow.builder().name(workflowName).shareContext(annotation.shareContext());
      workflows.forEach(builder::workflow);
      return builder.build();
    } else {
      SequentialWorkflow.SequentialWorkflowBuilder builder =
          SequentialWorkflow.builder().name(workflowName);
      workflows.forEach(builder::workflow);
      return builder.build();
    }
  }

  /**
   * Invokes a workflow method with parameter injection for @WorkflowRef parameters.
   *
   * @param element the workflow element to invoke
   * @param instance the instance to invoke the method on
   * @return the Workflow result
   * @throws InvocationTargetException if invocation fails
   * @throws IllegalAccessException if access fails
   */
  private Workflow invokeWorkflowMethodWithParameterInjection(
      WorkflowElement element, Object instance)
      throws InvocationTargetException, IllegalAccessException {
    Map<String, Workflow> workflowRefMap = buildWorkflowRefParameterMap(element.method());

    if (workflowRefMap.isEmpty()) {
      // No parameters to inject, invoke directly
      return element.invokeAsWorkflow(instance);
    }

    // Build argument array with injected workflows
    Object[] args = buildMethodArguments(element.method(), workflowRefMap);
    Object result = element.method().invoke(instance, args);

    if (result instanceof Workflow workflow) {
      return workflow;
    }
    throw new IllegalStateException(
        "Method " + element.method().getName() + " did not return a Workflow instance");
  }

  /**
   * Invokes a task method with parameter injection for @WorkflowRef parameters.
   *
   * @param element the task element to invoke
   * @param instance the instance to invoke the method on
   * @return the Task result
   * @throws InvocationTargetException if invocation fails
   * @throws IllegalAccessException if access fails
   */
  private Task invokeTaskMethodWithParameterInjection(WorkflowElement element, Object instance)
      throws InvocationTargetException, IllegalAccessException {
    java.util.Map<String, Workflow> workflowRefMap = buildWorkflowRefParameterMap(element.method());

    if (workflowRefMap.isEmpty()) {
      // No parameters to inject, invoke directly
      return element.invokeAsTask(instance);
    }

    // Build argument array with injected workflows
    Object[] args = buildMethodArguments(element.method(), workflowRefMap);
    Object result = element.method().invoke(instance, args);

    if (result instanceof Task task) {
      return task;
    }
    throw new IllegalStateException(
        "Method " + element.method().getName() + " did not return a Task instance");
  }

  /**
   * Builds the argument array for a method, injecting @WorkflowRef parameters.
   *
   * @param method the method
   * @param workflowRefMap map of parameter names to Workflow instances
   * @return the argument array
   */
  private Object[] buildMethodArguments(Method method, Map<String, Workflow> workflowRefMap) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
      args[i] = workflowRefMap.get(parameters[i].getName());
    }

    return args;
  }
}
