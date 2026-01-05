# Workflow Engine

> [!IMPORTANT]
> Note: Codebase is generated/implemented by Copilot, Claude and Gemini AI Tools

A lightweight, flexible Java workflow orchestration framework for composing and executing tasks with support for sequential and parallel execution, conditional branching, retry policies, and pluggable execution strategies.

## Overview

It provides a clean API for orchestrating tasks, handling errors, implementing retry logic, and managing execution contexts. The framework supports multiple execution patterns including sequential, parallel, conditional, and dynamic branching workflows.

## Key Features

- **🔄 Multiple Workflow Types**: Sequential, parallel, conditional, dynamic branching, fallback, rate-limited, timeout workflows
- **📝 Task Abstraction**: Rich set of built-in tasks (HTTP, file I/O, JavaScript execution, shell commands, etc.)
- **🔁 Retry & Timeout Policies**: Configurable retry logic with multiple backoff strategies and timeout handling
- **⚡ Execution Strategies**: Pluggable execution strategies (thread pool, reactive/Project Reactor)
- **🚦 Rate Limiting**: Built-in rate limiting with multiple algorithms (Fixed Window, Sliding Window, Token Bucket, Leaky Bucket, Resilience4j, Bucket4j)
- **🔧 Annotation-Based Configuration**: Define workflows using Java annotations or pure Java DSL
- **💾 Database Configuration**: Store workflow definitions in database for dynamic configuration
- **🔑 Type-Safe Context**: Thread-safe, type-safe context for data flow between tasks
- **🎯 Fail-Fast & Fallback**: Support for fail-fast execution and graceful fallback mechanisms
- **📊 Monitoring**: Track workflow execution with detailed results and timestamps. Workflow listeners for observability and custom actions
- **🧩 Composable**: Workflows can be nested and composed for complex orchestrations
- **🌳 Workflow Visualization**: Tree-based visualization of workflow hierarchies

## Quick Start

### Simple Sequential Workflow

```java
import java.nio.file.Path;

public class MyWorkflowApp {
    static void main(String[] args) {
        // Create a simple sequential workflow
        Workflow workflow = SequentialWorkflow.builder()
                .name("DataProcessingPipeline")
                .task(new FileReadTask(Path.of("data.json"), "rawData"))
                .task(context -> {
                    String data = context.getTyped("rawData", String.class);
                    context.put("processedData", data.toUpperCase());
                })
                .task(new FileWriteTask(Path.of("output.json"), "processedData"))
                .build();

        // Execute workflow
        WorkflowContext context = new WorkflowContext();
        WorkflowResult result = workflow.execute(context);

        if (result.getStatus() == WorkflowStatus.SUCCESS) {
            System.out.println("Pipeline completed in " + result.getExecutionDuration());
        }
    }
}
```

### Parallel Workflow with HTTP Tasks

```java
Workflow parallelWorkflow = ParallelWorkflow.builder()
    .name("DataAggregation")
    .task(new GetTask("https://api.example.com/users", "usersData"))
    .task(new GetTask("https://api.example.com/orders", "ordersData"))
    .task(new GetTask("https://api.example.com/products", "productsData"))
    .failFast(true)
    .build();

WorkflowContext context = new WorkflowContext();
WorkflowResult result = parallelWorkflow.execute(context);
```

### Conditional Workflow

```java
Workflow conditionalWorkflow = ConditionalWorkflow.builder()
    .name("OrderProcessing")
    .condition(context -> {
        Integer amount = context.getTyped("orderAmount", Integer.class);
        return amount != null && amount > 1000;
    })
    .whenTrue(expensiveOrderWorkflow)
    .whenFalse(standardOrderWorkflow)
    .build();
```

### With Retry and Timeout

