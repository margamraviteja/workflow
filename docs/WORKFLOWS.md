# Workflow Engine - Workflows Guide

## Table of Contents
- [Overview](#overview)
- [Workflow Types](#workflow-types)
- [Sequential Workflow](#sequential-workflow)
- [Parallel Workflow](#parallel-workflow)
- [Conditional Workflow](#conditional-workflow)
- [Dynamic Branching Workflow](#dynamic-branching-workflow)
- [Fallback Workflow](#fallback-workflow)
- [Saga Workflow](#saga-workflow)
- [Task Workflow](#task-workflow)
- [Rate Limited Workflow](#rate-limited-workflow)
- [Timeout Workflow](#timeout-workflow)
- [JavaScript Workflow](#javascript-workflow)
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

The framework provides ten core workflow types:

| Workflow              | Purpose              | Execution           | Use Case                  |
|-----------------------|----------------------|---------------------|---------------------------|
| **Sequential**        | Linear execution     | One after another   | Pipelines, ordered steps  |
| **Parallel**          | Concurrent execution | All at once         | Data aggregation, fan-out |
| **Conditional**       | Binary branching     | Based on predicate  | If-then-else logic        |
| **Dynamic Branching** | Multi-way branching  | Based on selector   | Switch/case logic         |
| **Fallback**          | Primary/secondary    | Fallback on failure | Error recovery            |
| **Saga**              | Distributed txn      | With compensations  | Rollback on failure       |
| **Task**              | Single task wrapper  | Task execution      | Wrap tasks as workflows   |
| **Rate Limited**      | Throttled execution  | Rate controlled     | API rate limits           |
| **Timeout**           | Time-bounded         | With timeout        | Time constraints          |
| **Javascript**        | Dynamic JS execution | Script-based logic  | Dynamic business rules    |

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
    .task(new GetHttpTask.Builder<>(client)
        .url("https://api.example.com/users/" + userId)
        .responseContextKey("userData")
        .build())
    .task(new GetHttpTask.Builder<>(client)
        .url("https://api.example.com/users/" + userId + "/orders")
        .responseContextKey("userOrders")
        .build())
    .task(new GetHttpTask.Builder<>(client)
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
        .task(new GetHttpTask.Builder<>(client)
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

## Saga Workflow

Implements the [Saga pattern](https://microservices.io/patterns/data/saga.html) for managing distributed transactions with compensating actions. When any step fails, all previously completed steps are automatically rolled back in reverse order.

### Features

- **Compensating Actions**: Automatic rollback on failure
- **Backward Recovery**: Compensations run in reverse order (last to first)
- **Failure Context**: Access failure information during compensation
- **Flexible Steps**: Support for tasks, workflows, or lambda functions
- **Partial Compensation**: Steps without side effects don't need compensation
- **Error Collection**: Aggregates both original failure and compensation errors

### Builder API

```
SagaWorkflow.builder()
    .name(String)                                     // Optional name
    .step(SagaStep)                                   // Add saga step
    .step(Task action, Task compensation)             // Convenience: tasks
    .step(Workflow action, Workflow compensation)     // Convenience: workflows
    .step(Task action)                                // Action only, no compensation
    .step(Workflow action)                            // Action only, no compensation
    .build()
```

### Basic Example

```java
SagaWorkflow orderSaga = SagaWorkflow.builder()
    .name("OrderProcessingSaga")
    .step(SagaStep.builder()
        .name("ReserveInventory")
        .action(new TaskWorkflow(new ReserveInventoryTask()))
        .compensation(new TaskWorkflow(new ReleaseInventoryTask()))
        .build())
    .step(SagaStep.builder()
        .name("ChargePayment")
        .action(new TaskWorkflow(new ChargePaymentTask()))
        .compensation(new TaskWorkflow(new RefundPaymentTask()))
        .build())
    .step(SagaStep.builder()
        .name("ShipOrder")
        .action(new TaskWorkflow(new ShipOrderTask()))
        .compensation(new TaskWorkflow(new CancelShipmentTask()))
        .build())
    .build();

public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("orderId", orderId);
    WorkflowResult result = orderSaga.execute(context);
}
```

### Using Convenience Methods

```java
// Using tasks directly
SagaWorkflow saga = SagaWorkflow.builder()
    .name("SimpleSaga")
    .step(actionTask, compensationTask)  // Both as Tasks
    .step(actionTask)                     // Action only, no compensation
    .build();

// Using workflows
SagaWorkflow saga = SagaWorkflow.builder()
    .name("WorkflowSaga")
    .step(actionWorkflow, compensationWorkflow)
    .step(readOnlyWorkflow)  // No compensation needed
    .build();

// Using lambda tasks
SagaWorkflow saga = SagaWorkflow.builder()
    .name("LambdaSaga")
    .step(
        ctx -> ctx.put("result", performWork()),
        ctx -> ctx.remove("result")
    )
    .build();
```

### Real-World Example: E-Commerce Order

```java
SagaWorkflow orderSaga = SagaWorkflow.builder()
    .name("OrderSaga")
    
    // Step 1: Validate order (no compensation needed)
    .step(SagaStep.builder()
        .name("ValidateOrder")
        .action(ctx -> {
            Order order = ctx.getTyped("order", Order.class);
            if (order.getItems().isEmpty()) {
                throw new TaskExecutionException("Order has no items");
            }
        })
        .build())
    
    // Step 2: Reserve inventory
    .step(SagaStep.builder()
        .name("ReserveInventory")
        .action(ctx -> {
            Order order = ctx.getTyped("order", Order.class);
            List<String> reservationIds = inventoryService.reserve(order.getItems());
            ctx.put("reservationIds", reservationIds);
        })
        .compensation(ctx -> {
            List<String> ids = ctx.getTyped("reservationIds", List.class);
            if (ids != null) {
                inventoryService.release(ids);
            }
        })
        .build())
    
    // Step 3: Charge payment
    .step(SagaStep.builder()
        .name("ChargePayment")
        .action(ctx -> {
            Order order = ctx.getTyped("order", Order.class);
            String transactionId = paymentService.charge(
                order.getCustomerId(), 
                order.getTotal()
            );
            ctx.put("paymentTransactionId", transactionId);
        })
        .compensation(ctx -> {
            String txnId = ctx.get("paymentTransactionId");
            if (txnId != null) {
                paymentService.refund(txnId);
            }
        })
        .build())
    
    .build();
```

### Failure Handling

#### Accessing Failure Information

```
SagaStep.builder()
    .name("DatabaseUpdate")
    .action(updateWorkflow)
    .compensation(ctx -> {
        // Access the original failure cause
        Throwable cause = ctx.get(SagaWorkflow.SAGA_FAILURE_CAUSE);
        
        // Access the name of the step that failed
        String failedStep = ctx.get(SagaWorkflow.SAGA_FAILED_STEP);
        
        // Perform informed rollback
        log.info("Rolling back due to failure in step: {}", failedStep);
        performRollback(ctx, cause);
    })
    .build()
```

#### Handling Compensation Failures

```java
public void example() {
    WorkflowResult result = saga.execute(context);

    if (result.isFailure()) {
        Throwable error = result.getError();

        if (error instanceof SagaCompensationException sce) {
            // Original failure
            Throwable originalCause = sce.getCause();

            // Compensation failures
            List<Throwable> compensationErrors = sce.getCompensationErrors();

            log.error("Saga failed at: {}", originalCause.getMessage());
            log.error("Additionally, {} compensations failed:", compensationErrors.size());
            for (Throwable compError : compensationErrors) {
                log.error("  - {}", compError.getMessage());
            }
        }
    }
}
```

### Execution Semantics

1. **Forward Execution**: Steps execute sequentially in the order they were added
2. **Failure Detection**: If any step returns `FAILED` status or throws an exception, forward execution stops
3. **Compensation Trigger**: On failure, all previously successful steps are compensated
4. **Compensation Order**: Compensations run in reverse order (last success → first success)
5. **Compensation Continuation**: If a compensation fails, remaining compensations still execute
6. **Context Sharing**: All steps and compensations share the same `WorkflowContext`

### Best Practices

1. **Idempotent Compensations**: Design compensating actions to be idempotent
2. **Store State for Rollback**: Save any IDs or references needed for compensation in the context
3. **Null Checks in Compensation**: Always check for null before using context values
4. **Log Compensation Actions**: Include detailed logging in compensations to aid debugging
5. **Test Failure Scenarios**: Write tests that simulate failures at each step

### When to Use Saga Pattern

| Use Saga When                    | Use Alternative When              |
|----------------------------------|-----------------------------------|
| Multiple independent services    | Single service/database           |
| Long-running transactions        | Short atomic transactions         |
| Eventual consistency acceptable  | Strong consistency required       |
| Compensating actions possible    | No way to undo operations         |

For complete documentation and more examples, see [SAGA.md](SAGA.md).

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
    .task(new GetHttpTask.Builder<>(client)
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

## JavaScript Workflow

Executes dynamic JavaScript logic using the GraalVM Polyglot API. This workflow type enables you to implement business logic that can be modified at runtime without recompiling or redeploying your application.

### Features

- **Dynamic Execution**: JavaScript code loaded and executed at runtime
- **High Performance**: Uses shared GraalVM engine with JIT compilation and AST caching
- **Secure Sandbox**: Scripts execute in a restricted environment with disabled host class lookup
- **Flexible Script Sources**: Load scripts from files, databases, URLs, or inline strings via `ScriptProvider`
- **Context Integration**: Full bidirectional access to `WorkflowContext`
- **Type Conversion**: Automatic conversion between JavaScript and Java types via `PolyglotMapper`
- **ECMAScript 2021**: Full ES2021 support including modern JavaScript features
- **ESM Module Support**: Full support for ES6 modules with import/export syntax
- **Module Resolution**: Relative and absolute module imports with proper URI-based resolution

### Builder API

```
JavascriptWorkflow.builder()
    .name(String)                       // Optional workflow name
    .scriptProvider(ScriptProvider)     // Required: provides script content
    .build()
```

### ScriptProvider Interface

Script content is provided through the `ScriptProvider` interface:

```java
public interface ScriptProvider {
    record ScriptSource(String content, URI uri) {}
    ScriptSource loadScript() throws ScriptLoadException;
}
```

The `ScriptSource` record contains:
- **content**: The JavaScript code to execute
- **uri**: Optional URI for module resolution (required for ESM imports)

**Built-in Providers:**
- `InlineScriptProvider`: Inline script with optional base URI
- `FileScriptProvider`: Load from filesystem (provides file URI automatically)
- `ClasspathScriptProvider`: Load from classpath resources (provides resource URI)
- Custom implementations: Database, HTTP, etc.

### Basic Example

```java
public void example() {
   // Inline script provider
    ScriptProvider provider = new InlineScriptProvider(
            """
            var total = ctx.get('price') * ctx.get('quantity');
            ctx.put('total', total);
            """);

    JavascriptWorkflow workflow = JavascriptWorkflow.builder()
            .name("CalculateTotal")
            .scriptProvider(provider)
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("price", 100.0);
    context.put("quantity", 5);

    WorkflowResult result = workflow.execute(context);
    Double total = context.getTyped("total", Double.class);  // 500.0
}
```

### Context Access

JavaScript code accesses the workflow context through a global `ctx` object (proxy to `WorkflowContext`):

```javascript
// Get values from context
const value = ctx.get('key');

// Put values into context (automatically converted from JS to Java)
ctx.put('result', { name: 'John', age: 30 });
ctx.put('numbers', [1, 2, 3, 4, 5]);
ctx.put('computed', someValue * 2);
```

**Type Conversion:**
- JavaScript objects → Java `Map<String, Object>`
- JavaScript arrays → Java `List<Object>`  
- JavaScript primitives → Java primitives
- Nested structures are recursively converted
- Conversion ensures data remains accessible after script execution

### File-Based Script

```java
// Load script from file
Path scriptPath = Path.of("scripts/discount.js");
FileScriptProvider provider = new FileScriptProvider(scriptPath);

JavascriptWorkflow workflow = JavascriptWorkflow.builder()
    .name("ApplyDiscount")
    .scriptProvider(provider)
    .build();

// Content of scripts/discount.js:
// var amount = context.get("orderTotal");
// if (amount > 1000) {
//     context.put("discountCode", "VIP_PROMO");
//     context.put("discountPercent", 15);
// } else if (amount > 500) {
//     context.put("discountCode", "REGULAR_PROMO");
//     context.put("discountPercent", 10);
// }
```

### Real-World Examples

#### Dynamic Pricing Rules

```java
// Pricing rules that can be updated without deployment
ScriptProvider pricingRules = new FileScriptProvider(
    Path.of("rules/pricing.js")
);

JavascriptWorkflow pricingWorkflow = JavascriptWorkflow.builder()
    .name("DynamicPricing")
    .scriptProvider(pricingRules)
    .build();

// rules/pricing.js:
// var basePrice = context.get('basePrice');
// var customer = context.get('customer');
// var season = context.get('season');
//
// var multiplier = 1.0;
// if (customer.tier === 'PREMIUM') multiplier *= 0.9;
// if (season === 'HOLIDAY') multiplier *= 1.2;
//
// context.put('finalPrice', Math.round(basePrice * multiplier * 100) / 100);
```

#### Order Risk Scoring

```java
public void example() {
    // Complex risk assessment logic in JavaScript
    ScriptProvider riskEngine = () -> """
            var order = JSON.parse(context.get('orderJson'));
            var riskScore = 0;
            
            // High value orders
            if (order.total > 5000) riskScore += 10;
            
            // International shipping
            if (order.shipping.country !== 'US') riskScore += 5;
            
            // New customer
            if (order.customer.yearsActive < 1) riskScore += 15;
            
            // Multiple payment attempts
            if (order.paymentAttempts > 1) riskScore += 20;
            
            var action = riskScore > 20 ? 'MANUAL_REVIEW' : 'AUTO_APPROVE';
            
            context.put('riskScore', riskScore);
            context.put('action', action);
            context.put('reasons', getRiskReasons(order, riskScore));
            
            function getRiskReasons(order, score) {
                var reasons = [];
                if (order.total > 5000) reasons.push('High value order');
                if (order.shipping.country !== 'US') reasons.push('International shipping');
                if (order.customer.yearsActive < 1) reasons.push('New customer');
                return reasons;
            }
            """;

    JavascriptWorkflow riskWorkflow = JavascriptWorkflow.builder()
            .name("OrderRiskAssessment")
            .scriptProvider(riskEngine)
            .build();

    context.put("orderJson", orderJsonString);
    riskWorkflow.execute(context);

    Integer riskScore = context.getTyped("riskScore", Integer.class);
    String action = context.getTyped("action", String.class);
}
```

#### Data Transformation and Filtering

```java
// Transform and filter complex data structures
ScriptProvider dataTransform = () -> """
    var apiResponse = JSON.parse(context.get('apiResponse'));
    
    // Filter active users
    var activeUsers = apiResponse.users.filter(u => u.status === 'ACTIVE');
    
    // Transform to simplified format
    var transformed = activeUsers.map(user => ({
        id: user.userId,
        name: user.profile.fullName,
        email: user.contact.primaryEmail,
        permissions: user.roles.flatMap(r => r.permissions)
    }));
    
    // Calculate statistics
    var stats = {
        totalUsers: apiResponse.users.length,
        activeUsers: activeUsers.length,
        inactiveUsers: apiResponse.users.length - activeUsers.length,
        avgPermissions: transformed.reduce((sum, u) =>
            sum + u.permissions.length, 0) / transformed.length
    };
    
    context.put('transformedUsers', transformed);
    context.put('statistics', stats);
    """;

JavascriptWorkflow transformWorkflow = JavascriptWorkflow.builder()
    .name("DataTransformation")
    .scriptProvider(dataTransform)
    .build();
```

#### Multi-Tenant Configuration

```java
// Tenant-specific rules loaded dynamically
public JavascriptWorkflow createTenantWorkflow(String tenantId) {
    ScriptProvider tenantRules = new FileScriptProvider(
        Path.of("tenants/" + tenantId + "/rules.js")
    );
    
    return JavascriptWorkflow.builder()
        .name("TenantRules-" + tenantId)
        .scriptProvider(tenantRules)
        .build();
}

// Each tenant has their own rules file
// tenants/acme/rules.js - ACME Corp specific rules
// tenants/globex/rules.js - Globex Corp specific rules
```

#### A/B Test Logic

```java
// A/B test logic that can be modified without deployment
ScriptProvider abTestLogic = () -> """
    var userId = context.get('userId');
    var experimentId = context.get('experimentId');
    
    // Deterministic assignment based on user ID
    var hash = userId.split('').reduce((h, c) =>
        ((h << 5) - h) + c.charCodeAt(0), 0);
    var variant = Math.abs(hash) % 100 < 50 ? 'A' : 'B';
    
    context.put('variant', variant);
    context.put('experimentAssignment', {
        userId: userId,
        experimentId: experimentId,
        variant: variant,
        timestamp: new Date().toISOString()
    });
    """;

JavascriptWorkflow abTestWorkflow = JavascriptWorkflow.builder()
    .name("ABTestAssignment")
    .scriptProvider(abTestLogic)
    .build();
```

### Integration with Other Workflows

#### Sequential Composition

```java
Workflow pipeline = SequentialWorkflow.builder()
    .name("OrderProcessingPipeline")
    .task(validateOrderTask)
    .workflow(JavascriptWorkflow.builder()
        .name("CalculatePricing")
        .scriptProvider(pricingScriptProvider)
        .build())
    .workflow(JavascriptWorkflow.builder()
        .name("ApplyPromotions")
        .scriptProvider(promotionScriptProvider)
        .build())
    .task(saveOrderTask)
    .build();
```

#### Conditional with JavaScript

```java
// Use JavaScript for complex condition evaluation
JavascriptWorkflow conditionEvaluator = JavascriptWorkflow.builder()
    .name("EvaluateCondition")
    .scriptProvider(() -> """
        var order = context.get('order');
        var result = order.total > 1000 &&
                     order.items.length > 5 &&
                     order.customer.tier === 'PREMIUM';
        context.put('shouldProcess', result);
        """)
    .build();

Workflow conditionalWorkflow = SequentialWorkflow.builder()
    .workflow(conditionEvaluator)
    .workflow(ConditionalWorkflow.builder()
        .condition(ctx -> ctx.getTyped("shouldProcess", Boolean.class))
        .onTrue(premiumProcessingWorkflow)
        .onFalse(standardProcessingWorkflow)
        .build())
    .build();
```

#### Dynamic Branching with JavaScript

```java
// JavaScript determines which branch to execute
JavascriptWorkflow routingLogic = JavascriptWorkflow.builder()
    .name("DetermineRoute")
    .scriptProvider(() -> """
        var request = context.get('request');
        var route;
        
        if (request.priority === 'URGENT' && request.amount < 10000) {
            route = 'express';
        } else if (request.documentType === 'CONTRACT') {
            route = 'legal';
        } else if (request.requiresApproval) {
            route = 'approval';
        } else {
            route = 'standard';
        }
        
        context.put('selectedRoute', route);
        """)
    .build();

Workflow dynamicWorkflow = SequentialWorkflow.builder()
    .workflow(routingLogic)
    .workflow(DynamicBranchingWorkflow.builder()
        .selector(ctx -> ctx.getTyped("selectedRoute", String.class))
        .branch("express", expressWorkflow)
        .branch("legal", legalWorkflow)
        .branch("approval", approvalWorkflow)
        .branch("standard", standardWorkflow)
        .build())
    .build();
```

### ECMAScript Module (ESM) Support

JavaScriptWorkflow fully supports ES6 modules with import/export syntax, enabling modular, reusable JavaScript code.

#### Module File Extensions

Use `.mjs` extension for module files to indicate ESM format:

```
project/
├── scripts/
│   ├── main.mjs           # Entry point
│   ├── lib/
│   │   ├── utils.mjs      # Utility module
│   │   └── validation.mjs # Validation module
│   └── business/
│       └── pricing.mjs    # Business logic module
```

#### Basic Module Import

**Utility Module (lib/math.mjs):**
```javascript
// Export named functions
export function add(a, b) {
    return a + b;
}

export function multiply(a, b) {
    return a * b;
}

// Export constants
export const PI = 3.14159;
```

**Main Script (main.mjs):**
```javascript
import { add, multiply, PI } from './lib/math.mjs';

const x = ctx.get('x');
const y = ctx.get('y');

ctx.put('sum', add(x, y));
ctx.put('product', multiply(x, y));
ctx.put('circleArea', PI * x * x);
```

**Java Code:**
```java
public void example() {
    Path mainScript = Path.of("scripts/main.mjs");
    JavascriptWorkflow workflow = JavascriptWorkflow.builder()
            .name("ModularCalculator")
            .scriptProvider(new FileScriptProvider(mainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("x", 5);
    context.put("y", 3);

    workflow.execute(context);
}
```

#### Import Syntax Variations

**Named Imports:**
```javascript
import { function1, function2 } from './module.mjs';
```

**Namespace Import:**
```javascript
import * as Utils from './utils.mjs';
Utils.function1();
Utils.function2();
```

**Named with Alias:**
```javascript
import { longFunctionName as fn } from './module.mjs';
```

**Mixed Imports:**
```javascript
import defaultExport, { named1, named2 } from './module.mjs';
```

#### Nested Module Dependencies

Modules can import other modules, creating dependency chains:

**Infrastructure Layer (infrastructure/dbConfig.mjs):**
```javascript
export const dbHost = 'localhost';
export const dbPort = 5432;
export const dbName = 'app_db';
```

**Repository Layer (infrastructure/repository.mjs):**
```javascript
import { dbHost, dbPort, dbName } from './dbConfig.mjs';

export function getConnectionString() {
    return `postgresql://${dbHost}:${dbPort}/${dbName}`;
}

export function fetchData(query) {
    return {
        connection: getConnectionString(),
        query: query,
        results: [/* ... */]
    };
}
```

**Service Layer (service.mjs):**
```javascript
import { getConnectionString, fetchData } from './infrastructure/repository.mjs';

const query = ctx.get('query');
const data = fetchData(query);

ctx.put('connectionString', getConnectionString());
ctx.put('results', data);
```

#### Shared Library Pattern

Create reusable modules shared across multiple workflows:

**Shared Library (lib/validation.mjs):**
```javascript
export function isValidEmail(email) {
    return email && email.includes('@');
}

export function isNotEmpty(value) {
    return value !== null && value !== undefined && value !== '';
}
```

**Workflow 1 (validateUser.mjs):**
```javascript
import { isValidEmail, isNotEmpty } from './lib/validation.mjs';

const user = ctx.get('user');
const errors = [];

if (!isNotEmpty(user.name)) errors.push('Name required');
if (!isValidEmail(user.email)) errors.push('Invalid email');

ctx.put('validationErrors', errors);
```

**Workflow 2 (validateOrder.mjs):**
```javascript
import { isNotEmpty } from './lib/validation.mjs';

const order = ctx.get('order');
const errors = [];

if (!isNotEmpty(order.customerId)) errors.push('Customer ID required');

ctx.put('validationErrors', errors);
```

Both workflows share the same validation module without code duplication.

#### Barrel Exports (Index Pattern)

Use `index.mjs` files to aggregate exports:

**utils/arrayUtils.mjs:**
```javascript
export function first(arr) { return arr[0]; }
export function last(arr) { return arr[arr.length - 1]; }
```

**utils/objectUtils.mjs:**
```javascript
export function isEmpty(obj) { return Object.keys(obj).length === 0; }
export function merge(obj1, obj2) { return { ...obj1, ...obj2 }; }
```

**utils/index.mjs (Barrel):**
```javascript
export * from './arrayUtils.mjs';
export * from './objectUtils.mjs';
```

**Main Script:**
```javascript
// Import everything from barrel
import { first, last, isEmpty, merge } from './utils/index.mjs';

const arr = ctx.get('array');
ctx.put('firstElement', first(arr));
ctx.put('lastElement', last(arr));
```

#### Circular Module References

GraalVM handles circular dependencies gracefully:

**moduleA.mjs:**
```javascript
import { valueB } from './moduleB.mjs';

export const valueA = 'A';

export function getCombined() {
    return valueA + valueB;
}
```

**moduleB.mjs:**
```javascript
import { valueA } from './moduleA.mjs';

export const valueB = 'B';
```

This works because modules are linked before execution. However, avoid calling functions during module initialization.

#### Module Resolution Rules

1. **Relative Imports**: Resolved relative to the importing file
   ```javascript
   import { fn } from './module.mjs';        // Same directory
   import { fn } from '../utils/module.mjs'; // Parent directory
   import { fn } from './lib/module.mjs';    // Subdirectory
   ```

2. **URI-Based Resolution**: Script provider must provide a valid URI
   ```
   // FileScriptProvider automatically provides file:// URI
   new FileScriptProvider(Path.of("scripts/main.mjs"))
   
   // InlineScriptProvider needs explicit URI for imports
   new InlineScriptProvider(scriptContent, baseUri)
   ```

3. **File Extensions Required**: Always include `.mjs` extension
   ```javascript
   import { fn } from './module.mjs'; // ✓ Correct
   import { fn } from './module';     // ✗ Wrong
   ```

#### Best Practices for ESM Modules

1. **Organize by Function**: Group related functionality in modules
   ```
   lib/
   ├── validation/     # Validation logic
   ├── formatting/     # Data formatting
   └── business/       # Business rules
   ```

2. **Use Barrel Exports**: Simplify imports with index files
3. **Keep Modules Focused**: One responsibility per module
4. **Export Named Functions**: Prefer named exports over default exports
5. **Document Dependencies**: Comment import statements with purpose
6. **Version Modules**: Use directory structure for versioning if needed

#### Real-World ESM Example: E-Commerce Pricing

**lib/pricing.mjs:**
```javascript
export function calculateSubtotal(items) {
    return items.reduce((sum, item) => sum + (item.price * item.quantity), 0);
}

export function applyDiscount(amount, percent) {
    return amount * (1 - percent / 100);
}

export function calculateTax(amount, rate) {
    return amount * (rate / 100);
}
```

**lib/customerTiers.mjs:**
```javascript
import { applyDiscount, calculateTax } from './pricing.mjs';

const TIERS = {
    'BASIC': { discount: 0, taxRate: 8.5 },
    'SILVER': { discount: 5, taxRate: 8.5 },
    'GOLD': { discount: 10, taxRate: 8.5 },
    'PLATINUM': { discount: 15, taxRate: 7.5 }
};

export function getPricingForTier(tier) {
    return TIERS[tier] || TIERS['BASIC'];
}

export function calculateFinalPrice(subtotal, tier) {
    const pricing = getPricingForTier(tier);
    const discounted = applyDiscount(subtotal, pricing.discount);
    const tax = calculateTax(discounted, pricing.taxRate);
    
    return {
        subtotal,
        discount: subtotal - discounted,
        tax,
        total: discounted + tax
    };
}
```

**main.mjs:**
```javascript
import { calculateSubtotal } from './lib/pricing.mjs';
import { calculateFinalPrice } from './lib/customerTiers.mjs';

const order = ctx.get('order');
const customerTier = ctx.get('customerTier');

const subtotal = calculateSubtotal(order.items);
const pricing = calculateFinalPrice(subtotal, customerTier);

ctx.put('pricing', pricing);
ctx.put('total', pricing.total);
```

**Java Workflow:**
```java
public void example() {
    JavascriptWorkflow pricingWorkflow = JavascriptWorkflow.builder()
            .name("CalculatePricing")
            .scriptProvider(new FileScriptProvider(Path.of("scripts/main.mjs")))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("order", order);
    context.put("customerTier", "GOLD");

    pricingWorkflow.execute(context);
    Map<String, Object> pricing = context.getTyped("pricing", Map.class);
}
```

### Script Reloading and Hot Updates

Scripts are loaded fresh on each execution, enabling hot updates:

```java
public void example() {
    FileScriptProvider provider = new FileScriptProvider(Path.of("rules/business.js"));
    JavascriptWorkflow workflow = JavascriptWorkflow.builder()
            .name("BusinessRules")
            .scriptProvider(provider)
            .build();

    // First execution - uses current script
    workflow.execute(context1);

    // Update the script file externally
    Files.writeString(Path.of("rules/business.js"), newScript);

    // Second execution - automatically uses updated script
    workflow.execute(context2);
}
```

### Security Considerations

The JavascriptWorkflow runs in a secure sandbox:

- **No Host Class Access**: Scripts cannot access `java.lang.System`, `java.lang.Runtime`, or use reflection
- **No File System Access**: Scripts cannot read/write files directly (except through provided APIs)
- **No Network Access**: Scripts cannot make HTTP requests directly
- **Context-Only Communication**: All data exchange happens through `WorkflowContext`

```java
// This will FAIL - security violation
ScriptProvider malicious = () -> """
    var System = Java.type('java.lang.System');
    System.exit(1);  // Not allowed
    """;
```

### Performance Considerations

- **Shared Engine**: All JavascriptWorkflow instances share a GraalVM engine for optimal performance
- **JIT Compilation**: Frequently executed scripts are compiled to native code
- **AST Caching**: Parsed scripts are cached for faster subsequent executions
- **First Execution Cost**: Initial script execution includes engine warmup time
- **Script Complexity**: Complex scripts with heavy computation may impact performance

### Error Handling

```java
public void example() {
    JavascriptWorkflow workflow = JavascriptWorkflow.builder()
            .name("RiskyOperation")
            .scriptProvider(riskyScriptProvider)
            .build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == WorkflowStatus.FAILED) {
        Throwable error = result.getError();

        if (error.getCause() instanceof ScriptLoadException) {
            // Script file not found or couldn't be loaded
            log.error("Failed to load script", error);
        } else {
            // JavaScript runtime error (syntax, runtime exception, etc.)
            log.error("JavaScript execution failed", error);
        }
    }
}
```

### Best Practices

1. **Keep Scripts Focused**: Each script should have a single, clear responsibility
2. **Use File-Based Scripts**: Store complex logic in files for better maintainability
3. **Validate Inputs**: Check context inputs at the start of scripts
4. **Error Messages**: Provide clear error messages in scripts
5. **Testing**: Test scripts independently before integrating into workflows
6. **Documentation**: Document expected context inputs and outputs
7. **Versioning**: Version your script files for audit trails
8. **Type Safety**: Use TypeScript definitions for better IDE support (optional)

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
9. **JavaScript Workflow**: Dynamic script-based business logic

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
- **Dynamic business rules** → JavaScript Workflow
