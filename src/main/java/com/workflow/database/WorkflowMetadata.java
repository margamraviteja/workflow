package com.workflow.database;

/**
 * Metadata about a workflow read from the database. Represents a row from the workflow table.
 *
 * <p>This record holds workflow-level configuration with minimal details: name, description, and
 * execution mode (parallel or sequential).
 *
 * @param name the workflow name
 * @param description the workflow description
 * @param isParallel whether the workflow should execute in parallel
 */
public record WorkflowMetadata(String name, String description, boolean isParallel) {
  /**
   * Creates workflow metadata from database values.
   *
   * @param name the workflow name
   * @param description the workflow description
   * @param isParallel whether the workflow should execute in parallel
   * @return the metadata record
   */
  public static WorkflowMetadata of(String name, String description, boolean isParallel) {
    return new WorkflowMetadata(name, description, isParallel);
  }
}
