package com.workflow.annotation.processor;

import com.workflow.annotation.WorkflowRef;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

/**
 * Metadata about an included workflow extracted from the {@link WorkflowRef} annotation. This class
 * holds all the information needed to resolve and compose a workflow reference. It supports both
 * field and parameter annotations.
 */
public record WorkflowRefMetadata(
    String name, Field field, Parameter parameter, WorkflowRef annotation, Class<?> workflowClass) {

  /**
   * Creates metadata from an WorkflowRef annotation and field.
   *
   * @param field the field annotated with @WorkflowRef
   * @param annotation the annotation instance
   * @return the metadata
   */
  public static WorkflowRefMetadata from(Field field, WorkflowRef annotation) {
    String name = field.getName();
    return new WorkflowRefMetadata(name, field, null, annotation, annotation.workflowClass());
  }

  /**
   * Creates metadata from an WorkflowRef annotation and parameter.
   *
   * @param parameter the parameter annotated with @WorkflowRef
   * @param annotation the annotation instance
   * @return the metadata
   */
  public static WorkflowRefMetadata from(Parameter parameter, WorkflowRef annotation) {
    String name = parameter.getName();
    return new WorkflowRefMetadata(name, null, parameter, annotation, annotation.workflowClass());
  }
}
