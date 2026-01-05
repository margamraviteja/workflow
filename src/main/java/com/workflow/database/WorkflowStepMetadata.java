package com.workflow.database;

/**
 * Metadata about a workflow step read from the database. Represents a row from the workflow_steps
 * table.
 *
 * <p>This record holds step-level configuration pointing to a registered workflow instance via the
 * instance_name column.
 *
 * @param name the step name
 * @param description the step description
 * @param instanceName the name of the registered workflow instance
 * @param orderIndex the execution order (lower numbers execute first)
 */
public record WorkflowStepMetadata(
    String name, String description, String instanceName, int orderIndex) {
  /**
   * Creates workflow step metadata from database values.
   *
   * @param name the step name
   * @param description the step description
   * @param instanceName the name of the registered workflow instance
   * @param orderIndex the execution order (lower numbers execute first)
   * @return the metadata record
   */
  public static WorkflowStepMetadata of(
      String name, String description, String instanceName, int orderIndex) {
    return new WorkflowStepMetadata(name, description, instanceName, orderIndex);
  }
}
