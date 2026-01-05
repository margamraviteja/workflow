package com.workflow.examples.database;

import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.database.DatabaseWorkflowProcessor;
import com.workflow.database.WorkflowMetadata;
import com.workflow.registry.WorkflowRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Example demonstrating how to use the {@link DatabaseWorkflowProcessor} to build workflows from
 * database configuration.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Setting up an H2 in-memory database with workflow and workflow_steps tables
 *   <li>Creating simple task-based workflows and registering them in WorkflowRegistry
 *   <li>Configuring workflows in the database
 *   <li>Building and executing workflows using DatabaseWorkflowProcessor
 * </ul>
 *
 * <p>Database Schema:
 *
 * <ul>
 *   <li>{@code workflow} table: id (INT), name (VARCHAR), description (VARCHAR), is_parallel
 *       (BOOLEAN)
 *   <li>{@code workflow_steps} table: id (INT), workflow_id (INT), name (VARCHAR), description
 *       (VARCHAR), instance_name (VARCHAR), order_index (INT)
 * </ul>
 *
 * <p>To run this example standalone:
 *
 * <pre>{@code
 * DatabaseWorkflowExample example = new DatabaseWorkflowExample();
 * example.run();
 * }</pre>
 */
@Slf4j
public class DatabaseWorkflowExample {

  /**
   * Initializes the database schema with workflow and workflow_steps tables.
   *
   * @param dataSource the data source to initialize
   * @throws SQLException if initialization fails
   */
  public static void initializeDatabase(DataSource dataSource) throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Create workflow table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS workflow ("
              + "id INT PRIMARY KEY AUTO_INCREMENT,"
              + "name VARCHAR(255) NOT NULL UNIQUE,"
              + "description VARCHAR(255),"
              + "is_parallel BOOLEAN DEFAULT FALSE"
              + ")");

