package com.workflow.database;

/**
 * Metadata about a workflow read from the database. Represents a row from the workflow table.
 *
 * <p>This record holds workflow-level configuration including execution mode, context sharing, and
 * fail-fast behavior for parallel workflows.
 *
 * @param name the workflow name
 * @param description the workflow description
 * @param isParallel whether the workflow should execute in parallel
 * @param failFast whether to stop immediately on first failure (parallel workflows only)
 * @param shareContext whether to share context across parallel workflows (parallel workflows only)
 */
public record WorkflowMetadata(
    String name, String description, boolean isParallel, boolean failFast, boolean shareContext) {
  /**
   * Creates workflow metadata from database values with default failFast=false and
   * shareContext=true.
   *
   * @param name the workflow name
   * @param description the workflow description
   * @param isParallel whether the workflow should execute in parallel
   * @return the metadata record
   */
  public static WorkflowMetadata of(String name, String description, boolean isParallel) {
    return new WorkflowMetadata(name, description, isParallel, false, true);
  }

  /**
   * Creates workflow metadata from database values with all options.
   *
   * @param name the workflow name
   * @param description the workflow description
   * @param isParallel whether the workflow should execute in parallel
   * @param failFast whether to stop immediately on first failure (parallel workflows only)
   * @param shareContext whether to share context across parallel workflows (parallel workflows
   *     only)
   * @return the metadata record
   */
  public static WorkflowMetadata of(
      String name, String description, boolean isParallel, boolean failFast, boolean shareContext) {
    return new WorkflowMetadata(name, description, isParallel, failFast, shareContext);
  }
}
