package com.workflow.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that creates and returns a {@link com.workflow.task.Task} instance. Methods
 * annotated with this annotation should return a Task object and can have any access modifier.
 *
 * <p>The method can accept parameters that will be injected based on the annotation processing
 * approach used. Spring based annotation processing supports parameters annotated with Autowired.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @TaskMethod(name = "LoadData", order = 1)
 * public Task createLoadDataTask() {
 *   return new FileReadTask("input.csv");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskMethod {
  /**
   * The name of the task. If not specified, the method name will be used.
   *
   * @return the task name
   */
  String name() default "";

  /**
   * The order in which this task should be executed relative to other workflows/tasks in the same
   * class. Lower numbers execute first.
   *
   * @return the execution order
   */
  int order() default 0;

  /**
   * Optional description of what this task does.
   *
   * @return task description
   */
  String description() default "";

  /**
   * Maximum number of retry attempts for this task if it fails.
   *
   * @return max retry attempts
   */
  int maxRetries() default 0;

  /**
   * Timeout in milliseconds for task execution. 0 means no timeout.
   *
   * @return timeout in milliseconds
   */
  long timeoutMs() default 0;
}
