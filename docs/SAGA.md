# Saga Workflow

The Saga Workflow implements the [Saga pattern](https://microservices.io/patterns/data/saga.html) for managing distributed transactions with compensating actions.

## Overview

In distributed systems, traditional ACID transactions are often not feasible across multiple services. The Saga pattern provides an alternative by breaking a transaction into a sequence of local transactions, where each local transaction has a corresponding compensating action that can undo its effects.

If any step in the saga fails, the compensating actions for all previously completed steps are executed in reverse order (backward recovery).

## Key Concepts

- **Step**: A unit of work in the saga, consisting of an action and optional compensation
- **Action**: The forward workflow that performs the main operation
- **Compensation**: The rollback workflow that undoes the action if a later step fails
- **Backward Recovery**: When a step fails, compensations run in reverse order (last successful step first)

## Basic Usage

```java
public void example() {
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

    WorkflowContext context = new WorkflowContext();
    context.put("orderId", orderId);
    WorkflowResult result = orderSaga.execute(context);
}
```

## Using the Workflows Helper

```java
import com.workflow.helper.Workflows;

SagaWorkflow saga = Workflows.saga("MyTransactionSaga")
    .step(SagaStep.builder()
        .name("Step1")
        .action(ctx -> performAction1(ctx))
        .compensation(ctx -> rollbackAction1(ctx))
        .build())
    .step(SagaStep.builder()
        .name("Step2")
        .action(ctx -> performAction2(ctx))
        .compensation(ctx -> rollbackAction2(ctx))
        .build())
    .build();
```

## Convenience Methods

The builder provides several convenience methods for adding steps:

### Using Tasks Directly

```java
SagaWorkflow saga = SagaWorkflow.builder()
    .name("SimpleSaga")
    .step(actionTask, compensationTask)  // Both as Tasks
    .step(actionTask)                     // Action only, no compensation
    .build();
```

### Using Workflows

```java
SagaWorkflow saga = SagaWorkflow.builder()
    .name("WorkflowSaga")
    .step(actionWorkflow, compensationWorkflow)
    .step(readOnlyWorkflow)  // No compensation needed
    .build();
```

### Using Lambda Tasks

```java
SagaWorkflow saga = SagaWorkflow.builder()
    .name("LambdaSaga")
    .step(
        ctx -> ctx.put("result", performWork()),
        ctx -> ctx.remove("result")
    )
    .build();
```

## Steps Without Compensation

Not all steps require compensation. Read-only operations or idempotent operations may not need rollback:

```java
SagaWorkflow saga = SagaWorkflow.builder()
    .name("PartialCompensationSaga")
    .step(SagaStep.builder()
        .name("ValidateInput")
        .action(validationWorkflow)
        // No compensation - validation doesn't modify state
        .build())
    .step(SagaStep.builder()
        .name("ProcessData")
        .action(processWorkflow)
        .compensation(rollbackWorkflow)
        .build())
    .build();
```

## Failure Handling

### Accessing Failure Information During Compensation

When a saga fails, compensating actions receive failure context through the `WorkflowContext`:

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

### Handling Compensation Failures

If compensation also fails, the saga continues compensating remaining steps and collects all errors:

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

## Execution Semantics

1. **Forward Execution**: Steps execute sequentially in the order they were added
2. **Failure Detection**: If any step returns `FAILED` status or throws an exception, forward execution stops
3. **Compensation Trigger**: On failure, all previously successful steps are compensated
4. **Compensation Order**: Compensations run in reverse order (last success → first success)
5. **Compensation Continuation**: If a compensation fails, remaining compensations still execute
6. **Context Sharing**: All steps and compensations share the same `WorkflowContext`

## Example: E-Commerce Order Processing

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
    
    // Step 4: Create shipment
    .step(SagaStep.builder()
        .name("CreateShipment")
        .action(ctx -> {
            Order order = ctx.getTyped("order", Order.class);
            String shipmentId = shippingService.createShipment(order);
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

public void example() {
    // Execute the saga
    WorkflowContext context = new WorkflowContext();
    context.put("order", order);
    WorkflowResult result = orderSaga.execute(context);

    if (result.isSuccess()) {
        log.info("Order processed successfully");
    } else {
        log.error("Order processing failed: {}", result.getError().getMessage());
    }
}
```

## Best Practices

1. **Idempotent Compensations**: Design compensating actions to be idempotent - they should produce the same result even if executed multiple times
2. **Store State for Rollback**: Save any IDs or references needed for compensation in the `WorkflowContext`
3. **Null Checks in Compensation**: Always check for null before using context values in compensation - the action might have failed before setting them
4. **Log Compensation Actions**: Include detailed logging in compensations to aid debugging
5. **Test Failure Scenarios**: Write tests that simulate failures at each step to verify compensations work correctly

## Comparison with Other Patterns

| Pattern                | Use Case                                                |
|------------------------|---------------------------------------------------------|
| **SequentialWorkflow** | Simple sequential execution without rollback            |
| **SagaWorkflow**       | Distributed transactions requiring compensating actions |
| **FallbackWorkflow**   | Primary/backup execution paths                          |
| **ParallelWorkflow**   | Independent tasks that can run concurrently             |
