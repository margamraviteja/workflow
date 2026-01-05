package com.workflow.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that creates and returns a {@link com.workflow.Workflow} instance. Methods
 * annotated with this annotation should return a Workflow object and can have any access modifier.
 *
 * <p>The method can accept parameters that will be injected based on the annotation processing
 * approach used. Spring based annotation processing supports parameters annotated with Autowired.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @WorkflowMethod(name = "DataValidation", order = 1)
 * public Workflow createValidationWorkflow() {
 *   return SequentialWorkflow.builder()
 *     .name("Validation")
 *     .task(new ValidateSchemaTask())
 *     .task(new ValidateDataTask())
 *     .build();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowMethod {
  /**
   * The name of the workflow. If not specified, the method name will be used.
   *
   * @return the workflow name
   */
  String name() default "";

  /**
   * The order in which this workflow should be executed relative to other workflows/tasks in the
   * same class. Lower numbers execute first.
   *
   * @return the execution order
   */
  int order() default 0;

  /**
   * Optional description of what this workflow does.
   *
   * @return workflow description
   */
  String description() default "";
}
