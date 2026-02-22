# Workflow Engine

> [!IMPORTANT]
> Note: Codebase is generated/implemented by Copilot, Claude and Gemini AI Tools

A flexible workflow orchestration library for Java that enables you to build complex business processes with ease. Built on top of modern Java features and GraalVM, it provides both compile-time type safety and runtime flexibility.

## Overview

The Workflow Engine is a comprehensive orchestration framework that allows you to:

- **Compose Complex Workflows**: Build sophisticated business processes by composing simple, reusable workflow components
- **Execute Dynamic Logic**: Run JavaScript-based business rules that can be updated without recompiling
- **Handle Failures Gracefully**: Built-in retry policies, fallback mechanisms, and circuit breakers
- **Control Execution**: Rate limiting, timeouts, parallel execution with pluggable strategies
- **Monitor and Debug**: Comprehensive logging, workflow listeners, and execution tracing
- **Integrate Seamlessly**: Spring Boot autoconfiguration, annotation-based workflows, database-driven configuration

## Key Features

### 🎯 **Multiple Workflow Types**
- **Sequential**: Execute workflows one after another
- **Parallel**: Run workflows concurrently with configurable strategies
- **Conditional**: Branch based on runtime conditions
- **Dynamic Branching**: Multi-way routing with selector functions
- **Repeat**: Execute workflow a fixed number of times with iteration tracking
- **ForEach**: Iterate over collections and process each item
- **Fallback**: Graceful error recovery with primary/fallback pattern
- **Saga**: Distributed transactions with compensating actions for rollback
- **Rate Limited**: Control execution frequency with multiple algorithms
- **Timeout**: Add time constraints to any workflow
- **Chaos**: Resilience testing with controlled failure injection
- **JavaScript**: Execute dynamic business logic with full ESM support

### 🚀 **JavaScript Workflow with ESM Support**
- **GraalVM-Powered**: High-performance JavaScript execution with JIT compilation
- **ES6 Modules**: Full support for import/export syntax
- **Secure Sandbox**: Restricted execution environment for safety
- **Flexible Script Sources**: Load from files, classpath, or inline
- **Hot Reload**: Update scripts without restarting the application

### 🔧 **Rich Task Library**
- HTTP tasks (GET, POST, PUT, DELETE)
- JDBC database operations (Query, Update, Batch, Callable, Streaming, Typed, Transactions)
- File I/O operations (Read, Write)
- Shell command execution
- Logging with configurable levels (TRACE, DEBUG, INFO, WARN, ERROR)
- Timing control (Delays, Rate limiting)
- Conditional execution
- Composite and Parallel task execution

### 🛡️ **Resilience Patterns**
- Retry policies (fixed, exponential backoff, jitter)
- Timeout policies
- Fallback workflows
- Rate limiting (fixed window, sliding window, token bucket, leaky bucket, Resilience4j, Bucket4j)

### 📊 **Observability**
- Workflow lifecycle listeners
- Detailed execution logging
- Visual workflow tree rendering

### 🏗️ **Framework Integration**
- Annotation-based workflow definition
- Database-driven workflow configuration
- Spring Boot auto-configuration
- Dependency injection support

## Prerequisites

- **Java 25 or higher** (Required)
- **Maven 3.9+** for dependency management

## Quick Start

### Simple Sequential Workflow

```java
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.*;
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
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.GetHttpTask;

import java.net.http.HttpClient;

public class ApiAggregator {
    public void aggregateData() {
        HttpClient client = HttpClient.newHttpClient();

        Workflow parallelWorkflow = ParallelWorkflow.builder()
                .name("DataAggregation")
                .task(new GetHttpTask.Builder<>(client)
                        .url("https://api.example.com/users")
                        .responseContextKey("usersData")
                        .build())
                .task(new GetHttpTask.Builder<>(client)
                        .url("https://api.example.com/orders")
                        .responseContextKey("ordersData")
                        .build())
                .task(new GetHttpTask.Builder<>(client)
                        .url("https://api.example.com/products")
                        .responseContextKey("productsData")
                        .build())
                .failFast(true)
                .build();

        WorkflowContext context = new WorkflowContext();
        WorkflowResult result = parallelWorkflow.execute(context);
    }
}
```

### Conditional Workflow