```java
TaskDescriptor taskWithPolicies = TaskDescriptor.builder()
    .task(new PostTask("https://api.example.com/process", "requestBody", "response"))
    .retryPolicy(RetryPolicy.exponentialBackoff(3, 1000))
    .timeoutPolicy(TimeoutPolicy.ofSeconds(30))
    .build();

Workflow workflow = new TaskWorkflow(taskWithPolicies);
```

### With Rate Limiting

```java
// Limit API calls to 100 per minute
RateLimitStrategy limiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));

Workflow rateLimited = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// Or use Token Bucket for burst support
RateLimitStrategy tokenBucket = new TokenBucketRateLimiter(
    100,                        // 100 requests per second
    200,                        // Burst capacity of 200
    Duration.ofSeconds(1)
);

// Or use Resilience4j for production-ready rate limiting
RateLimitStrategy resilience4j = new Resilience4jRateLimiter(
    100,                        // 100 requests per second
    Duration.ofSeconds(1)       // Refresh period
);
```

### With Workflow Listeners

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowRunner {
    public void runWorkflow(Workflow workflow) {
        // 1. Initialize Context
        WorkflowContext context = new WorkflowContext();

        // 2. Register listeners for monitoring
        context.getListeners().register(new WorkflowListener() {
            @Override
            public void onStart(String name, WorkflowContext ctx) {
                log.info("Workflow started: {}", name);
            }

            @Override
            public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
                log.info("Workflow completed in {}", result.getExecutionDuration());
            }

            @Override
            public void onFailure(String name, WorkflowContext ctx, Throwable error) {
                log.error("Workflow failed: {}", name, error);
            }
        });

        // 3. Execute with automatic event notifications
        workflow.execute(context);
    }
}
```

### Workflow Visualization

```java
public class MyWorkflowApp {
    static void main(String[] args) {
        // Visualize workflow hierarchy
        Workflow workflow = SequentialWorkflow.builder()
                .name("ComplexPipeline")
                .workflow(step1)
                .workflow(ParallelWorkflow.builder()
                        .workflow(step2a)
                        .workflow(step2b)
                        .build())
                .workflow(step3)
                .build();

        // Print tree structure
        System.out.println(workflow.toTreeString());
        /* Output:
        └── ComplexPipeline [Sequence]
            ├── Step1 [Task]
            ├── ParallelTasks [Parallel]
            │   ├── Step2A [Task]
            │   └── Step2B [Task]
            └── Step3 [Task]
        */
    }
}
```

## Workflow Types

### Sequential Workflow
Executes child workflows one after another. Stops on first failure.

```java
public class WorkflowConfiguration {
    public Workflow buildMainPipeline(Workflow step1, Workflow step2, Workflow step3) {
        return SequentialWorkflow.builder()
                .name("MainSequentialPipeline")
                .workflow(step1)
                .workflow(step2)
                .workflow(step3)
                .build();
    }
}
```

### Parallel Workflow
Executes child workflows concurrently with configurable fail-fast and context sharing.

```java
public class WorkflowManager {
    public Workflow createParallelPipeline(Workflow task1, Workflow task2) {
        return ParallelWorkflow.builder()
                .name("ParallelDataProcessor")
                .workflow(task1)
                .workflow(task2)
                .failFast(true)       // If one task fails, stop the others immediately
                .shareContext(false)  // Give each task its own context to prevent race conditions
                .build();
    }
}
```

### Conditional Workflow
Branches execution based on a predicate evaluated against the context.

```java
public class WorkflowEngine {
    public Workflow buildConditionalLogic(Workflow activeWorkflow, Workflow inactiveWorkflow) {
        return ConditionalWorkflow.builder()
                .name("UserStatusCheck")
                .condition(ctx -> {
                    String status = ctx.getTyped("status", String.class);
                    return "active".equals(status); // Null-safe comparison
                })
                .whenTrue(activeWorkflow)
                .whenFalse(inactiveWorkflow)
                .build();
    }
}
```

### Dynamic Branching Workflow
Dynamically selects workflow based on context state.

```java
public class WorkflowFactory {
    public Workflow buildDynamicWorkflow(
            Workflow fastProcessingWorkflow,
            Workflow accurateProcessingWorkflow,
            Workflow standardWorkflow) {
        return DynamicBranchingWorkflow.builder()
                .name("ModeSelectorWorkflow")
                // The selector returns a key that matches one of the branches
                .selector(ctx -> ctx.getTyped("processingMode", String.class))
                .branch("fast", fastProcessingWorkflow)
                .branch("accurate", accurateProcessingWorkflow)
                .defaultBranch(standardWorkflow)
                .build();
    }
}
```

### Fallback Workflow
Executes primary workflow, falls back to secondary on failure.

```java
public class ResilientWorkflowFactory {
    public Workflow buildResilientProcess(Workflow primaryWorkflow, Workflow fallbackWorkflow) {
        return FallbackWorkflow.builder()
                .name("SafeDataSync")
                .primary(primaryWorkflow)
                .fallback(fallbackWorkflow)
                .build();
    }
}
```

### Rate Limited Workflow
Wraps any workflow with rate limiting.

```java
import java.time.Duration;

