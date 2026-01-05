package com.workflow.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or method parameter to include another workflow defined with {@link
 * WorkflowAnnotation}. This enables composition of workflows into larger workflows using the
 * composite pattern.
 *
 * <p>Can be used on fields or method parameters:
 *
 * <pre>{@code
 * @WorkflowAnnotation(name = "CompositeWorkflow")
 * public class CompositeWorkflowDefinition {
 *   @WorkflowRef(workflowClass = DataValidationWorkflow.class)
 *   private Workflow validationWorkflow;
 *
 *   @WorkflowRef(workflowClass = DataProcessingWorkflow.class)
 *   private Workflow processingWorkflow;
 *
 *   @WorkflowMethod(name = "Main")
 *   public Workflow mainWorkflow() {
 *     return SequentialWorkflow.builder()
 *       .workflow(validationWorkflow)
 *       .workflow(processingWorkflow)
 *       .build();
 *   }
 *
 *   @WorkflowMethod(name = "Alternative")
 *   public Workflow alternativeWorkflow(
 *       @WorkflowRef(workflowClass = DataValidationWorkflow.class) Workflow validation) {
 *     return validation;
 *   }
 * }
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowRef {
  /**
   * The class annotated with {@link WorkflowAnnotation} that defines the workflow to include.
   *
   * @return the workflow class
   */
  Class<?> workflowClass();

  /**
   * Optional description of why this workflow is included.
   *
   * @return description
   */
  String description() default "";
}
