package com.workflow.database;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.TaskWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.registry.WorkflowRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DatabaseWorkflowProcessor}. */
@Slf4j
class DatabaseWorkflowProcessorTest {

  private DataSource dataSource;
  private WorkflowRegistry registry;
  private DatabaseWorkflowProcessor processor;

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

    // Create registry and repository
    this.registry = new WorkflowRegistry();
    WorkflowConfigRepository repository = new WorkflowConfigRepository(dataSource);
    this.processor = new DatabaseWorkflowProcessor(repository, registry);
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
  void testBuildSequentialWorkflow() throws SQLException {
    // Setup
    insertWorkflowAndSteps("TestWorkflow", false, "Step1", "ValidationTask");
    registerTestWorkflow("ValidationTask");

    // Execute
    Workflow workflow = processor.buildWorkflow("TestWorkflow");

    // Assert
    assertNotNull(workflow);
    assertEquals("TestWorkflow", workflow.getName());
    assertInstanceOf(SequentialWorkflow.class, workflow);
  }

  @Test
  void testBuildParallelWorkflow() throws SQLException {
    // Setup
    insertWorkflowAndSteps("ParallelWorkflow", true, "Step1", "Task1");
    insertWorkflowAndSteps("ParallelWorkflow", true, "Step2", "Task2");
    registerTestWorkflow("Task1");
    registerTestWorkflow("Task2");

    // Execute
    Workflow workflow = processor.buildWorkflow("ParallelWorkflow");

    // Assert
    assertNotNull(workflow);
    assertEquals("ParallelWorkflow", workflow.getName());
    assertInstanceOf(ParallelWorkflow.class, workflow);
  }

  @Test
  void testWorkflowExecutionSequential() throws SQLException {
    // Setup
    insertWorkflowAndSteps("ExecutionWorkflow", false, "Step1", "Task1");
    insertWorkflowAndSteps("ExecutionWorkflow", false, "Step2", "Task2");

    Workflow task1 = new TaskWorkflow(context -> context.put("task1_executed", true));
    Workflow task2 = new TaskWorkflow(context -> context.put("task2_executed", true));

    registry.register("Task1", task1);
    registry.register("Task2", task2);

    // Execute
    Workflow workflow = processor.buildWorkflow("ExecutionWorkflow");
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    // Assert
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue((boolean) context.get("task1_executed"));
    assertTrue((boolean) context.get("task2_executed"));
  }

  @Test
  void testWorkflowNotFound() {
    // Execute & Assert
    assertThrows(
        WorkflowBuildException.class,
        () -> processor.buildWorkflow("NonExistentWorkflow"),
        "Workflow not found in database");
  }

  @Test
  void testInstanceNotInRegistry() throws SQLException {
    // Setup - workflow exists but task not registered
    insertWorkflowAndSteps("TestWorkflow", false, "Step1", "UnregisteredTask");

    // Execute & Assert
    assertThrows(
        WorkflowBuildException.class,
        () -> processor.buildWorkflow("TestWorkflow"),
        "Workflow instance not found in registry");
  }