public class ApiRateLimitConfig {
    public Workflow buildRateLimitedApiWorkflow(Workflow apiWorkflow) {
        return RateLimitedWorkflow.builder()
                .name("ExternalApiWorkflow")
                .workflow(apiWorkflow)
                .rateLimitStrategy(new FixedWindowRateLimiter(100, Duration.ofMinutes(1)))
                .build();
    }
}
```

### Timeout Workflow
Wraps any workflow with a timeout constraint.

```java
public class WorkflowSecurity {
    public Workflow buildWithTimeout(Workflow longRunningWorkflow) {
        return TimeoutWorkflow.builder()
                .name("SafeTimeoutWorkflow")
                .workflow(longRunningWorkflow)
                .timeoutMs(30000)  // 30 seconds
                .build();
    }
}
```

## Built-in Tasks

The framework provides a rich set of pre-built tasks:

### HTTP Tasks
- `GetTask` - HTTP GET requests
- `PostTask` - HTTP POST requests
- `PutTask` - HTTP PUT requests
- `DeleteTask` - HTTP DELETE requests

### File Tasks
- `FileReadTask` - Read file contents
- `FileWriteTask` - Write data to files

### Control Flow Tasks
- `ConditionalTask` - Conditional execution
- `SwitchTask` - Multi-way branching
- `DelayTask` - Introduce delays
- `NoOpTask` - No operation (testing)

### Processing Tasks
- `JavaScriptTask` - Execute JavaScript code
- `ShellCommandTask` - Execute shell commands
- `CompositeTask` - Compose multiple tasks
- `ParallelTask` - Parallel task execution

### Resilience Tasks
- `RetryingTask` - Add retry logic to any task
- `TimedTask` - Add timeout to any task

## Rate Limiting Strategies

The framework provides six rate limiting algorithms:

### Fixed Window
Simple time-window based limiting. Best for basic use cases.

```java
RateLimitStrategy limiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
```

### Sliding Window
Accurate rate limiting with no boundary effects. Best for strict rate limiting.

```java
RateLimitStrategy limiter = new SlidingWindowRateLimiter(100, Duration.ofMinutes(1));
```

### Token Bucket
Allows controlled bursts while maintaining average rate. Best for APIs that support bursts.

```java
RateLimitStrategy limiter = new TokenBucketRateLimiter(
    100,                        // Tokens per second
    200,                        // Burst capacity
    Duration.ofSeconds(1)
);
```

### Leaky Bucket
Ensures constant output rate. Best for steady-state processing.

```java
RateLimitStrategy limiter = new LeakyBucketRateLimiter(
    100,                        // Requests per second
    Duration.ofSeconds(1)
);
```

### Resilience4j Rate Limiter
Production-ready rate limiting using the Resilience4j library. Best for production applications requiring battle-tested implementation, observability, and integration with existing Resilience4j setup.

```java
// Basic usage
RateLimitStrategy limiter = new Resilience4jRateLimiter(
    100,                        // Requests per second
    Duration.ofSeconds(1)       // Refresh period
);

