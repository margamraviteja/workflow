# Database-Based Workflow Configuration

This module provides support for defining workflows using database tables instead of Java annotations. It allows you to configure workflow definitions and workflow steps in a database, with workflow implementations resolved from the `WorkflowRegistry`.

## Overview

The database configuration approach provides a clean separation between workflow metadata and implementation, making it easy to:

- Define workflows without code changes
- Dynamically load workflow configurations from a database
- Manage workflow definitions at runtime
- Configure workflows for different environments

## Database Schema

### Workflow Table

Stores workflow metadata with the following columns:

| Column          | Type              | Description                                                          |
|-----------------|-------------------|----------------------------------------------------------------------|
| `id`            | INT (PRIMARY KEY) | Unique workflow identifier                                           |
| `name`          | VARCHAR(255)      | Unique workflow name                                                 |
| `description`   | VARCHAR(255)      | Optional workflow description                                        |
| `is_parallel`   | BOOLEAN           | Whether steps execute in parallel (default: false)                   |
| `fail_fast`     | BOOLEAN           | Stop immediately on first failure in parallel mode (default: false)  |
| `share_context` | BOOLEAN           | Share context across parallel workflows (default: true)              |

```sql
CREATE TABLE workflow (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL UNIQUE,
  description VARCHAR(255),
  is_parallel BOOLEAN DEFAULT FALSE,
  fail_fast BOOLEAN DEFAULT FALSE,
  share_context BOOLEAN DEFAULT TRUE
);
```

### Workflow Steps Table

Stores workflow step definitions with the following columns:

| Column          | Type              | Description                                                      |
|-----------------|-------------------|------------------------------------------------------------------|
| `id`            | INT (PRIMARY KEY) | Unique step identifier                                           |
| `workflow_id`   | INT (FOREIGN KEY) | Reference to parent workflow                                     |
| `name`          | VARCHAR(255)      | Step name                                                        |
| `description`   | VARCHAR(255)      | Optional step description                                        |
| `instance_name` | VARCHAR(255)      | Name of the registered workflow instance (from WorkflowRegistry) |
| `order_index`   | INT               | Execution order (lower numbers execute first)                    |

```sql
CREATE TABLE workflow_steps (
  id INT PRIMARY KEY AUTO_INCREMENT,
  workflow_id INT NOT NULL,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  instance_name VARCHAR(255) NOT NULL,
  order_index INT NOT NULL,
  FOREIGN KEY (workflow_id) REFERENCES workflow(id),
  UNIQUE(workflow_id, name)
);
```

## Key Components

### WorkflowMetadata

A record representing a workflow row in the database:

```java
public record WorkflowMetadata(
    String name, 
    String description, 
    boolean isParallel, 
    boolean failFast, 
    boolean shareContext) {
  
  // Factory method with default values
  public static WorkflowMetadata of(String name, String description, boolean isParallel) {
    return new WorkflowMetadata(name, description, isParallel, false, true);
  }
}
```

### WorkflowStepMetadata

A record representing a workflow step row in the database:

```java
public record WorkflowStepMetadata(
    String name, String description, String instanceName, int orderIndex) {}
```

### WorkflowConfigRepository

Repository for reading workflow and step configurations from the database:

```java
import java.util.List;
import java.util.Optional;

public class WorkflowService {
    private final WorkflowConfigRepository repository;

    public WorkflowService(WorkflowConfigRepository repository) {
        this.repository = repository;
    }

    public void displayWorkflowDetails(String name) throws SQLException {
        // 1. Fetch a single workflow (wrapped in Optional for safety)
        Optional<WorkflowMetadata> workflow = repository.getWorkflow(name);

        // 2. Fetch all steps for a workflow (ordered by order_index)
        List<WorkflowStepMetadata> steps = repository.getWorkflowSteps(name);

        // 3. Get all workflows
        List<WorkflowMetadata> allWorkflows = repository.getAllWorkflows();

        // 4. Check if a workflow exists
        boolean exists = repository.workflowExists(name);

        // Example usage:
        workflow.ifPresent(m -> System.out.println("Found: " + m.name()));
    }
}
```

### DatabaseWorkflowProcessor

Processor that builds executable Workflow instances from database configuration:

```java
// Create processor with DataSource
DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);

// Build a workflow from database
Workflow workflow = processor.buildWorkflow("DataProcessingPipeline");

// Execute workflow
WorkflowContext context = new WorkflowContext();
WorkflowResult result = workflow.execute(context);
```

## Usage Example

### Step 1: Set up the Database

```java
DataSource dataSource = createDataSource(); // Your DataSource implementation

// Initialize schema (from DatabaseWorkflowExample)
// Refer DatabaseWorkflowExample.initializeDatabase(dataSource);
```

### Step 2: Create and Register Workflow Implementations

Register the actual workflow implementations in the registry:

```java
public class WorkflowManager {
    public void initializeRegistry() {
        // 1. Create the registry
        WorkflowRegistry registry = new WorkflowRegistry();

        // 2. Define individual tasks (as Lambdas)
        Task validationTask = context -> {
            System.out.println("Validating data...");
            // Validation logic here
        };

        Task processingTask = context -> {
            System.out.println("Processing data...");
            // Processing logic here
        };

        // 3. Wrap tasks in Workflows and register them
        registry.register("ValidationWorkflow", new TaskWorkflow(validationTask));
        registry.register("ProcessingWorkflow", new TaskWorkflow(processingTask));
    }
}
```

### Step 3: Configure Workflows in Database

Insert workflow definitions:

```sql
-- Sequential workflow (default behavior)
INSERT INTO workflow (name, description, is_parallel, fail_fast, share_context) VALUES 
  ('DataProcessingPipeline', 'Sequential data processing', FALSE, FALSE, TRUE);

-- Parallel workflow with fail-fast enabled
INSERT INTO workflow (name, description, is_parallel, fail_fast, share_context) VALUES 
  ('ParallelValidation', 'Parallel validation with fail-fast', TRUE, TRUE, TRUE);

-- Parallel workflow with isolated contexts
INSERT INTO workflow (name, description, is_parallel, fail_fast, share_context) VALUES 
  ('IsolatedParallelProcessing', 'Parallel processing with isolated contexts', TRUE, FALSE, FALSE);

-- Get workflow ID
SELECT id FROM workflow WHERE name = 'DataProcessingPipeline'; -- e.g., 1

-- Insert workflow steps
INSERT INTO workflow_steps (workflow_id, name, description, instance_name, order_index) VALUES
  (1, 'Validate', 'Validate input', 'ValidationWorkflow', 1),
  (1, 'Process', 'Process data', 'ProcessingWorkflow', 2);
```

### Step 4: Build and Execute Workflows

```java
public class DatabaseWorkflowLauncher {
    public void runDatabaseWorkflow(DataSource dataSource, WorkflowRegistry registry) {
        // 1. Initialize the processor
        DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);

        // 2. Build workflow from database (must be inside a method)
        Workflow workflow = processor.buildWorkflow("DataProcessingPipeline");

        // 3. Execute
        WorkflowContext context = new WorkflowContext();
        WorkflowResult result = workflow.execute(context);

        // 4. Handle results
        if (result.getStatus() == WorkflowStatus.SUCCESS) {
            System.out.println("Workflow completed successfully");
        } else {
            System.out.println("Workflow failed: " + result.getError());
        }
    }
}
```

## Best Practices

1. **Register All Instances**: Ensure all workflow instances referenced in the database are registered in the `WorkflowRegistry` before building workflows.

2. **Validate Order Indices**: Set `order_index` correctly to ensure proper execution order.

3. **Unique Names**: Use unique and descriptive names for workflows and steps.

4. **Use Transactions**: When inserting multiple workflows and steps, use database transactions to ensure consistency.

5. **Parallel Workflow Configuration**: When setting `is_parallel` to true, consider also configuring `fail_fast` and `share_context` based on your requirements:
   - Set `fail_fast=true` if you want to abort all parallel tasks when the first failure occurs
   - Set `share_context=false` if you want to isolate context between parallel workflow branches

## Example: Complete Setup

See `DatabaseWorkflowExample` in the examples directory for a complete, runnable example that:

- Creates an H2 in-memory database
- Initializes the schema
- Registers sample workflows
- Configures workflows in the database
- Builds and executes workflows

Run with:
```bash
mvn exec:java -Dexec.mainClass="com.workflow.examples.database.DatabaseWorkflowExample"
```
