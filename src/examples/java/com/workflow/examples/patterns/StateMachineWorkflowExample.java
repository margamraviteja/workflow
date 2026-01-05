package com.workflow.examples.patterns;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates a State Machine pattern using DynamicBranchingWorkflow.
 *
 * <p>A state machine consists of states and transitions. Each state can trigger transitions to
 * other states based on events or conditions. This example shows an order lifecycle state machine.
 *
 * <p><b>Order States:</b>
 *
 * <ul>
 *   <li>PENDING → PROCESSING
 *   <li>PROCESSING → SHIPPED or FAILED
 *   <li>SHIPPED → DELIVERED or RETURNED
 *   <li>DELIVERED → COMPLETED
 *   <li>RETURNED → REFUNDED
 *   <li>FAILED → CANCELLED
 * </ul>
 */
@Slf4j
public class StateMachineWorkflowExample {

  private static final Random random = new Random();
  public static final String STATE = "state";

  public static void main(String[] args) {
    WorkflowContext context = new WorkflowContext();
    context.put("orderId", "ORD-456");
    context.put(STATE, "PENDING");

    // Run the state machine until terminal state
    Workflow stateMachine = createOrderStateMachine();

    while (!isTerminalState(context.getTyped(STATE, String.class))) {
      log.info("\n>>> Current State: {}", context.get(STATE));
      WorkflowResult result = stateMachine.execute(context);

      if (result.getStatus() == WorkflowStatus.FAILED) {
        log.error("State machine failed: {}", result.getError().getMessage());
        break;
      }

      String newState = context.getTyped(STATE, String.class);
      log.info("→ Transitioned to: {}", newState);
    }

    log.info("\n✅ Final State: {}", context.get(STATE));
  }

  private static Workflow createOrderStateMachine() {
    return DynamicBranchingWorkflow.builder()
        .name("OrderStateMachine")
        .selector(ctx -> ctx.getTyped(STATE, String.class))
        .branch("PENDING", createPendingStateWorkflow())
        .branch("PROCESSING", createProcessingStateWorkflow())
        .branch("SHIPPED", createShippedStateWorkflow())
        .branch("DELIVERED", createDeliveredStateWorkflow())
        .branch("RETURNED", createReturnedStateWorkflow())
        .branch("FAILED", createFailedStateWorkflow())
        .defaultBranch(new TaskWorkflow(ctx -> log.info("Unknown state: {}", ctx.get(STATE))))
        .build();
  }

  private static Workflow createPendingStateWorkflow() {
    return SequentialWorkflow.builder()
        .name("PendingState")
        .task(_ -> log.info("  Processing pending order..."))
        .task(ctx -> ctx.put(STATE, "PROCESSING"))
        .build();
  }

  private static Workflow createProcessingStateWorkflow() {
    return ConditionalWorkflow.builder()
        .name("ProcessingState")
        .condition(_ -> random.nextBoolean()) // Simulate success/failure
        .whenTrue(
            SequentialWorkflow.builder()
                .task(_ -> log.info("  Order processed successfully"))
                .task(ctx -> ctx.put(STATE, "SHIPPED"))
                .build())
        .whenFalse(
            SequentialWorkflow.builder()
                .task(_ -> log.info("  Order processing failed"))
                .task(ctx -> ctx.put(STATE, "FAILED"))
                .build())
        .build();
  }

  private static Workflow createShippedStateWorkflow() {
    return ConditionalWorkflow.builder()
        .name("ShippedState")
        .condition(_ -> random.nextInt(10) < 8) // 80% delivery success
        .whenTrue(
            SequentialWorkflow.builder()
                .task(_ -> log.info("  Order delivered"))
                .task(ctx -> ctx.put(STATE, "DELIVERED"))
                .build())
        .whenFalse(
            SequentialWorkflow.builder()
                .task(_ -> log.info("  Order returned"))
                .task(ctx -> ctx.put(STATE, "RETURNED"))
                .build())
        .build();
  }

  private static Workflow createDeliveredStateWorkflow() {
    return SequentialWorkflow.builder()
        .name("DeliveredState")
        .task(_ -> log.info("  Completing order..."))
        .task(ctx -> ctx.put(STATE, "COMPLETED"))
        .build();
  }

  private static Workflow createReturnedStateWorkflow() {
    return SequentialWorkflow.builder()
        .name("ReturnedState")
        .task(_ -> log.info("  Processing refund..."))
        .task(ctx -> ctx.put(STATE, "REFUNDED"))
        .build();
  }

  private static Workflow createFailedStateWorkflow() {
    return SequentialWorkflow.builder()
        .name("FailedState")
        .task(_ -> log.info("  Cancelling order..."))
        .task(ctx -> ctx.put(STATE, "CANCELLED"))
        .build();
  }

  private static boolean isTerminalState(String state) {
    return "COMPLETED".equals(state) || "REFUNDED".equals(state) || "CANCELLED".equals(state);
  }
}
