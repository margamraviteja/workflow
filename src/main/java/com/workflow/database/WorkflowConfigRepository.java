package com.workflow.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Repository for reading workflow and workflow step metadata from database tables.
 *
 * <p>This repository provides methods to fetch workflow configurations and their steps from a
 * relational database. The database schema should have the following tables:
 *
 * <pre>{@code
 * CREATE TABLE workflow (
 *   id INT PRIMARY KEY AUTO_INCREMENT,
 *   name VARCHAR(255) NOT NULL UNIQUE,
 *   description VARCHAR(255),
 *   is_parallel BOOLEAN DEFAULT FALSE,
 *   fail_fast BOOLEAN DEFAULT FALSE,
 *   share_context BOOLEAN DEFAULT TRUE
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
 * <p>Usage example:
 *
 * <pre>{@code
 * DataSource dataSource = ...;
 * WorkflowConfigRepository repository = new WorkflowConfigRepository(dataSource);
 * Optional<WorkflowMetadata> metadata = repository.getWorkflow("MyWorkflow");
 * List<WorkflowStepMetadata> steps = repository.getWorkflowSteps("MyWorkflow");
 * }</pre>
 */
@Slf4j
public class WorkflowConfigRepository {
  public static final String NAME = "name";
  public static final String DESCRIPTION = "description";
  public static final String IS_PARALLEL = "is_parallel";
  public static final String FAIL_FAST = "fail_fast";
  public static final String SHARE_CONTEXT = "share_context";
  public static final String INSTANCE_NAME = "instance_name";
  public static final String ORDER_INDEX = "order_index";

  private final javax.sql.DataSource dataSource;

  /**
   * Creates a new repository with a JDBC DataSource.
   *
   * @param dataSource the JDBC DataSource to use
   */
  public WorkflowConfigRepository(javax.sql.DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Fetches workflow metadata by name.
   *
   * @param workflowName the workflow name
   * @return Optional containing the workflow metadata if found
   * @throws SQLException if database access fails
   */
  public Optional<WorkflowMetadata> getWorkflow(String workflowName) throws SQLException {
    String query =
        "SELECT name, description, is_parallel, fail_fast, share_context FROM workflow WHERE name = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, workflowName);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          String name = rs.getString(NAME);
          String description = rs.getString(DESCRIPTION);
          boolean isParallel = rs.getBoolean(IS_PARALLEL);
          boolean failFast = rs.getBoolean(FAIL_FAST);
          boolean shareContext = rs.getBoolean(SHARE_CONTEXT);
          log.debug("Fetched workflow metadata: {}", name);
          return Optional.of(
              WorkflowMetadata.of(name, description, isParallel, failFast, shareContext));
        }
      }
    }

    log.debug("Workflow not found: {}", workflowName);
    return Optional.empty();
  }

  /**
   * Fetches all workflow steps for a given workflow, ordered by order_index.
   *
   * @param workflowName the workflow name
   * @return list of workflow step metadata in execution order
   * @throws SQLException if database access fails
   */
  public List<WorkflowStepMetadata> getWorkflowSteps(String workflowName) throws SQLException {
    String query =
        "SELECT ws.name, ws.description, ws.instance_name, ws.order_index "
            + "FROM workflow_steps ws "
            + "JOIN workflow w ON ws.workflow_id = w.id "
            + "WHERE w.name = ? "
            + "ORDER BY ws.order_index ASC";

    List<WorkflowStepMetadata> steps = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, workflowName);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          String name = rs.getString(NAME);
          String description = rs.getString(DESCRIPTION);
          String instanceName = rs.getString(INSTANCE_NAME);
          int orderIndex = rs.getInt(ORDER_INDEX);
          steps.add(WorkflowStepMetadata.of(name, description, instanceName, orderIndex));
        }
      }
    }

    log.debug("Fetched {} steps for workflow: {}", steps.size(), workflowName);
    return steps;
  }

  /**
   * Fetches all registered workflows.
   *
   * @return list of all workflow metadata
   * @throws SQLException if database access fails
   */
  public List<WorkflowMetadata> getAllWorkflows() throws SQLException {
    String query =
        "SELECT name, description, is_parallel, fail_fast, share_context FROM workflow ORDER BY name ASC";
    List<WorkflowMetadata> workflows = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          String name = rs.getString(NAME);
          String description = rs.getString(DESCRIPTION);
          boolean isParallel = rs.getBoolean(IS_PARALLEL);
          boolean failFast = rs.getBoolean(FAIL_FAST);
          boolean shareContext = rs.getBoolean(SHARE_CONTEXT);
          workflows.add(WorkflowMetadata.of(name, description, isParallel, failFast, shareContext));
        }
      }
    }

    log.debug("Fetched {} workflows", workflows.size());
    return workflows;
  }

  /**
   * Checks if a workflow exists in the database.
   *
   * @param workflowName the workflow name
   * @return true if the workflow exists
   * @throws SQLException if database access fails
   */
  public boolean workflowExists(String workflowName) throws SQLException {
    return getWorkflow(workflowName).isPresent();
  }
}
