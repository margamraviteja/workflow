package com.workflow.database;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link WorkflowConfigRepository}. */
@Slf4j
class WorkflowConfigRepositoryTest {

  private DataSource dataSource;
  private WorkflowConfigRepository repository;

  @BeforeEach
  void setUp() throws SQLException {
    // Create H2 in-memory database with DB_CLOSE_DELAY to keep it alive between connections
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    this.dataSource = ds;

    // Initialize schema
    initializeDatabase();
    clearDatabase();
    this.repository = new WorkflowConfigRepository(dataSource);
  }

  private void initializeDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS workflow ("
              + "id INT PRIMARY KEY AUTO_INCREMENT,"
              + "name VARCHAR(255) NOT NULL UNIQUE,"
              + "description VARCHAR(255),"
              + "is_parallel BOOLEAN DEFAULT FALSE"
              + ")");

      stmt.execute(
          "CREATE TABLE IF NOT EXISTS workflow_steps ("
              + "id INT PRIMARY KEY AUTO_INCREMENT,"
              + "workflow_id INT NOT NULL,"
              + "name VARCHAR(255) NOT NULL,"
              + "description VARCHAR(255),"
              + "instance_name VARCHAR(255) NOT NULL,"
              + "order_index INT NOT NULL,"
              + "FOREIGN KEY (workflow_id) REFERENCES workflow(id)"
              + ")");
    }
  }

  private void clearDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM workflow_steps");
      stmt.execute("DELETE FROM workflow");
    }
  }

  @Test
  void testGetWorkflow() throws SQLException {
    // Setup
    insertWorkflow("TestWorkflow", "A test workflow", false);

    // Execute
    Optional<WorkflowMetadata> metadata = repository.getWorkflow("TestWorkflow");

    // Assert
    assertTrue(metadata.isPresent());
    assertEquals("TestWorkflow", metadata.get().name());
    assertEquals("A test workflow", metadata.get().description());
    assertFalse(metadata.get().isParallel());
  }

  @Test
  void testGetWorkflowNotFound() throws SQLException {
    // Execute
    Optional<WorkflowMetadata> metadata = repository.getWorkflow("NonExistent");

    // Assert
    assertTrue(metadata.isEmpty());
  }

  @Test
  void testGetWorkflowSteps() throws SQLException {
    // Setup
    insertWorkflow("TestWorkflow", "Test", false);
    insertWorkflowSteps("TestWorkflow", "Step1", "Validate", "ValidationTask", 1);
    insertWorkflowSteps("TestWorkflow", "Step2", "Process", "ProcessingTask", 2);

    // Execute
    List<WorkflowStepMetadata> steps = repository.getWorkflowSteps("TestWorkflow");

    // Assert
    assertEquals(2, steps.size());
    assertEquals("Step1", steps.get(0).name());
    assertEquals("Step2", steps.get(1).name());
    assertEquals("ValidationTask", steps.get(0).instanceName());
    assertEquals("ProcessingTask", steps.get(1).instanceName());
  }

  @Test
  void testGetWorkflowStepsOrdering() throws SQLException {
    // Setup - insert steps out of order
    insertWorkflow("OrderedWorkflow", "Test", false);
    insertWorkflowSteps("OrderedWorkflow", "Step3", "Third", "Task3", 3);
    insertWorkflowSteps("OrderedWorkflow", "Step1", "First", "Task1", 1);
    insertWorkflowSteps("OrderedWorkflow", "Step2", "Second", "Task2", 2);

    // Execute
    List<WorkflowStepMetadata> steps = repository.getWorkflowSteps("OrderedWorkflow");

    // Assert - should be ordered by order_index
    assertEquals(3, steps.size());
    assertEquals(1, steps.get(0).orderIndex());
    assertEquals(2, steps.get(1).orderIndex());
    assertEquals(3, steps.get(2).orderIndex());
  }

  @Test
  void testGetWorkflowStepsNoResults() throws SQLException {
    // Setup
    insertWorkflow("EmptyWorkflow", "Empty", false);

    // Execute
    List<WorkflowStepMetadata> steps = repository.getWorkflowSteps("EmptyWorkflow");

    // Assert
    assertTrue(steps.isEmpty());
  }

  @Test
  void testGetAllWorkflows() throws SQLException {
    // Setup
    insertWorkflow("Workflow1", "First", false);
    insertWorkflow("Workflow2", "Second", true);
    insertWorkflow("Workflow3", "Third", false);

    // Execute
    List<WorkflowMetadata> workflows = repository.getAllWorkflows();

    // Assert
    assertEquals(3, workflows.size());
    assertTrue(workflows.stream().anyMatch(w -> w.name().equals("Workflow1")));
    assertTrue(workflows.stream().anyMatch(w -> w.name().equals("Workflow2")));
    assertTrue(workflows.stream().anyMatch(w -> w.name().equals("Workflow3")));
  }

  @Test
  void testGetAllWorkflowsEmpty() throws SQLException {
    // Execute
    List<WorkflowMetadata> workflows = repository.getAllWorkflows();

    // Assert
    assertTrue(workflows.isEmpty());
  }

  @Test
  void testWorkflowExists() throws SQLException {
    // Setup
    insertWorkflow("ExistingWorkflow", "Exists", false);

    // Execute & Assert
    assertTrue(repository.workflowExists("ExistingWorkflow"));
    assertFalse(repository.workflowExists("NonExistentWorkflow"));
  }

  @Test
  void testParallelFlagHandling() throws SQLException {
    // Setup
    insertWorkflow("SequentialWF", "Sequential", false);
    insertWorkflow("ParallelWF", "Parallel", true);

    // Execute
    Optional<WorkflowMetadata> sequential = repository.getWorkflow("SequentialWF");
    Optional<WorkflowMetadata> parallel = repository.getWorkflow("ParallelWF");

    // Assert
    assertTrue(sequential.isPresent());
    assertFalse(sequential.get().isParallel());
    assertTrue(parallel.isPresent());
    assertTrue(parallel.get().isParallel());
  }

  @Test
  void testNullDescriptionHandling() throws SQLException {
    // Setup
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO workflow (name, is_parallel) VALUES ('NoDescriptionWF', FALSE)");
    }

    // Execute
    Optional<WorkflowMetadata> metadata = repository.getWorkflow("NoDescriptionWF");

    // Assert
    assertTrue(metadata.isPresent());
    assertNull(metadata.get().description());
  }

  @Test
  void testMultipleWorkflowsWithSteps() throws SQLException {
    // Setup
    insertWorkflow("WF1", "Workflow 1", false);
    insertWorkflow("WF2", "Workflow 2", true);
    insertWorkflowSteps("WF1", "S1", "Step 1", "Task1", 1);
    insertWorkflowSteps("WF1", "S2", "Step 2", "Task2", 2);
    insertWorkflowSteps("WF2", "S1", "Step 1", "Task3", 1);

    // Execute
    List<WorkflowMetadata> workflows = repository.getAllWorkflows();
    List<WorkflowStepMetadata> wf1Steps = repository.getWorkflowSteps("WF1");
    List<WorkflowStepMetadata> wf2Steps = repository.getWorkflowSteps("WF2");

    // Assert
    assertEquals(2, workflows.size());
    assertEquals(2, wf1Steps.size());
    assertEquals(1, wf2Steps.size());
  }

  private void insertWorkflow(String name, String description, boolean isParallel)
      throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "INSERT INTO workflow (name, description, is_parallel) "
              + "VALUES ('"
              + name
              + "', '"
              + description
              + "', "
              + isParallel
              + ")");
    }
  }

  private void insertWorkflowSteps(
      String workflowName, String stepName, String description, String instanceName, int orderIndex)
      throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      var result = stmt.executeQuery("SELECT id FROM workflow WHERE name = '" + workflowName + "'");
      int workflowId = result.next() ? result.getInt("id") : -1;

      if (workflowId != -1) {
        stmt.execute(
            "INSERT INTO workflow_steps (workflow_id, name, description, instance_name, order_index) "
                + "VALUES ("
                + workflowId
                + ", '"
                + stepName
                + "', '"
                + description
                + "', '"
                + instanceName
                + "', "
                + orderIndex
                + ")");
      }
    }
  }
}
