# Workflow Engine - Architecture Overview

## Table of Contents
- [Introduction](#introduction)
- [System Architecture](#system-architecture)
- [Core Components](#core-components)
- [Layered Architecture](#layered-architecture)
- [Component Interactions](#component-interactions)
- [Data Flow](#data-flow)
- [Execution Model](#execution-model)
- [Extension Points](#extension-points)
- [Visualization](#visualization)
- [Performance Considerations](#performance-considerations)

## Introduction

The Workflow Engine is designed as a flexible, extensible orchestration framework that enables the composition and execution of complex workflows. The architecture follows clean separation of concerns, dependency inversion, and open-closed principles to provide maximum flexibility while maintaining simplicity.

## System Architecture

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                    CLIENT APPLICATIONS                         │
│  (Spring Boot Apps, CLI Tools, Microservices)                  │
└────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                  WORKFLOW DEFINITION LAYER                     │
├────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Annotations  │  │   Database   │  │   Java DSL/Builder   │  │
│  │  Processor   │  │  Processor   │  │      Pattern         │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                      WORKFLOW LAYER                            │
├────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Sequential   │  │   Parallel   │  │    Conditional       │  │
│  │   Workflow   │  │   Workflow   │  │     Workflow         │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Dynamic    │  │   Fallback   │  │      Task            │  │
│  │  Branching   │  │   Workflow   │  │     Workflow         │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Rate Limited │  │   Timeout    │  │    Javascript        │  │
│  │   Workflow   │  │   Workflow   │  │     Workflow         │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                        TASK LAYER                              │
├────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  HTTP Tasks  │  │  File Tasks  │  │   Control Flow       │  │
│  │ GET/POST/PUT │  │  Read/Write  │  │  Conditional/Switch  │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Processing   │  │  Resilience  │  │     Composite        │  │
│  │ JS/Shell Cmd │  │Retry/Timeout │  │       Tasks          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                   INFRASTRUCTURE LAYER                         │
├────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Context    │  │   Policies   │  │     Execution        │  │
│  │ Management   │  │ Retry/Timeout│  │     Strategies       │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Registry   │  │   Helpers    │  │     Exceptions       │  │
│  │              │  │  JSON/Future │  │                      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### Workflow Listeners

The event system provides observability and extensibility through the `WorkflowListener` interface.

```
┌──────────────────────────────────────────────────────────┐
│                    Workflow Execution                    │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ↓
┌───────────────────────────────────────────────────────────┐
│                WorkflowListeners Registry                 │
│          (CopyOnWriteArrayList for thread safety)         │
├───────────────────────────────────────────────────────────┤
│  ┌────────────────┐  ┌────────────────┐  ┌─────────────┐  │
│  │ MetricsListener│  │TracingListener │  │AuditListener│  │
│  └────────────────┘  └────────────────┘  └─────────────┘  │
└───────────────────────────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ↓              ↓              ↓
    onStart()      onSuccess()    onFailure()
```

**Key Components**:

1. **WorkflowListener Interface**
   ```java
   public interface WorkflowListener {
       void onStart(String workflowName, WorkflowContext context);
       void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result);
       void onFailure(String workflowName, WorkflowContext context, Throwable error);
   }
   ```

2. **WorkflowListeners Registry**
    - Thread-safe listener management
    - Automatic notification during workflow lifecycle
    - Error isolation between listeners

3. **Integration Points**
    - `WorkflowContext` holds a `WorkflowListeners` instance
    - `AbstractWorkflow` automatically notifies listeners
    - Custom workflows can manually trigger notifications

**Lifecycle Flow**:

```
Workflow.execute(context)
    ↓
listeners.notifyStart(name, context)
    ↓
doExecute(context, execContext)
    ↓
if (success):
    listeners.notifySuccess(name, context, result)
else:
    listeners.notifyFailure(name, context, error)
```

**Use Cases**:
- Metrics collection (Prometheus, Micrometer)
- Distributed tracing (OpenTelemetry, Zipkin)
- Audit logging
- Real-time alerting
- Resource cleanup

## Core Components

### 1. Workflow Interface Hierarchy

```
         Workflow (interface)
               │
               ├── AbstractWorkflow (abstract class)
               │         │
               │         ├── SequentialWorkflow
               │         ├── ParallelWorkflow
               │         ├── ConditionalWorkflow
               │         ├── DynamicBranchingWorkflow
               │         ├── FallbackWorkflow
               │         ├── TaskWorkflow
               │         ├── RateLimitedWorkflow
               │         └── TimeoutWorkflow
               │
               └── Custom Implementations
```

**Key Responsibilities:**
- `Workflow`: Core contract defining `execute(WorkflowContext)` and `getName()`
- `AbstractWorkflow`: Provides lifecycle management, logging, error handling
- Concrete implementations: Specific execution semantics

### 2. Task Interface Hierarchy

```
         Task (interface)
               │
               ├── AbstractTask (abstract class)
               │         │
               │         ├── AbstractHttpTask
               │         │         ├── GetTask
               │         │         ├── PostTask
               │         │         ├── PutTask
               │         │         └── DeleteTask
               │         │
               │         ├── FileReadTask
               │         ├── FileWriteTask
               │         ├── JavaScriptTask
               │         └── ShellCommandTask
               │
               ├── ConditionalTask
               ├── SwitchTask
               ├── CompositeTask
               ├── ParallelTask
               ├── RetryingTask
               ├── TimedTask
               └── Custom Tasks
```

**Key Responsibilities:**
- `Task`: Core contract defining `execute(WorkflowContext)`
- `AbstractTask`: Provides common task infrastructure
- `AbstractHttpTask`: Common HTTP functionality
- Specialized tasks: Domain-specific implementations

### 3. Context Management

```
┌────────────────────────────────────────────────────┐
│  WorkflowContext                                   │
├────────────────────────────────────────────────────┤
│  • Map<String, Object> context                     │
│  • WorkflowListeners listeners                     │
├────────────────────────────────────────────────────┤
│  Type-Safe Access Methods:                         │
│  • getTyped(key, Class<T>)                         │
│  • getTyped(key, TypedKey<T>)                      │
│  • getTyped(key, TypeReference<T>)                 │
│                                                    │
│  Scoping:                                          │
│  • scope(namespace)                                │
│  • copy() / copy(filter)                           │
│                                                    │
│  Events:                                           │
│  • getListeners() → WorkflowListeners              │
└────────────────────────────────────────────────────┘
```

**Key Features:**
- Thread-safe concurrent access
- Type-safe getters/setters
- Scoping for namespace isolation
- Shallow copy for parallel execution

```
┌─────────────────────────────────────────────┐
│         WorkflowContext                     │
├─────────────────────────────────────────────┤
│  getTyped(key, Class<T>)                    │ ← Simple types
│  getTyped(key, TypedKey<T>)                 │ ← Compile-time safety
│  getTyped(key, TypeReference<T>)            │ ← Generic types
└─────────────────────────────────────────────┘
```

**Type Safety Levels**:

1. **Basic**: `getTyped(key, Class)`
    - Simple types (String, Integer, etc.)
    - No generics support

2. **Typed Keys**: `getTyped(TypedKey<T>)`
    - Compile-time type checking
    - Refactoring-safe
    - No generics support

3. **TypeReference**: `getTyped(key, TypeReference<T>)`
    - Full generic type safety
    - Runtime type checking
    - Complex nested types

### 4. Policy Framework

```
   RetryPolicy (interface)
        │
        ├── shouldRetry(attempt, error)
        ├── backoff()
        └── BackoffStrategy (interface)
              ├── constant(delayMs)
              ├── linear(baseDelay)
              ├── exponential(baseDelay)
              └── exponentialWithJitter(...)

   TimeoutPolicy (class)
        │
        ├── ofSeconds(seconds)
        ├── ofMillis(millis)
        └── ofMinutes(minutes)
```

### 5. Execution Strategies

```
   ExecutionStrategy (interface)
        │
        ├── submit(Callable<T>)
        └── shutdown()
              │
              ├── ThreadPoolExecutionStrategy
              │   └── Uses Executors.newCachedThreadPool()
              │
              └── ReactorExecutionStrategy
                  └── Uses Project Reactor schedulers
```

## Layered Architecture

### Layer 1: Definition Layer
**Purpose**: Multiple ways to define workflows

**Components**:
- **Annotation Processor** (`AnnotationWorkflowProcessor`)
  - Scans `@WorkflowAnnotation` classes
  - Processes `@WorkflowMethod`, `@TaskMethod`, `@WorkflowRef`
  - Builds workflow from annotated methods
  - Supports Spring and pure Java

- **Database Processor** (`DatabaseWorkflowProcessor`)
  - Reads workflow metadata from database
  - Resolves workflow instances from registry
  - Builds workflows dynamically

- **Builder Pattern** (Java DSL)
  - Fluent API for workflow construction
  - Type-safe configuration
  - Compile-time validation

### Layer 2: Workflow Layer
**Purpose**: Orchestration and composition

**Workflows**:
- **SequentialWorkflow**: Linear execution, fail-fast
- **ParallelWorkflow**: Concurrent execution, configurable context sharing
- **ConditionalWorkflow**: Binary branching based on predicate
- **DynamicBranchingWorkflow**: Multi-way branching with selector
- **FallbackWorkflow**: Primary/secondary execution pattern
- **TaskWorkflow**: Wraps single task as workflow

**Common Features**:
- Result tracking with timestamps
- Error propagation
- Context sharing/isolation
- Lifecycle logging

### Layer 3: Task Layer
**Purpose**: Atomic units of work

**Task Categories**:

1. **HTTP Tasks**: RESTful API interactions
   - Configurable headers, bodies, timeout
   - Response mapping to context
   - Error handling

2. **File Tasks**: File I/O operations
   - Read/write with encoding support
   - Path-based operations

3. **Control Flow Tasks**: Conditional execution
   - ConditionalTask: if-then-else
   - SwitchTask: multi-way branching
   - DelayTask: Introduce pauses

4. **Processing Tasks**: External execution
   - JavaScriptTask: GraalVM JS execution
   - ShellCommandTask: System command execution

5. **Resilience Tasks**: Decorators
   - RetryingTask: Add retry logic
   - TimedTask: Add timeout

6. **Composite Tasks**: Task composition
   - CompositeTask: Sequential composition
   - ParallelTask: Parallel composition

### Layer 4: Infrastructure Layer
**Purpose**: Cross-cutting concerns

**Components**:

1. **Context Management**
   - WorkflowContext: Thread-safe key-value store
   - ScopedWorkflowContext: Namespace isolation
   - TypedKey: Type-safe context keys

2. **Policy Framework**
   - RetryPolicy: Retry logic with backoff
   - TimeoutPolicy: Execution time limits

3. **Execution Strategies**
   - Thread pool based
   - Reactive (Project Reactor)
   - Custom strategies

4. **Helpers**
   - JsonUtils: JSON serialization
   - FutureUtils: CompletableFuture utilities
   - ResponseMappers: HTTP response mapping
   - WorkflowResults: Result builders

5. **Exception Handling**
   - TaskExecutionException
   - WorkflowCompositionException
   - TaskTimeoutException
   - Circular dependency detection

6. **Rate Limiting**
   - Six rate limiting strategies (Fixed Window, Sliding Window, Token Bucket, Leaky Bucket, Resilience4j, Bucket4j)
   - High-performance implementations
   - Production-ready observability
   - Burst support and accurate limiting

7. **JavaScript Execution**
   - GraalVM Polyglot integration
   - ESM module support
   - Security sandbox
   - Context binding

## Component Interactions

### Workflow Execution Flow

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ 1. execute(context)
       ↓
┌─────────────────┐
│    Workflow     │
└──────┬──────────┘
       │ 2. doExecute(context, execContext)
       ↓
┌─────────────────────────────────────┐
│      Workflow Implementation        │
│  (Sequential/Parallel/Conditional)  │
└──────┬──────────────────────────────┘
       │ 3. execute child workflows/tasks
       ↓
┌─────────────────┐
│      Task       │
└──────┬──────────┘
       │ 4. execute(context)
       ↓
┌─────────────────┐
│ Task Logic      │
│ - Read context  │
│ - Do work       │
│ - Write context │
└──────┬──────────┘
       │ 5. return/throw
       ↓
┌─────────────────┐
│ WorkflowResult  │
│ - Status        │
│ - Timestamps    │
│ - Error (if any)│
└─────────────────┘
```

### Parallel Execution Flow

```
                    ParallelWorkflow
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ↓               ↓               ↓
              ExecutionStrategy.submit()
           │               │               │
    ┌──────↓──────┐ ┌──────↓──────┐ ┌──────↓──────┐
    │  Workflow1  │ │  Workflow2  │ │  Workflow3  │
    │  (Thread 1) │ │  (Thread 2) │ │  (Thread 3) │
    └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
           │               │               │
    ┌──────↓───────────────↓───────────────↓──────┐
    │         FutureUtils.allOf()                 │
    └──────────────────────┬──────────────────────┘
                           │ Wait for completion
                           ↓
                    Aggregate Results
```

### Context Data Flow

```
     Initial Context
            │
            ↓
    ┌───────────────┐
    │  Workflow1    │
    │  put("key1")  │
    └───────────────┘
            │
            ↓
    ┌───────────────┐
    │  Workflow2    │
    │  get("key1")  │
    │  put("key2")  │
    └───────────────┘
            │
            ↓
    ┌───────────────┐
    │  Workflow3    │
    │  get("key2")  │
    │  put("key3")  │
    └───────────────┘
            │
            ↓
      Final Context
```

## Data Flow

### Sequential Data Pipeline

```
Input Data
    ↓
[Context: rawData]
    ↓
Task 1: Validate
    ↓
[Context: validatedData]
    ↓
Task 2: Transform
    ↓
[Context: transformedData]
    ↓
Task 3: Enrich
    ↓
[Context: enrichedData]
    ↓
Task 4: Persist
    ↓
Output/Result
```

### Parallel Data Aggregation

```
Input Parameters
    │
    ├─────────────┬─────────────┐
    ↓             ↓             ↓
  API 1         API 2         API 3
    │             │             │
    ↓             ↓             ↓
[users]       [orders]      [products]
    │             │             │
    └─────────────┴─────────────┘
              ↓
        Aggregation Task
              ↓
         Final Result
```

## Execution Model

### Task Execution Lifecycle

```
1. Task Creation
   └─> Builder/Constructor
   
2. Task Wrapping (optional)
   └─> TaskDescriptor with policies
   
3. Workflow Integration
   └─> TaskWorkflow wrapper
   
4. Execution Request
   └─> workflow.execute(context)
   
5. Pre-execution
   ├─> Validation
   └─> Logging
   
6. Task Execution
   ├─> Read from context
   ├─> Execute logic
   ├─> Write to context
   └─> Return/Throw
   
7. Policy Application
   ├─> Retry on failure (if configured)
   ├─> Timeout enforcement
   └─> Error handling
   
8. Result Building
   ├─> Status determination
   ├─> Timestamp recording
   └─> Error wrapping
   
9. Post-execution
   ├─> Logging
   └─> Cleanup
   
10. Result Return
    └─> WorkflowResult
```

### Retry Execution Flow

```
Attempt 1
    │
    ↓ [Failure]
Check RetryPolicy.shouldRetry(1, error)
    │
    ↓ [true]
Backoff.computeDelayMs(1)
    │
    ↓ [Sleep]
Attempt 2
    │
    ↓ [Failure]
Check RetryPolicy.shouldRetry(2, error)
    │
    ↓ [true]
Backoff.computeDelayMs(2)
    │
    ↓ [Sleep]
Attempt 3
    │
    ↓ [Success]
Return Result
```

## Visualization

### TreeRenderer

Provides hierarchical visualization of workflow structures using ASCII trees.

```
┌────────────────────────────────────────┐
│      Workflow Hierarchy                │
└──────────────┬─────────────────────────┘
               │
               ↓
┌────────────────────────────────────────┐
│        WorkflowContainer               │
│  (Interface for traversable workflows) │
├────────────────────────────────────────┤
│  getSubWorkflows()                     │
│  → List<Workflow>                      │
└──────────────┬─────────────────────────┘
               │
               ↓
┌────────────────────────────────────────┐
│         TreeRenderer                   │
├────────────────────────────────────────┤
│  render(Workflow root)                 │
│  renderHeader(...)                     │
│  renderSubTree(...)                    │
└──────────────┬─────────────────────────┘
               │
               ↓
        ASCII Tree Output
```

**Key Components**:

1. **WorkflowContainer Interface**
   ```java
   public interface WorkflowContainer extends Workflow {
       List<Workflow> getSubWorkflows();
   }
   ```

2. **TreeRenderer Utility**
    - Recursive tree traversal
    - ASCII art rendering
    - Type metadata display
    - Branch formatting

3. **Workflow.toTreeString()**
    - Convenience method on Workflow interface
    - Default implementation delegates to TreeRenderer

**Implementation**:

```java
// Workflows implement WorkflowContainer
public class SequentialWorkflow extends AbstractWorkflow 
    implements WorkflowContainer {
    
    @Override
    public List<Workflow> getSubWorkflows() {
        return workflows;
    }
    
    @Override
    public String getWorkflowType() {
        return "[Sequence]";
    }
}

// TreeRenderer traverses hierarchy
public static String render(Workflow workflow) {
    StringBuilder sb = new StringBuilder();
    renderHeader(sb, workflow, "", true);
    renderSubTree(sb, workflow, "");
    return sb.toString();
}
```

**Use Cases**:
- Documentation generation
- Workflow debugging
- Structure validation
- Test assertions
- Visual workflow design

## Extension Points

### 1. Custom Workflows
Extend `AbstractWorkflow` and implement `doExecute`:

```java
public class CustomWorkflow extends AbstractWorkflow {
    @Override
    protected WorkflowResult doExecute(WorkflowContext context, 
                                     ExecutionContext execContext) {
        // Custom logic
        // Listeners automatically notified
        return execContext.success();
    }

    @Override
    public String getWorkflowType() {
        return "[Custom]"; // For tree visualization
    }
}
```

### 2. Custom Tasks
Extend `AbstractTask` and implement `doExecute`:

```java
public class CustomTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
        // Task logic
    }
}
```

### 3. Custom Execution Strategies
Implement `ExecutionStrategy`:

```java
public class CustomExecutionStrategy implements ExecutionStrategy {
    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        // Custom execution logic
    }
}
```

### 4. Custom Retry Policies
Implement `RetryPolicy`:

```java
public class CustomRetryPolicy implements RetryPolicy {
    @Override
    public boolean shouldRetry(int attempt, Exception error) {
        // Custom retry logic
    }
    
    @Override
    public BackoffStrategy backoff() {
        return BackoffStrategy.exponential(100);
    }
}
```

### 5. Custom Listeners

```java
public class CustomListener implements WorkflowListener {
    @Override
    public void onStart(String name, WorkflowContext ctx) {
        // Track workflow start
    }
    
    @Override
    public void onSuccess(String name, WorkflowContext ctx, 
                         WorkflowResult result) {
        // Record metrics
    }
    
    @Override
    public void onFailure(String name, WorkflowContext ctx, 
                         Throwable error) {
        // Send alerts
    }
}
```

### 6. Custom Sleepers
Implement `Sleeper` for testing or specialized sleep behavior:

```java
public class CustomSleeper implements Sleeper {
    @Override
    public void sleep(long millis) {
        // Custom sleep implementation
    }
}
```

## Performance Considerations

### 1. Context Sharing vs. Isolation
- **Shared Context**: Better performance, less memory, but requires thread-safe tasks
- **Isolated Context**: Safer for independent tasks, more memory overhead

### 2. Execution Strategy Selection
- **ThreadPool**: Good for mixed workloads, automatic scaling
- **Reactor**: Better for I/O-bound tasks, reactive pipelines

### 3. Fail-Fast Optimization
- Enable fail-fast for quick feedback
- Reduces resource consumption on errors
- Cancels pending work

### 4. Task Granularity
- **Fine-grained tasks**: More flexibility, higher orchestration overhead
- **Coarse-grained tasks**: Less overhead, less flexibility

### 5. Retry Strategy
- Exponential backoff prevents thundering herd
- Jitter reduces retry collision
- Consider external service rate limits

### 6. Context Size
- Keep context lean (avoid large objects)
- Use references to external storage for large data
- Consider context cleanup for long-running workflows

### 7. Logging Level
- DEBUG logging impacts performance
- Use INFO for production
- Enable DEBUG for troubleshooting

### 8. Listeners

- **Listener Overhead**: Minimal (~1-2μs per listener per event)
- **Thread Safety**: CopyOnWriteArrayList for listener registry
- **Error Isolation**: Exceptions in one listener don't affect others
- **Async Recommended**: For heavy operations, offload to executor

```java
// Good: Lightweight listener
public void onSuccess(String name, WorkflowContext ctx, 
                     WorkflowResult result) {
    metrics.counter("success").increment();
}

// Bad: Blocking listener
public void onSuccess(String name, WorkflowContext ctx, 
                     WorkflowResult result) {
    sendEmailSync(); // Blocks workflow completion!
}

// Better: Async listener
private final ExecutorService executor = Executors.newSingleThreadExecutor();
public void onSuccess(String name, WorkflowContext ctx, 
                     WorkflowResult result) {
    executor.submit(() -> sendEmail());
}
```

## Thread Safety

### Thread-Safe Components
- ✅ WorkflowContext (ConcurrentHashMap-backed)
- ✅ WorkflowListeners (CopyOnWriteArrayList-backed)
- ✅ All workflow implementations
- ✅ RetryPolicy implementations
- ✅ TimeoutPolicy
- ✅ ExecutionStrategy implementations

### Not Thread-Safe (by design)
- ❌ Individual Tasks (unless documented)
- ❌ Context values (depends on object type)
- ❌ Builder instances (use per-thread)

### Best Practices
1. Don't share builders across threads
2. Ensure task implementations are thread-safe when used in parallel workflows
3. Use thread-safe collections in context for parallel workflows
4. Avoid mutable state in workflow/task fields

## Monitoring and Observability

### Built-in Observability

The framework provides multiple observability layers:

1. **Execution Results**
    - WorkflowResult with timestamps
    - Execution duration
    - Status (SUCCESS, FAILED, SKIPPED)
    - Error information

2. **Workflow Listeners**
    - Real-time event notifications
    - Lifecycle tracking
    - Custom metrics integration
    - Distributed tracing support

3. **Tree Visualization**
    - Workflow structure inspection
    - Composition validation
    - Documentation generation

4. **Logging**
    - SLF4J integration
    - Configurable log levels
    - Workflow lifecycle logging

### Integration Example

```java
public class WorkflowRunner {
   // 1. Setup method is already correct, but needs dependencies (Registry, Tracer, etc.)
   public void setupMonitoring(WorkflowContext context) {
      context.getListeners().register(new MetricsListener(metricsRegistry));
      context.getListeners().register(new TracingListener(tracer));
      context.getListeners().register(new AuditListener(auditLog));
      context.getListeners().register(new AlertListener(alertService));
   }

   // 2. Wrap the execution logic in a method (like main)
   public void run() {
      // Build the structure
      Workflow workflow = buildWorkflow();
      WorkflowContext context = new WorkflowContext();

      // Apply observability
      setupMonitoring(context);

      // Execute
      WorkflowResult result = workflow.execute(context);

      // Visualize
      System.out.println(workflow.toTreeString());
   }

   // Dummy method to represent your workflow builder
   private Workflow buildWorkflow() {
      return SequentialWorkflow.builder().name("ObservedPipeline").build();
   }
}
```

## Summary

The Workflow Engine architecture provides:

1. **Flexibility**: Multiple workflow types and composition patterns
2. **Extensibility**: Clear extension points for custom behavior
3. **Reliability**: Retry, timeout, and fallback mechanisms
4. **Performance**: Pluggable execution strategies and optimizations
5. **Maintainability**: Clean separation of concerns and SOLID principles
6. **Type Safety**: Type-safe context and compile-time validation
7. **Observability**: Comprehensive logging and result tracking

The architecture supports both simple use cases (sequential task execution) and complex scenarios (parallel orchestration with dynamic branching and retry logic), all while maintaining clean abstractions and predictable behavior.

### External Resources

- [GraalVM JavaScript](https://www.graalvm.org/latest/reference-manual/js/) - JavaScript runtime documentation
- [Project Reactor](https://projectreactor.io/docs) - Reactive programming guide
- [Resilience4j](https://resilience4j.readme.io/) - Resilience patterns documentation
- [Bucket4j](https://bucket4j.com/) - Rate limiting library documentation
