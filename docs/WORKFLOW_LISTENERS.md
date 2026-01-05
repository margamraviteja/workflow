# Workflow Engine - Workflow Listeners Guide

## Table of Contents
- [Overview](#overview)
- [WorkflowListener Interface](#workflowlistener-interface)
- [WorkflowListeners Registry](#workflowlisteners-registry)
- [Integration with Workflows](#integration-with-workflows)
- [Use Cases](#use-cases)
- [Best Practices](#best-practices)
- [Examples](#examples)

## Overview

Workflow Listeners provide an event-driven mechanism for observing and reacting to workflow lifecycle events. This enables monitoring, metrics collection, logging, and custom actions without modifying workflow code.

### Why Use Workflow Listeners?

- **Observability**: Track workflow execution in real-time
- **Metrics**: Collect execution metrics for monitoring
- **Tracing**: Integrate with distributed tracing systems
- **Auditing**: Log workflow execution for compliance
- **Notifications**: Trigger alerts on workflow events
- **Custom Actions**: Execute cleanup or follow-up tasks

### Core Concepts

```java
// 1. Implement listener
public class LoggingListener implements WorkflowListener {
    public void onStart(String name, WorkflowContext ctx) {
        log.info("Workflow started: {}", name);
    }
    
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
        log.info("Workflow succeeded: {}", name);
    }
    
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
        log.error("Workflow failed: {}", name, error);
    }
}

// 2. Register listener
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.getListeners().register(new LoggingListener());

    // 3. Execute workflow - listeners automatically notified
    workflow.execute(context);
}
```

## WorkflowListener Interface

The `WorkflowListener` interface defines three lifecycle events:

```java
public interface WorkflowListener {
    void onStart(String workflowName, WorkflowContext context);
    void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result);
    void onFailure(String workflowName, WorkflowContext context, Throwable error);
}
```

### Event Descriptions

| Event         | When Called                     | Parameters            | Use Case                         |
|---------------|---------------------------------|-----------------------|----------------------------------|
| **onStart**   | Workflow execution begins       | name, context         | Initialize timers, log start     |
| **onSuccess** | Workflow completes successfully | name, context, result | Record metrics, trigger followup |
| **onFailure** | Workflow fails with error       | name, context, error  | Alert, rollback, retry logic     |

### Thread Safety

- Listener methods may be invoked from workflow execution threads
- Implementations should be thread-safe if shared across workflows
- Avoid long-running or blocking operations in callbacks

## WorkflowListeners Registry

The `WorkflowListeners` class manages a thread-safe registry of listeners.

### Key Features

- **Thread-Safe**: Uses `CopyOnWriteArrayList` for concurrent access
- **Multiple Listeners**: Support for registering multiple listeners
- **Error Isolation**: Exceptions in one listener don't affect others
- **Automatic Notification**: AbstractWorkflow automatically notifies listeners

### API

```java
public void example() {
    // Register listener
    context.getListeners().register(listener);

    // Notify all listeners (usually called by framework)
    context.getListeners().notifyStart(workflowName, context);
    context.getListeners().notifySuccess(workflowName, context, result);
    context.getListeners().notifyFailure(workflowName, context, error);
}
```

## Integration with Workflows

### How It Works

1. **WorkflowContext** contains a `WorkflowListeners` instance
2. **AbstractWorkflow** automatically calls listener methods during execution
3. Custom workflows can also manually notify listeners

### Automatic Integration

All workflows extending `AbstractWorkflow` automatically support listeners:

```java
public class MyWorkflow extends AbstractWorkflow {
    @Override
    protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
        // Listeners are automatically notified:
        // - onStart() called before this method
        // - onSuccess() called if this returns SUCCESS
        // - onFailure() called if this throws exception
        
        return execContext.success();
    }
}
```

### Manual Notification

For custom workflows not extending `AbstractWorkflow`:

```java
public class CustomWorkflow implements Workflow {
    @Override
    public WorkflowResult execute(WorkflowContext context) {
        context.getListeners().notifyStart(getName(), context);
        
        try {
            // Execute workflow logic
            WorkflowResult result = doWork(context);
            
            if (result.getStatus() == WorkflowStatus.SUCCESS) {
                context.getListeners().notifySuccess(getName(), context, result);
            } else {
                context.getListeners().notifyFailure(getName(), context, result.getError());
            }
            
            return result;
        } catch (Exception e) {
            context.getListeners().notifyFailure(getName(), context, e);
            throw e;
        }
    }
}
```

## Use Cases

### 1. Execution Metrics

```java
public class MetricsListener implements WorkflowListener {
    private final MetricsRegistry metrics;
    
    @Override
    public void onStart(String workflowName, WorkflowContext context) {
        metrics.counter("workflow.started", "name", workflowName).increment();
    }
    
    @Override
    public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result) {
        long duration = result.getExecutionDuration().toMillis();
        metrics.timer("workflow.duration", "name", workflowName).record(duration);
        metrics.counter("workflow.success", "name", workflowName).increment();
    }
    
    @Override
    public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
        metrics.counter("workflow.failure", 
            "name", workflowName,
            "error", error.getClass().getSimpleName()
        ).increment();
    }
}
```

### 2. Distributed Tracing

```java
public class TracingListener implements WorkflowListener {
    private final Tracer tracer;
    
    @Override
    public void onStart(String workflowName, WorkflowContext context) {
        Span span = tracer.spanBuilder(workflowName)
            .setAttribute("workflow.type", "sequential")
            .startSpan();
        context.put("trace.span", span);
    }
    
    @Override
    public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result) {
        Span span = context.getTyped("trace.span", Span.class);
        if (span != null) {
            span.setStatus(StatusCode.OK);
            span.end();
        }
    }
    
    @Override
    public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
        Span span = context.getTyped("trace.span", Span.class);
        if (span != null) {
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.recordException(error);
            span.end();
        }
    }
}
```

### 3. Audit Logging

```java
public class AuditListener implements WorkflowListener {
    private final AuditLog auditLog;
    
    @Override
    public void onStart(String workflowName, WorkflowContext context) {
        String userId = context.getTyped("userId", String.class);
        auditLog.log(AuditEntry.builder()
            .action("WORKFLOW_START")
            .workflowName(workflowName)
            .userId(userId)
            .timestamp(Instant.now())
            .build());
    }
    
    @Override
    public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result) {
        String userId = context.getTyped("userId", String.class);
        auditLog.log(AuditEntry.builder()
            .action("WORKFLOW_SUCCESS")
            .workflowName(workflowName)
            .userId(userId)
            .duration(result.getExecutionDuration())
            .timestamp(Instant.now())
            .build());
    }
    
    @Override
    public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
        String userId = context.getTyped("userId", String.class);
        auditLog.log(AuditEntry.builder()
            .action("WORKFLOW_FAILURE")
            .workflowName(workflowName)
            .userId(userId)
            .error(error.getMessage())
            .timestamp(Instant.now())
            .build());
    }
}
```

### 4. Alerting

```java
public class AlertListener implements WorkflowListener {
    private final AlertService alertService;
    private final Set<String> criticalWorkflows = Set.of("PaymentProcessing", "OrderFulfillment");
    
    @Override
    public void onStart(String workflowName, WorkflowContext context) {
        // No alerts on start
    }
    
    @Override
    public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result) {
        // No alerts on success
    }
    
    @Override
    public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
        if (criticalWorkflows.contains(workflowName)) {
            alertService.sendAlert(Alert.builder()
                .severity(Severity.CRITICAL)
                .title("Critical Workflow Failed: " + workflowName)
                .message(error.getMessage())
                .workflow(workflowName)
                .timestamp(Instant.now())
                .build());
        }
    }
}
```

### 5. Resource Cleanup

```java
public class CleanupListener implements WorkflowListener {
    @Override
    public void onStart(String workflowName, WorkflowContext context) {
        // Allocate resources
        Connection conn = createDatabaseConnection();
        context.put("db.connection", conn);
    }
    
    @Override
    public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result) {
        cleanup(context);
    }
    
    @Override
    public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
        cleanup(context);
    }
    
    private void cleanup(WorkflowContext context) {
        Connection conn = context.getTyped("db.connection", Connection.class);
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                log.error("Failed to close connection", e);
            }
        }
    }
}
```

## Best Practices

### 1. Keep Listeners Lightweight

```java
// Good: Offload heavy work
public class AsyncMetricsListener implements WorkflowListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
        executor.submit(() -> sendMetricsToRemoteSystem(name, result));
    }
}

// Bad: Blocking in listener
public class BlockingListener implements WorkflowListener {
    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
        sendMetricsToRemoteSystem(name, result); // Blocks workflow completion!
    }
}
```

### 2. Handle Exceptions Gracefully

```java
public class RobustListener implements WorkflowListener {
    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
        try {
            sendAlert(name, error);
        } catch (Exception e) {
            // Don't let listener errors propagate
            log.error("Failed to send alert", e);
        }
    }
}
```

### 3. Use Conditional Logic

```java
public class ConditionalListener implements WorkflowListener {
    @Override
    public void onStart(String name, WorkflowContext ctx) {
        boolean enableMetrics = ctx.getTyped("metrics.enabled", Boolean.class, false);
        if (enableMetrics) {
            startTimer(name, ctx);
        }
    }
}
```

### 4. Register Early

```java
public void example() {
    // Register listeners before workflow execution
    WorkflowContext context = new WorkflowContext();
    context.getListeners().register(new MetricsListener());
    context.getListeners().register(new TracingListener());
    context.getListeners().register(new AuditListener());

    // Now execute workflows
    workflow.execute(context);
}
```

### 5. Share Listeners Across Contexts

```java
public void example() {
    // Create listeners once
    WorkflowListener metricsListener = new MetricsListener(metricsRegistry);
    WorkflowListener tracingListener = new TracingListener(tracer);

    // Reuse across contexts
    WorkflowContext context1 = new WorkflowContext();
    context1.getListeners().register(metricsListener);
    context1.getListeners().register(tracingListener);

    WorkflowContext context2 = new WorkflowContext();
    context2.getListeners().register(metricsListener);
    context2.getListeners().register(tracingListener);
}
```

## Examples

### Complete Monitoring Setup

```java
public class WorkflowMonitoring {
    public static void setupMonitoring(WorkflowContext context) {
        // Metrics
        context.getListeners().register(new MetricsListener(
            MetricsRegistry.getInstance()
        ));
        
        // Distributed tracing
        context.getListeners().register(new TracingListener(
            GlobalTracer.get()
        ));
        
        // Audit logging
        context.getListeners().register(new AuditListener(
            AuditLog.getInstance()
        ));
        
        // Alerting
        context.getListeners().register(new AlertListener(
            AlertService.getInstance()
        ));
    }
    
    static void main(String[] args) {
        Workflow workflow = buildWorkflow();
        WorkflowContext context = new WorkflowContext();
        
        // Setup all monitoring
        setupMonitoring(context);
        
        // Execute with full observability
        WorkflowResult result = workflow.execute(context);
    }
}
```

### Custom Composite Listener

```java
public class CompositeListener implements WorkflowListener {
    private final List<WorkflowListener> delegates;
    
    public CompositeListener(WorkflowListener... listeners) {
        this.delegates = List.of(listeners);
    }
    
    @Override
    public void onStart(String name, WorkflowContext ctx) {
        delegates.forEach(l -> l.onStart(name, ctx));
    }
    
    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
        delegates.forEach(l -> l.onSuccess(name, ctx, result));
    }
    
    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
        delegates.forEach(l -> l.onFailure(name, ctx, error));
    }
}

// Usage
public void example() {
    WorkflowListener composite = new CompositeListener(
            new MetricsListener(),
            new TracingListener(),
            new AuditListener()
    );

    context.getListeners().register(composite);
}
```

### Testing with Listeners

```java
@Test
void testWorkflowWithListeners() {
    // Create test listener
    TestListener testListener = new TestListener();
    
    // Setup context
    WorkflowContext context = new WorkflowContext();
    context.getListeners().register(testListener);
    
    // Execute workflow
    Workflow workflow = createTestWorkflow();
    WorkflowResult result = workflow.execute(context);
    
    // Verify listener was called
    assertTrue(testListener.wasStartCalled());
    assertTrue(testListener.wasSuccessCalled());
    assertFalse(testListener.wasFailureCalled());
}

class TestListener implements WorkflowListener {
    private boolean startCalled;
    private boolean successCalled;
    private boolean failureCalled;
    
    @Override
    public void onStart(String name, WorkflowContext ctx) {
        startCalled = true;
    }
    
    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
        successCalled = true;
    }
    
    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
        failureCalled = true;
    }
    
    public boolean wasStartCalled() { return startCalled; }
    public boolean wasSuccessCalled() { return successCalled; }
    public boolean wasFailureCalled() { return failureCalled; }
}
```

## Summary

Workflow Listeners provide powerful observability and extensibility:

1. **Event-Driven**: React to workflow lifecycle events
2. **Non-Invasive**: No workflow code changes required
3. **Composable**: Multiple listeners can coexist
4. **Thread-Safe**: Safe for concurrent execution
5. **Flexible**: Support for metrics, tracing, auditing, and more

Key takeaways:
- Register listeners early in the context
- Keep listener logic lightweight
- Handle exceptions gracefully
- Use for cross-cutting concerns
- Integrate with existing observability tools