```java
public class OrderProcessor {
    public Workflow buildOrderWorkflow(Workflow expensiveFlow, Workflow standardFlow) {
        return ConditionalWorkflow.builder()
                .name("OrderProcessing")
                .condition(context -> {
                    Integer amount = context.getTyped("orderAmount", Integer.class);
                    return amount != null && amount > 1000;
                })
                .whenTrue(expensiveFlow)
                .whenFalse(standardFlow)
                .build();
    }
}
```

### Saga Workflow

```java
import com.workflow.SagaWorkflow;
import com.workflow.SagaStep;

public class OrderSagaExample {
    public Workflow buildOrderSaga() {
        return SagaWorkflow.builder()
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
                        .name("ChargePayment")
                        .action(ctx -> {
                            String orderId = ctx.getTyped("orderId", String.class);
                            String txnId = paymentService.charge(orderId);
                            ctx.put("transactionId", txnId);
                        })
                        .compensation(ctx -> {
                            String txnId = ctx.get("transactionId");
                            if (txnId != null) {
                                paymentService.refund(txnId);
                            }
                        })
                        .build())
                .build();
    }
}
```

### JDBC Database Operations

```java
import com.workflow.*;
import com.workflow.task.*;
import com.workflow.context.WorkflowContext;
import javax.sql.DataSource;
import java.util.*;

public class DatabaseWorkflowExample {
    public void runDatabaseWorkflow() {
        DataSource dataSource = createDataSource(); // Your connection pool
        
        // Query task
        JdbcQueryTask queryTask = JdbcQueryTask.builder()
                .dataSource(dataSource)
                .readingSqlFrom("sql")
                .readingParamsFrom("params")
                .writingResultsTo("results")
                .build();
        
        // Update task
        JdbcUpdateTask updateTask = JdbcUpdateTask.builder()
                .dataSource(dataSource)
                .readingSqlFrom("updateSql")
                .readingParamsFrom("updateParams")
                .writingRowsAffectedTo("rowsAffected")
                .build();
        
        // Build workflow
        Workflow workflow = SequentialWorkflow.builder()
                .name("UserDataPipeline")
                .task(context -> {
                    context.put("sql", "SELECT * FROM users WHERE status = ?");
                    context.put("params", List.of("ACTIVE"));
                })
                .task(queryTask)
                .task(context -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> users = 
                        (List<Map<String, Object>>) context.get("results");
                    // Process users...
                    Integer userId = (Integer) users.getFirst().get("id");
                    context.put("updateSql", "UPDATE users SET last_login = NOW() WHERE id = ?");
                    context.put("updateParams", Collections.singletonList(userId));
                })
                .task(updateTask)
                .build();
        
        WorkflowContext context = new WorkflowContext();
        workflow.execute(context);
    }
    
    private DataSource createDataSource() {
        // Implementation using HikariCP or other connection pool
        return null; // Placeholder
    }
}
```

### JavaScript Workflow

```java
import com.workflow.JavascriptWorkflow;
import com.workflow.script.FileScriptProvider;
import com.workflow.context.WorkflowContext;
import org.graalvm.polyglot.Context;
import java.nio.file.Path;

public class JavaScriptExample {
    public void runJavaScriptWorkflow() {
        // Create workflow from JavaScript file
        Workflow workflow = JavascriptWorkflow.builder()
            .name("DynamicPricing")
            .scriptProvider(new FileScriptProvider(Path.of("rules/pricing.js")))
            .context(Context.newBuilder("js")
                    .allowAllAccess(false)
                    .build())
            .build();

        WorkflowContext context = new WorkflowContext();
        context.put("basePrice", 100.0);
        context.put("customerTier", "GOLD");
        
        workflow.execute(context);
        
        Double finalPrice = context.getTyped("finalPrice", Double.class);
        System.out.println("Final price: " + finalPrice);
    }
}
```

### ESM Module Support

```javascript
// lib/pricing.mjs
export function calculateDiscount(price, tier) {
    const discounts = {
        'BASIC': 0,
        'SILVER': 0.05,
        'GOLD': 0.10,
        'PLATINUM': 0.15
    };
    return price * (1 - discounts[tier]);
}

// main.mjs
import { calculateDiscount } from './lib/pricing.mjs';

const price = ctx.get('basePrice');
const tier = ctx.get('customerTier');
const final = calculateDiscount(price, tier);

ctx.put('finalPrice', final);
```

### With Retry and Timeout

