# Getting Started with Workflow Engine

## Quick Start Guide

This guide will help you get up and running with the Workflow Engine in minutes.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Your First Workflow](#your-first-workflow)
- [Common Patterns](#common-patterns)
- [Next Steps](#next-steps)

## Prerequisites

Before you begin, ensure you have:

- **Java 25 or higher** installed (Required)
- **Maven 3.9+** for dependency management (Required)
- Basic understanding of Java and functional programming concepts

### Optional Dependencies

- **Spring Framework 6.2+** - For Spring integration and annotation processing
- **Project Reactor** - For reactive execution strategies
- **H2 Database** - For database-based workflow configuration

## Your First Workflow

### Step 1: Create a Simple Task

```java
import com.workflow.task.AbstractTask;
import com.workflow.context.WorkflowContext;

public class GreetingTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
        String name = context.getTyped("name", String.class);
        String greeting = "Hello, " + name + "!";
        context.put("greeting", greeting);
        System.out.println(greeting);
    }
    
    @Override
    public String getName() {
        return "GreetingTask";
    }
}
```

### Step 2: Create a Workflow

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;

public class HelloWorldExample {
    static void main(String[] args) {
        // Create a simple sequential workflow
        Workflow workflow = SequentialWorkflow.builder()
            .name("HelloWorldWorkflow")
            .task(new GreetingTask())
            .build();
        
        // Create context and add input
        WorkflowContext context = new WorkflowContext();
        context.put("name", "World");
        
        // Execute workflow
        WorkflowResult result = workflow.execute(context);
        
        // Check result
        if (result.isSuccess()) {
            String greeting = context.getTyped("greeting", String.class);
            System.out.println("Workflow completed: " + greeting);
            System.out.println("Duration: " + result.getExecutionDuration());
        } else {
            System.err.println("Workflow failed: " + result.getError());
        }
    }
}
```

### Step 3: Run Your Workflow

```bash
mvn exec:java -Dexec.mainClass="com.example.HelloWorldExample"
```

**Output:**
```
Hello, World!
Workflow completed: Hello, World!
Duration: 0.015 seconds
```

## Common Patterns

### Pattern 1: Data Processing Pipeline

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;
import com.workflow.exception.TaskExecutionException;

public class DataPipelineRunner {
    public void runProcess() {
        // Define processing tasks
        Task validateTask = context -> {
            String data = context.getTyped("rawData", String.class);
            if (data == null || data.isEmpty()) {
                throw new TaskExecutionException("Data is required");
            }
            context.put("validData", data);
        };

        Task transformTask = context -> {
            String data = context.getTyped("validData", String.class);
            String transformed = data.toUpperCase();
            context.put("transformedData", transformed);
        };

        Task saveTask = context -> {
            String data = context.getTyped("transformedData", String.class);
            // Logic to save to database or file
            context.put("saved", true);
        };

        // Create pipeline
        Workflow pipeline = SequentialWorkflow.builder()
                .name("DataPipeline")
                .task(validateTask)
                .task(transformTask)
                .task(saveTask)
                .build();

        // Execute
        WorkflowContext context = new WorkflowContext();
        context.put("rawData", "hello world");
        WorkflowResult result = pipeline.execute(context);

        System.out.println("Result: " + result.getStatus());
    }
}
```

### Pattern 2: Parallel API Calls

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.GetHttpTask;

import java.net.http.HttpClient;

public class UserDataService {
    public void fetchUserDataParallel() {
        HttpClient client = HttpClient.newHttpClient();

        // Define the parallel workflow
        Workflow parallelFetch = ParallelWorkflow.builder()
                .name("FetchUserData")
                .task(new GetHttpTask.Builder<>(client)
                        .url("https://api.example.com/users/123")
                        .responseContextKey("userData")
                        .build())
                .task(new GetHttpTask.Builder<>(client)
                        .url("https://api.example.com/orders?userId=123")
                        .responseContextKey("ordersData")
                        .build())
                .task(new GetHttpTask.Builder<>(client)
                        .url("https://api.example.com/preferences/123")
                        .responseContextKey("preferencesData")
                        .build())
                .build();

        // Execute
        WorkflowContext context = new WorkflowContext();
        WorkflowResult result = parallelFetch.execute(context);

        // Extract results from context
        if (result.isSuccess()) {
            String userData = context.getTyped("userData", String.class);
            String ordersData = context.getTyped("ordersData", String.class);
            String preferencesData = context.getTyped("preferencesData", String.class);

            System.out.println("Data fetched successfully!");
        }
    }
}
```

### Pattern 3: Conditional Processing

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;

public class UserWorkflowFactory {
    public WorkflowResult processUser(String userType) {
        // Build the conditional logic
        Workflow conditionalWorkflow = ConditionalWorkflow.builder()
                .name("UserTypeProcessor")
                .condition(ctx -> {
                    String type = ctx.getTyped("userType", String.class);
                    return "premium".equals(type);
                })
                .whenTrue(SequentialWorkflow.builder()
                        .name("PremiumFlow")
                        .task(new PremiumValidationTask())
                        .task(new PremiumProcessingTask())
                        .task(new PremiumRewardsTask())
                        .build())
                .whenFalse(SequentialWorkflow.builder()
                        .name("StandardFlow")
                        .task(new StandardValidationTask())
                        .task(new StandardProcessingTask())
                        .build())
                .build();

        // Prepare context and execute
        WorkflowContext context = new WorkflowContext();
        context.put("userType", userType);

        return conditionalWorkflow.execute(context);
    }
}
```

### Pattern 4: Retry with Timeout

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.TaskDescriptor;
import com.workflow.policy.*;

public class ResilientTaskExample {
    public WorkflowResult executeWithRetry(WorkflowContext context) {
        TaskDescriptor resilientTask = TaskDescriptor.builder()
                .task(new ApiCallTask())
                .retryPolicy(RetryPolicy.exponentialBackoff(
                        3,      // 3 retries
                        1000    // Starting at 1 second
                ))
                .timeoutPolicy(TimeoutPolicy.ofSeconds(30))
                .build();

        Workflow workflow = new TaskWorkflow(resilientTask);
        return workflow.execute(context);
    }
}
```

### Pattern 5: Fallback on Failure

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.*;

import java.net.http.HttpClient;
import java.nio.file.Path;

public class FallbackExample {
    public WorkflowResult fetchDataWithFallback(WorkflowContext context) {
        HttpClient client = HttpClient.newHttpClient();

        Workflow withFallback = FallbackWorkflow.builder()
                .name("DataRetrieval")
                .primary(SequentialWorkflow.builder()
                        .name("APIRetrieval")
                        .task(new GetHttpTask.Builder<>(client)
                                .url("https://api.example.com/data")
                                .responseContextKey("data")
                                .build())
                        .build())
                .fallback(SequentialWorkflow.builder()
                        .name("CacheRetrieval")
                        .task(new FileReadTask(
                                Path.of("cache/data.json"),
                                "data"
                        ))
                        .build())
                .build();

        // If API fails, reads from cache
        return withFallback.execute(context);
    }
}
```

### Pattern 6: Saga with Compensation

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;

public class SagaExample {
    public WorkflowResult processOrder(Order order) {
        // Build saga workflow with compensating actions
        SagaWorkflow orderSaga = SagaWorkflow.builder()
                .name("OrderProcessingSaga")
                .step(SagaStep.builder()
                        .name("ReserveInventory")
                        .action(ctx -> {
                            String orderId = ctx.getTyped("orderId", String.class);
                            String reservationId = inventoryService.reserve(orderId);
                            ctx.put("reservationId", reservationId);
                        })
                        .compensation(ctx -> {
                            String reservationId = ctx.get("reservationId");
                            if (reservationId != null) {
                                inventoryService.release(reservationId);
                            }
                        })
                        .build())
                .step(SagaStep.builder()
                        .name("ProcessPayment")
                        .action(ctx -> {
                            Double amount = ctx.getTyped("amount", Double.class);
                            String txnId = paymentService.charge(amount);
                            ctx.put("transactionId", txnId);
                        })
                        .compensation(ctx -> {
                            String txnId = ctx.get("transactionId");
                            if (txnId != null) {
                                paymentService.refund(txnId);
                            }
                        })
                        .build())
                .step(SagaStep.builder()
                        .name("CreateShipment")
                        .action(ctx -> {
                            String orderId = ctx.getTyped("orderId", String.class);
                            String shipmentId = shippingService.createShipment(orderId);
                            ctx.put("shipmentId", shipmentId);
                        })
                        .compensation(ctx -> {
                            String shipmentId = ctx.get("shipmentId");
                            if (shipmentId != null) {
                                shippingService.cancelShipment(shipmentId);
                            }
                        })
                        .build())
                .build();

        // Execute saga
        WorkflowContext context = new WorkflowContext();
        context.put("orderId", order.getId());
        context.put("amount", order.getTotal());
        
        // If any step fails, all previous steps are automatically rolled back
        return orderSaga.execute(context);
    }
}
```

### Pattern 7: Repeat Workflow

Execute a workflow a fixed number of times with iteration tracking:

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.DelayTask;
import java.time.Duration;

public class RepeatWorkflowExample {
    public WorkflowResult retryWithPolling() {
        Workflow pollWorkflow = RepeatWorkflow.builder()
                .name("PollForCompletion")
                .times(10)
                .indexVariable("attempt")
                .workflow(SequentialWorkflow.builder()
                        .task(new DelayTask(Duration.ofSeconds(1)))
                        .task(ctx -> {
                            String jobId = ctx.getTyped("jobId", String.class);
                            JobStatus status = jobService.getStatus(jobId);
                            ctx.put("status", status);
                            System.out.println("Attempt " + ctx.get("attempt") + ": " + status);
                        })
                        .build())
                .build();

        WorkflowContext context = new WorkflowContext();
        context.put("jobId", "job-123");
        return pollWorkflow.execute(context);
    }
}
```

### Pattern 8: ForEach Workflow

Iterate over a collection and execute workflow for each item:

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import java.util.List;

public class ForEachWorkflowExample {
    public WorkflowResult processBatch(List<User> users) {
        Workflow batchProcessor = ForEachWorkflow.builder()
                .name("ProcessAllUsers")
                .itemsKey("userList")
                .itemVariable("currentUser")
                .indexVariable("userIndex")
                .workflow(SequentialWorkflow.builder()
                        .task(ctx -> {
                            User user = ctx.getTyped("currentUser", User.class);
                            Integer index = ctx.getTyped("userIndex", Integer.class);
                            System.out.println("Processing user " + index + ": " + user.getName());
                        })
                        .task(new ProcessUserTask())
                        .build())
                .build();

        WorkflowContext context = new WorkflowContext();
        context.put("userList", users);
        return batchProcessor.execute(context);
    }
    
    public WorkflowResult processFilesWithParallel(List<File> files) {
        // Combine ForEach with Parallel for efficient batch processing
        Workflow parallelBatchProcessor = ForEachWorkflow.builder()
                .name("ProcessFileBatches")
                .itemsKey("fileBatches")
                .itemVariable("currentBatch")
                .workflow(ParallelWorkflow.builder()
                        .name("ProcessBatchItems")
                        .task(new ValidateFileTask())
                        .task(new TransformFileTask())
                        .task(new UploadFileTask())
                        .build())
                .build();

        WorkflowContext context = new WorkflowContext();
        context.put("fileBatches", files);
        return parallelBatchProcessor.execute(context);
    }
}
```

## Configuration Options

### Context Management

```java
import com.workflow.context.*;
import java.util.Optional;

public class WorkflowDataProcessor {
    // Declare Type-safe keys as constants at the class level
    private static final TypedKey<User> USER_KEY = TypedKey.of("user", User.class);

    public void handleContext(User user) {
        // All logic must reside inside a method
        WorkflowContext context = new WorkflowContext();

        // Basic operations
        context.put("key", "value");
        String value = context.getTyped("key", String.class);

        // Using the Type-safe key
        context.put(USER_KEY, user);
        User retrievedUser = context.get(USER_KEY);

        // Scoped contexts (Namespace isolation)
        WorkflowContext userScope = context.scope("user");
        userScope.put("id", 123);  // Stored as "user.id" in main context
    }
}
```

### Execution Strategies

```java
import com.workflow.*;
import com.workflow.execution.strategy.*;

public class WorkflowEngineConfig {
    // Define strategies
    private ExecutionStrategy threadPool = new ThreadPoolExecutionStrategy();
    private ExecutionStrategy reactive = new ReactorExecutionStrategy();
    
    public Workflow buildParallelProcess(Workflow workflow1, Workflow workflow2) {
        // Build and return the workflow
        return ParallelWorkflow.builder()
                .name("HybridExecutionWorkflow")
                .executionStrategy(reactive) // Choosing the Reactive strategy here
                .workflow(workflow1)
                .workflow(workflow2)
                .build();
    }
}
```

### Rate Limiting

```java
import com.workflow.*;
import com.workflow.ratelimit.*;
import java.time.Duration;

public class RateLimitConfig {
    public Workflow buildRateLimitedWorkflow(Workflow apiWorkflow) {
        // Fixed window: 100 requests per minute
        RateLimitStrategy limiter = new FixedWindowRateLimiter(
                100, 
                Duration.ofMinutes(1)
        );

        // Token bucket: 100/sec with burst of 200
        RateLimitStrategy tokenBucket = new TokenBucketRateLimiter(
                100,    // Rate
                200,    // Burst capacity
                Duration.ofSeconds(1)
        );

        // Apply to workflow
        return RateLimitedWorkflow.builder()
                .workflow(apiWorkflow)
                .rateLimitStrategy(limiter)
                .build();
    }
}
```

### Pattern 6: Timeout Workflow

Add time constraints to workflow execution:

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskTimeoutException;

public class TimeoutExample {
    public void executeWithTimeout(Workflow apiWorkflow) {
        // Create workflow with timeout
        Workflow timedWorkflow = TimeoutWorkflow.builder()
                .name("TimedAPICall")
                .workflow(apiWorkflow)
                .timeoutMs(30000)  // 30 second timeout
                .build();

        WorkflowContext context = new WorkflowContext();
        WorkflowResult result = timedWorkflow.execute(context);

        // Check for timeout
        if (result.getStatus() == WorkflowStatus.FAILED &&
                result.getError() instanceof TaskTimeoutException) {
            System.err.println("Workflow timed out");
        }
    }
}
```

## Testing Your Workflows

### Unit Testing Tasks

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreetingTaskTest {
    @Test
    void testGreetingTask() {
        // Arrange
        GreetingTask task = new GreetingTask();
        WorkflowContext context = new WorkflowContext();
        context.put("name", "Test");
        
        // Act
        task.execute(context);
        
        // Assert
        String greeting = context.getTyped("greeting", String.class);
        assertEquals("Hello, Test!", greeting);
    }
}
```

### Integration Testing Workflows

```java
@Test
void testDataPipeline() {
    // Arrange
    Workflow pipeline = SequentialWorkflow.builder()
        .name("TestPipeline")
        .task(validateTask)
        .task(transformTask)
        .task(saveTask)
        .build();
    
    WorkflowContext context = new WorkflowContext();
    context.put("rawData", "test data");
    
    // Act
    WorkflowResult result = pipeline.execute(context);
    
    // Assert
    assertTrue(result.isSuccess());
    assertTrue(context.containsKey("saved"));
}
```

## Next Steps

Now that you've created your first workflow, explore these topics:

1. **[Workflow Types](WORKFLOWS.md)** - Learn about all 6 workflow types
2. **[Tasks Reference](TASKS.md)** - Explore built-in tasks
3. **[Architecture](ARCHITECTURE.md)** - Understand the framework design
4. **[Rate Limiting](RATE_LIMITING.md)** - Control execution frequency
5. **[Helpers](HELPERS.md)** - Utility classes and helpers
6. **[Database Configuration](DATABASE_CONFIGURATION.md)** - Dynamic workflow configuration

## Common Questions

### How do I handle errors?

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowRunner {
    public void runProcess(Task task1, Task task2, WorkflowContext context) {
        // Build the workflow
        Workflow workflow = SequentialWorkflow.builder()
                .name("ProcessWorkflow")
                .task(task1)
                .task(task2)
                .build();

        // Execute and capture the result
        WorkflowResult result = workflow.execute(context);

        // Handle the lifecycle outcome
        if (result.isFailure()) {
            Throwable error = result.getError();
            // Handle error logic (e.g., rollback or notification)
            log.error("Workflow failed: {}", error.getMessage(), error);
        } else {
            log.info("Workflow completed successfully!");
        }
    }
}
```

### How do I pass data between tasks?

Tasks communicate through the shared `WorkflowContext`:

```java
import com.workflow.context.WorkflowContext;

public class UserWorkflow {
    public void executeWorkflow() {
        WorkflowContext context = new WorkflowContext();

        // Task 1 writes data to the shared context
        context.put("userId", 123);

        // Task 2 reads that same data using the unique key
        Integer userId = context.getTyped("userId", Integer.class);

        System.out.println("Processing User ID: " + userId);
    }
}
```

### Can workflows be reused?

Yes! Workflows are reusable. Create once, execute multiple times:

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;

public class WorkflowReuse {
    public void demonstrateReuse(Task task1, Task task2) {
        Workflow reusableWorkflow = SequentialWorkflow.builder()
                .name("ReusableWorkflow")
                .task(task1)
                .task(task2)
                .build();

        // Execute multiple times with different contexts
        WorkflowContext context1 = new WorkflowContext();
        WorkflowContext context2 = new WorkflowContext();
        
        WorkflowResult result1 = reusableWorkflow.execute(context1);
        WorkflowResult result2 = reusableWorkflow.execute(context2);
    }
}
```

### How do I debug workflows?

- Enable DEBUG logging:
```properties
# log4j2.properties
logger.com.workflow.level = DEBUG
```

- Use workflow tree visualization:
```java
import com.workflow.Workflow;

public class WorkflowDebugger {
    public void visualizeWorkflow(Workflow workflow) {
        // Generate and print the tree representation
        String tree = workflow.toTreeString();
        System.out.println(tree);
    }
}
```

- Add custom logging in tasks:
```java
@Override
void doExecute(WorkflowContext context) {
    log.debug("Processing user: {}", context.get("userId"));
    // Your logic
    log.debug("Completed processing");
}
```

## Examples

Check out the `src/examples` directory for complete working examples:

- **OrderProcessingExample** - E-commerce order workflow
- **DataPipelineWorkflow** - ETL pipeline
- **ApiOrchestrationExample** - Microservices coordination
- **EmailCampaignExample** - Batch email processing
- **DocumentProcessingExample** - Multi-stage document processing
- **RateLimitingExample** - API throttling

## Summary

You now know how to:
- ✅ Create tasks and workflows
- ✅ Execute workflows with context
- ✅ Handle success and failure cases
- ✅ Use common workflow patterns
- ✅ Test your workflows
- ✅ Configure execution strategies

Ready to build more complex workflows? Dive into the [comprehensive documentation](WORKFLOWS.md)!
