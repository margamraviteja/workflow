package com.workflow;

import java.util.List;

/**
 * Interface for workflows that act as containers for other workflows. Used for hierarchical
 * traversal and visualization.
 */
public interface WorkflowContainer extends Workflow {
  /** Returns the list of workflows nested within this container. */
  List<Workflow> getSubWorkflows();
}