```java
import com.workflow.policy.*;
import com.workflow.task.*;

import java.net.http.HttpClient;

public class ResilientWorkflowExample {
    public Workflow buildResilientTask(HttpClient client) {
        TaskDescriptor taskWithPolicies = TaskDescriptor.builder()
                .task(new PostHttpTask.Builder<>(client)
                        .url("https://api.example.com/process")
                        .build())
                .retryPolicy(RetryPolicy.exponentialBackoff(3, 1000))
                .timeoutPolicy(TimeoutPolicy.ofSeconds(30))
                .build();

        return new TaskWorkflow(taskWithPolicies);
    }
}
```

### With Rate Limiting

```java
import com.workflow.ratelimit.*;
import java.time.Duration;

public class RateLimitedApiExample {
    public Workflow buildRateLimitedWorkflow(Workflow apiWorkflow) {
        // Limit API calls to 100 per minute
        RateLimitStrategy limiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));

        return RateLimitedWorkflow.builder()
                .name("RateLimitedAPI")
                .workflow(apiWorkflow)
                .rateLimitStrategy(limiter)
                .build();
    }
    
    public Workflow buildTokenBucketWorkflow(Workflow apiWorkflow) {
        // Token Bucket for burst support
        RateLimitStrategy tokenBucket = new TokenBucketRateLimiter(
                100,                        // 100 requests per second
                200,                        // Burst capacity of 200
                Duration.ofSeconds(1)
        );

        return RateLimitedWorkflow.builder()
                .workflow(apiWorkflow)
                .rateLimitStrategy(tokenBucket)
                .build();
    }
    
    public Workflow buildResilience4jWorkflow(Workflow apiWorkflow) {
        // Resilience4j for production-ready rate limiting
        RateLimitStrategy resilience4j = new Resilience4jRateLimiter(
                100,                        // 100 requests per second
                Duration.ofSeconds(1)       // Refresh period
        );

        return RateLimitedWorkflow.builder()
                .workflow(apiWorkflow)
                .rateLimitStrategy(resilience4j)
                .build();
    }
}
```

### Chaos Engineering for Resilience Testing

```java
import com.workflow.*;
import com.workflow.chaos.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.ChaosException;

public class ChaosTestingExample {
    public void testWithFailureInjection() {
        // Create unreliable service (30% failure rate)
        Workflow unreliableService = ChaosWorkflow.builder()
                .name("UnreliableAPI")
                .workflow(apiCallWorkflow)
                .strategy(FailureInjectionStrategy.builder()
                        .probability(0.3)
                        .build())
                .build();

        // Test with retry policy
        TaskDescriptor resilientTask = TaskDescriptor.builder()
                .task(new ExecuteWorkflowTask(unreliableService))
                .retryPolicy(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)))
                .build();
                
        WorkflowResult result = new TaskWorkflow(resilientTask).execute(context);
        // Should eventually succeed despite chaos
    }
    
    public void testWithLatencyInjection() {
        // Simulate slow service
        Workflow slowService = ChaosWorkflow.builder()
                .workflow(databaseWorkflow)
                .strategy(LatencyInjectionStrategy.builder()
                        .minDelayMs(500)
                        .maxDelayMs(2000)
                        .probability(0.5)  // 50% of requests are slow
                        .build())
                .build();

        // Test timeout handling
        Workflow timedWorkflow = TimeoutWorkflow.builder()
                .workflow(slowService)
                .timeoutMs(1000)
                .build();
                
        WorkflowResult result = timedWorkflow.execute(context);
        // Some executions will timeout
    }
    
    public void testFallbackMechanism() {
        // Primary service with high failure rate
        Workflow unreliablePrimary = ChaosWorkflow.builder()
                .workflow(primaryService)
                .strategy(FailureInjectionStrategy.withProbability(0.7))
                .build();

        // Should frequently use fallback
        Workflow resilient = FallbackWorkflow.builder()
                .primary(unreliablePrimary)
                .fallback(backupService)
                .build();
                
        for (int i = 0; i < 100; i++) {
            WorkflowResult result = resilient.execute(context);
            // System remains available despite chaos
            assertTrue(result.getStatus() == WorkflowStatus.SUCCESS);
        }
    }
    
    public void testMultipleChaosStrategies() {
        // Combine multiple chaos strategies
        Workflow chaosWorkflow = ChaosWorkflow.builder()
                .workflow(serviceWorkflow)
                .strategy(LatencyInjectionStrategy.withFixedDelay(100))
                .strategy(FailureInjectionStrategy.withProbability(0.2))
                .strategy(ExceptionInjectionStrategy.builder()
                        .exceptionSupplier(() -> new IllegalStateException("Chaos!"))
                        .probability(0.1)
                        .build())
                .build();
                
        // Test system under complex failure scenarios
        WorkflowResult result = chaosWorkflow.execute(context);
    }
}
```

