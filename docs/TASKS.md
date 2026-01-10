# Workflow Engine - Task Reference

## Table of Contents
- [Overview](#overview)
- [Task Hierarchy](#task-hierarchy)
- [HTTP Tasks](#http-tasks)
- [File Tasks](#file-tasks)
- [Control Flow Tasks](#control-flow-tasks)
- [Processing Tasks](#processing-tasks)
- [Composite Tasks](#composite-tasks)
- [Utility Tasks](#utility-tasks)
- [Creating Custom Tasks](#creating-custom-tasks)

## Overview

Tasks are the atomic units of work in the Workflow Engine. Each task implements a single responsibility and can be composed into complex workflows. Tasks read inputs from and write outputs to the shared `WorkflowContext`.

### Task Contract

All tasks implement the `Task` interface:

```java
public interface Task {
    void execute(WorkflowContext context) throws TaskExecutionException;
    String getName();
}
```

### Base Classes

- **`AbstractTask`**: Base class providing common task infrastructure
- **`AbstractHttpTask`**: Base class for HTTP tasks with request/response handling

## Task Hierarchy

```
Task (interface)
    │
    ├── AbstractTask (abstract)
    │   ├── ConditionalTask
    │   ├── SwitchTask
    │   ├── CompositeTask
    │   ├── ParallelTask
    │   ├── DelayTask
    │   ├── NoOpTask
    │   ├── RetryingTask
    │   ├── TimedTask
    │   ├── FileReadTask
    │   ├── FileWriteTask
    │   ├── JavaScriptTask
    │   ├── ShellCommandTask
    │   └── AbstractHttpTask (abstract)
    │       ├── GetTask
    │       ├── PostTask
    │       ├── PutTask
    │       └── DeleteTask
    │
    └── Custom implementations
```

## HTTP Tasks

### GetTask

Performs HTTP GET requests with automatic JSON handling.

**Purpose**: Retrieve data from RESTful APIs

**Features**:
- Automatic `Accept: application/json` header
- Type-safe response deserialization
- Query parameter support
- Custom headers

**Builder Configuration**:
```
GetTask.Builder<T>
    .url(String)                    // Target URL
    .urlFromContext(String)         // URL from context key
    .header(String, String)         // Add custom header
    .headers(Map<String, String>)   // Add multiple headers
    .responseType(Class<T>)         // Response type for deserialization
    .responseContextKey(String)     // Context key for response (default: "httpResponse")
    .build()
```

**Examples**:

```java
public void example() {
    HttpClient client = HttpClient.newHttpClient();
    GetTask<String> task = new GetTask.Builder<String>(client)
            .url("https://api.example.com/users")
            .build();

    task.execute(new WorkflowContext());

    String response = context.getTyped("httpResponse", String.class);

    // Typed response with auth
    GetTask<User> task = new GetTask.Builder<User>(client)
            .url("https://api.example.com/users/123")
            .responseType(User.class)
            .header("Authorization", "Bearer " + token)
            .responseContextKey("userData")
            .build();

    User user = context.getTyped("userData", User.class);

   // Dynamic URL from context
    context.put("apiUrl", "https://api.example.com/search");
    context.put("queryParams", Map.of("q", "java", "limit", "10"));

    GetTask<String> task = new GetTask.Builder<String>(client)
            .urlFromContext("apiUrl")
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - Optional `queryParams` (Map<String, String>) for dynamic query parameters
  - Optional URL from context if using `urlFromContext()`
- **Outputs**: 
  - Response stored at configured key (default: "httpResponse")

### PostTask

Performs HTTP POST requests with JSON or form data.

**Purpose**: Create resources or submit data to APIs

**Features**:
- JSON body support
- Form data URL encoding
- Automatic Content-Type header
- Response deserialization

**Builder Configuration**:
```
PostTask.Builder<T>
    .url(String)
    .urlFromContext(String)
    .body(String)                   // JSON body (highest precedence)
    .form(Map<String, String>)      // Form data (URL-encoded)
    .header(String, String)
    .responseType(Class<T>)
    .responseContextKey(String)
    .build()
```

**Body Priority**:
1. Explicit `body()` parameter
2. Context key "REQUEST_BODY"
3. Form data via `form()`
4. Empty body

**Examples**:

```java
public void example() {
    PostTask<ApiResponse> task = new PostTask.Builder<ApiResponse>(client)
            .url("https://api.example.com/users")
            .body("{\"name\":\"John\",\"email\":\"john@example.com\"}")
            .responseType(ApiResponse.class)
            .build();

    // Form submission
    PostTask<String> formTask = new PostTask.Builder<String>(client)
            .url("https://api.example.com/login")
            .form(Map.of(
                    "username", "john",
                    "password", "secret123"
            ))
            .build();

    // Body from context
    context.put("REQUEST_BODY", userJson);
    PostTask<User> task = new PostTask.Builder<User>(client)
            .url("https://api.example.com/users")
            .responseType(User.class)
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - Optional `REQUEST_BODY` (String) for body content
  - Optional URL from context
- **Outputs**: 
  - Response stored at configured key

### PutTask

Performs HTTP PUT requests for updating resources.

**Purpose**: Update existing resources

**Features**: Same as PostTask (JSON body, form data, etc.)

**Examples**:

```java
// Update user
PutTask<User> task = new PutTask.Builder<User>(client)
    .url("https://api.example.com/users/123")
    .body("{\"name\":\"John Updated\"}")
    .responseType(User.class)
    .build();

// Form update
PutTask<String> task = new PutTask.Builder<String>(client)
    .url("https://api.example.com/settings")
    .form(Map.of("theme", "dark", "language", "en"))
    .build();
```

### DeleteTask

Performs HTTP DELETE requests.

**Purpose**: Delete resources

**Features**:
- Simple DELETE operations
- Optional request body
- Response handling

**Examples**:

```java
// Simple delete
DeleteTask<String> task = new DeleteTask.Builder<String>(client)
    .url("https://api.example.com/users/123")
    .build();

// Delete with auth
DeleteTask<ApiResponse> task = new DeleteTask.Builder<ApiResponse>(client)
    .url("https://api.example.com/sessions/" + sessionId)
    .header("Authorization", "Bearer " + token)
    .responseType(ApiResponse.class)
    .build();
```

## File Tasks

### FileReadTask

Reads file contents into context.

**Purpose**: Load data from files

**Features**:
- Path-based file reading
- Encoding support
- UTF-8 default encoding

**Constructor**:
```java
FileReadTask(Path filePath, String contextKey);
```

**Examples**:

```java
// Read JSON file
public void example() {
    FileReadTask task = new FileReadTask(
            Path.of("data/users.json"),
            "userData"
    );

    task.execute(context);
    String jsonData = context.getTyped("userData", String.class);

    // Read configuration
    FileReadTask configTask = new FileReadTask(
            Path.of("config/application.properties"),
            "config"
    );
}
```

**Context Usage**:
- **Inputs**: None
- **Outputs**: 
  - File contents stored at specified context key (String)

**Error Handling**:
- Throws `TaskExecutionException` on:
  - File not found
  - Permission errors
  - I/O errors

### FileWriteTask

Writes context data to files.

**Purpose**: Persist processed data

**Features**:
- Path-based file writing
- Creates parent directories if needed
- UTF-8 encoding
- Overwrites existing files

**Constructor**:
```java
FileWriteTask(Path filePath, String contextKey);
```

**Examples**:

```java
// Write processed data
public void example() {
    context.put("processedData", jsonString);
    FileWriteTask task = new FileWriteTask(
            Path.of("output/results.json"),
            "processedData"
    );

    task.execute(context);

    // Save report
    context.put("report", reportContent);
    FileWriteTask reportTask = new FileWriteTask(
            Path.of("reports/daily-report.txt"),
            "report"
    );
}
```

**Context Usage**:
- **Inputs**: 
  - Data from specified context key (toString() called)
- **Outputs**: None

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Permission errors
  - Disk full
  - I/O errors

## Control Flow Tasks

### ConditionalTask

Conditionally executes a task based on predicate.

**Purpose**: Optional task execution

**Features**:
- Predicate-based branching
- No-op when condition is false
- Exception propagation

**Constructor**:
```java
ConditionalTask(
    Predicate<WorkflowContext> condition,
    Task innerTask
);
```

**Examples**:

```java
// Feature toggle
Task premiumFeature = new PremiumFeatureTask();
Task conditional = new ConditionalTask(
    ctx -> Boolean.TRUE.equals(ctx.get("isPremiumUser")),
    premiumFeature
);

// Only process if data exists
Task processing = new DataProcessingTask();
Task conditionalProcess = new ConditionalTask(
    ctx -> ctx.containsKey("rawData"),
    processing
);

// Conditional validation
Task validation = new ValidationTask();
Task conditionalValidation = new ConditionalTask(
    ctx -> "strict".equals(ctx.get("validationMode")),
    validation
);
```

**Context Usage**:
- **Inputs**: Read by predicate and inner task
- **Outputs**: Modified by inner task if condition is true

**Best Practices**:
- Keep predicates simple and fast
- Predicates should be read-only (no context mutation)
- Consider using for feature flags

### SwitchTask

Multi-way branching based on selector function.

**Purpose**: Select one of many tasks based on context state

**Features**:
- Multiple branches
- Default branch fallback
- Case-insensitive matching
- Fast case lookup

**Builder Configuration**:
```
SwitchTask.builder()
    .selector(Function<WorkflowContext, String>)  // Selector function
    .branch(String key, Task task)                // Add branch
    .defaultBranch(Task task)                     // Fallback task
    .build()
```

**Examples**:

```java
// Processing mode switch
SwitchTask task = SwitchTask.builder()
    .selector(ctx -> ctx.getTyped("mode", String.class))
    .branch("fast", new FastProcessingTask())
    .branch("accurate", new AccurateProcessingTask())
    .branch("balanced", new BalancedProcessingTask())
    .defaultBranch(new StandardProcessingTask())
    .build();

// Payment method routing
SwitchTask paymentRouter = SwitchTask.builder()
    .selector(ctx -> ctx.getTyped("paymentMethod", String.class))
    .branch("credit_card", new CreditCardTask())
    .branch("paypal", new PayPalTask())
    .branch("bitcoin", new BitcoinTask())
    .defaultBranch(new UnsupportedPaymentTask())
    .build();

// Data source selection
SwitchTask dataSource = SwitchTask.builder()
    .selector(ctx -> ctx.getTyped("source", String.class))
    .branch("database", new DatabaseReadTask())
    .branch("api", new ApiReadTask())
    .branch("file", new FileReadTask())
    .build();
```

**Context Usage**:
- **Inputs**: Read by selector function
- **Outputs**: Modified by selected branch task

**Best Practices**:
- Always provide a default branch
- Use enum values for type safety
- Keep selector function fast

### DelayTask

Introduces a delay in workflow execution.

**Purpose**: Rate limiting, testing, or waiting

**Features**:
- Configurable delay duration
- Thread sleep based
- Interruptible

**Constructor**:
```java
DelayTask(Duration delay);
```

**Examples**:

```java
public void example() {
    Task delay = new DelayTask(Duration.ofSeconds(1));

    SequentialWorkflow.builder()
            .task(new ApiCallTask())
            .task(delay)
            .task(new ApiCallTask())
            .build();

    // Testing timeout behavior
    Task longDelay = new DelayTask(Duration.ofMinutes(5));

    // Retry backoff
    Task backoffDelay = new DelayTask(Duration.ofMillis(500));
}
```

**Context Usage**:
- **Inputs**: None
- **Outputs**: None

**Best Practices**:
- Use sparingly (impacts performance)
- Consider using retry policies instead
- Be aware of thread blocking

## Processing Tasks

### JavaScriptTask

Executes JavaScript code using GraalVM.

**Purpose**: Dynamic business logic, transformations, data processing

**Features**:
- Full JavaScript ES2021+ support
- Input/output bindings
- Multiple output modes
- Return multiple values from single script
- Sandboxed execution
- GraalVM polyglot engine

**Builder Configuration**:
```
JavaScriptTask.builder()
    .scriptText(String)                    // JavaScript code (inline)
    .scriptFile(Path)                      // JavaScript file path
    .inputBindings(Map<String, String>)    // Context key → JS variable name
    .outputKey(String)                     // Single output key (default mode)
    .unpackResult(boolean)                 // Unpack object properties (new!)
    .polyglot(Context)                     // GraalVM context
    .build()
```

**Output Modes**:

1. **Single Key Mode** (`unpackResult=false`, default):
   - Entire JavaScript result stored at `outputKey`
   - Traditional approach for single values

2. **Unpack Mode** (`unpackResult=true`, NEW!):
   - JavaScript must return an object
   - Each object property becomes a separate context entry
   - Perfect for returning multiple computed values

**Examples - Traditional Single Output**:

```java
public void example() {
    // Simple calculation
    WorkflowContext context = new WorkflowContext();
    context.put("x", 10);
    context.put("y", 20);

    JavaScriptTask calc = JavaScriptTask.builder()
            .scriptText("x + y")
            .inputBindings(Map.of("x", "x", "y", "y"))
            .outputKey("sum")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    calc.execute(context);
    Integer sum = context.getTyped("sum", Integer.class);  // 30

    // Data transformation
    JavaScriptTask transform = JavaScriptTask.builder()
            .scriptText("JSON.parse(rawData)")
            .inputBindings(Map.of("rawData", "rawData"))
            .outputKey("parsedData")
            .polyglot(Context.newBuilder("js").build())
            .build();
}
```

**Examples - New Unpack Mode (Multiple Outputs)**:

```java
public void example() {
    // Financial calculations - return multiple metrics
    WorkflowContext context = new WorkflowContext();
    context.put("price", 100.0);
    context.put("quantity", 10);

    JavaScriptTask financials = JavaScriptTask.builder()
            .scriptText(
                    "({ " +
                            "  subtotal: price * quantity, " +
                            "  tax: price * quantity * 0.08, " +
                            "  total: price * quantity * 1.08, " +
                            "  savings: price > 50 ? 10 : 0 " +
                            "})"
            )
            .inputBindings(Map.of("price", "price", "quantity", "quantity"))
            .unpackResult(true)  // Enable unpacking!
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    financials.execute(context);

    // All values are now directly accessible in context:
    Double subtotal = context.getTyped("subtotal", Double.class);  // 1000.0
    Double tax = context.getTyped("tax", Double.class);            // 80.0
    Double total = context.getTyped("total", Double.class);        // 1080.0
    Integer savings = context.getTyped("savings", Integer.class);  // 10

    // Data analysis - return multiple statistics
    context.put("orders", List.of(
            Map.of("status", "paid", "amount", 100.0),
            Map.of("status", "pending", "amount", 200.0),
            Map.of("status", "paid", "amount", 150.0)
    ));

    JavaScriptTask analyze = JavaScriptTask.builder()
            .scriptText(
                    "const paid = orders.filter(o => o.status === 'paid');" +
                            "const pending = orders.filter(o => o.status === 'pending');" +
                            "({ " +
                            "  paidCount: paid.length, " +
                            "  paidTotal: paid.reduce((sum, o) => sum + o.amount, 0), " +
                            "  pendingCount: pending.length, " +
                            "  pendingTotal: pending.reduce((sum, o) => sum + o.amount, 0), " +
                            "  totalOrders: orders.length " +
                            "})"
            )
            .inputBindings(Map.of("orders", "orders"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").build())
            .build();

    analyze.execute(context);

    // Access all computed statistics directly:
    Integer paidCount = context.getTyped("paidCount", Integer.class);      // 2
    Double paidTotal = context.getTyped("paidTotal", Double.class);        // 250.0
    Integer pendingCount = context.getTyped("pendingCount", Integer.class);// 1
    Double pendingTotal = context.getTyped("pendingTotal", Double.class);  // 200.0

    // String processing - multiple transformations
    context.put("text", "hello world");

    JavaScriptTask stringOps = JavaScriptTask.builder()
            .scriptText(
                    "({ " +
                            "  uppercase: text.toUpperCase(), " +
                            "  lowercase: text.toLowerCase(), " +
                            "  titleCase: text.replace(/\\b\\w/g, c => c.toUpperCase()), " +
                            "  wordCount: text.split(/\\s+/).length, " +
                            "  charCount: text.length " +
                            "})"
            )
            .inputBindings(Map.of("text", "text"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").build())
            .build();

    stringOps.execute(context);

    // All transformations available:
    String uppercase = context.getTyped("uppercase", String.class);  // "HELLO WORLD"
    String titleCase = context.getTyped("titleCase", String.class);  // "Hello World"
    Integer wordCount = context.getTyped("wordCount", Integer.class);// 2
}
```

**Examples - From File**:

```java
// Load script from file
JavaScriptTask fileTask = JavaScriptTask.builder()
    .scriptFile(Path.of("scripts/process-data.js"))
    .inputBindings(Map.of("input", "data"))
    .unpackResult(true)
    .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
    .build();

// scripts/process-data.js:
// ({
//   result1: data * 2,
//   result2: data * 3,
//   result3: data * 4
// })
```

**Examples - In Sequential Workflow**:

```java
public void example() {
    // Multistep pipeline with unpacked results
    SequentialWorkflow workflow = SequentialWorkflow.builder()
            .name("DataAnalysisPipeline")

            // Step 1: Parse JSON
            .task(JavaScriptTask.builder()
                    .scriptText("JSON.parse(rawData)")
                    .inputBindings(Map.of("rawData", "rawData"))
                    .outputKey("parsedData")
                    .build())

            // Step 2: Calculate statistics (unpack multiple values)
            .task(JavaScriptTask.builder()
                    .scriptText(
                            "const sum = parsedData.reduce((a, b) => a + b, 0);" +
                                    "({ " +
                                    "  sum: sum, " +
                                    "  average: sum / parsedData.length, " +
                                    "  min: Math.min(...parsedData), " +
                                    "  max: Math.max(...parsedData), " +
                                    "  count: parsedData.length " +
                                    "})"
                    )
                    .inputBindings(Map.of("parsedData", "parsedData"))
                    .unpackResult(true)  // Unpack all statistics
                    .build())

            .build();

    workflow.execute(context);

    // All statistics available directly in context:
    Double sum = context.getTyped("sum", Double.class);
    Double average = context.getTyped("average", Double.class);
    Double min = context.getTyped("min", Double.class);
    Double max = context.getTyped("max", Double.class);
}
```

**Context Usage**:
- **Inputs**: 
  - Mapped from context keys to JavaScript variables via `inputBindings`
  - Example: `Map.of("contextKey", "jsVarName")`
- **Outputs** (Single Key Mode): 
  - Result stored at `outputKey`
- **Outputs** (Unpack Mode): 
  - Each property of returned JavaScript object becomes a context entry
  - Keys are the property names from JavaScript object

**Type Conversions**:
GraalVM automatically converts between JavaScript and Java types:
- JavaScript numbers → Java Long (integers) or Double (decimals)
- JavaScript strings → Java String
- JavaScript booleans → Java Boolean
- JavaScript arrays → Java List
- JavaScript objects → Java Map
- JavaScript null → Java null

**Best Practices**:
1. **Use Unpack Mode When**: You need multiple computed values from one script
2. **Use Single Key Mode When**: You need one result or want to preserve object structure
3. **Return Objects**: When using unpack mode, always return an object `({ key1: val1, key2: val2 })`
4. **Descriptive Keys**: Use clear property names that make sense in Java code
5. **Type Consistency**: Return consistent types for predictable Java conversion
6. **Error Handling**: Validate inputs in JavaScript before processing

**Validation Rules**:
- When `unpackResult=true` and `outputKey!=null`: throws TaskExecutionException
- When `unpackResult=false` and `outputKey==null`: throws TaskExecutionException  
- When `unpackResult=true` and script returns non-object: throws TaskExecutionException

**Security Considerations**:
- Scripts are sandboxed by default
- No file system access (unless allowAllAccess=true)
- No network access (unless allowAllAccess=true)
- Safe for untrusted code with proper context configuration
- Review security implications before enabling `allowAllAccess`

**Performance Notes**:
- First execution has GraalVM warmup cost
- Reuse Context objects for better performance
- GraalVM compiles JavaScript to native code for performance
- Complex scripts may benefit from file caching

### ShellCommandTask

Executes system shell commands.

**Purpose**: System integration, external tools

**Features**:
- Command execution
- Working directory support
- stdout/stderr capture
- Exit code checking

**Builder Configuration**:
```
ShellCommandTask.builder()
    .command(String...)           // Command and arguments
    .workingDirectory(Path)       // Working directory
    .stdoutContextKey(String)     // Key for stdout
    .stderrContextKey(String)     // Key for stderr
    .exitCodeContextKey(String)   // Key for exit code
    .build()
```

**Examples**:

```java
// Run script
ShellCommandTask task = ShellCommandTask.builder()
    .command("python3", "process.py", "input.json")
    .workingDirectory(Path.of("/opt/scripts"))
    .stdoutContextKey("scriptOutput")
    .stderrContextKey("scriptErrors")
    .build();

// Git operations
ShellCommandTask gitClone = ShellCommandTask.builder()
    .command("git", "clone", repoUrl)
    .workingDirectory(Path.of("/tmp"))
    .build();

// Data processing
ShellCommandTask csvProcess = ShellCommandTask.builder()
    .command("awk", "-F,", "{print $1,$3}", "data.csv")
    .stdoutContextKey("processedCsv")
    .build();
```

**Context Usage**:
- **Inputs**: Command arguments can reference context
- **Outputs**: 
  - stdout at specified key
  - stderr at specified key
  - exit code at specified key

**Security Considerations**:
- ⚠️ Be careful with user input in commands
- Validate command arguments
- Consider command injection risks
- Run with minimal privileges

## Composite Tasks

### CompositeTask

Executes multiple tasks sequentially.

**Purpose**: Group related tasks

**Features**:
- Sequential execution
- Fail-fast on errors
- Shared context
- Task naming

**Builder Configuration**:
```
CompositeTask.builder()
    .task(Task)         // Add task
    .build()
```

**Examples**:

```java
// Data processing pipeline
CompositeTask pipeline = CompositeTask.builder()
    .task(new DataValidationTask())
    .task(new DataCleaningTask())
    .task(new DataTransformTask())
    .task(new DataEnrichmentTask())
    .build();

// User registration workflow
CompositeTask registration = CompositeTask.builder()
    .task(new ValidateEmailTask())
    .task(new CheckDuplicateTask())
    .task(new CreateUserTask())
    .task(new SendWelcomeEmailTask())
    .build();

// File processing
CompositeTask fileProcess = CompositeTask.builder()
    .task(new FileReadTask(path, "content"))
    .task(new ParseJsonTask())
    .task(new ValidateSchemaTask())
    .task(new ProcessDataTask())
    .build();
```

**Context Usage**:
- **Inputs**: Shared across all tasks
- **Outputs**: Accumulated from all tasks

**Comparison with SequentialWorkflow**:
- `CompositeTask`: Task-level composition
- `SequentialWorkflow`: Workflow-level composition
- Both execute sequentially, fail-fast

### ParallelTask

Executes multiple tasks concurrently.

**Purpose**: Concurrent task execution

**Features**:
- Parallel execution
- Wait for all completion
- Exception aggregation
- Execution strategy support

**Builder Configuration**:
```
ParallelTask.builder()
    .task(Task)                          // Add task
    .executionStrategy(ExecutionStrategy) // Execution strategy
    .build()
```

**Examples**:

```java
// Parallel API calls
ParallelTask apiCalls = ParallelTask.builder()
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/users")
        .responseContextKey("users")
        .build())
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/orders")
        .responseContextKey("orders")
        .build())
    .task(new GetTask.Builder<>(client)
        .url("https://api.example.com/products")
        .responseContextKey("products")
        .build())
    .build();

// Parallel processing
ParallelTask processing = ParallelTask.builder()
    .task(new ImageResizeTask())
    .task(new ThumbnailGenerationTask())
    .task(new MetadataExtractionTask())
    .build();

// With custom execution strategy
ParallelTask reactiveParallel = ParallelTask.builder()
    .task(ioTask1)
    .task(ioTask2)
    .executionStrategy(new ReactorExecutionStrategy())
    .build();
```

**Context Usage**:
- **Inputs**: Shared context (thread-safe)
- **Outputs**: All tasks write to shared context
- **Consideration**: Use thread-safe collections

**Best Practices**:
- Ensure tasks are thread-safe
- Use for I/O-bound operations
- Consider context isolation for independent tasks
- Handle partial failures appropriately

## Utility Tasks

### NoOpTask

Does nothing - useful for testing and placeholders.

**Purpose**: Testing, placeholders

**Examples**:

```java
public void example() {
    // Placeholder in development
    Task placeholder = new NoOpTask();

    // Testing workflow structure
    SequentialWorkflow.builder()
            .task(new NoOpTask())
            .task(new NoOpTask())
            .task(new NoOpTask())
            .build();

    // Conditional default
    Task defaultBranch = new NoOpTask();
}
```

**Context Usage**:
- **Inputs**: None
- **Outputs**: None

## Creating Custom Tasks

### Extending AbstractTask

**Simple Task Template**:

```java
public class MyTask extends AbstractTask {
    private final String config;
    
    public MyTask(String config) {
        this.config = config;
    }
    
    @Override
    protected void doExecute(WorkflowContext context) 
        throws TaskExecutionException {
        
        // 1. Read inputs from context
        String input = context.getTyped("inputKey", String.class);
        
        // 2. Validate inputs
        if (input == null || input.isEmpty()) {
            throw new TaskExecutionException("Input is required");
        }
        
        // 3. Execute business logic
        String result = processData(input, config);
        
        // 4. Write outputs to context
        context.put("outputKey", result);
    }
    
    @Override
    public String getName() {
        return "MyTask";
    }
    
    private String processData(String input, String config) {
        // Implementation
        return input.toUpperCase();
    }
}
```

### Best Practices for Custom Tasks

1. **Single Responsibility**: One task, one job
2. **Idempotent**: Same input → same output, no side effects
3. **Fail Fast**: Validate inputs early
4. **Clear Errors**: Descriptive exception messages
5. **Document Context**: What keys are read/written
6. **Thread Safety**: If used in parallel workflows
7. **Resource Cleanup**: Release resources in finally blocks
8. **Naming**: Descriptive task names

### Example: Custom Integration Task

```java
public class SlackNotificationTask extends AbstractTask {
    private final String webhookUrl;
    private final HttpClient httpClient;
    
    public SlackNotificationTask(String webhookUrl, HttpClient httpClient) {
        this.webhookUrl = webhookUrl;
        this.httpClient = httpClient;
    }
    
    @Override
    protected void doExecute(WorkflowContext context) 
        throws TaskExecutionException {
        
        // Read message from context
        String message = context.getTyped("notificationMessage", String.class);
        if (message == null) {
            throw new TaskExecutionException("Message is required");
        }
        
        // Build Slack payload
        String payload = String.format("{\"text\":\"%s\"}", message);
        
        // Send notification
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new TaskExecutionException(
                    "Slack notification failed: " + response.body()
                );
            }
            
            // Store response
            context.put("notificationSent", true);
            context.put("notificationTimestamp", Instant.now());
            
        } catch (IOException | InterruptedException e) {
            throw new TaskExecutionException("Failed to send notification", e);
        }
    }
    
    @Override
    public String getName() {
        return "SlackNotification";
    }
}
```

## Task Composition Patterns

### Sequential Composition

```java
public void example() {
    // Pattern 1: CompositeTask
    CompositeTask.builder()
            .task(task1)
            .task(task2)
            .task(task3)
            .build();

    // Pattern 2: SequentialWorkflow with tasks
    SequentialWorkflow.builder()
            .task(task1)
            .task(task2)
            .task(task3)
            .build();
}
```

### Parallel Composition

```java
public void example() {
    // Pattern 1: ParallelTask
    ParallelTask.builder()
            .task(task1)
            .task(task2)
            .task(task3)
            .build();

    // Pattern 2: ParallelWorkflow with tasks
    ParallelWorkflow.builder()
            .task(task1)
            .task(task2)
            .task(task3)
            .build();
}
```

### Decorator Composition

```java
Task baseTask = new ApiCallTask();
Task withTimeout = new TimedTask(baseTask, timeoutPolicy);

// Or chain using TaskDescriptor
TaskDescriptor descriptor = TaskDescriptor.builder()
    .task(baseTask)
    .retryPolicy(retryPolicy)
    .timeoutPolicy(timeoutPolicy)
    .build();
```

### Conditional Composition

```java
public void example() {
    // Pattern 1: ConditionalTask
    ConditionalTask.builder()
            .condition(predicate)
            .innerTask(task)
            .build();

    // Pattern 2: SwitchTask
    SwitchTask.builder()
            .selector(selector)
            .branch("case1", task1)
            .branch("case2", task2)
            .defaultBranch(defaultTask)
            .build();
}
```

## Summary

The Workflow Engine provides a comprehensive set of tasks covering:

1. **HTTP Operations**: GET, POST, PUT, DELETE with full request/response handling
2. **File Operations**: Read and write with encoding support
3. **Control Flow**: Conditional, switch, delay for branching and timing
4. **Processing**: JavaScript and shell command execution
5. **Resilience**: Retry and timeout decorators
6. **Composition**: Sequential and parallel task composition
7. **Utilities**: Testing and placeholder tasks

All tasks follow consistent patterns:
- Read inputs from WorkflowContext
- Execute business logic
- Write outputs to WorkflowContext
- Throw TaskExecutionException on failure
- Provide descriptive names for logging

Tasks are composable, testable, and designed for both simple and complex workflow scenarios.
