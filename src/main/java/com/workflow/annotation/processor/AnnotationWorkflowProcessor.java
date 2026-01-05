package com.workflow.annotation.processor;

import com.workflow.TaskWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.WorkflowRef;
import com.workflow.annotation.processor.WorkflowElement.ElementType;
import com.workflow.exception.CircularDependencyException;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.exception.WorkflowCompositionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import org.slf4j.Logger;

/**
 * Abstract base class for annotation-based workflow processors.
 *
 * <p>This class provides common functionality for processing workflow annotations, extracting
 * workflow elements, validating dependencies, and building workflows from annotated classes.
 */
public abstract class AnnotationWorkflowProcessor {
  /**
   * Gets the logger for this processor.
   *
   * @return the logger
   */
  protected abstract Logger getLogger();

  /**
   * Builds a workflow from an annotated class.
   *
   * @param workflowClass the class annotated with {@link WorkflowAnnotation}
   * @return the constructed Workflow
   * @throws IllegalArgumentException if the class is not properly annotated
   * @throws WorkflowBuildException if workflow construction fails
   */
  public Workflow buildWorkflow(Class<?> workflowClass) {
    WorkflowAnnotation annotation = workflowClass.getAnnotation(WorkflowAnnotation.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Class " + workflowClass.getName() + " is not annotated with @WorkflowAnnotation");
    }

    try {
      Object instance = workflowClass.getDeclaredConstructor().newInstance();
      return buildWorkflow(instance, annotation);
    } catch (WorkflowCompositionException e) {
      // Re-throw composition exceptions as-is (includes CircularDependencyException)
      throw e;
    } catch (Exception e) {
      throw new WorkflowBuildException(
          "Failed to instantiate workflow class: " + workflowClass.getName(), e);
    }
  }

  /**
   * Builds a workflow from an instance of an annotated class.
   *
   * @param instance the instance of a class annotated with {@link WorkflowAnnotation}
   * @return the constructed Workflow
   * @throws IllegalArgumentException if the instance's class is not properly annotated
   * @throws WorkflowBuildException if workflow construction fails
   */
  public Workflow buildWorkflow(Object instance) {
    Class<?> clazz = instance.getClass();
    WorkflowAnnotation annotation = clazz.getAnnotation(WorkflowAnnotation.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Class " + clazz.getName() + " is not annotated with @WorkflowAnnotation");
    }

    return buildWorkflow(instance, annotation);
  }

  /**
   * Builds a workflow from an instance and its workflow annotation.
   *
   * @param instance the workflow instance
   * @param annotation the workflow annotation
   * @return the built workflow
   */
  protected abstract Workflow buildWorkflow(Object instance, WorkflowAnnotation annotation);

  /**
   * Builds a workflow from a workflow ref annotation.
   *
   * @param metadata the workflow ref metadata
   * @return the built workflow
   */
  protected Workflow buildWorkflow(WorkflowRefMetadata metadata) {
    Class<?> workflowClass = metadata.workflowClass();
    getLogger().debug("Building workflow refs: {}", workflowClass.getName());

    try {
      Object instance = workflowClass.getDeclaredConstructor().newInstance();
      WorkflowAnnotation annotation = workflowClass.getAnnotation(WorkflowAnnotation.class);
      if (annotation == null) {
        throw new WorkflowCompositionException(
            "WorkflowRef class "
                + workflowClass.getName()
                + " is not annotated with @WorkflowAnnotation");
      }
      return buildWorkflow(instance, annotation);
    } catch (WorkflowCompositionException e) {
      // Re-throw composition exceptions as-is (includes CircularDependencyException)
      throw e;
    } catch (Exception e) {
      throw new WorkflowCompositionException(
          "Failed to instantiate workflow ref: " + workflowClass.getName(), e);
    }
  }

  /**
   * Extracts all workflow ref field annotations from a class.
   *
   * @param clazz the class to extract from
   * @return list of workflow ref metadata
   */
  protected List<WorkflowRefMetadata> extractWorkflowRefs(Class<?> clazz) {
    List<WorkflowRefMetadata> workflowRefMetadata = new ArrayList<>();

    for (Field field : clazz.getDeclaredFields()) {
      WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);
      if (annotation != null) {
        if (!field.getType().equals(Workflow.class)) {
          throw new IllegalArgumentException(
              "Field " + field.getName() + " annotated with @WorkflowRef must be of type Workflow");
        }
        WorkflowRefMetadata metadata = WorkflowRefMetadata.from(field, annotation);
        getLogger().debug("Extracted workflowRefMetadata workflow: {}", metadata);
        workflowRefMetadata.add(metadata);
      }
    }

