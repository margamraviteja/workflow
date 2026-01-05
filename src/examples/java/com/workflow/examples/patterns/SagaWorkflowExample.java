package com.workflow.examples.patterns;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates the Saga pattern for distributed transactions with compensating actions.
 *
 * <p>A Saga is a sequence of local transactions where each transaction updates data and publishes
 * an event or message to trigger the next transaction in the saga. If a transaction fails, the saga
 * executes compensating transactions to undo the changes made by preceding transactions.
 *
 * <p><b>Pattern Overview:</b>
 *
 * <ul>
 *   <li>Break down distributed transaction into local transactions
 *   <li>Each local transaction has a compensating transaction
 *   <li>On failure, execute compensating transactions in reverse order
 *   <li>Ensures eventual consistency across microservices
 * </ul>
 *
 * <p><b>Example Scenario:</b> E-commerce order placement spanning multiple services:
 *
 * <ol>
 *   <li>Reserve inventory
 *   <li>Process payment
 *   <li>Create shipment
 *   <li>Send notification
 * </ol>
 *
 * If any step fails, compensating actions are executed in reverse:
 *
 * <ol>
 *   <li>Cancel notification
 *   <li>Cancel shipment
 *   <li>Refund payment
 *   <li>Release inventory
 * </ol>
 *
 * @see FallbackWorkflow
 * @see SequentialWorkflow
 */
@Slf4j
public class SagaWorkflowExample {

  public static final String TOTAL_AMOUNT = "totalAmount";
  public static final String SHIPMENT_ID = "shipmentId";
  public static final String RESERVATION_ID = "reservationId";
  public static final String INVENTORY_RESERVED = "inventoryReserved";
  public static final String TRANSACTION_ID = "transactionId";
  public static final String PAYMENT_PROCESSED = "paymentProcessed";
  public static final String SHIPMENT_CREATED = "shipmentCreated";

  /**
   * Creates a saga workflow for order processing with compensating actions.
   *
   * @return the configured saga workflow
   */
  public static Workflow createOrderSaga() {
    // Step 1: Reserve inventory with compensation
    Workflow reserveInventoryStep =
        FallbackWorkflow.builder()
            .name("ReserveInventory")
            .primary(new TaskWorkflow(new ReserveInventoryTask()))
            .fallback(
                SequentialWorkflow.builder()
                    .name("CompensateReservation")
                    .task(new LogCompensationTask("Skipping inventory reservation"))
                    .build())
            .build();

    // Step 2: Process payment with compensation
    Workflow processPaymentStep =
        FallbackWorkflow.builder()
            .name("ProcessPayment")
            .primary(new TaskWorkflow(new ProcessPaymentTask()))
            .fallback(
                SequentialWorkflow.builder()
                    .name("CompensatePaymentAndInventory")
                    .task(new RefundPaymentTask())
                    .task(new ReleaseInventoryTask())
                    .build())
            .build();

    // Step 3: Create shipment with compensation
    Workflow createShipmentStep =
        FallbackWorkflow.builder()
            .name("CreateShipment")
            .primary(new TaskWorkflow(new CreateShipmentTask()))
            .fallback(
                SequentialWorkflow.builder()
                    .name("CompensateShipmentPaymentAndInventory")
                    .task(new CancelShipmentTask())
                    .task(new RefundPaymentTask())
                    .task(new ReleaseInventoryTask())
                    .build())
            .build();

    // Step 4: Send notification (the best effort - no compensation)
    Workflow sendNotificationStep =
        FallbackWorkflow.builder()
            .name("SendNotification")
            .primary(new TaskWorkflow(new SendNotificationTask()))
            .fallback(
                new TaskWorkflow(
                    new LogCompensationTask("Notification failed - order still valid")))
            .build();

    // Compose the saga
    return SequentialWorkflow.builder()
        .name("OrderPlacementSaga")
        .workflow(reserveInventoryStep)
        .workflow(processPaymentStep)
        .workflow(createShipmentStep)
        .workflow(sendNotificationStep)
        .build();
  }