// With custom configuration
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(100)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ofSeconds(5))
    .build();

RateLimitStrategy limiter = new Resilience4jRateLimiter("myAPI", config);

// Integration with existing Resilience4j setup
RateLimiter existingLimiter = RateLimiter.of("appLimiter", config);
RateLimitStrategy limiter = new Resilience4jRateLimiter(existingLimiter);
```

**Features:**
- Battle-tested in production environments
- Event monitoring and metrics support
- Integration with Micrometer and Prometheus
- Thread-safe with minimal overhead
- Supports dynamic configuration

### Bucket4j Rate Limiter
High-performance rate limiting using the Bucket4j library based on token bucket algorithm. Best for high-throughput applications requiring minimal overhead and flexible burst capacity.

```java
// Basic usage
RateLimitStrategy limiter = new Bucket4jRateLimiter(
    100,                        // Capacity
    Duration.ofSeconds(1)       // Refill period
);

// With burst capacity
RateLimitStrategy limiter = new Bucket4jRateLimiter(
    200,                        // Burst capacity
    100,                        // Refill tokens per period
    Duration.ofSeconds(1),      // Refill period
    Bucket4jRateLimiter.RefillStrategy.GREEDY
);

// With custom bandwidth
Bandwidth bandwidth = Bandwidth.builder()
    .capacity(100)
    .refillGreedy(100, Duration.ofSeconds(1))
    .build();

RateLimitStrategy limiter = new Bucket4jRateLimiter(bandwidth);
```

**Features:**
- Lock-free atomic operations for high performance
- Token bucket algorithm with burst support
- Flexible refill strategies (GREEDY vs INTERVALLY)
- Minimal memory footprint and CPU usage
- Support for multiple bandwidth limits
- Extensible to distributed rate limiting

## Annotation-Based Workflows

Define workflows declaratively using annotations:

```java
@WorkflowAnnotation(name = "OrderProcessingWorkflow", parallel = false)
public class OrderWorkflowDefinition {
    
    @WorkflowMethod(order = 1)
    public Workflow validateOrder() {
        return new TaskWorkflow(new ValidationTask());
    }
    
    @TaskMethod(order = 2)
    public Task processPayment() {
        return context -> {
            // Payment processing logic
        };
    }
    
    @WorkflowRef(name = "EmailNotification", order = 3)
    public void sendNotification() {
        // References registered workflow
    }
}
```

### Spring Integration

```java
@Configuration
public class WorkflowConfig {
    
    @Bean
    public Workflow orderProcessing(AnnotationWorkflowProcessor processor) {
        return processor.buildWorkflow(OrderWorkflowDefinition.class);
    }
}
```

## Database Configuration

Configure workflows dynamically from database:

```sql
-- Define workflow
INSERT INTO workflow (name, description, is_parallel) 
VALUES ('DataPipeline', 'Data processing pipeline', FALSE);

-- Define workflow steps
INSERT INTO workflow_steps (workflow_id, name, instance_name, order_index) VALUES
  (1, 'Extract', 'ExtractWorkflow', 1),
  (1, 'Transform', 'TransformWorkflow', 2),
  (1, 'Load', 'LoadWorkflow', 3);