    return workflowRefMetadata;
  }

  /**
   * Builds a map of parameter names to Workflow instances for @WorkflowRef annotated parameters in
   * a method.
   *
   * @param method the method to extract parameters from
   * @return map of parameter name to Workflow instance
   */
  protected Map<String, Workflow> buildWorkflowRefParameterMap(Method method) {
    Map<String, Workflow> parameterMap = new HashMap<>();

    for (Parameter parameter : method.getParameters()) {
      WorkflowRef annotation = parameter.getAnnotation(WorkflowRef.class);
      if (annotation != null) {
        if (!Workflow.class.equals(parameter.getType())) {
          throw new IllegalArgumentException(
              "Parameter "
                  + parameter.getName()
                  + " annotated with @WorkflowRef must be of type Workflow");
        }
        // Build the workflow for this parameter
        try {
          Object refInstance = annotation.workflowClass().getDeclaredConstructor().newInstance();
          WorkflowAnnotation refAnnotation =
              annotation.workflowClass().getAnnotation(WorkflowAnnotation.class);
          if (refAnnotation == null) {
            throw new WorkflowCompositionException(
                "@WorkflowRef parameter "
                    + parameter.getName()
                    + " references class "
                    + annotation.workflowClass().getName()
                    + " which is not annotated with @WorkflowAnnotation");
          }
          Workflow workflowRef = buildWorkflow(refInstance, refAnnotation);
          parameterMap.put(parameter.getName(), workflowRef);
          getLogger().debug("Built workflow for @WorkflowRef parameter: {}", parameter.getName());
        } catch (WorkflowCompositionException e) {
          throw e;
        } catch (Exception e) {
          throw new WorkflowBuildException(
              "Failed to build workflow for @WorkflowRef parameter: " + parameter.getName(), e);
        }
      }
    }

    return parameterMap;
  }

  /**
   * Validates that there are no circular dependencies in the workflow composition.
   *
   * @param clazz the class to validate
   * @param visited set of already visited classes
   * @throws CircularDependencyException if a circular dependency is detected
   */
  protected void validateNoDependencies(Class<?> clazz, Set<Class<?>> visited) {
    if (visited.contains(clazz)) {
      throw new CircularDependencyException(
          "Circular dependency detected involving class: " + clazz.getName());
    }

    visited.add(clazz);

    for (Field field : clazz.getDeclaredFields()) {
      WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);
      if (annotation != null) {
        Class<?> workflowRefClass = annotation.workflowClass();
        validateNoDependencies(workflowRefClass, new HashSet<>(visited));
      }
    }
  }

  /**
   * Extracts all workflow and task method annotations from a class.
   *
   * @param clazz the class to extract from
   * @return list of workflow elements
   */
  protected List<WorkflowElement> extractWorkflowElements(Class<?> clazz) {
    List<WorkflowElement> elements = new ArrayList<>();

    for (Method method : clazz.getDeclaredMethods()) {
      WorkflowMethod workflowMethod = method.getAnnotation(WorkflowMethod.class);
      TaskMethod taskMethod = method.getAnnotation(TaskMethod.class);

      if (workflowMethod != null) {
        validateWorkflowMethod(method);
        String name = workflowMethod.name().isEmpty() ? method.getName() : workflowMethod.name();
        elements.add(
            new WorkflowElement(
                name, workflowMethod.order(), method, ElementType.WORKFLOW, workflowMethod));
      } else if (taskMethod != null) {
        validateTaskMethod(method);
        String name = taskMethod.name().isEmpty() ? method.getName() : taskMethod.name();
        elements.add(
            new WorkflowElement(name, taskMethod.order(), method, ElementType.TASK, taskMethod));
      }
    }

    elements.sort(WorkflowElement::compareTo);
    getLogger().debug("Extracted {} workflow elements from {}", elements.size(), clazz.getName());
    return elements;
  }

  /**
   * Validates that a method annotated with @WorkflowMethod returns a Workflow instance.
   *
   * @param method the method to validate
   */
  protected void validateWorkflowMethod(Method method) {
    if (!Workflow.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          "Method "
              + method.getName()
              + " annotated with @WorkflowMethod must return a Workflow instance");
    }
  }

  /**
   * Validates that a method annotated with @TaskMethod returns a Task instance.
   *
   * @param method the method to validate
   */
  protected void validateTaskMethod(Method method) {
    if (!Task.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          "Method " + method.getName() + " annotated with @TaskMethod must return a Task instance");
    }
  }

  /**
   * Wraps a Task instance as a TaskWorkflow with the appropriate metadata.
   *
   * @param task the task instance
   * @param element the workflow element metadata
   * @return the wrapped TaskWorkflow
   */
  protected Workflow wrapTaskAsWorkflow(Task task, WorkflowElement element) {
    TaskMethod taskMethod = (TaskMethod) element.metadata();
    TaskDescriptor.TaskDescriptorBuilder descriptorBuilder =
        TaskDescriptor.builder().task(task).name(element.name());
    if (taskMethod.maxRetries() > 0) {
      descriptorBuilder.retryPolicy(RetryPolicy.limitedRetries(taskMethod.maxRetries()));
    }
    if (taskMethod.timeoutMs() > 0) {
      descriptorBuilder.timeoutPolicy(TimeoutPolicy.ofMillis(taskMethod.timeoutMs()));
    }
    return new TaskWorkflow(descriptorBuilder.build());
  }
}