  /**
   * Example usage demonstrating saga execution.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    WorkflowContext context = new WorkflowContext();
    context.put("orderId", "ORD-12345");
    context.put("customerId", "CUST-789");
    context.put("items", List.of("item1", "item2", "item3"));
    context.put(TOTAL_AMOUNT, 299.99);

    Workflow saga = createOrderSaga();
    WorkflowResult result = saga.execute(context);

    if (result.getStatus() == WorkflowStatus.SUCCESS) {
      log.info("✅ Order placement saga completed successfully");
      log.info("Order ID: {}", context.get("orderId"));
      log.info("Shipment ID: {}", context.get(SHIPMENT_ID));
    } else {
      log.error("❌ Order placement saga failed");
      log.error("Error: {}", result.getError().getMessage());
      log.info("Compensating transactions were executed");
    }

    log.info("\nSaga execution duration: {}", result.getExecutionDuration());
  }

  // ==================== Saga Tasks ====================

  /** Task to reserve inventory for order items. */
  static class ReserveInventoryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("→ Reserving inventory...");
      // Simulate inventory reservation
      context.put(RESERVATION_ID, "RES-" + UUID.randomUUID().toString().substring(0, 8));
      context.put(INVENTORY_RESERVED, true);
      log.info("✓ Inventory reserved: {}", context.get(RESERVATION_ID));
    }
  }

  /** Compensating task to release inventory reservation. */
  static class ReleaseInventoryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      if (Boolean.TRUE.equals(context.get(INVENTORY_RESERVED))) {
        log.info("↩ Releasing inventory reservation: {}", context.get(RESERVATION_ID));
        context.put(INVENTORY_RESERVED, false);
        log.info("✓ Inventory released");
      }
    }
  }

  /** Task to process payment for the order. */
  static class ProcessPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("→ Processing payment...");
      Double amount = context.getTyped(TOTAL_AMOUNT, Double.class);
      // Simulate payment processing
      context.put(TRANSACTION_ID, "TXN-" + UUID.randomUUID().toString().substring(0, 8));
      context.put(PAYMENT_PROCESSED, true);
      log.info("✓ Payment processed: ${} (TXN: {})", amount, context.get(TRANSACTION_ID));
    }
  }

  /** Compensating task to refund payment. */
  static class RefundPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      if (Boolean.TRUE.equals(context.get(PAYMENT_PROCESSED))) {
        log.info("↩ Refunding payment: {}", context.get(TRANSACTION_ID));
        Double amount = context.getTyped(TOTAL_AMOUNT, Double.class);
        context.put("refundId", "REF-" + UUID.randomUUID().toString().substring(0, 8));
        context.put(PAYMENT_PROCESSED, false);
        log.info("✓ Payment refunded: ${}", amount);
      }
    }
  }

  /** Task to create shipment. */
  static class CreateShipmentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("→ Creating shipment...");
      // Simulate shipment creation
      context.put(SHIPMENT_ID, "SHIP-" + UUID.randomUUID().toString().substring(0, 8));
      context.put(SHIPMENT_CREATED, true);
      log.info("✓ Shipment created: {}", context.get(SHIPMENT_ID));
    }
  }

  /** Compensating task to cancel shipment. */
  static class CancelShipmentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      if (Boolean.TRUE.equals(context.get(SHIPMENT_CREATED))) {
        log.info("↩ Canceling shipment: {}", context.get(SHIPMENT_ID));
        context.put(SHIPMENT_CREATED, false);
        log.info("✓ Shipment canceled");
      }
    }
  }

  /** Task to send order confirmation notification. */
  static class SendNotificationTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("→ Sending order confirmation...");
      String customerId = context.getTyped("customerId", String.class);
      // Simulate notification
      log.info("✓ Notification sent to customer: {}", customerId);
    }
  }

  /** Task to log compensation. */
  static class LogCompensationTask implements Task {
    private final String message;

    public LogCompensationTask(String message) {
      this.message = message;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("⚠ Compensation: {}", message);
    }
  }
}