### With Workflow Listeners

```java
import lombok.extern.slf4j.Slf4j;
import com.workflow.listener.*;

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
    public void visualizeWorkflow(Workflow step1, Workflow step2a, 
                                  Workflow step2b, Workflow step3) {
        // Visualize workflow hierarchy
        Workflow workflow = SequentialWorkflow.builder()
                .name("ComplexPipeline")
                .workflow(step1)
                .workflow(ParallelWorkflow.builder()
                        .name("ParallelTasks")
                        .workflow(step2a)
                        .workflow(step2b)
                        .build())
                .workflow(step3)
                .build();

        // Print tree structure
        System.out.println(workflow.toTreeString());
        /* Output:
        └── ComplexPipeline [Sequence]
            ├── Step1 (Task)
            ├── ParallelTasks [Parallel]
            │   ├── Step2A (Task)
            │   └── Step2B (Task)
            └── Step3 (Task)
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

### Saga Workflow
Implements the Saga pattern for distributed transactions with compensating actions.

```java
import com.workflow.SagaWorkflow;
import com.workflow.SagaStep;
import com.workflow.helper.Workflows;

public class SagaWorkflowFactory {
    public Workflow buildDistributedTransaction() {
        return SagaWorkflow.builder()
                .name("PaymentSaga")
                .step(SagaStep.builder()
                        .name("ReserveInventory")
                        .action(reserveInventoryWorkflow)
                        .compensation(releaseInventoryWorkflow)
                        .build())
                .step(SagaStep.builder()
                        .name("ProcessPayment")
                        .action(chargePaymentWorkflow)
                        .compensation(refundPaymentWorkflow)
                        .build())
                .step(SagaStep.builder()
                        .name("SendConfirmation")
                        .action(sendEmailWorkflow)
                        // No compensation needed for read-only operations
                        .build())
                .build();
    }
    
