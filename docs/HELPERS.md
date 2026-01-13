# Workflow Engine - Helper Utilities Reference

## Table of Contents
- [Overview](#overview)
- [Workflow Builder Utilities](#workflow-builder-utilities)
- [Validation Utilities](#validation-utilities)
- [Context Helpers](#context-helpers)
- [JSON Utilities](#json-utilities)
- [Future Utilities](#future-utilities)
- [Response Mappers](#response-mappers)
- [Workflow Results](#workflow-results)
- [Workflow Support](#workflow-support)
- [HTTP Response Wrapper](#http-response-wrapper)
- [Tree Renderer](#tree-renderer)
- [Sleeper Implementations](#sleeper-implementations)
- [Policy Helpers](#policy-helpers)
- [Registry](#registry)

## Overview

The Workflow Engine provides a comprehensive set of helper classes and utilities to simplify common tasks like workflow creation, validation, JSON processing, asynchronous operations, HTTP response handling, workflow visualization, and more.

## Workflow Builder Utilities

### Workflows

Factory class providing convenient methods for creating workflow builders with minimal boilerplate.

#### Methods

```java
// Sequential workflow builder
public static SequentialWorkflowBuilder sequential(String name);

// Parallel workflow builder
public static ParallelWorkflowBuilder parallel(String name);

// Conditional workflow builder
public static ConditionalWorkflowBuilder conditional(String name);

// Dynamic branching workflow builder
public static DynamicBranchingWorkflowBuilder dynamic(String name);

// Fallback workflow builder
public static FallbackWorkflowBuilder fallback(String name);

// Rate-limited workflow builder
public static RateLimitedWorkflowBuilder rateLimited(String name);

// Timeout workflow builder
public static TimeoutWorkflowBuilder timeout(String name);
```

#### Examples

**Concise Workflow Creation**:

```java
import static com.workflow.helper.Workflows.*;

public class WorkflowFactory {
    public Workflow createDataPipeline() {
        // Before: Verbose
        Workflow oldStyle = SequentialWorkflow.builder()
            .name("Pipeline")
            .task(task1)
            .task(task2)
            .build();
        
        // After: Concise with static import
        return sequential("Pipeline")
            .task(task1)
            .task(task2)
            .build();
    }
    
    public Workflow createParallelProcessor() {
        return parallel("ParallelTasks")
            .workflow(apiCall1)
            .workflow(apiCall2)
            .workflow(apiCall3)
            .failFast(true)
            .build();
    }
    
    public Workflow createConditionalFlow() {
        return conditional("UserCheck")
            .condition(ctx -> ctx.getTyped("isAdmin", Boolean.class))
            .whenTrue(adminWorkflow)
            .whenFalse(userWorkflow)
            .build();
    }
}
```

**Chaining Multiple Workflow Types**:

```java
public Workflow createComplexPipeline() {
    return sequential("MainPipeline")
        .workflow(parallel("DataFetch")
            .workflow(fetchUsers)
            .workflow(fetchOrders)
            .build())
        .workflow(conditional("Validation")
            .condition(ctx -> isDataValid(ctx))
            .whenTrue(processWorkflow)
            .whenFalse(errorWorkflow)
            .build())
        .build();
}
```

## Validation Utilities

### ValidationUtils

Utility class for common validation operations with consistent error messages.

#### Methods

```java
// Null checks
public static <T> T requireNonNull(T obj, String paramName);
public static <T> T requireNonNull(T obj, Supplier<String> messageSupplier);

// Collection/Map validation
public static <T extends Collection<?>> T requireNonEmpty(T collection, String paramName);
public static <T extends Map<?, ?>> T requireNonEmpty(T map, String paramName);

// String validation
public static String requireNonBlank(String str, String paramName);

// Numeric validation
public static long requirePositive(long value, String paramName);
public static long requireNonNegative(long value, String paramName);
public static long requireInRange(long value, long min, long max, String paramName);
public static double requireInRange(double value, double min, double max, String paramName);

// Conditional validation
public static void requireAtLeastOne(String errorMessage, Object... objects);
public static void require(boolean condition, String errorMessage);
public static void require(boolean condition, Supplier<String> messageSupplier);
```

#### Examples

**Basic Validation**:

```java
public class WorkflowBuilder {
    public Workflow buildWorkflow(String name, List<Task> tasks) {
        // Validate inputs
        ValidationUtils.requireNonBlank(name, "workflow name");
        ValidationUtils.requireNonEmpty(tasks, "task list");
        
        return SequentialWorkflow.builder()
            .name(name)
            .tasks(tasks)
            .build();
    }
}
```

**Numeric Validation**:

```java
public class RateLimiterConfig {
    public RateLimiter create(int requestsPerSecond, int burstCapacity) {
        ValidationUtils.requirePositive(requestsPerSecond, "requestsPerSecond");
        ValidationUtils.requireInRange(burstCapacity, 1, 10000, "burstCapacity");
        
        return new TokenBucketRateLimiter(requestsPerSecond, burstCapacity, Duration.ofSeconds(1));
    }
}
```

**Custom Message with Supplier**:

```java
public class WorkflowValidator {
    public void validate(Workflow workflow) {
        ValidationUtils.requireNonNull(
            workflow,
            () -> "Workflow is required for " + getOperationName()
        );
        
        ValidationUtils.require(
            workflow.getName() != null,
            () -> String.format("Workflow %s must have a name", workflow.getClass().getSimpleName())
        );
    }
}
```

**Conditional Validation**:

```java
public class WorkflowConfiguration {
    public void configure(Workflow primary, Workflow fallback, Workflow alternative) {
        // At least one workflow must be provided
        ValidationUtils.requireAtLeastOne(
            "At least one workflow must be provided",
            primary, fallback, alternative
        );
    }
}
```

## Context Helpers

### WorkflowContext

Thread-safe key-value store for shared workflow state.

#### Basic Operations

```java
import java.util.List;

public class ContextHandler {
    public void processContext(List<Order> ordersList) {
        // 1. Initialize the context inside the method
        WorkflowContext context = new WorkflowContext();

        // 2. Store values
        context.put("userId", 12345);
        context.put("userName", "john_doe");
        context.put("orderList", ordersList);

        // 3. Retrieve values (standard Object return)
        Object value = context.get("userId");
        Object valueWithDefault = context.get("userName", "unknown");

        // 4. Type-safe retrieval (Removes the need for manual casting)
        Integer userId = context.getTyped("userId", Integer.class);
        String userName = context.getTyped("userName", String.class);

        // Note: Generic types like List require careful handling or a TypeReference
        List<Order> orders = context.getTyped("orderList", List.class);

        // 5. Using defaults for missing keys
        Integer timeout = context.getTyped("timeout", Integer.class, 5000);

        // 6. Check existence
        boolean hasUser = context.containsKey("userId");
    }
}
```

#### Type-Safe Keys

```java
import java.util.List;

public class UserWorkflowManager {

    // 1. Define typed keys as static constants at the class level
    private static final TypedKey<Integer> USER_ID = TypedKey.of("userId", Integer.class);
    private static final TypedKey<String> USER_NAME = TypedKey.of("userName", String.class);
    // Note: For complex generics like List, ensure your library supports List.class or use TypeReference
    private static final TypedKey<List<Order>> ORDERS = TypedKey.of("orders", List.class);

    public void processUserData(WorkflowContext context, List<Order> ordersList) {
        // 2. Use typed keys inside a method
        context.put(USER_ID, 12345);
        context.put(USER_NAME, "john_doe");
        context.put(ORDERS, ordersList);

        // 3. Retrieve with full type safety (no casting needed!)
        Integer userId = context.get(USER_ID);
        String userName = context.get(USER_NAME);
        List<Order> orders = context.get(ORDERS);

        System.out.println("Processing user: " + userName);
    }
}
```

**Benefits**:
- Compile-time type checking
- Refactoring-friendly
- Self-documenting
- IDE autocomplete support

#### Jackson's TypeReference<T> Support

For complete type safety with generic types:

```java
import java.util.List;
import java.util.Map;
import com.workflow.WorkflowContext;
import com.workflow.TypeReference;

public class WorkflowDataService {
    public void processComplexData() {
        WorkflowContext ctx = new WorkflowContext();

        // 1. Handling Lists with TypeReference
        List<User> users = List.of(new User("Alice"), new User("Bob"));
        ctx.put("users", users);

        // Retrieve while preserving the <User> generic
        List<User> retrieved = ctx.getTyped("users", new TypeReference<List<User>>() {});
        System.out.println(retrieved.getFirst().getName());

        // 2. Handling defaults for complex types
        List<User> fallback = ctx.getTyped("missingUsers", new TypeReference<List<User>>() {}, List.of());
        System.out.println("Is empty: " + fallback.isEmpty());

        // 3. Handling deeply nested types (e.g., Map of Lists)
        Map<String, List<Order>> ordersByUser = Map.of("Alice", List.of(new Order(101)));
        ctx.put("orderMap", ordersByUser);

        Map<String, List<Order>> retrievedMap = ctx.getTyped("orderMap",
                new TypeReference<Map<String, List<Order>>>() {});
    }
}
```

**Why Use TypeReference?**

```java
// Problem: Generic type erasure
List<User> users = ctx.getTyped("users", List.class); // Returns List<Object>
User user = users.getFirst(); // ClassCastException at runtime!

// Solution: TypeReference preserves generic type
List<User> users = ctx.getTyped("users", new TypeReference<List<User>>() {});
User user = users.getFirst(); // Safe!
```

#### Workflow Listeners Integration

Access the listener registry from context:

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObservedWorkflowRunner {
    public void runWithMonitoring(Workflow workflow) {
        // 2. Initialize context inside the method
        WorkflowContext context = new WorkflowContext();

        // 3. Register the listener (Anonymous Inner Class)
        context.getListeners().register(new WorkflowListener() {
            @Override
            public void onStart(String name, WorkflowContext ctx) {
                log.info("Started: {}", name);
            }

            @Override
            public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
                log.info("Completed in: {}ms", result.getDuration());
            }

            @Override
            public void onFailure(String name, WorkflowContext ctx, Throwable error) {
                log.error("Failed: {}", error.getMessage());
            }
        });

        // 4. Execute the workflow
        workflow.execute(context);
    }
}
```

See [WORKFLOW_LISTENERS.md](WORKFLOW_LISTENERS.md) for complete documentation.

#### Context Scoping

Create namespaced contexts to avoid key collisions:

```java
public class ScopedWorkflowRunner {
    public void initializeScopedData() {
        // 1. Initialize main context
        WorkflowContext context = new WorkflowContext();

        // 2. Create scoped contexts (Namespacing)
        WorkflowContext userScope = context.scope("user");
        WorkflowContext orderScope = context.scope("order");
        WorkflowContext paymentScope = context.scope("payment");

        // 3. Write data through the scopes
        userScope.put("id", 123);           // Internally: "user.id"
        userScope.put("name", "John");      // Internally: "user.name"
        orderScope.put("id", 456);          // Internally: "order.id"
        paymentScope.put("status", "paid"); // Internally: "payment.status"

        // 4. Verification: The main context holds everything with prefixes
        System.out.println("User ID from root: " + context.get("user.id"));
    }
}
```

**Use Cases**:
- Parallel workflow isolation
- Logical grouping of related data
- Preventing key collisions in complex workflows
- Debugging (clear namespace structure)

**Example - Complex Workflow**:

```java
import java.util.List;

public class OrderProcessingService {
    public void initializeContext() {
        // 1. Create the root context
        WorkflowContext context = new WorkflowContext();
        context.put("orderId", "ORD-123");

        // 2. Create sub-scopes (Namespacing)
        WorkflowContext userValidation = context.scope("validation.user");
        userValidation.put("valid", true);
        userValidation.put("errors", List.of());

        WorkflowContext orderValidation = context.scope("validation.order");
        orderValidation.put("valid", true);
        orderValidation.put("itemCount", 5);

        // 3. Create sibling scope
        WorkflowContext payment = context.scope("payment");
        payment.put("method", "credit_card");
        payment.put("amount", 99.99);

        // Verification: Accessing data from the root using dot-notation
        System.out.println("Payment Amount: " + context.get("payment.amount"));
    }
}
```

#### Context Copying

```java
public class WorkflowIsolationManager {
    public void executeIsolatedWorkflows(WorkflowContext context, Workflow w1, Workflow w2) {
        // 1. Shallow copy: Creates a new context with the same data references
        WorkflowContext shallowCopy = context.copy();

        // 2. Filtered copy: Creates a new context containing only specific data
        WorkflowContext userKeys = context.copy(
                key -> key.startsWith("user.")
        );

        // 3. Parallel Execution with Isolation
        // By setting shareContext(false), the engine internally calls .copy()
        // for each branch so they don't overwrite each other's data.
        Workflow parallelFetch = ParallelWorkflow.builder()
                .name("IsolatedParallelTasks")
                .shareContext(false)
                .workflow(w1)
                .workflow(w2)
                .build();

        parallelFetch.execute(context);
    }
}
```

### ScopedWorkflowContext

Automatically prefixes all keys with a namespace.

```java
public class PaymentService {
    public void processPayment() {
        // 1. Root context
        WorkflowContext mainContext = new WorkflowContext();

        // 2. Wrap root context in a scope (Must be inside a method)
        ScopedWorkflowContext scoped = new ScopedWorkflowContext(mainContext, "payment");

        // 3. Perform prefixed operations
        scoped.put("status", "completed");  // In mainContext: "payment.status"
        scoped.put("amount", 100.00);       // In mainContext: "payment.amount"

        // 4. Retrieve data (The scope automatically prepends the prefix)
        String status = scoped.getTyped("status", String.class);

        System.out.println("Payment status: " + status);
    }
}
```

### TypedKey

Type-safe context keys for compile-time validation.

```java
public record TypedKey<T>(String name, Class<T> type) {
    public static <T> TypedKey<T> of(String name, Class<T> type) {
        return new TypedKey<>(name, type);
    }
}

// Define keys as constants
public class ContextKeys {
    public static final TypedKey<User> USER = 
        TypedKey.of("user", User.class);
    public static final TypedKey<List<Order>> ORDERS = 
        TypedKey.of("orders", List.class);
    public static final TypedKey<PaymentStatus> PAYMENT_STATUS = 
        TypedKey.of("paymentStatus", PaymentStatus.class);
}

public class UserWorkflowProcessor {
    public void processUser(WorkflowContext context, User currentUser) {
        context.put(ContextKeys.USER, currentUser);
        User user = context.get(ContextKeys.USER);
        System.out.println("Processing: " + user.getName());
    }
}
```

## JSON Utilities

### JsonUtils

Utility class for JSON serialization and deserialization using Jackson.

#### Configuration

- **Lenient Parsing**: Does not fail on unknown properties
- **Null Handling**: Returns null for null/empty input
- **Thread-Safe**: Shared Jackson mapper

#### Methods

```java
// Deserialize JSON to object
<T> T fromJson(String json, Class<T> clazz) throws IOException;

// Serialize object to JSON
String toJson(Object obj) throws IOException;
```

#### Examples

**Basic Deserialization**:

```java
public class JsonMappingExample {
    public void runExample() {
        // 1. Define the raw JSON (Text blocks require Java 15+)
        String jsonStr = """
                {
                    "id": 123,
                    "name": "John Doe",
                    "email": "john@example.com",
                    "age": 30
                }
                """;

        // 2. Map JSON to a Java Object (POJO)
        // Ensure you have a User class with fields: id, name, email, age
        User user = JsonUtils.fromJson(jsonStr, User.class);

        // 3. Use the object
        if (user != null) {
            System.out.println(user.getName()); // "John Doe"
        }
    }
}
```

**Basic Serialization**:

```java
public class JsonSerializationRunner {
    public void convertUserToJson() {
        // 1. Initialize the object (Inside a method)
        User user = new User(123, "John Doe", "john@example.com", 30);

        // 2. Convert the object to a JSON string
        String json = JsonUtils.toJson(user);

        // 3. Print the result
        System.out.println(json); // {"id":123,"name":"John Doe","email":"john@example.com","age":30}
    }
}
```

**In Task Implementation**:

```java
public class JsonTransformTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
        try {
            // Deserialize input
            String rawJson = context.getTyped("rawJson", String.class);
            User user = JsonUtils.fromJson(rawJson, User.class);
            
            // Transform
            user.setProcessed(true);
            user.setProcessedAt(Instant.now());
            
            // Serialize output
            String processedJson = JsonUtils.toJson(user);
            context.put("processedJson", processedJson);
            
        } catch (IOException e) {
            throw new TaskExecutionException("JSON processing failed", e);
        }
    }
}
```

**Handling Lists**:

```java
String jsonArray = """
    [
        {"id": 1, "name": "Alice"},
        {"id": 2, "name": "Bob"},
        {"id": 3, "name": "Charlie"}
    ]
    """;

// Use Jackson's TypeReference for generic types
TypeReference<List<User>> typeRef = new TypeReference<>() {};
List<User> users = JsonUtils.fromJson(jsonArray, typeRef);
```

**Error Handling**:

```java
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;

@Slf4j
public class JsonHandler {
    public void processUserData(String invalidJson) {
        // 2. The try-catch block must reside inside a method
        try {
            User user = JsonUtils.fromJson(invalidJson, User.class);
            log.info("Successfully parsed user: {}", user.getName());
        } catch (IOException e) {
            // 3. Error handling logic
            log.error("Failed to parse JSON: {}", invalidJson, e);

            // Optional: Re-throw as a custom exception or return a default object
            throw new RuntimeException("Data corruption detected", e);
        }
    }
}
```

**Null Safety**:

```java
String result = JsonUtils.toJson(null);     // Returns null
User user = JsonUtils.fromJson("", User.class);  // Returns null
User user2 = JsonUtils.fromJson(null, User.class); // Returns null
```

## Future Utilities

### FutureUtils

Utilities for working with `CompletableFuture` in parallel workflows.

#### allOf Method

Combines multiple futures with optional fail-fast behavior.

```java
public static CompletableFuture<Void> allOf(
    List<CompletableFuture<?>> futures,
    boolean failFast
);
```

**Parameters**:
- `futures`: List of futures to wait for
- `failFast`: If true, cancel all futures on first failure

**Return**: CompletableFuture that completes when all complete (or first fails if fail-fast)

#### cancelFuture Method

Safely cancels a future without throwing exceptions.

```java
public static void cancelFuture(Future<?> future);
```

**Features**:
- Null-safe: handles null futures gracefully
- Done-check: skips already completed futures
- Exception-safe: swallows all exceptions
- Interruption: passes `true` to `mayInterruptIfRunning`

**Example**:

```java
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.*;

@Slf4j
public class AsyncWorkflowRunner {
    public void executeAsyncWithTimeout() {
        // 1. Start the async task
        CompletableFuture<WorkflowResult> future =
                CompletableFuture.supplyAsync(this::longRunningTask);

        try {
            // 2. Wait for the result with a strict timeout
            WorkflowResult result = future.get(10, TimeUnit.SECONDS);
            log.info("Task completed: {}", result);

        } catch (TimeoutException e) {
            // 3. Handle the timeout and clean up
            // Using a utility to interrupt the thread and free up resources
            boolean cancelled = future.cancel(true);
            log.warn("Task timed out after 10s. Cancelled: {}", cancelled);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Task failed or was interrupted", e);
            Thread.currentThread().interrupt(); // Restore interrupt status
        }
    }

    private WorkflowResult longRunningTask() {
        // Simulation of heavy work
        return new WorkflowResult();
    }
}
```

**Use Cases**:
- Timeout handling in TimeoutWorkflow
- Cleanup in finally blocks
- Fail-fast cancellation in ParallelWorkflow
- Resource cleanup on workflow interruption

#### Examples

**Wait for All (Default)**:

```java
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ParallelWorkflowRunner {
    public void runWorkflowsInParallel(ExecutionStrategy executionStrategy, WorkflowContext context) {
        // 1. Collect all futures into a list
        List<CompletableFuture<WorkflowResult>> futures = List.of(
                executionStrategy.submit(() -> workflow1.execute(context)),
                executionStrategy.submit(() -> workflow2.execute(context)),
                executionStrategy.submit(() -> workflow3.execute(context))
        );

        // 2. Use a utility to wait for all (false = don't fail-fast, wait for all to finish)
        CompletableFuture<Void> allOf = FutureUtils.allOf(futures, false);

        // 3. Register a callback (using Java 21+ underscore for unused parameters)
        allOf.whenComplete((unused, ex) -> {
            if (ex != null) {
                log.error("One or more workflows failed", ex);
            } else {
                log.info("All workflows completed successfully");
            }
        });

        // 4. Block the current thread until all are done
        allOf.join();
    }
}
```

**Fail-Fast Mode**:

```java
public void executeWithFailFast() {
    CompletableFuture<Void> allOf = FutureUtils.allOf(futures, true);

    // If any future fails, all others are canceled immediately
    allOf.exceptionally(ex -> {
        log.error("Workflow failed fast", ex);
        return null;
    });
}
```

**Safe Cancellation**:

```java
public void executeWithStrictTimeout() {
    List<CompletableFuture<WorkflowResult>> futures = startParallelWorkflows();
    try {
        FutureUtils.allOf(futures, false).get(30, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        // Cancel all futures safely
        futures.forEach(FutureUtils::cancelFuture);
        log.warn("Parallel execution timed out, all futures cancelled");
    }
}
```

## Response Mappers

### ResponseMappers

Utilities for mapping HTTP responses with comprehensive error handling and type safety.

#### Methods

```java
// Strict typed mapper - checks HTTP status and deserializes JSON
public static <T> Function<HttpResponse<String>, T> strictTypedMapper(Class<T> responseType);

// Default response mapper - returns body as String
public static <T> Function<HttpResponse<String>, T> defaultResponseMapper();

// Default typed mapper - deserializes JSON without status check
public static <T> Function<HttpResponse<String>, T> defaultTypedResponseMapper(Class<T> responseType);

// Wrap mapper to convert exceptions to TaskExecutionException
public static <T> Function<HttpResponse<String>, T> wrapToTaskException(
    Function<HttpResponse<String>, T> mapper
);

// Legacy methods for backward compatibility
public static Consumer<HttpResponseWrapper<?>> toContextKey(
    WorkflowContext context,
    String contextKey
);

public static <T> Consumer<HttpResponseWrapper<T>> withMapper(
    Function<T, ?> mapper,
    WorkflowContext context,
    String contextKey
);
```

#### Examples

**Simple Response Mapping**:

```java
public void handleApiResponse(WorkflowContext context) {
    // In HTTP task
    HttpResponseWrapper<String> response = executeRequest();
    ResponseMappers.toContextKey(context, "apiResponse")
            .accept(response);

    // Response body now in context
    String responseBody = context.getTyped("apiResponse", String.class);
}
```

**Custom Mapping**:

```java
public void processApiResponse(WorkflowContext context, HttpResponseWrapper<String> response) {
    // Map and transform response
    ResponseMappers.withMapper(
            jsonStr -> JsonUtils.fromJson(jsonStr, User.class),
            context,
            "userData"
    ).accept(response);

    // Transformed object in context
    User user = context.getTyped("userData", User.class);
}
```

**Extracting Specific Fields**:

```java
public void extractUserId(WorkflowContext context, HttpResponseWrapper<String> response) {
    // Extract just the user ID from response
    ResponseMappers.withMapper(
            userJson -> {
                User user = JsonUtils.fromJson(userJson, User.class);
                return user.getId();
            },
            context,
            "userId"
    ).accept(response);

    Integer userId = context.getTyped("userId", Integer.class);
}
```

**Error Handling in Mapper**:

```java
public void parseUser(WorkflowContext context, HttpResponseWrapper<String> response) {
    ResponseMappers.withMapper(
            jsonStr -> {
                try {
                    return JsonUtils.fromJson(jsonStr, User.class);
                } catch (IOException e) {
                    log.error("Failed to parse user JSON", e);
                    return null;
                }
            },
            context,
            "userData"
    ).accept(response);
}
```

#### Examples

**Strict Typed Mapper (Recommended for Production)**:

```java
public class ApiTaskWithValidation extends AbstractHttpTask<User> {
    @Override
    protected HttpRequest buildRequest(WorkflowContext context) {
        return HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/users/123"))
            .build();
    }
    
    @Override
    protected Function<HttpResponse<String>, User> getResponseMapper() {
        // Automatically checks HTTP status and deserializes
        // Throws HttpResponseProcessingException on non-2xx
        // Throws JsonProcessingException on invalid JSON
        return ResponseMappers.strictTypedMapper(User.class);
    }
}
```

**Default Response Mapper (Simple Cases)**:

```java
public class SimpleGetHttpTask extends AbstractHttpTask<String> {
    @Override
    protected Function<HttpResponse<String>, String> getResponseMapper() {
        // Just returns the body as-is, no validation
        return ResponseMappers.defaultResponseMapper();
    }
}
```

**Wrapping Custom Mappers**:

```java
public class CustomMappingTask extends AbstractHttpTask<ProcessedData> {
    @Override
    protected Function<HttpResponse<String>, ProcessedData> getResponseMapper() {
        Function<HttpResponse<String>, ProcessedData> customMapper = response -> {
            // Custom processing logic
            String body = response.body();
            if (body.contains("error")) {
                throw new RuntimeException("Custom error detected");
            }
            return new ProcessedData(body);
        };
        
        // Wrap to ensure all exceptions become TaskExecutionException
        return ResponseMappers.wrapToTaskException(customMapper);
    }
}
```

**Error Handling Examples**:

```java
public void demonstrateErrorHandling() {
    // Strict mapper throws on non-2xx status
    var strictMapper = ResponseMappers.strictTypedMapper(User.class);
    HttpResponse<String> badResponse = createResponse(404, "Not Found");
    
    try {
        User user = strictMapper.apply(badResponse);
    } catch (HttpResponseProcessingException e) {
        log.error("HTTP error: {}", e.getMessage()); // "HTTP 404 returned: Not Found"
    }
    
    // Strict mapper throws on invalid JSON
    HttpResponse<String> invalidJsonResponse = createResponse(200, "{invalid json}");
    
    try {
        User user = strictMapper.apply(invalidJsonResponse);
    } catch (JsonProcessingException e) {
        log.error("JSON error: {}", e.getMessage());
    }
}
```

## Workflow Results

### WorkflowResults

Utility for creating `WorkflowResult` objects with consistent timestamp handling.

#### Methods

```java
// Create success result with timestamps
public static WorkflowResult success(Instant startedAt, Instant completedAt);

// Create failure result with timestamps and error
public static WorkflowResult failure(Instant startedAt, Instant completedAt, Throwable error);
```

#### Examples

**Basic Usage**:

```java
public class CustomWorkflow extends AbstractWorkflow {
    @Override
    protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
        // Using ExecutionContext (preferred)
        try {
            performWork(context);
            return execContext.success();
        } catch (Exception e) {
            return execContext.failure(e);
        }
    }
}
```

**Manual Result Creation**:

```java
public class ManualResultBuilder {
    public WorkflowResult buildResult(boolean success, Throwable error) {
        Instant startedAt = Instant.now().minusSeconds(10);
        Instant completedAt = Instant.now();
        
        if (success) {
            return WorkflowResults.success(startedAt, completedAt);
        } else {
            return WorkflowResults.failure(startedAt, completedAt, error);
        }
    }
}
```

**Result Analysis**:

```java
public void analyzeResults(WorkflowResult result) {
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration()); // "5.123 seconds"
    log.info("Success: {}", result.isSuccess());
    log.info("Failure: {}", result.isFailure());
    
    if (result.isFailure()) {
        log.error("Error: {}", result.getError().getMessage());
    }
}
```

## Workflow Support

### WorkflowSupport

Internal utility class providing common functionality for workflow implementations.

#### Methods

```java
// Resolve workflow name (returns provided name or generates default)
public static String resolveName(String providedName, Workflow workflow);

// Check if workflow list is empty
public static boolean isEmptyWorkflowList(Collection<?> workflows);

// Format workflow type with brackets
public static String formatWorkflowType(String type);

// Format task type with parentheses
public static String formatTaskType(String type);
```

#### Examples

**Name Resolution in Custom Workflows**:

```java
public class CustomWorkflow extends AbstractWorkflow {
    private final String name;
    
    public CustomWorkflow(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        // Returns name if provided, otherwise generates default
        return WorkflowSupport.resolveName(name, this);
    }
}
```

**Empty List Validation**:

```java
public class SequentialWorkflowImpl extends AbstractWorkflow {
    private final List<Workflow> workflows;
    
    @Override
    protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
        // Early return for empty workflows
        if (WorkflowSupport.isEmptyWorkflowList(workflows)) {
            return execContext.success();
        }
        
        // Process workflows...
    }
}
```

**Type Formatting for Tree Rendering**:

```java
public class CustomWorkflow implements Workflow {
    @Override
    public String getWorkflowType() {
        // Formats as "[CustomType]"
        return WorkflowSupport.formatWorkflowType("CustomType");
    }
}

public class CustomTask implements Task {
    public String getTaskType() {
        // Formats as "(CustomTask)"
        return WorkflowSupport.formatTaskType("CustomTask");
    }
}
```

## HTTP Response Wrapper

### HttpResponseWrapper

Wrapper for HTTP responses with typed body.

#### Structure

```java
@Getter
public final class HttpResponseWrapper<T> {
    private final int status;
    private final Map<String, List<String>> headers;
    private final T body;

    public HttpResponseWrapper(int status, Map<String, List<String>> headers, T body) {
        this.status = status;
        this.headers = Map.copyOf(headers);
        this.body = body;
    }
}
```

#### Response Processing

```java
// Extract specific header
String authToken = response.getHeaders()
    .getOrDefault("Authorization", List.of())
    .stream()
    .findFirst()
    .orElse(null);

// Parse JSON body
String jsonBody = response.getBody();
User user = JsonUtils.fromJson(jsonBody, User.class);
```

## Tree Renderer

Utility for rendering workflow hierarchies as ASCII trees.

### Features

- **Hierarchical Visualization**: Shows nested workflow structures
- **Type Metadata**: Displays workflow types ([Sequence], [Parallel], etc.)
- **Clean Output**: ASCII tree format for easy reading
- **Debug Tool**: Perfect for understanding complex compositions

### API

```java
public void visualizeWorkflow(Workflow workflow) {
    // Render workflow tree
    String tree = TreeRenderer.render(workflow);
    System.out.println(tree);

    // Or use convenience method
    String tree = workflow.toTreeString();
}
```

### Examples

**Simple Sequential Workflow**:

```java
public class PipelineRunner {
    static void main(String[] args) {
        Workflow workflow = SequentialWorkflow.builder()
                .name("DataPipeline")
                .task(new ValidationTask())
                .task(new TransformTask())
                .task(new SaveTask())
                .build();

        System.out.println(workflow.toTreeString());
        /* Output:
        └── DataPipeline [Sequence]
            ├── ValidationTask (Task)
            ├── TransformTask (Task)
            └── SaveTask (Task)
        */
    }
}
```

Refer `TreeRendererTest.java` for more examples

### Use Cases

- **Documentation**: Generate workflow documentation
- **Debugging**: Understand workflow structure
- **Validation**: Verify workflow composition

## Sleeper Implementations

### Sleeper Interface

Interface for sleep behavior - useful for testing retry logic.

```java
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
```

### ThreadSleepingSleeper (Default)

Production implementation using `Thread.sleep()`.

```java
public void performDelay() {
    Sleeper sleeper = new ThreadSleepingSleeper();
    sleeper.sleep(1000); // Actual 1-second sleep
}
```

### NoOpSleeper (Testing)

Does nothing - useful for fast tests.

```java
public void doNothing() {
    Sleeper testSleeper = new NoOpSleeper();
    sleeper.sleep(10000); // Returns immediately
}
```

### CountingSleeper (Testing)

Counts sleep calls without actual sleeping.

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryWorkflowTest {
    @Test
    void testRetryCount() {
        // 1. Initialize the mock sleeper
        CountingSleeper counter = new CountingSleeper();

        // 2. Logic that triggers sleeping (e.g., a workflow with 3 retries)
        // This is where you'd call the code you are testing
        simulateRetryLogic(counter);

        // 3. Verify the result
        assertEquals(3, counter.getCount(), "The workflow should have attempted 3 retries");
    }

    private void simulateRetryLogic(CountingSleeper sleeper) {
        for (int i = 0; i < 3; i++) {
            sleeper.sleep(100); // Increments counter without pausing execution
        }
    }
}
```

### JitterSleeper

Adds random jitter to sleep duration.

```java
public void executeWithJitter() {
    // 1. Initialize the JitterSleeper (Inside a method)
    // A 0.2 factor means the actual sleep will be +/- 20% of the target
    Sleeper jitterSleeper = new JitterSleeper(0.2);

    try {
        // 2. Perform the sleep
        // This will result in a duration between 800ms and 1200ms
        jitterSleeper.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.err.println("Jittered sleep interrupted");
    }
}
```

## Policy Helpers

### RetryPolicy Builders

```java
public class WorkflowConfig {
    // 1. Defining policies as reusable constants
    public static final RetryPolicy DEFAULT_RETRY = RetryPolicy.limitedRetries(3);

    public static final RetryPolicy CRITICAL_API_RETRY = RetryPolicy.limitedRetriesWithBackoff(
            5,
            BackoffStrategy.exponentialWithJitter(100, 10000)
    );

    public void configureWorkflow() {
        // 2. Using a policy in a workflow builder (Inside a method)
        Workflow workflow = SequentialWorkflow.builder()
                .name("OrderProcessing")
                .retryPolicy(RetryPolicy.fixedBackoff(3, 1000))
                .task(new PaymentTask())
                .build();
    }
}
```

### TimeoutPolicy Builders

```java
public class WorkflowSettings {
    // 1. Define as constants for reusability across the project
    public static final TimeoutPolicy GLOBAL_TIMEOUT = TimeoutPolicy.ofMinutes(5);
    public static final TimeoutPolicy API_TIMEOUT = TimeoutPolicy.ofSeconds(30);

    public void configureTask(TaskBuilder builder) {
        // 2. Use directly in a configuration call (Inside a method)
        builder.withTimeout(TimeoutPolicy.ofMillis(5000));
    }
}
```

## Registry

### WorkflowRegistry

Registry for managing reusable workflow instances.

#### Methods

```java
// Register workflow
public void register(String name, Workflow workflow);

// Get workflow
public Optional<Workflow> get(String name);

// Check existence
public boolean contains(String name);

// Get all names
public Set<String> getNames();
```

#### Examples

**Basic Registration**:

```java
public void manageWorkflows(WorkflowContext context) {
    WorkflowRegistry registry = new WorkflowRegistry();

    // Register workflows
    Task validationTask = new ValidationTask();
    registry.register("Validation", new TaskWorkflow(validationTask));

    Task processingTask = new ProcessingTask();
    registry.register("Processing", new TaskWorkflow(processingTask));

    // Retrieve workflow
    Optional<Workflow> workflow = registry.get("Validation");
    if (workflow.isPresent()) {
        WorkflowResult result = workflow.get().execute(context);
    }
}
```

**Database Workflow Configuration**:

```java
public Workflow initializeEtlPipeline() {
    // Register base workflows
    WorkflowRegistry registry = new WorkflowRegistry();
    registry.register("ExtractData", extractWorkflow);
    registry.register("TransformData", transformWorkflow);
    registry.register("LoadData", loadWorkflow);

    // Build from database
    DatabaseWorkflowProcessor processor =
            new DatabaseWorkflowProcessor(dataSource, registry);

    // Pipeline built from database, using registered workflows
    return processor.buildWorkflow("ETLPipeline");
}
```

**Spring Integration**:

```java
@Configuration
public class WorkflowConfig {
    
    @Bean
    public WorkflowRegistry workflowRegistry() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register("Validation", validationWorkflow());
        registry.register("Processing", processingWorkflow());
        return registry;
    }
    
    @Bean
    public Workflow validationWorkflow() {
        return SequentialWorkflow.builder()
            .task(new SchemaValidationTask())
            .task(new BusinessRuleValidationTask())
            .build();
    }
}
```

## Best Practices

### 1. Context Key Management

```java
// Define keys as constants
public class ContextKeys {
    // Typed keys for compile-time safety
    public static final TypedKey<User> CURRENT_USER = 
        TypedKey.of("currentUser", User.class);
    public static final TypedKey<List<Order>> ORDERS = 
        TypedKey.of("orders", List.class);
    
    // String keys for simple cases
    public static final String REQUEST_ID = "requestId";
    public static final String TIMESTAMP = "timestamp";
}

public void processUser(WorkflowContext context, User user) {
    context.put(ContextKeys.CURRENT_USER, user);
    User user = context.get(ContextKeys.CURRENT_USER);
}
```

### 2. Use TypeReference for Generic Types

```java
// Good: Type-safe generic retrieval
List<User> users = ctx.getTyped("users", new TypeReference<List<User>>() {});

// Bad: Loses type information
List<User> users = ctx.getTyped("users", List.class); // Runtime error risk
```

### 3. Error Handling

```java
public WorkflowResult execute(WorkflowContext context) {
    // Always handle JSON errors
    try {
        User user = JsonUtils.fromJson(json, User.class);
        context.put("user", user);
    } catch (IOException e) {
        throw new TaskExecutionException("Invalid JSON", e);
    }

    // Validate before using
    String json = context.getTyped("jsonData", String.class);
    if (json == null || json.isEmpty()) {
        throw new TaskExecutionException("JSON data is required");
    }
}
```

### 4. Resource Cleanup

```java
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AsyncWorkflowProcessor {
    public void runParallelTask(WorkflowContext context) {
        // 1. Thread Pool Cleanup Pattern
        ExecutionStrategy strategy = new ThreadPoolExecutionStrategy();
        try {
            ParallelWorkflow workflow = ParallelWorkflow.builder()
                    .executionStrategy(strategy)
                    .build();
            workflow.execute(context);
        } finally {
            // Ensures resources are released even if the workflow crashes
            strategy.shutdown();
        }
    }

    public void safeExecution(Future<?> future, long timeout) {
        // 2. Safe Future Cancellation Pattern
        try {
            future.get(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Task timed out or failed: " + e.getMessage());
        } finally {
            // Ensures the thread is interrupted if the timeout is reached
            FutureUtils.cancelFuture(future);
        }
    }
}
```

### 5. Context Scoping in Complex Workflows

```java
public class WorkflowOrchestrator {
    public void processWorkflow(Workflow userWorkflow, Workflow orderWorkflow) {
        // 1. Initialize the root context
        WorkflowContext context = new WorkflowContext();

        // 2. User processing in isolated scope
        // This creates a logical sub-container for data
        WorkflowContext userScope = context.scope("user");
        userWorkflow.execute(userScope);

        // 3. Order processing in isolated scope
        // Variables put in "orderScope" won't overwrite variables in "userScope"
        WorkflowContext orderScope = context.scope("order");
        orderWorkflow.execute(orderScope);

        // 4. Data is namespaced but accessible
        System.out.println("Main context contains all scoped data.");
    }
}
```

## Summary

The Workflow Engine provides comprehensive helper utilities:

1. **Context Management**: Thread-safe, type-safe, scoped contexts with TypeReference support
2. **JSON Processing**: Simple serialization/deserialization
3. **Async Operations**: Future composition and safe cancellation
4. **HTTP Handling**: Response mapping and processing
5. **Workflow Visualization**: Tree rendering for debugging
6. **Testing Support**: Mock sleepers, counters, no-ops
7. **Policy Management**: Retry and timeout builders
8. **Registry**: Reusable workflow management
9. **Event System**: WorkflowListeners for observability

All helpers follow consistent patterns:
- Thread-safe where appropriate
- Null-safe operations
- Clear error handling
- Easy to test and mock
- Well-documented behavior
