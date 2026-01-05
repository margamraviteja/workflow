# Workflow Engine - Workflows Guide

## Table of Contents
- [Overview](#overview)
- [Workflow Types](#workflow-types)
- [Sequential Workflow](#sequential-workflow)
- [Parallel Workflow](#parallel-workflow)
- [Conditional Workflow](#conditional-workflow)
- [Dynamic Branching Workflow](#dynamic-branching-workflow)
- [Fallback Workflow](#fallback-workflow)
- [Task Workflow](#task-workflow)
- [Rate Limited Workflow](#rate-limited-workflow)
- [Timeout Workflow](#timeout-workflow)
- [Workflow Composition](#workflow-composition)
- [Best Practices](#best-practices)

## Overview

Workflows are the orchestration layer in the Workflow Engine. They define how tasks and sub-workflows are composed and executed. Every workflow implements the `Workflow` interface and returns a `WorkflowResult`.

### Workflow Contract

```java
public interface Workflow {
    WorkflowResult execute(WorkflowContext context);
    String getName();
}
```

### Workflow Result

All workflows return a `WorkflowResult`:

```java
public class WorkflowResult {
    WorkflowStatus status;    // SUCCESS, FAILED, SKIPPED
    Throwable error;          // Present if FAILED
    Instant startedAt;        // Start timestamp
    Instant completedAt;      // Completion timestamp
    
    Duration getExecutionDuration(); // Computed duration
}
```

## Workflow Types

The framework provides eight core workflow types:

| Workflow              | Purpose              | Execution           | Use Case                  |
|-----------------------|----------------------|---------------------|---------------------------|
| **Sequential**        | Linear execution     | One after another   | Pipelines, ordered steps  |
| **Parallel**          | Concurrent execution | All at once         | Data aggregation, fan-out |
| **Conditional**       | Binary branching     | Based on predicate  | If-then-else logic        |
| **Dynamic Branching** | Multi-way branching  | Based on selector   | Switch/case logic         |
| **Fallback**          | Primary/secondary    | Fallback on failure | Error recovery            |
| **Task**              | Single task wrapper  | Task execution      | Wrap tasks as workflows   |
| **Rate Limited**      | Throttled execution  | Rate controlled     | API rate limits           |
| **Timeout**           | Time-bounded         | With timeout        | Time constraints          |

## Sequential Workflow

Executes child workflows one after another in order. Stops at first failure.

### Features

- **Ordered Execution**: Workflows run in the exact order added
- **Fail-Fast**: Stops at first failure
- **Context Sharing**: All workflows share the same context
- **Empty List**: Returns SUCCESS if no workflows provided

### Builder API

```
SequentialWorkflow.builder()
    .name(String)                      // Optional workflow name
    .workflow(Workflow)                // Add workflow
    .task(Task)                        // Add task (auto-wrapped)
    .task(TaskDescriptor)              // Add task with policies
    .build()
```

### Basic Example

```java
public void example() {
    Workflow pipeline = SequentialWorkflow.builder()
            .name("DataProcessingPipeline")
            .workflow(validationWorkflow)
            .workflow(transformationWorkflow)
            .workflow(persistenceWorkflow)
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("inputData", data);
    WorkflowResult result = pipeline.execute(context);
}
```

### Task Convenience Methods

```java
public void example() {
    // Automatically wraps tasks in TaskWorkflow
    SequentialWorkflow.builder()
            .name("SimpleSequence")
            .task(new ValidationTask())        // Auto-wrapped
            .task(new TransformTask())          // Auto-wrapped
            .task(new SaveTask())               // Auto-wrapped
            .build();

    // With policies
    SequentialWorkflow.builder()
            .task(TaskDescriptor.builder()
                    .task(new ApiCallTask())
                    .retryPolicy(RetryPolicy.limitedRetries(3))
                    .timeoutPolicy(TimeoutPolicy.ofSeconds(30))
                    .build())
            .build();
}
```

### Real-World Examples

#### E-Commerce Order Processing

```java
public void example() {
    Workflow orderProcessing = SequentialWorkflow.builder()
            .name("OrderProcessing")
            .workflow(validateOrderWorkflow)      // Check inventory, pricing
            .workflow(authorizePaymentWorkflow)   // Authorize credit card
            .workflow(createOrderWorkflow)        // Create order record
            .workflow(capturePaymentWorkflow)     // Capture payment
            .workflow(fulfillmentWorkflow)        // Start fulfillment
            .workflow(sendConfirmationWorkflow)   // Email customer
            .build();

    context.put("order", orderRequest);
    WorkflowResult result = orderProcessing.execute(context);

    if (result.getStatus() == WorkflowStatus.SUCCESS) {
        Order order = context.getTyped("createdOrder", Order.class);
        System.out.println("Order created: " + order.getId());
    }
}
```

#### Data Migration Pipeline

```java
Workflow dataMigration = SequentialWorkflow.builder()
    .name("DataMigration")
    .workflow(extractFromLegacyDbWorkflow)
    .workflow(validateDataWorkflow)
    .workflow(transformSchemaWorkflow)
    .workflow(loadToNewDbWorkflow)
    .workflow(verifyMigrationWorkflow)
    .workflow(generateReportWorkflow)
    .build();
```

#### CI/CD Pipeline

```java
Workflow cicdPipeline = SequentialWorkflow.builder()
    .name("CI/CD Pipeline")
    .workflow(checkoutCodeWorkflow)
    .workflow(buildWorkflow)
    .workflow(unitTestWorkflow)
    .workflow(integrationTestWorkflow)
    .workflow(securityScanWorkflow)
    .workflow(deployToStagingWorkflow)
    .workflow(smokeTestWorkflow)
    .workflow(deployToProductionWorkflow)
    .build();
```

### Execution Semantics

```
Input Context
    ↓
Workflow 1 executes
    ↓
Context updated with Workflow 1 outputs
    ↓
Workflow 2 executes (uses Workflow 1 outputs)
    ↓
Context updated with Workflow 2 outputs
    ↓
Workflow 3 executes (uses previous outputs)
    ↓
Final Context with all outputs
```

If any workflow fails:
```
Workflow 1 → SUCCESS
Workflow 2 → FAILED (Error: XYZ)
Workflow 3 → SKIPPED (not executed)
Workflow 4 → SKIPPED (not executed)

Result: FAILED with error from Workflow 2
```

## Parallel Workflow

Executes child workflows concurrently using an execution strategy.

### Features

- **Concurrent Execution**: All workflows start simultaneously
- **Configurable Fail-Fast**: Stop all on first failure or wait for all
- **Context Sharing**: Share context or provide isolated copies
- **Pluggable Strategy**: Thread pool or reactive execution

### Builder API

```
ParallelWorkflow.builder()
    .name(String)                           // Optional workflow name
    .workflow(Workflow)                     // Add workflow
    .task(Task)                             // Add task (auto-wrapped)
    .task(TaskDescriptor)                   // Add task with policies
    .failFast(boolean)                      // Stop on first failure (default: false)
    .shareContext(boolean)                  // Share context (default: true)
    .executionStrategy(ExecutionStrategy)   // Execution strategy (default: ThreadPool)
    .build()
```

### Basic Example

```java
Workflow parallel = ParallelWorkflow.builder()
    .name("DataAggregation")
    .workflow(fetchUsersWorkflow)
    .workflow(fetchOrdersWorkflow)
    .workflow(fetchProductsWorkflow)
    .build();

WorkflowContext context = new WorkflowContext();
WorkflowResult result = parallel.execute(context);

// All data now in context
List<User> users = context.getTyped("users", List.class);
List<Order> orders = context.getTyped("orders", List.class);
```

### Context Sharing Modes

#### Shared Context (Default)

All workflows access the same context instance:

```java
public void example() {
    ParallelWorkflow.builder()
            .shareContext(true)  // Default
            .workflow(workflow1)
            .workflow(workflow2)
            .build();
}
```

**Pros:**
- Efficient (no copying)
- Workflows can share data
- Suitable for data aggregation

**Cons:**
- Requires thread-safe workflows
- Potential for key collisions

#### Isolated Context

Each workflow gets a copy:

```java
public void example() {
    ParallelWorkflow.builder()
            .shareContext(false)
            .workflow(workflow1)
            .workflow(workflow2)
            .build();
}
```

**Pros:**
- Complete isolation
- No concurrency concerns
- Safe for independent workflows

**Cons:**
- More memory overhead
- No data sharing between workflows

### Fail-Fast Modes

#### Wait for All (Default)

```java
public void example() {
    ParallelWorkflow.builder()
            .failFast(false)  // Default
            .workflow(workflow1)
            .workflow(workflow2)
            .build();
}
```

- All workflows complete even if some fail
- Collect all errors
- Suitable for aggregation where partial results are useful

#### Fail-Fast

```java
public void example() {
    ParallelWorkflow.builder()
            .failFast(true)
            .workflow(workflow1)
            .workflow(workflow2)
            .build();
}
```

- Stop all workflows on first failure
- Fast feedback
- Save resources
- Suitable when any failure invalidates the entire operation

### Execution Strategies

#### Thread Pool Strategy (Default)

```java
public void example() {
    ExecutionStrategy strategy = new ThreadPoolExecutionStrategy();

    ParallelWorkflow.builder()
            .executionStrategy(strategy)
            .build();
}
```

- Uses `Executors.newCachedThreadPool()`
- Grows as needed
- Good for mixed workloads

#### Reactive Strategy

```java
public void example() {
    ExecutionStrategy strategy = new ReactorExecutionStrategy();

    ParallelWorkflow.builder()
            .executionStrategy(strategy)
            .build();
}
```

- Uses Project Reactor
- Non-blocking I/O
- Better for I/O-bound workflows

### Real-World Examples

#### API Orchestration

```java
Workflow apiOrchestration = ParallelWorkflow.builder()
    .name("UserDataAggregation")
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/users/" + userId)
        .responseContextKey("userData")
        .build())
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/users/" + userId + "/orders")
        .responseContextKey("userOrders")
        .build())
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/users/" + userId + "/preferences")
        .responseContextKey("userPreferences")
        .build())
    .failFast(true)
    .build();
```

#### Batch Processing

```java
Workflow batchProcessing = ParallelWorkflow.builder()
    .name("BatchImageProcessing")
    .workflow(resizeImagesWorkflow)
    .workflow(generateThumbnailsWorkflow)
    .workflow(extractMetadataWorkflow)
    .workflow(runOcrWorkflow)
    .shareContext(false)  // Independent processing
    .build();
```

#### Microservices Coordination

```java
Workflow microservicesCall = ParallelWorkflow.builder()
    .name("OrderCreation")
    .workflow(inventoryServiceWorkflow)   // Check inventory
    .workflow(pricingServiceWorkflow)     // Calculate pricing
    .workflow(shippingServiceWorkflow)    // Get shipping options
    .failFast(true)                       // Fail if any service fails
    .shareContext(true)                   // Aggregate results
    .build();
```

#### Health Check System

```java
Workflow healthCheck = ParallelWorkflow.builder()
    .name("SystemHealthCheck")
    .workflow(databaseHealthWorkflow)
    .workflow(cacheHealthWorkflow)
    .workflow(externalApiHealthWorkflow)
    .workflow(diskSpaceCheckWorkflow)
    .failFast(false)  // Check all systems
    .build();
```

## Conditional Workflow

Branches execution based on a predicate evaluated against the context.

### Features

- **Binary Branching**: True/false paths
- **Lazy Evaluation**: Only chosen branch executes
- **Context-Based**: Decision based on context state

### Builder API

```
ConditionalWorkflow.builder()
    .name(String)                               // Optional name
    .condition(Predicate<WorkflowContext>)      // Condition predicate
    .whenTrue(Workflow)                         // Execute if true
    .whenFalse(Workflow)                        // Execute if false (optional)
    .build()
```

### Basic Example

```java
Workflow conditional = ConditionalWorkflow.builder()
    .name("OrderValidation")
    .condition(ctx -> {
        Integer amount = ctx.getTyped("orderAmount", Integer.class);
        return amount != null && amount > 1000;
    })
    .whenTrue(premiumOrderWorkflow)
    .whenFalse(standardOrderWorkflow)
    .build();
```

### Real-World Examples

#### Feature Toggle

```java
Workflow featureToggle = ConditionalWorkflow.builder()
    .condition(ctx -> 
        Boolean.TRUE.equals(ctx.get("useNewAlgorithm")))
    .whenTrue(newAlgorithmWorkflow)
    .whenFalse(legacyAlgorithmWorkflow)
    .build();
```

#### User Permission Check

```java
Workflow permissionCheck = ConditionalWorkflow.builder()
    .condition(ctx -> {
        User user = ctx.getTyped("currentUser", User.class);
        return user != null && user.hasPermission("ADMIN");
    })
    .whenTrue(adminWorkflow)
    .whenFalse(unauthorizedWorkflow)
    .build();
```

#### Data Validation

```java
Workflow validation = ConditionalWorkflow.builder()
    .condition(ctx -> {
        String data = ctx.getTyped("inputData", String.class);
        return data != null && !data.isEmpty() && isValidFormat(data);
    })
    .whenTrue(processDataWorkflow)
    .whenFalse(dataErrorWorkflow)
    .build();
```

#### Environment-Based Configuration

```java
Workflow envConfig = ConditionalWorkflow.builder()
    .condition(ctx -> 
        "production".equals(System.getenv("ENV")))
    .whenTrue(productionConfigWorkflow)
    .whenFalse(developmentConfigWorkflow)
    .build();
```

### Optional False Branch

If no `whenFalse` is provided, the workflow returns SUCCESS when condition is false:

```java
public void example() {
    ConditionalWorkflow.builder()
            .condition(predicate)
            .whenTrue(optionalWorkflow)
            // No whenFalse - returns SUCCESS if condition is false
            .build();
}
```

## Dynamic Branching Workflow

Selects one of many workflows based on a selector function.

### Features

- **Multi-Way Branching**: Select from many options
- **Selector Function**: Dynamic branch selection
- **Default Branch**: Fallback for unmatched cases
- **Case-Insensitive**: Key matching is case-insensitive

### Builder API

```
DynamicBranchingWorkflow.builder()
    .name(String)                                      // Optional name
    .selector(Function<WorkflowContext, String>)       // Selector function
    .branch(String key, Workflow workflow)             // Add branch
    .defaultBranch(Workflow)                           // Default fallback
    .build();
```

### Basic Example

```java
Workflow branching = DynamicBranchingWorkflow.builder()
    .name("ProcessingModeSelector")
    .selector(ctx -> ctx.getTyped("mode", String.class))
    .branch("fast", fastProcessingWorkflow)
    .branch("accurate", accurateProcessingWorkflow)
    .branch("balanced", balancedProcessingWorkflow)
    .defaultBranch(standardProcessingWorkflow)
    .build();
```

### Real-World Examples

#### Payment Method Routing

```java
Workflow paymentRouter = DynamicBranchingWorkflow.builder()
    .name("PaymentRouter")
    .selector(ctx -> ctx.getTyped("paymentMethod", String.class))
    .branch("credit_card", creditCardWorkflow)
    .branch("debit_card", debitCardWorkflow)
    .branch("paypal", paypalWorkflow)
    .branch("bitcoin", bitcoinWorkflow)
    .branch("bank_transfer", bankTransferWorkflow)
    .defaultBranch(unsupportedPaymentWorkflow)
    .build();
```

#### Document Type Processing

```java
Workflow documentProcessor = DynamicBranchingWorkflow.builder()
    .name("DocumentProcessor")
    .selector(ctx -> {
        String filename = ctx.getTyped("filename", String.class);
        return getFileExtension(filename);
    })
    .branch("pdf", pdfProcessingWorkflow)
    .branch("docx", wordProcessingWorkflow)
    .branch("xlsx", excelProcessingWorkflow)
    .branch("csv", csvProcessingWorkflow)
    .defaultBranch(unsupportedFormatWorkflow)
    .build();
```

#### User Role-Based Workflow

```java
Workflow roleBasedWorkflow = DynamicBranchingWorkflow.builder()
    .name("RoleBasedAccess")
    .selector(ctx -> {
        User user = ctx.getTyped("user", User.class);
        return user.getRole();
    })
    .branch("admin", adminWorkflow)
    .branch("manager", managerWorkflow)
    .branch("employee", employeeWorkflow)
    .branch("guest", guestWorkflow)
    .defaultBranch(unauthorizedWorkflow)
    .build();
```

#### Data Source Selection

```java
Workflow dataSourceSelector = DynamicBranchingWorkflow.builder()
    .name("DataSourceSelector")
    .selector(ctx -> ctx.getTyped("dataSource", String.class))
    .branch("database", databaseReadWorkflow)
    .branch("api", apiCallWorkflow)
    .branch("file", fileReadWorkflow)
    .branch("cache", cacheReadWorkflow)
    .branch("stream", streamProcessingWorkflow)
    .defaultBranch(databaseReadWorkflow)  // Default to database
    .build();
```

### Best Practices

1. **Always Provide Default**: Handle unexpected values gracefully
2. **Use Enums**: For type-safe selector values
3. **Fast Selector**: Keep selector function simple and fast
4. **Log Selection**: Log which branch was selected for debugging

```java
// Using enum for type safety
enum ProcessingMode {
    FAST, ACCURATE, BALANCED
}

public void example() {
    DynamicBranchingWorkflow.builder()
            .selector(ctx -> {
                ProcessingMode mode = ctx.getTyped("mode", ProcessingMode.class);
                return mode.name().toLowerCase();
            })
            .branch("fast", fastWorkflow)
            .branch("accurate", accurateWorkflow)
            .branch("balanced", balancedWorkflow)
            .build();
}
```

## Fallback Workflow

Executes primary workflow, falls back to secondary on failure.

### Features

- **Primary/Secondary**: Try primary first, fallback on failure
- **Error Recovery**: Graceful degradation
- **Transparent**: Client doesn't know which workflow executed

### Builder API

```
FallbackWorkflow.builder()
    .name(String)            // Optional name
    .primary(Workflow)       // Primary workflow
    .fallback(Workflow)      // Fallback workflow
    .build()
```

### Basic Example

```java
Workflow withFallback = FallbackWorkflow.builder()
    .name("DataRetrieval")
    .primary(apiCallWorkflow)       // Try API first
    .fallback(cacheReadWorkflow)    // Use cache if API fails
    .build();
```

### Real-World Examples

#### API with Cache Fallback

```java
Workflow dataRetrieval = FallbackWorkflow.builder()
    .name("UserDataRetrieval")
    .primary(SequentialWorkflow.builder()
        .task(new GetTask.Builder<>(client)
            .url("https://api.example.com/users/" + userId)
            .responseContextKey("userData")
            .build())
        .build())
    .fallback(SequentialWorkflow.builder()
        .task(new CacheReadTask("user:" + userId, "userData"))
        .build())
    .build();
```

#### Database with File Fallback

```java
Workflow configRetrieval = FallbackWorkflow.builder()
    .primary(databaseConfigWorkflow)     // Try database first
    .fallback(fileConfigWorkflow)        // Use file if DB unavailable
    .build();
```

#### Primary and Backup Service

```java
Workflow serviceCall = FallbackWorkflow.builder()
    .primary(primaryServiceWorkflow)     // Primary data center
    .fallback(backupServiceWorkflow)     // Backup data center
    .build();
```

#### Algorithm Fallback

```java
Workflow processing = FallbackWorkflow.builder()
    .primary(mlModelWorkflow)           // Try ML model
    .fallback(ruleBasedWorkflow)        // Fallback to rules
    .build();
```

### Chaining Fallbacks

You can chain multiple fallback levels:

```java
Workflow multiLevelFallback = FallbackWorkflow.builder()
    .primary(primaryWorkflow)
    .fallback(FallbackWorkflow.builder()
        .primary(secondaryWorkflow)
        .fallback(tertiaryWorkflow)
        .build())
    .build();

// Execution order: primary → secondary → tertiary
```

## Task Workflow

Wraps a single task as a workflow.

### Features

- **Adapter Pattern**: Task → Workflow conversion
- **Policy Support**: Add retry/timeout via TaskDescriptor
- **Uniform Interface**: Tasks become workflows

### Constructors

```java
public void example() {
    // Simple task wrapper
    new TaskWorkflow(task);

    // With policies
    new TaskWorkflow(descriptor);
}
```

### Basic Example

```java
// Wrap task
Task task = new ValidationTask();
Workflow workflow = new TaskWorkflow(task);

// With policies
TaskDescriptor descriptor = TaskDescriptor.builder()
    .task(new ApiCallTask())
    .retryPolicy(RetryPolicy.limitedRetries(3))
    .timeoutPolicy(TimeoutPolicy.ofSeconds(30))
    .build();
    
Workflow resilientWorkflow = new TaskWorkflow(descriptor);
```

### Automatic Wrapping

Most builders automatically wrap tasks:

```java
// Explicit
public void example() {
    SequentialWorkflow.builder()
            .workflow(new TaskWorkflow(myTask))
            .build();

    // Automatic (preferred)
    SequentialWorkflow.builder()
            .task(myTask)  // Automatically wrapped
            .build();
}
```

## Rate Limited Workflow

Wraps any workflow with rate limiting to control execution frequency.

### Features

- **Rate Control**: Limits workflow execution rate
- **Multiple Strategies**: Fixed window, sliding window, token bucket, leaky bucket
- **Transparent**: Wraps any workflow without modification
- **Thread-Safe**: Safe for concurrent access

### Builder API

```
RateLimitedWorkflow.builder()
    .name(String)                           // Optional name
    .workflow(Workflow)                     // Workflow to rate limit
    .rateLimitStrategy(RateLimitStrategy)   // Rate limiting strategy
    .build()
```

### Basic Example

```java
// Create rate limiter: 100 requests per minute
RateLimitStrategy limiter = new FixedWindowRateLimiter(
    100,
    Duration.ofMinutes(1)
);

// Wrap workflow with rate limiting
Workflow rateLimited = RateLimitedWorkflow.builder()
    .name("RateLimitedAPI")
    .workflow(apiCallWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// Execute - automatically rate limited
WorkflowContext context = new WorkflowContext();
WorkflowResult result = rateLimited.execute(context);
```

### Rate Limiting Strategies

#### Fixed Window

Simple time-window based limiting:

```java
// 100 requests per minute
RateLimitStrategy limiter = new FixedWindowRateLimiter(
    100,
    Duration.ofMinutes(1)
);
```

#### Sliding Window

Accurate rate limiting with no boundary effects:

```java
// 100 requests per minute with sliding window
RateLimitStrategy limiter = new SlidingWindowRateLimiter(
    100,
    Duration.ofMinutes(1)
);
```

#### Token Bucket

Allows controlled bursts while maintaining average rate:

```java
// 100 req/sec with burst capacity of 200
RateLimitStrategy limiter = new TokenBucketRateLimiter(
    100,                        // Tokens per second
    200,                        // Burst capacity
    Duration.ofSeconds(1)
);
```

#### Leaky Bucket

Ensures constant output rate:

```java
// Steady 100 requests per second
RateLimitStrategy limiter = new LeakyBucketRateLimiter(
    100,
    Duration.ofSeconds(1)
);
```

### Real-World Examples

#### API Rate Limiting

```java
// Respect external API rate limits (100/min)
RateLimitStrategy apiLimiter = new FixedWindowRateLimiter(
    100,
    Duration.ofMinutes(1)
);

Workflow apiWorkflow = SequentialWorkflow.builder()
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/data")
        .build())
    .build();

Workflow rateLimitedApi = RateLimitedWorkflow.builder()
    .name("ExternalAPICall")
    .workflow(apiWorkflow)
    .rateLimitStrategy(apiLimiter)
    .build();
```

#### Parallel Workflows with Shared Limiter

```java
// Shared limiter for multiple parallel workflows
RateLimitStrategy sharedLimiter = new SlidingWindowRateLimiter(
    50,
    Duration.ofSeconds(1)
);

Workflow parallel = ParallelWorkflow.builder()
    .workflow(RateLimitedWorkflow.builder()
        .workflow(workflow1)
        .rateLimitStrategy(sharedLimiter)
        .build())
    .workflow(RateLimitedWorkflow.builder()
        .workflow(workflow2)
        .rateLimitStrategy(sharedLimiter)
        .build())
    .build();
```

#### Database Connection Throttling

```java
// Limit database operations to prevent overload
RateLimitStrategy dbLimiter = new TokenBucketRateLimiter(
    50,    // Normal rate
    100,   // Allow bursts
    Duration.ofSeconds(1)
);

Workflow dbWorkflow = RateLimitedWorkflow.builder()
    .workflow(databaseOperationWorkflow)
    .rateLimitStrategy(dbLimiter)
    .build();
```

For detailed information about rate limiting strategies, see [RATE_LIMITING.md](RATE_LIMITING.md).

## Timeout Workflow

Wraps any workflow with a timeout constraint.

### Features

- **Time Bounds**: Ensures workflow completes within specified time
- **Interruption**: Attempts to interrupt execution on timeout
- **Transparent**: Wraps any workflow without modification
- **Flexible**: Configurable timeout duration

### Builder API

```
TimeoutWorkflow.builder()
    .name(String)           // Optional name
    .workflow(Workflow)     // Workflow to timeout
    .timeoutMs(long)        // Timeout in milliseconds
    .build()
```

### Basic Example

```java
public void example() {
    // Create a workflow with 30-second timeout
    Workflow timedWorkflow = TimeoutWorkflow.builder()
            .name("TimedAPICall")
            .workflow(apiWorkflow)
            .timeoutMs(30000)  // 30 seconds
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = timedWorkflow.execute(context);

    if (result.getStatus() == WorkflowStatus.FAILED &&
            result.getError() instanceof TaskTimeoutException) {
        System.err.println("Workflow timed out");
    }
}
```

### Real-World Examples

#### Long-Running Operation with Timeout

```java
Workflow processingWorkflow = SequentialWorkflow.builder()
    .task(new DataProcessingTask())
    .task(new ReportGenerationTask())
    .build();

Workflow timedProcessing = TimeoutWorkflow.builder()
    .name("TimedProcessing")
    .workflow(processingWorkflow)
    .timeoutMs(60000)  // 1 minute max
    .build();
```

#### Parallel Workflows with Overall Timeout

```java
// All parallel workflows must complete within timeout
Workflow parallelAPIs = ParallelWorkflow.builder()
    .workflow(apiCall1)
    .workflow(apiCall2)
    .workflow(apiCall3)
    .build();

Workflow timedParallel = TimeoutWorkflow.builder()
    .name("TimedParallelAPIs")
    .workflow(parallelAPIs)
    .timeoutMs(60000)  // All must complete in 60 seconds
    .build();
```

#### Fallback After Timeout

```java
// Primary with timeout, fallback to alternative
Workflow primaryWithTimeout = TimeoutWorkflow.builder()
    .workflow(slowApiWorkflow)
    .timeoutMs(5000)  // 5 second timeout
    .build();

Workflow withFallback = FallbackWorkflow.builder()
    .primary(primaryWithTimeout)
    .fallback(cachedDataWorkflow)  // Use cache if timeout
    .build();
```

#### Different Timeouts for Different Steps

```java
Workflow pipeline = SequentialWorkflow.builder()
    .workflow(TimeoutWorkflow.builder()
        .workflow(validationWorkflow)
        .timeoutMs(5000)  // Quick validation
        .build())
    .workflow(TimeoutWorkflow.builder()
        .workflow(processingWorkflow)
        .timeoutMs(30000)  // Longer processing
        .build())
    .workflow(TimeoutWorkflow.builder()
        .workflow(saveWorkflow)
        .timeoutMs(10000)  // Save timeout
        .build())
    .build();
```

### Important Notes

**Interruption Behavior:**
- The workflow attempts to interrupt execution on timeout
- Inner workflows must handle interruption properly
- Workflows that ignore interrupts may continue running

**Timeout vs Task Timeout:**
- `TimeoutWorkflow`: Applies to entire workflow execution
- `TaskDescriptor.timeoutPolicy`: Applies to individual task execution
- Both can be used together for fine-grained control

## Workflow Composition

### Nesting Workflows

Workflows can be nested to any depth:

```java
Workflow outer = SequentialWorkflow.builder()
    .workflow(task1Workflow)
    .workflow(ParallelWorkflow.builder()
        .workflow(task2Workflow)
        .workflow(task3Workflow)
        .build())
    .workflow(task4Workflow)
    .build();
```

### Complex Composition Example

```java
Workflow complexOrchestration = SequentialWorkflow.builder()
    .name("ComplexOrchestration")
    
    // Step 1: Validate input
    .workflow(validationWorkflow)
    
    // Step 2: Parallel data fetching
    .workflow(ParallelWorkflow.builder()
        .workflow(fetchUserDataWorkflow)
        .workflow(fetchOrdersWorkflow)
        .workflow(fetchPreferencesWorkflow)
        .build())
    
    // Step 3: Conditional processing
    .workflow(ConditionalWorkflow.builder()
        .condition(ctx -> 
            ctx.getTyped("userType", String.class).equals("premium"))
        .whenTrue(premiumProcessingWorkflow)
        .whenFalse(standardProcessingWorkflow)
        .build())
    
    // Step 4: Dynamic routing
    .workflow(DynamicBranchingWorkflow.builder()
        .selector(ctx -> ctx.getTyped("outputFormat", String.class))
        .branch("json", jsonFormatterWorkflow)
        .branch("xml", xmlFormatterWorkflow)
        .branch("csv", csvFormatterWorkflow)
        .defaultBranch(jsonFormatterWorkflow)
        .build())
    
    // Step 5: Parallel output with fallback
    .workflow(ParallelWorkflow.builder()
        .workflow(FallbackWorkflow.builder()
            .primary(saveToDbWorkflow)
            .fallback(saveToFileWorkflow)
            .build())
        .workflow(FallbackWorkflow.builder()
            .primary(sendEmailWorkflow)
            .fallback(queueEmailWorkflow)
            .build())
        .build())
    
    .build();
```

### Reusable Workflow Components

```java
// Define reusable components
Workflow dataValidation = SequentialWorkflow.builder()
    .name("DataValidation")
    .task(new SchemaValidationTask())
    .task(new BusinessRuleValidationTask())
    .build();

Workflow dataEnrichment = ParallelWorkflow.builder()
    .name("DataEnrichment")
    .workflow(geoEnrichmentWorkflow)
    .workflow(demographicEnrichmentWorkflow)
    .workflow(behavioralEnrichmentWorkflow)
    .build();

// Compose into larger workflows
Workflow pipeline1 = SequentialWorkflow.builder()
    .workflow(dataValidation)
    .workflow(dataEnrichment)
    .workflow(processingWorkflow1)
    .build();

Workflow pipeline2 = SequentialWorkflow.builder()
    .workflow(dataValidation)  // Reused
    .workflow(dataEnrichment)  // Reused
    .workflow(processingWorkflow2)
    .build();
```

## Best Practices

### 1. Workflow Naming

Always name your workflows for debugging:

```java
public void example() {
    // Good
    SequentialWorkflow.builder()
            .name("OrderProcessingPipeline")
            .build();

    // Less helpful
    SequentialWorkflow.builder()
            .build();  // Gets auto-generated name
}
```

### 2. Error Handling

Plan for failures:

```java
public void example() {
    // With fallback
    Workflow robust = FallbackWorkflow.builder()
            .primary(primaryWorkflow)
            .fallback(fallbackWorkflow)
            .build();

    // With retry
    TaskDescriptor resilient = TaskDescriptor.builder()
            .task(task)
            .retryPolicy(RetryPolicy.exponentialBackoff(3, 1000))
            .build();
}
```

### 3. Context Management

Use scoped contexts for namespace isolation:

```java
// In parent workflow
public void example() {
    WorkflowContext userContext = context.scope("user");
    userWorkflow.execute(userContext);
    // userWorkflow writes to "id", stored as "user.id" in main context
}
```

### 4. Parallel Workflow Guidelines

- Use `shareContext(false)` for independent workflows
- Use `failFast(true)` when any failure invalidates the result
- Ensure thread-safe tasks when sharing context
- Consider execution strategy based on workload

### 5. Composition Strategy

- Keep workflows focused (single responsibility)
- Create reusable workflow components
- Nest workflows for logical grouping
- Document workflow structure

### 6. Performance Optimization

```java
public void example() {
    // Good: Parallel for independent I/O operations
    ParallelWorkflow.builder()
            .workflow(ioWorkflow1)
            .workflow(ioWorkflow2)
            .build();
}
```

### 7. Testing Strategy

Test workflows at multiple levels:

```java
// Unit test: Test individual workflows
@Test
void testValidationWorkflow() {
    WorkflowContext context = new WorkflowContext();
    context.put("input", testData);
    
    WorkflowResult result = validationWorkflow.execute(context);
    
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
}

// Integration test: Test composed workflows
@Test
void testCompleteOrchestration() {
    Workflow orchestration = buildOrchestration();
    WorkflowContext context = createTestContext();
    
    WorkflowResult result = orchestration.execute(context);
    
    verifyExpectedState(context);
}
```

### 8. Monitoring and Logging

```properties
# Workflows automatically log lifecycle events
# Configure logging level in log4j2.properties:
logger.com.workflow.level = DEBUG
```
```java
// Custom logging in tasks
@Slf4j
public class MyTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
        log.info("Processing user: {}", context.get("userId"));
        // Process
        log.debug("Processed {} records", count);
    }
}
```

### 9. Workflow Documentation

Document workflows inline:

```java
/**
 * Order processing workflow that:
 * 1. Validates order (inventory, pricing)
 * 2. Processes payment (authorize → capture)
 * 3. Creates fulfillment (warehouse allocation)
 * 4. Sends notifications (customer + warehouse)
 * 
 * <p>
 * Context Inputs:
 * - "orderRequest" (OrderRequest): Order details
 * - "customerId" (String): Customer ID
 * 
 * <p>
 * Context Outputs:
 * - "order" (Order): Created order
 * - "fulfillmentId" (String): Fulfillment tracking ID
 * 
 * <p>
 * Failure Scenarios:
 * - Insufficient inventory → FAILED
 * - Payment declined → FAILED
 * - Warehouse unavailable → Retried 3 times
 */
public static Workflow createOrderProcessingWorkflow() {
    return SequentialWorkflow.builder()
        .name("OrderProcessing")
        .workflow(validateOrderWorkflow())
        .workflow(processPaymentWorkflow())
        .workflow(createFulfillmentWorkflow())
        .workflow(sendNotificationsWorkflow())
        .build();
}
```

## Summary

The Workflow Engine provides powerful orchestration capabilities through:

1. **Sequential Workflow**: Linear pipelines with fail-fast
2. **Parallel Workflow**: Concurrent execution with configurable strategies
3. **Conditional Workflow**: Binary branching based on predicates
4. **Dynamic Branching**: Multi-way routing with selectors
5. **Fallback Workflow**: Graceful error recovery
6. **Task Workflow**: Uniform task/workflow interface
7. **Rate Limited Workflow**: Throttle workflow execution
8. **Timeout Workflow**: Add time constraints to workflows

All workflows:
- Share a common interface
- Return structured results
- Support composition and nesting
- Provide consistent logging
- Enable complex orchestration patterns

Choose the right workflow type based on your execution semantics:
- **Linear dependencies** → Sequential
- **Independent operations** → Parallel
- **Conditional logic** → Conditional or Dynamic Branching
- **Error recovery** → Fallback
- **Single task** → Task Workflow
- **Rate control** → Rate Limited Workflow
- **Time constraints** → Timeout Workflow
