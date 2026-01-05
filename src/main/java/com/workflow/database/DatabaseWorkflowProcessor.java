package com.workflow.database;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.registry.WorkflowRegistry;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor for building workflows from database configuration.
 *
 * <p>This processor reads workflow metadata and workflow steps from database tables and composes
 * them into executable Workflow instances. The actual workflow implementations are resolved from a
 * {@link WorkflowRegistry} using the instance_name column from the workflow_steps table.
 *
 * <p>The database schema should have the following tables:
 *
 * <pre>{@code
 * CREATE TABLE workflow (
 *   id INT PRIMARY KEY AUTO_INCREMENT,
 *   name VARCHAR(255) NOT NULL UNIQUE,
 *   description VARCHAR(255),
 *   is_parallel BOOLEAN DEFAULT FALSE
 * );
 *
 * CREATE TABLE workflow_steps (
 *   id INT PRIMARY KEY AUTO_INCREMENT,
 *   workflow_id INT NOT NULL,
 *   name VARCHAR(255) NOT NULL,
 *   description VARCHAR(255),
 *   instance_name VARCHAR(255) NOT NULL,
 *   order_index INT NOT NULL,
 *   FOREIGN KEY (workflow_id) REFERENCES workflow(id),
 *   UNIQUE(workflow_id, name)
 * );
 * }</pre>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * DataSource dataSource = ...;
 * WorkflowRegistry registry = new WorkflowRegistry();
 * registry.register("ValidationWorkflow", validationWorkflow);
 * registry.register("ProcessingWorkflow", processingWorkflow);
 *
 * DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);
 * Workflow workflow = processor.buildWorkflow("DataPipeline");
 * }</pre>
 */
@Slf4j
public class DatabaseWorkflowProcessor {
  private final WorkflowConfigRepository repository;
  private final WorkflowRegistry registry;

  /**
   * Creates a new database workflow processor.
   *
   * @param dataSource the JDBC DataSource
   * @param registry the workflow registry for resolving workflow instances
   */
  public DatabaseWorkflowProcessor(DataSource dataSource, WorkflowRegistry registry) {
    this.repository = new WorkflowConfigRepository(dataSource);
    this.registry = registry;
  }

  /**
   * Creates a new database workflow processor with an existing repository.
   *
   * @param repository the workflow configuration repository
   * @param registry the workflow registry for resolving workflow instances
   */
  public DatabaseWorkflowProcessor(WorkflowConfigRepository repository, WorkflowRegistry registry) {
    this.repository = repository;
    this.registry = registry;
  }

  /**
   * Builds a workflow from database configuration by workflow name.
   *
   * @param workflowName the workflow name
   * @return the constructed Workflow
   * @throws WorkflowBuildException if the workflow cannot be built
   */
  public Workflow buildWorkflow(String workflowName) {
    try {
      log.info("Building workflow from database: {}", workflowName);

      // Fetch workflow metadata
      Optional<WorkflowMetadata> metadata = repository.getWorkflow(workflowName);
      if (metadata.isEmpty()) {
        throw new WorkflowBuildException(
            "Workflow not found in database: " + workflowName, new IllegalArgumentException());
      }

      WorkflowMetadata workflowMetadata = metadata.get();

      // Fetch workflow steps
      List<WorkflowStepMetadata> steps = repository.getWorkflowSteps(workflowName);
      if (steps.isEmpty()) {
        throw new WorkflowBuildException(
            "No workflow steps found for workflow: " + workflowName,
            new IllegalArgumentException());
      }

      // Resolve workflow instances from registry
      List<Workflow> workflows = resolveWorkflowInstances(steps);

      // Build the composite workflow
      Workflow compositeWorkflow =
          buildCompositeWorkflow(workflowName, workflowMetadata, workflows);

      log.info("Successfully built workflow: {} with {} steps", workflowName, workflows.size());
      return compositeWorkflow;

    } catch (SQLException e) {
      throw new WorkflowBuildException(
          "Database error while building workflow: " + workflowName, e);
    }
  }

  /**
   * Resolves all workflow instances from the registry based on step metadata.
   *
   * @param steps the workflow step metadata
   * @return list of resolved workflow instances in order
   * @throws WorkflowBuildException if any instance cannot be resolved
   */
  private List<Workflow> resolveWorkflowInstances(List<WorkflowStepMetadata> steps) {
    List<Workflow> workflows = new ArrayList<>();

    for (WorkflowStepMetadata step : steps) {
      String instanceName = step.instanceName();
      log.debug("Resolving workflow instance: {}", instanceName);

      Optional<Workflow> workflowOptional = registry.getWorkflow(instanceName);
      if (workflowOptional.isEmpty()) {
        throw new WorkflowBuildException(
            "Workflow instance not found in registry: " + instanceName,
            new IllegalArgumentException());
      }

      workflows.add(workflowOptional.get());
    }

    return workflows;
  }

  /**
   * Builds the composite workflow (sequential or parallel) from resolved workflow instances.
   *
   * @param workflowName the workflow name
   * @param metadata the workflow metadata
   * @param workflows the resolved workflow instances
   * @return the composite workflow
   */
  private Workflow buildCompositeWorkflow(
      String workflowName, WorkflowMetadata metadata, List<Workflow> workflows) {
    if (metadata.isParallel()) {
      log.debug("Creating parallel workflow: {}", workflowName);
      return ParallelWorkflow.builder().name(workflowName).workflows(workflows).build();
    } else {
      log.debug("Creating sequential workflow: {}", workflowName);
      return SequentialWorkflow.builder().name(workflowName).workflows(workflows).build();
    }
  }

  /**
   * Checks if a workflow exists in the database.
   *
   * @param workflowName the workflow name
   * @return true if the workflow exists in the database
   */
  public boolean workflowExists(String workflowName) {
    try {
      return repository.workflowExists(workflowName);
    } catch (SQLException e) {
      log.error("Database error while checking workflow existence: {}", workflowName, e);
      return false;
    }
  }

  /**
   * Fetches all available workflows from the database.
   *
   * @return list of workflow metadata for all registered workflows in the database
   */
  public List<WorkflowMetadata> getAllWorkflows() {
    try {
      return repository.getAllWorkflows();
    } catch (SQLException e) {
      log.error("Database error while fetching all workflows", e);
      return new ArrayList<>();
    }
  }
}