```

```java
DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);
Workflow workflow = processor.buildWorkflow("DataPipeline");
```

See [DATABASE_CONFIGURATION.md](docs/DATABASE_CONFIGURATION.md) for complete documentation.

## Retry Policies

Configure retry behavior with various backoff strategies:

```java
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RetryConfig {
    public void configureRetryPolicies() {
        // 1. Fixed retry count
        RetryPolicy simplePolicy = RetryPolicy.limitedRetries(3);

        // 2. With exponential backoff
        RetryPolicy backoffPolicy = RetryPolicy.exponentialBackoff(5, 100);

        // 3. With custom backoff strategy (Exponential + Jitter)
        RetryPolicy jitterPolicy = RetryPolicy.limitedRetriesWithBackoff(3,
                BackoffStrategy.exponentialWithJitter(100, 10000));

        // 4. Retry only specific exceptions
        RetryPolicy exceptionSpecificPolicy = RetryPolicy.limitedRetries(3,
                IOException.class, TimeoutException.class);
    }
}
```

## Timeout Policies

```java
import java.time.Duration;

public class TimeoutConfiguration {
    public void initializePolicies() {
        // 1. Simple timeout (seconds)
        TimeoutPolicy shortTimeout = TimeoutPolicy.ofSeconds(30);

        // 2. With millisecond precision
        TimeoutPolicy preciseTimeout = TimeoutPolicy.ofMillis(5000);

        // 3. Minutes
        TimeoutPolicy longTimeout = TimeoutPolicy.ofMinutes(5);
    }

    // Example of a method that returns a specific policy
    public TimeoutPolicy getStandardTimeout() {
        return TimeoutPolicy.ofSeconds(30);
    }
}
```

## Context Management

Thread-safe, type-safe context for data flow:

```java
import java.util.List;

public class WorkflowDataHandler {
    // 1. Constants (TypedKeys) can be declared at the class level
    private static final TypedKey<List<Order>> ORDERS = TypedKey.of("orders", List.class);

    public void manageContextData() {
        WorkflowContext context = new WorkflowContext();

        // 2. Store values
        context.put("userId", 12345);
        context.put("userName", "john_doe");

        // 3. Retrieve typed values
        Integer userId = context.getTyped("userId", Integer.class);
        String userName = context.getTyped("userName", String.class);

        // 4. Using the Type-safe key declared above
        List<Order> orderList = List.of(new Order());
        context.put(ORDERS, orderList);
        List<Order> orders = context.get(ORDERS);

        // 5. Generic type support with TypeReference
        List<User> users = List.of(new User("Alice"), new User("Bob"));
        context.put("users", users);
        List<User> retrieved = context.getTyped("users", new TypeReference<List<User>>() {});

        // 6. Scoped contexts (namespace isolation)
        WorkflowContext userScope = context.scope("user");
        userScope.put("id", 123); // Actually stored in 'context' as "user.id"
    }
}
```

## Execution Strategies

### Thread Pool Strategy (Default)
```java
public class WorkflowConfiguration {
    public Workflow buildManagedParallelWorkflow(Workflow taskA, Workflow taskB) {
        // 1. Define the execution strategy (e.g., using a thread pool)
        ExecutionStrategy strategy = new ThreadPoolExecutionStrategy();

        // 2. Build the workflow using that strategy
        return ParallelWorkflow.builder()
                .name("AsyncDataProcessor")
                .executionStrategy(strategy) // Use the custom strategy
                .workflow(taskA)
                .workflow(taskB)
                .build();
    }
}
```

### Reactive Strategy (Project Reactor)
```java
public class ReactiveWorkflowConfig {
    public Workflow buildReactiveParallelWorkflow(Workflow task1, Workflow task2) {
        // 1. Define the strategy (must be inside a method)
        ExecutionStrategy strategy = new ReactorExecutionStrategy();

        // 2. Build the workflow
        return ParallelWorkflow.builder()
                .name("ReactiveDataPipeline")
                .executionStrategy(strategy)
                .workflow(task1)
                .workflow(task2)
                .build();
    }
}
```

## Workflow Listeners

Add observability and custom actions without modifying workflow code:

```java
// Create custom listener
public class MetricsListener implements WorkflowListener {
    @Override
    public void onStart(String name, WorkflowContext ctx) {
        metrics.counter("workflow.started").increment();
    }
    
    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
        metrics.timer("workflow.duration").record(result.getExecutionDuration());
    }
    
    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
        metrics.counter("workflow.failed").increment();
    }
}

