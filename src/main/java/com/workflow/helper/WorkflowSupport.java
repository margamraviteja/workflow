package com.workflow.helper;

import com.workflow.Workflow;
import java.util.Collection;
import lombok.experimental.UtilityClass;

/**
 * Support utilities for Workflow implementations to reduce boilerplate code.
 *
 * <p>This class provides common functionality used across multiple workflow implementations,
 * eliminating duplication and ensuring consistency.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public class MyWorkflow extends AbstractWorkflow {
 *     private String name;
 *
 *     @Override
 *     public String getName() {
 *         return WorkflowSupport.resolveName(name, this);
 *     }
 * }
 * }</pre>
 */
@UtilityClass
public class WorkflowSupport {

  /**
   * Resolves the workflow name, returning the provided name if non-null, otherwise returning a
   * default name.
   *
   * <p>This eliminates the common pattern:
   *
   * <pre>{@code
   * return name != null ? name : getDefaultName();
   * }</pre>
   *
   * @param providedName the name provided to the workflow (maybe null)
   * @param workflow the workflow instance
   * @return the resolved name
   */
  public static String resolveName(String providedName, Workflow workflow) {
    return providedName != null ? providedName : workflow.getDefaultName();
  }

  /**
   * Validates that a workflow list is not null or empty, returning success for empty lists.
   *
   * <p>This is a common pattern in Sequential and Parallel workflows where an empty workflow list
   * is valid and should return SUCCESS.
   *
   * @param workflows the workflow list to check
   * @return true if the list is null or empty
   */
  public static boolean isEmptyWorkflowList(Collection<?> workflows) {
    return workflows == null || workflows.isEmpty();
  }

  /**
   * Generates a workflow type identifier with brackets.
   *
   * <p>Example: getWorkflowType("Sequence") returns "[Sequence]"
   *
   * @param type the workflow type name
   * @return the formatted workflow type
   */
  public static String formatWorkflowType(String type) {
    return "[" + type + "]";
  }

  /**
   * Generates a workflow type identifier with parentheses (for tasks).
   *
   * <p>Example: getTaskType("Task") returns "(Task)"
   *
   * @param type the task type name
   * @return the formatted task type
   */
  public static String formatTaskType(String type) {
    return "(" + type + ")";
  }
}
