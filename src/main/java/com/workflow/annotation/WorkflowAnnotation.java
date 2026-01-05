package com.workflow.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a workflow definition. The annotated class should contain methods annotated with
 * {@link WorkflowMethod} and/or {@link TaskMethod} to define the workflow structure.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @WorkflowAnnotation(name = "DataProcessingWorkflow", parallel = false)
 * public class DataProcessingWorkflowDefinition {
 *   @WorkflowMethod(name = "Validation", order = 1)
 *   public Workflow createValidationWorkflow() {
 *     return SequentialWorkflow.builder()
 *       .name("ValidationWorkflow")
 *       .workflow(...)
 *       .build();
 *   }
 *
 *   @TaskMethod(name = "LoadData", order = 2)
 *   public Task createLoadTask() {
 *     return context -> { ... };
 *   }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowAnnotation {
  /**
   * The name of the workflow. If not specified, the class name will be used.
   *
   * @return the workflow name
   */
  String name() default "";

  /**
   * Whether the workflows/tasks defined in this class should be executed in parallel. Defaults to
   * false (sequential execution).
   *
   * @return true if workflows should execute in parallel
   */
  boolean parallel() default false;

  /**
   * Whether the workflow should share the parent workflow's {@link
   * com.workflow.context.WorkflowContext}. Defaults to true and is configurable for parallel
   * workflows.
   *
   * @return true if context should be shared for parallel workflows
   */
  boolean shareContext() default true;

  /**
   * Optional description of the workflow for documentation purposes.
   *
   * @return workflow description
   */
  String description() default "";

  /**
   * Tags for categorizing workflows (e.g., "data-processing", "ml-pipeline").
   *
   * @return array of tags
   */
  String[] tags() default {};
}