    // Using convenience methods
    public Workflow buildSagaWithTasks() {
        return Workflows.saga("SimpleSaga")
                .step(actionTask, compensationTask)  // Using tasks
                .step(ctx -> performWork(ctx),       // Using lambdas
                      ctx -> rollbackWork(ctx))
                .build();
    }
}
```

**Key Features:**
- **Compensating Actions**: Automatic rollback on failure
- **Backward Recovery**: Executes compensations in reverse order
- **Failure Context**: Access failure information during compensation
- **Flexible Steps**: Support for tasks, workflows, or lambda functions
- **Partial Compensation**: Steps without side effects don't need compensation

For complete Saga workflow documentation, see [SAGA.md](docs/SAGA.md).

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

### Repeat Workflow
Executes a child workflow a fixed number of times with index tracking.

```java
public class RepeatPatternExample {
    public Workflow buildRetryLoop(Workflow task) {
        return RepeatWorkflow.builder()
                .name("RetryLoop")
                .times(3)
                .indexVariable("attempt")
                .workflow(task)
                .build();
    }
}
```

**Features:**
- Fixed iteration count
- Optional index variable binding (default: "iteration")
- Fails fast on first failure
- Thread-safe execution

### ForEach Workflow
Iterates over a collection and executes a child workflow for each item.

```java
public class ForEachPatternExample {
    public Workflow buildBatchProcessor(Workflow processItem) {
        return ForEachWorkflow.builder()
                .name("ProcessAllItems")
                .itemsKey("itemList")
                .itemVariable("currentItem")
                .indexVariable("itemIndex")
                .workflow(processItem)
                .build();
    }
}
```

**Features:**
- Collection iteration (List, Set, Array, etc.)
- Per-item variable binding
- Optional index tracking
- Fails fast on first failure
- Handles null/empty collections gracefully
- Thread-safe execution

## Built-in Tasks

The framework provides a rich set of pre-built tasks. For complete documentation, see [TASKS.md](docs/TASKS.md).

### HTTP Tasks
- `GetHttpTask` - HTTP GET requests
- `PostHttpTask` - HTTP POST requests
- `PutHttpTask` - HTTP PUT requests
- `DeleteHttpTask` - HTTP DELETE requests

### File Tasks
- `FileReadTask` - Read file contents
- `FileWriteTask` - Write data to files

### Database Tasks
- `JdbcQueryTask` - Execute SQL SELECT queries
- `JdbcUpdateTask` - Execute SQL INSERT/UPDATE/DELETE statements
- `JdbcBatchUpdateTask` - Execute batch SQL updates
- `JdbcTypedQueryTask` - Type-safe query results
- `JdbcStreamingQueryTask` - Stream large result sets
- `JdbcCallableTask` - Execute stored procedures
- `JdbcTransactionTask` - Transactional task execution

### Utility Tasks
- `DelayTask` - Introduce delays
- `NoOpTask` - No operation (testing)

### Processing Tasks
- `ShellCommandTask` - Execute shell commands

## Rate Limiting Strategies

The framework provides six rate limiting algorithms. For complete documentation, see [RATE_LIMITING.md](docs/RATE_LIMITING.md).

### Fixed Window
Simple time-window based limiting. Best for basic use cases.

```java
public class RateLimitExample {
    public RateLimitStrategy createFixedWindow() {
        return new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
    }
}
```

### Sliding Window
Accurate rate limiting with no boundary effects. Best for strict rate limiting.

```java
public class RateLimitExample {
    public RateLimitStrategy createSlidingWindow() {
        return new SlidingWindowRateLimiter(100, Duration.ofMinutes(1));
    }
}
```

### Token Bucket
Allows controlled bursts while maintaining average rate. Best for APIs that support bursts.

```java
public class RateLimitExample {
    public RateLimitStrategy createTokenBucket() {
        return new TokenBucketRateLimiter(
                100,                        // Tokens per second
                200,                        // Burst capacity
                Duration.ofSeconds(1)
        );
    }
}
```

### Leaky Bucket
Ensures constant output rate. Best for steady-state processing.

```java
public class RateLimitExample {
    public RateLimitStrategy createLeakyBucket() {
        return new LeakyBucketRateLimiter(
                100,                        // Requests per second
                Duration.ofSeconds(1)
        );
    }
}
```

### Resilience4j Rate Limiter
Production-ready rate limiting using the Resilience4j library.

```java
import io.github.resilience4j.ratelimiter.*;

public class RateLimitExample {
    public RateLimitStrategy createResilience4j() {
        // Basic usage
        return new Resilience4jRateLimiter(
                100,                        // Requests per second
                Duration.ofSeconds(1)       // Refresh period
        );
    }
    
    public RateLimitStrategy createCustomResilience4j() {
        // With custom configuration
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return new Resilience4jRateLimiter("myAPI", config);
    }
}
```

**Features:**
- Battle-tested in production environments
- Event monitoring and metrics support
- Integration with Micrometer and Prometheus
- Thread-safe with minimal overhead
- Supports dynamic configuration

### Bucket4j Rate Limiter
High-performance rate limiting using the Bucket4j library.

```java
import io.github.bucket4j.*;

public class RateLimitExample {
    public RateLimitStrategy createBucket4j() {
        // Basic usage
        return new Bucket4jRateLimiter(
                100,                        // Capacity
                Duration.ofSeconds(1)       // Refill period
        );
    }
    
    public RateLimitStrategy createBucket4jWithBurst() {
        // With burst capacity
        return new Bucket4jRateLimiter(
                200,                        // Burst capacity
                100,                        // Refill tokens per period
                Duration.ofSeconds(1),      // Refill period
                Bucket4jRateLimiter.RefillStrategy.GREEDY
        );
    }
}
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

Configure workflows dynamically from database. For complete documentation, see [DATABASE_CONFIGURATION.md](docs/DATABASE_CONFIGURATION.md).

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
public class DatabaseWorkflowExample {
    public Workflow loadFromDatabase(DataSource dataSource, WorkflowRegistry registry) {
        DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);
        return processor.buildWorkflow("DataPipeline");
    }
}
```

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
import com.workflow.context.*;
import tools.jackson.core.type.TypeReference;

public class WorkflowDataHandler {
    // Constants (TypedKeys) can be declared at the class level
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
        List<User> retrieved = context.getTyped("users", new TypeReference<>() {});

        // 6. Scoped contexts (namespace isolation)
        WorkflowContext userScope = context.scope("user");
        userScope.put("id", 123); // Actually stored in 'context' as "user.id"
    }
}
```

## Execution Strategies