      // Create workflow_steps table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS workflow_steps ("
              + "id INT PRIMARY KEY AUTO_INCREMENT,"
              + "workflow_id INT NOT NULL,"
              + "name VARCHAR(255) NOT NULL,"
              + "description VARCHAR(255),"
              + "instance_name VARCHAR(255) NOT NULL,"
              + "order_index INT NOT NULL,"
              + "FOREIGN KEY (workflow_id) REFERENCES workflow(id),"
              + "UNIQUE(workflow_id, name)"
              + ")");

      log.info("Database schema initialized successfully");
    }
  }

  /**
   * Populates the database with sample workflow configurations.
   *
   * @param dataSource the data source to populate
   * @throws SQLException if population fails
   */
  public static void populateSampleData(DataSource dataSource) throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Insert workflow definitions
      stmt.execute(
          "INSERT INTO workflow (name, description, is_parallel) "
              + "VALUES ('DataProcessingPipeline', 'A sequential data processing pipeline', FALSE)");

      stmt.execute(
          "INSERT INTO workflow (name, description, is_parallel) "
              + "VALUES ('ParallelValidation', 'Parallel validation workflow', TRUE)");

      // Get workflow IDs
      var workflowId1 =
          stmt.executeQuery("SELECT id FROM workflow WHERE name = 'DataProcessingPipeline'");
      int dataProcessingId = 0;
      if (workflowId1.next()) {
        dataProcessingId = workflowId1.getInt("id");
      }

      var workflowId2 =
          stmt.executeQuery("SELECT id FROM workflow WHERE name = 'ParallelValidation'");
      int parallelValidationId = 0;
      if (workflowId2.next()) {
        parallelValidationId = workflowId2.getInt("id");
      }

      // Insert workflow steps for DataProcessingPipeline
      stmt.execute(
          "INSERT INTO workflow_steps "
              + "(workflow_id, name, description, instance_name, order_index) "
              + "VALUES ("
              + dataProcessingId
              + ", 'Validation', 'Validate input data', "
              + "'ValidationWorkflow', 1)");

      stmt.execute(
          "INSERT INTO workflow_steps "
              + "(workflow_id, name, description, instance_name, order_index) "
              + "VALUES ("
              + dataProcessingId
              + ", 'Processing', 'Process data', "
              + "'ProcessingWorkflow', 2)");

      stmt.execute(
          "INSERT INTO workflow_steps "
              + "(workflow_id, name, description, instance_name, order_index) "
              + "VALUES ("
              + dataProcessingId
              + ", 'Output', 'Generate output', "
              + "'OutputWorkflow', 3)");

      // Insert workflow steps for ParallelValidation
      stmt.execute(
          "INSERT INTO workflow_steps "
              + "(workflow_id, name, description, instance_name, order_index) "
              + "VALUES ("
              + parallelValidationId
              + ", 'SchemaCheck', 'Validate schema', "
              + "'SchemaCheckWorkflow', 1)");

      stmt.execute(
          "INSERT INTO workflow_steps "
              + "(workflow_id, name, description, instance_name, order_index) "
              + "VALUES ("
              + parallelValidationId
              + ", 'DataCheck', 'Validate data', "
              + "'DataCheckWorkflow', 2)");

      log.info("Sample data populated successfully");
    }
  }

  /**
   * Creates sample task-based workflows and registers them in the registry.
   *
   * @param registry the workflow registry
   */
  public static void registerSampleWorkflows(WorkflowRegistry registry) {
    // Simple validation workflow
    com.workflow.task.Task validationTask =
        context -> {
          log.info("Validating input data...");
          context.put("validated", true);
        };

    // Simple processing workflow
    com.workflow.task.Task processingTask =
        context -> {
          log.info("Processing data...");
          context.put("processed", true);
        };

    // Simple output workflow
    com.workflow.task.Task outputTask =
        context -> {
          log.info("Generating output...");
          context.put("output_generated", true);
        };

    // Schema check workflow
    com.workflow.task.Task schemaCheckTask =
        context -> {
          log.info("Checking schema...");
          context.put("schema_valid", true);
        };

    // Data check workflow
    com.workflow.task.Task dataCheckTask =
        context -> {
          log.info("Checking data integrity...");
          context.put("data_valid", true);
        };

    // Register workflows as TaskWorkflows
    registry.register("ValidationWorkflow", new com.workflow.TaskWorkflow(validationTask));
    registry.register("ProcessingWorkflow", new com.workflow.TaskWorkflow(processingTask));
    registry.register("OutputWorkflow", new com.workflow.TaskWorkflow(outputTask));
    registry.register("SchemaCheckWorkflow", new com.workflow.TaskWorkflow(schemaCheckTask));
    registry.register("DataCheckWorkflow", new com.workflow.TaskWorkflow(dataCheckTask));

    log.info("Sample workflows registered");
  }

  /** Main method to run the example. */
  public static void main(String[] args) {
    try {
      // Create H2 in-memory data source
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:workflowdb");
      dataSource.setUser("sa");
      dataSource.setPassword("");

      // Initialize database
      initializeDatabase(dataSource);

      // Populate sample data
      populateSampleData(dataSource);

      // Create and populate workflow registry
      WorkflowRegistry registry = new WorkflowRegistry();
      registerSampleWorkflows(registry);

      // Create database workflow processor
      DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);

      // Verify workflows exist
      List<WorkflowMetadata> allWorkflows = processor.getAllWorkflows();
      log.info("Available workflows in database:");
      for (WorkflowMetadata workflow : allWorkflows) {
        log.info(
            "  - {} (parallel: {}, description: {})",
            workflow.name(),
            workflow.isParallel(),
            workflow.description());
      }

      // Build and execute the sequential data processing pipeline
      log.info("\n=== Executing DataProcessingPipeline ===");
      Workflow dataProcessingWorkflow = processor.buildWorkflow("DataProcessingPipeline");
      WorkflowContext context = new WorkflowContext();
      var result = dataProcessingWorkflow.execute(context);
      log.info("DataProcessingPipeline result: {}", result.getStatus());

      // Build and execute the parallel validation workflow
      log.info("\n=== Executing ParallelValidation ===");
      Workflow parallelValidationWorkflow = processor.buildWorkflow("ParallelValidation");
      WorkflowContext context2 = new WorkflowContext();
      var result2 = parallelValidationWorkflow.execute(context2);
      log.info("ParallelValidation result: {}", result2.getStatus());

    } catch (SQLException e) {
      log.error("Error running example", e);
    }
  }
}