  @Test
  void testNoWorkflowSteps() throws SQLException {
    // Setup - workflow exists but no steps
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "INSERT INTO workflow (name, description, is_parallel) "
              + "VALUES ('EmptyWorkflow', 'No steps', FALSE)");
    }

    // Execute & Assert
    assertThrows(
        WorkflowBuildException.class,
        () -> processor.buildWorkflow("EmptyWorkflow"),
        "No workflow steps found");
  }

  @Test
  void testWorkflowExistsCheck() throws SQLException {
    // Setup
    insertWorkflowAndSteps("TestWorkflow", false, "Step1", "Task1");

    // Execute & Assert
    assertTrue(processor.workflowExists("TestWorkflow"));
    assertFalse(processor.workflowExists("NonExistentWorkflow"));
  }

  @Test
  void testGetAllWorkflows() throws SQLException {
    // Setup
    insertWorkflowAndSteps("Workflow1", false, "Step1", "Task1");
    insertWorkflowAndSteps("Workflow2", true, "Step1", "Task1");

    // Execute
    java.util.List<WorkflowMetadata> workflows = processor.getAllWorkflows();

    // Assert
    assertEquals(2, workflows.size());
    assertTrue(workflows.stream().anyMatch(w -> w.name().equals("Workflow1")));
    assertTrue(workflows.stream().anyMatch(w -> w.name().equals("Workflow2")));
  }

  @Test
  void testStepOrderingIsRespected() throws SQLException {
    // Setup - insert steps out of order (by name, but with correct order_index)
    // Insert Step1 with order 1, Step3 with order 3, Step2 with order 2
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "INSERT INTO workflow (name, description, is_parallel) VALUES ('OrderedWorkflow', 'Test', FALSE)");
      var idResult =
          stmt.executeQuery(
              "SELECT id FROM workflow WHERE name = 'OrderedWorkflow' ORDER BY id DESC LIMIT 1");
      idResult.next();
      int workflowId = idResult.getInt("id");

      // Insert with explicit order_index values
      stmt.execute(
          "INSERT INTO workflow_steps (workflow_id, name, description, instance_name, order_index) VALUES ("
              + workflowId
              + ", 'Step1', 'Test step', 'Task1', 1)");
      stmt.execute(
          "INSERT INTO workflow_steps (workflow_id, name, description, instance_name, order_index) VALUES ("
              + workflowId
              + ", 'Step3', 'Test step', 'Task3', 3)");
      stmt.execute(
          "INSERT INTO workflow_steps (workflow_id, name, description, instance_name, order_index) VALUES ("
              + workflowId
              + ", 'Step2', 'Test step', 'Task2', 2)");
    }

    java.util.List<Integer> executionOrder = new java.util.ArrayList<>();

    registerWorkflowWithTracker("Task1", 1, executionOrder);
    registerWorkflowWithTracker("Task2", 2, executionOrder);
    registerWorkflowWithTracker("Task3", 3, executionOrder);

    // Execute
    Workflow workflow = processor.buildWorkflow("OrderedWorkflow");
    WorkflowContext context = new WorkflowContext();
    workflow.execute(context);

    // Assert - verify steps executed in order
    assertEquals(java.util.List.of(1, 2, 3), executionOrder);
  }

  @Test
  void testMultipleWorkflows() throws SQLException {
    // Setup
    insertWorkflowAndSteps("Workflow1", false, "Step1", "Task1");
    insertWorkflowAndSteps("Workflow2", false, "Step1", "Task2");

    registerTestWorkflow("Task1");
    registerTestWorkflow("Task2");

    // Execute
    Workflow workflow1 = processor.buildWorkflow("Workflow1");
    Workflow workflow2 = processor.buildWorkflow("Workflow2");

    // Assert
    assertNotNull(workflow1);
    assertNotNull(workflow2);
    assertEquals("Workflow1", workflow1.getName());
    assertEquals("Workflow2", workflow2.getName());
  }

  private void insertWorkflowAndSteps(
      String workflowName, boolean isParallel, String stepName, String instanceName)
      throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Check if workflow exists
      var result = stmt.executeQuery("SELECT id FROM workflow WHERE name = '" + workflowName + "'");
      int workflowId;

      if (result.next()) {
        workflowId = result.getInt("id");
      } else {
        stmt.execute(
            "INSERT INTO workflow (name, description, is_parallel) "
                + "VALUES ('"
                + workflowName
                + "', 'Test workflow', "
                + isParallel
                + ")");
        // Query to find the newly inserted workflow ID
        var idResult =
            stmt.executeQuery(
                "SELECT id FROM workflow WHERE name = '"
                    + workflowName
                    + "' ORDER BY id DESC LIMIT 1");
        idResult.next();
        workflowId = idResult.getInt("id");
      }

      // Get current max order_index for this workflow
      var orderResult =
          stmt.executeQuery(
              "SELECT COALESCE(MAX(order_index), 0) as max_order FROM workflow_steps WHERE workflow_id = "
                  + workflowId);
      int nextOrder = 1;
      if (orderResult.next()) {
        nextOrder = orderResult.getInt("max_order") + 1;
      }

      // Insert step
      stmt.execute(
          "INSERT INTO workflow_steps (workflow_id, name, description, instance_name, order_index) "
              + "VALUES ("
              + workflowId
              + ", '"
              + stepName
              + "', 'Test step', '"
              + instanceName
              + "', "
              + nextOrder
              + ")");
    }
  }

  private void registerTestWorkflow(String name) {
    Workflow workflow = new TaskWorkflow(context -> context.put(name + "_executed", true));
    registry.register(name, workflow);
  }

  private void registerWorkflowWithTracker(
      String name, int order, java.util.List<Integer> tracker) {
    Workflow workflow = new TaskWorkflow(_ -> tracker.add(order));
    registry.register(name, workflow);
  }
}