### Thread Pool Strategy (Default)
```java
import com.workflow.execution.strategy.*;

public class WorkflowConfiguration {
    public Workflow buildManagedParallelWorkflow(Workflow taskA, Workflow taskB) {
        ExecutionStrategy strategy = new ThreadPoolExecutionStrategy();

        // Build the workflow using that strategy
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
import com.workflow.execution.strategy.*;

public class ReactiveWorkflowConfig {
    public Workflow buildReactiveParallelWorkflow(Workflow task1, Workflow task2) {
        ExecutionStrategy strategy = new ReactorExecutionStrategy();

        // Build the workflow
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

Add observability and custom actions without modifying workflow code. For complete documentation, see [WORKFLOW_LISTENERS.md](docs/WORKFLOW_LISTENERS.md).

```java
import com.workflow.listener.*;

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

The framework provides comprehensive utility classes to simplify common operations. For complete documentation, see [HELPERS.md](docs/HELPERS.md).

### Workflows Helper
Factory methods for creating workflow builders:

```java
import com.workflow.helper.Workflows;
import com.workflow.task.Task;

public class WorkflowFactory {
    public Workflow createSequential(Task task1, Task task2) {
        return Workflows.sequential("MyWorkflow")
                .task(task1)
                .task(task2)
                .build();
    }

    public Workflow createParallel(Workflow workflow1, Workflow workflow2) {
        return Workflows.parallel("ParallelTasks")
                .workflow(workflow1)
                .workflow(workflow2)
                .build();
    }
}
```

### ValidationUtils
Comprehensive validation helpers:

```java
import com.workflow.helper.ValidationUtils;
import com.workflow.task.Task;
import java.util.List;

public class WorkflowValidator {
    public void validate(Workflow workflow, List<Task> tasks, int retries, int timeout) {
        ValidationUtils.requireNonNull(workflow, "workflow");
        ValidationUtils.requireNonEmpty(tasks, "task list");
        ValidationUtils.requirePositive(timeout, "timeout");
        ValidationUtils.requireInRange(retries, 0, 10, "retry count");
    }
}
```

### ResponseMappers
HTTP response mapping utilities:

```java
import com.workflow.helper.ResponseMappers;

public class ResponseHandler {
    public void handleResponses() {
        var mapper = ResponseMappers.strictTypedMapper(User.class);
        var defaultMapper = ResponseMappers.defaultResponseMapper();
        var wrapped = ResponseMappers.wrapToTaskException(customMapper);
    }
}
```

### WorkflowResults
Convenient result creation:

```java
import com.workflow.helper.WorkflowResults;
import java.time.Instant;

public class ResultBuilder {
    public WorkflowResult buildResults(Instant startedAt, Instant completedAt, Throwable error) {
        WorkflowResult success = WorkflowResults.success(startedAt, completedAt);
        WorkflowResult failure = WorkflowResults.failure(startedAt, completedAt, error);
        return success;
    }
}
```

## Examples

The framework includes comprehensive examples demonstrating various patterns in the `src/examples` directory:

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

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) - System design and component relationships
- [Getting Started Guide](docs/GETTING_STARTED.md) - Quick start and common patterns
- [Task Reference](docs/TASKS.md) - Complete task documentation
- [Workflow Guide](docs/WORKFLOWS.md) - Detailed workflow documentation
- [Saga Pattern Guide](docs/SAGA.md) - Distributed transactions with compensating actions
- [Helper Utilities](docs/HELPERS.md) - Utility classes and helpers
- [Rate Limiting Guide](docs/RATE_LIMITING.md) - Rate limiting strategies and usage
- [Workflow Listeners Guide](docs/WORKFLOW_LISTENERS.md) - Event-driven monitoring
- [Database Configuration](docs/DATABASE_CONFIGURATION.md) - Database-based workflow configuration

## Building

```bash
# Build project
mvn clean install

# Run tests
mvn test

# Generate Javadoc
mvn javadoc:javadoc

# View Javadoc
open target/site/apidocs/index.html

# Run tests with coverage
mvn clean test jacoco:report

# Check test coverage report
open target/site/jacoco/index.html

# Check code style
mvn spotless:check

# Apply formatting
mvn spotless:apply
```

### Run Examples

```bash
# Run with Maven exec plugin and examples profile
mvn exec:java -Pexamples -Dexec.mainClass="com.workflow.examples.DataPipelineWorkflow"
```
