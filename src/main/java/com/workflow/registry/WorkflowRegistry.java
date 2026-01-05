package com.workflow.registry;

import com.workflow.Workflow;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry for managing workflow instances and their metadata. This registry maintains a cache of
 * built workflows to avoid redundant construction and provides lookup mechanisms.
 *
 * <p>Thread-safe implementation that supports concurrent access and workflow discovery.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WorkflowRegistry registry = new WorkflowRegistry();
 * registry.register("DataProcessing", dataProcessingWorkflow);
 * Optional<Workflow> workflow = registry.getWorkflow("DataProcessing");
 * }</pre>
 */
@Slf4j
public class WorkflowRegistry {
  private final Map<String, Workflow> workflowCache = new HashMap<>();
  private final Set<String> registeredWorkflows = new HashSet<>();

  /** Creates a new empty workflow registry. */
  public WorkflowRegistry() {
    // no-arg constructor
  }

  /**
   * Registers a workflow instance in the registry by name.
   *
   * @param name the unique workflow name
   * @param workflow the workflow instance
   * @throws IllegalArgumentException if a workflow with the same name is already registered
   */
  public synchronized void register(String name, Workflow workflow) {
    if (workflowCache.containsKey(name)) {
      throw new IllegalArgumentException("Workflow with name '" + name + "' is already registered");
    }
    workflowCache.put(name, workflow);
    registeredWorkflows.add(name);
    log.debug("Registered workflow: {}", name);
  }

  /**
   * Retrieves a registered workflow by name.
   *
   * @param name the workflow name
   * @return an Optional containing the workflow if found, empty otherwise
   */
  public synchronized Optional<Workflow> getWorkflow(String name) {
    return Optional.ofNullable(workflowCache.get(name));
  }

  /**
   * Checks if a workflow is registered by name.
   *
   * @param name the workflow name
   * @return true if the workflow is registered
   */
  public synchronized boolean isRegistered(String name) {
    return registeredWorkflows.contains(name);
  }
}