public class WorkflowMonitoring {
    public void setupObservability(WorkflowContext context) {
        context.getListeners().register(new MetricsListener());
    }
}
```

## Helper Utilities

The framework provides comprehensive utility classes to simplify common operations:

### Workflows Helper
Factory methods for creating workflow builders:

```java
// Concise workflow creation
Workflow sequential = Workflows.sequential("MyWorkflow")
    .task(task1)
    .task(task2)
    .build();

Workflow parallel = Workflows.parallel("ParallelTasks")
    .workflow(workflow1)
    .workflow(workflow2)
    .build();
```

### ValidationUtils
Comprehensive validation helpers:

```java
public void validate(Workflow workflow, List<Task>  tasks, int retries, int timeout) {
    // Validate required parameters
    ValidationUtils.requireNonNull(workflow, "workflow");
    ValidationUtils.requireNonEmpty(tasks, "task list");
    ValidationUtils.requirePositive(timeout, "timeout");
    ValidationUtils.requireInRange(retries, 0, 10, "retry count");
}
```

### ResponseMappers
HTTP response mapping utilities:

```java
// Strict mapper with error checking
var mapper = ResponseMappers.strictTypedMapper(User.class);

// Default mapper for simple cases
var defaultMapper = ResponseMappers.defaultResponseMapper();

// Wrap in TaskExecutionException
var wrapped = ResponseMappers.wrapToTaskException(customMapper);
```

### WorkflowResults
Convenient result creation:

```java
// Create success/failure results
WorkflowResult success = WorkflowResults.success(startedAt, completedAt);
WorkflowResult failure = WorkflowResults.failure(startedAt, completedAt, error);
```

## Examples

The framework includes comprehensive examples demonstrating various patterns:

- **Order Processing**: E-commerce order workflow with validation, payment, and fulfillment
- **Data Pipeline**: ETL workflow with extraction, transformation, and loading
- **API Orchestration**: Parallel API calls with aggregation
- **Document Processing**: Multi-stage document processing pipeline
- **Email Campaign**: Batch email processing with tracking
- **Microservices Orchestration**: Coordinating multiple microservices
- **Log Processing**: Parallel log analysis and aggregation
- **Circuit Breaker**: Fault-tolerant workflows preventing cascade failures
- **Batch Processing**: Large dataset processing with parallel batches
- **Event-Driven Workflows**: Event-based triggering with comprehensive monitoring
- **Saga Pattern**: Long-running transactions with compensation
- **Fan-Out/Fan-In**: Parallel task execution with result aggregation
- **State Machine**: Complex state transitions and conditional flows

See the `src/examples` directory for complete implementations.

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) - System design and component relationships
- [Getting Started Guide](docs/GETTING_STARTED.md) - Quick start and common patterns
- [Task Reference](docs/TASKS.md) - Complete task documentation
- [Workflow Guide](docs/WORKFLOWS.md) - Detailed workflow documentation
- [Helper Utilities](docs/HELPERS.md) - Utility classes and helpers
- [Rate Limiting Guide](docs/RATE_LIMITING.md) - Rate limiting strategies and usage
- [Workflow Listeners Guide](docs/WORKFLOW_LISTENERS.md) - Event-driven monitoring
- [Database Configuration](docs/DATABASE_CONFIGURATION.md) - Database-based workflow configuration

## Requirements

- Java 25+
- Maven 3.9+
- Project Reactor (for reactive execution strategy)
- Optional: Spring Framework 6.2+ (for annotation processing)

## Building

```bash
# Build project
mvn clean install

# Run tests
mvn test

# Generate Javadoc
mvn javadoc:javadoc

# Check code style
mvn spotless:check

# Apply formatting
mvn spotless:apply
```
