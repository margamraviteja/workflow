package com.workflow.examples;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.TaskWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example: Order Processing System
 *
 * <p>This example demonstrates a complete order processing workflow that handles: - Order
 * validation - Inventory management - Payment processing - Notification delivery - Order
 * confirmation and shipment scheduling
 *
 * <p>The workflow uses parallel execution for independent operations (payment + inventory) and
 * sequential execution for dependent operations (validate -> process -> ship).
 */
@UtilityClass
@Slf4j
public class OrderProcessingExample {

  public static final String ORDER_ID = "orderId";
  public static final String PRODUCT_ID = "productId";
  public static final String QUANTITY = "quantity";

  static class ValidateOrderTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String orderId = (String) context.get(ORDER_ID);
      log.info("Validating order: {}", orderId);

      boolean isValid = orderId != null && !orderId.isEmpty();
      context.put("orderValid", isValid);

      if (!isValid) {
        throw new IllegalArgumentException("Invalid order ID");
      }
      log.info("Order {} validation passed", orderId);
    }

    @Override
    public String getName() {
      return "validate-order";
    }
  }

  static class CheckInventoryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String productId = (String) context.get(PRODUCT_ID);
      int quantity = (Integer) context.get(QUANTITY);
      log.info("Checking inventory for product: {}, quantity: {}", productId, quantity);

      int available = 100;
      boolean inStock = available >= quantity;
      context.put("inStock", inStock);

      if (!inStock) {
        throw new IllegalStateException("Insufficient inventory for product: " + productId);
      }
      log.info("Inventory check passed for product {}", productId);
    }

    @Override
    public String getName() {
      return "check-inventory";
    }
  }

  static class ProcessPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String orderId = (String) context.get(ORDER_ID);
      Double amount = (Double) context.get("totalAmount");
      log.info("Processing payment for order: {}, amount: ${}", orderId, amount);

      boolean paymentSuccess = Math.random() > 0.2;
      context.put("paymentProcessed", paymentSuccess);

      if (!paymentSuccess) {
        throw new IllegalStateException("Payment processing failed");
      }
      log.info("Payment processed successfully for order {}", orderId);
    }

    @Override
    public String getName() {
      return "process-payment";
    }
  }

  static class SendNotificationTask implements Task {
    private final String notificationType;

    SendNotificationTask(String notificationType) {
      this.notificationType = notificationType;
    }

    @Override
    public void execute(WorkflowContext context) {
      String orderId = (String) context.get(ORDER_ID);
      String customerEmail = (String) context.get("customerEmail");
      log.info(
          "Sending {} notification to {} for order {}", notificationType, customerEmail, orderId);

      try {
        Thread.sleep(100);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
      log.info("{} notification sent successfully", notificationType);
    }

    @Override
    public String getName() {
      return "send-" + notificationType.toLowerCase().replace(" ", "-");
    }
  }

  static class ReserveInventoryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String productId = (String) context.get(PRODUCT_ID);
      int quantity = (Integer) context.get(QUANTITY);
      log.info("Reserving {} units of product {}", quantity, productId);

      try {
        Thread.sleep(50);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
      context.put("inventoryReserved", true);
      log.info("Inventory reserved successfully");
    }

    @Override
    public String getName() {
      return "reserve-inventory";
    }
  }

  static class ScheduleShipmentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String orderId = (String) context.get(ORDER_ID);
      log.info("Scheduling shipment for order: {}", orderId);

      String shipmentDate = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_DATE);
      context.put("shipmentDate", shipmentDate);

      log.info("Shipment scheduled for order {} on {}", orderId, shipmentDate);
    }

    @Override
    public String getName() {
      return "schedule-shipment";
    }
  }

  static void processOrder() {
    log.info("\n=== Order Processing Workflow Example ===\n");

    WorkflowContext context = new WorkflowContext();
    context.put(ORDER_ID, "ORD-2025-12345");
    context.put(PRODUCT_ID, "PROD-789");
    context.put(QUANTITY, 5);
    context.put("totalAmount", 299.99);
    context.put("customerEmail", "customer@example.com");

    RetryPolicy inventoryRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 3;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.exponential(200);
          }
        };

    RetryPolicy paymentRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 2;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.constant(500);
          }
        };

    TaskWorkflow validateOrder =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ValidateOrderTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    TaskWorkflow checkInventory =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CheckInventoryTask())
                .retryPolicy(inventoryRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow processPayment =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ProcessPaymentTask())
                .retryPolicy(paymentRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    TaskWorkflow reserveInventory =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ReserveInventoryTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    ParallelWorkflow parallelOperations =
        ParallelWorkflow.builder()
            .name("parallel-payment-and-inventory")
            .workflow(processPayment)
            .workflow(reserveInventory)
            .shareContext(true)
            .failFast(true)
            .build();

    TaskWorkflow sendConfirmation =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new SendNotificationTask("Order Confirmation"))
                .retryPolicy(inventoryRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow scheduleShipment =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ScheduleShipmentTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    TaskWorkflow sendShipping =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new SendNotificationTask("Shipment Scheduled"))
                .retryPolicy(inventoryRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    SequentialWorkflow orderProcessing =
        SequentialWorkflow.builder()
            .name("order-processing-pipeline")
            .workflow(validateOrder)
            .workflow(checkInventory)
            .workflow(parallelOperations)
            .workflow(sendConfirmation)
            .workflow(scheduleShipment)
            .workflow(sendShipping)
            .build();

    WorkflowResult result = orderProcessing.execute(context);

    log.info("\n=== Order Processing Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration());
    log.info("Order ID: {}", context.get(ORDER_ID));
    log.info("Shipment Date: {}", context.get("shipmentDate"));

    if (result.getError() != null) {
      log.error("Error: {}", result.getError().getMessage());
    }
  }

  static void main() {
    processOrder();
  }
}
