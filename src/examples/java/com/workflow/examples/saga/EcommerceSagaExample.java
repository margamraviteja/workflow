package com.workflow.examples.saga;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.SagaCompensationException;
import com.workflow.exception.TaskExecutionException;
import com.workflow.saga.SagaStep;
import com.workflow.task.Task;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example demonstrating SagaWorkflow for an e-commerce order processing system.
 *
 * <p><b>Business Scenario:</b> An e-commerce platform processing customer orders that involves:
 *
 * <ul>
 *   <li>Order validation
 *   <li>Inventory reservation
 *   <li>Payment processing
 *   <li>Shipping label creation
 *   <li>Loyalty points allocation
 *   <li>Notification sending
 * </ul>
 *
 * <p><b>Challenge:</b> Each step interacts with different microservices. If any step fails (e.g.,
 * payment declined, out of stock), all previous operations must be rolled back to maintain data
 * consistency across the distributed system.
 *
 * <p><b>Solution:</b> SagaWorkflow pattern with compensating transactions ensures that partial
 * failures don't leave the system in an inconsistent state.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * EcommerceSagaExample example = new EcommerceSagaExample();
 *
 * Order order = Order.builder()
 *     .orderId("ORD-12345")
 *     .customerId("CUST-789")
 *     .items(Arrays.asList(
 *         new OrderItem("PROD-001", "Laptop", 2, new BigDecimal("999.99")),
 *         new OrderItem("PROD-002", "Mouse", 1, new BigDecimal("29.99"))
 *     ))
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("order", order);
 *
 * WorkflowResult result = example.getWorkflow().execute(context);
 *
 * if (result.isSuccess()) {
 *     String trackingNumber = context.getTyped("trackingNumber", String.class);
 *     System.out.println("Order placed successfully! Tracking: " + trackingNumber);
 * } else {
 *     System.err.println("Order failed: " + result.getError().getMessage());
 * }
 * }</pre>
 */
@Slf4j
public class EcommerceSagaExample {

  // Context keys
  private static final String ORDER = "order";
  private static final String RESERVATION_IDS = "reservationIds";
  private static final String PAYMENT_TRANSACTION_ID = "paymentTransactionId";
  private static final String SHIPPING_LABEL_ID = "shippingLabelId";
  private static final String TRACKING_NUMBER = "trackingNumber";
  private static final String LOYALTY_TRANSACTION_ID = "loyaltyTransactionId";
  private static final String NOTIFICATION_IDS = "notificationIds";

  /**
   * Creates the e-commerce order processing saga workflow.
   *
   * @return configured SagaWorkflow
   */
  public Workflow getWorkflow() {
    return SagaWorkflow.builder()
        .name("EcommerceOrderSaga")
        // Step 1: Validate Order
        .step(
            SagaStep.builder()
                .name("ValidateOrder")
                .action(new ValidateOrderTask())
                .build()) // Read-only validation, no compensation needed
        // Step 2: Reserve Inventory
        .step(
            SagaStep.builder()
                .name("ReserveInventory")
                .action(new ReserveInventoryTask())
                .compensation(new ReleaseInventoryTask())
                .build())
        // Step 3: Process Payment
        .step(
            SagaStep.builder()
                .name("ProcessPayment")
                .action(new ProcessPaymentTask())
                .compensation(new RefundPaymentTask())
                .build())
        // Step 4: Create Shipping Label
        .step(
            SagaStep.builder()
                .name("CreateShippingLabel")
                .action(new CreateShippingLabelTask())
                .compensation(new CancelShippingLabelTask())
                .build())
        // Step 5: Allocate Loyalty Points
        .step(
            SagaStep.builder()
                .name("AllocateLoyaltyPoints")
                .action(new AllocateLoyaltyPointsTask())
                .compensation(new ReverseLoyaltyPointsTask())
                .build())
        // Step 6: Send Notifications
        .step(
            SagaStep.builder()
                .name("SendNotifications")
                .action(new SendNotificationsTask())
                .build()) // Best effort, no compensation needed
        .build();
  }

  static void main() {
    EcommerceSagaExample example = new EcommerceSagaExample();

    // Scenario 1: Successful order
    log.info("=== Scenario 1: Successful Order Processing ===");
    runSuccessfulOrder(example);

    // Scenario 2: Payment failure
    log.info("\n=== Scenario 2: Payment Failure - Inventory Released ===");
    runPaymentFailure(example);

    // Scenario 3: Shipping failure
    log.info("\n=== Scenario 3: Shipping Failure - Payment Refunded ===");
    runShippingFailure(example);

    // Scenario 4: Multiple compensation failures
    log.info("\n=== Scenario 4: Compensation Failures ===");
    runCompensationFailures(example);
  }

  private static void runSuccessfulOrder(EcommerceSagaExample example) {
    WorkflowContext context = createOrderContext("ORD-001", "CUST-001", false, false, false);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isSuccess()) {
      String trackingNumber = context.getTyped(TRACKING_NUMBER, String.class);
      String loyaltyTxnId = context.getTyped(LOYALTY_TRANSACTION_ID, String.class);
      log.info("✅ Order placed successfully!");
      log.info("   Tracking Number: {}", trackingNumber);
      log.info("   Loyalty Points Added: {}", loyaltyTxnId);
    }
  }

  private static void runPaymentFailure(EcommerceSagaExample example) {
    WorkflowContext context = createOrderContext("ORD-002", "CUST-002", true, false, false);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      log.info("❌ Order failed: {}", result.getError().getMessage());
      log.info("   Compensation executed: Inventory reservations have been automatically released");
    }
  }

  private static void runShippingFailure(EcommerceSagaExample example) {
    WorkflowContext context = createOrderContext("ORD-003", "CUST-003", false, true, false);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      log.info("❌ Order failed: {}", result.getError().getMessage());
      log.info("   Compensation executed: Payment refunded, inventory released in reverse order");
    }
  }

  private static void runCompensationFailures(EcommerceSagaExample example) {
    WorkflowContext context = createOrderContext("ORD-004", "CUST-004", false, true, true);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure() && result.getError() instanceof SagaCompensationException sce) {
      log.info("❌ Order failed with compensation issues:");
      log.info("   Original error: {}", sce.getCause().getMessage());
      log.info("   Compensation failures: {}", sce.getCompensationFailureCount());

      for (Throwable compError : sce.getCompensationErrors()) {
        log.info("     - {}", compError.getMessage());
      }
    }
  }

  private static WorkflowContext createOrderContext(
      String orderId,
      String customerId,
      boolean simulatePaymentFailure,
      boolean simulateShippingFailure,
      boolean simulateCompensationFailure) {

    Order order =
        Order.builder()
            .orderId(orderId)
            .customerId(customerId)
            .customerEmail(customerId + "@example.com")
            .items(
                Arrays.asList(
                    OrderItem.builder()
                        .productId("PROD-101")
                        .productName("Gaming Laptop")
                        .quantity(1)
                        .unitPrice(new BigDecimal("1299.99"))
                        .build(),
                    OrderItem.builder()
                        .productId("PROD-102")
                        .productName("Wireless Mouse")
                        .quantity(2)
                        .unitPrice(new BigDecimal("39.99"))
                        .build(),
                    OrderItem.builder()
                        .productId("PROD-103")
                        .productName("USB-C Cable")
                        .quantity(3)
                        .unitPrice(new BigDecimal("15.99"))
                        .build()))
            .shippingAddress("123 Main St, New York, NY 10001")
            .orderDate(LocalDateTime.now())
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(ORDER, order);
    context.put("simulatePaymentFailure", simulatePaymentFailure);
    context.put("simulateShippingFailure", simulateShippingFailure);
    context.put("simulateCompensationFailure", simulateCompensationFailure);

    return context;
  }

  // ==================== Domain Models ====================

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class Order {
    private String orderId;
    private String customerId;
    private String customerEmail;
    private List<OrderItem> items;
    private String shippingAddress;
    private LocalDateTime orderDate;

    public BigDecimal getTotalAmount() {
      return items.stream()
          .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class OrderItem {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
  }

  // ==================== Action Tasks ====================

  /** Validates order data and business rules. */
  static class ValidateOrderTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Order order = context.getTyped(ORDER, Order.class);
      log.info("→ Validating order: {}", order.getOrderId());

      // Validate order items
      if (order.getItems() == null || order.getItems().isEmpty()) {
        throw new TaskExecutionException("Order must contain at least one item");
      }

      // Validate amounts
      for (OrderItem item : order.getItems()) {
        if (item.getQuantity() <= 0) {
          throw new TaskExecutionException("Invalid quantity for product: " + item.getProductId());
        }
        if (item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
          throw new TaskExecutionException("Invalid price for product: " + item.getProductId());
        }
      }

      // Validate shipping address
      if (order.getShippingAddress() == null || order.getShippingAddress().trim().isEmpty()) {
        throw new TaskExecutionException("Shipping address is required");
      }

      log.info(
          "✓ Order validation passed: {} items, total: ${}",
          order.getItems().size(),
          order.getTotalAmount());
    }
  }

  /** Reserves inventory for order items. */
  static class ReserveInventoryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Order order = context.getTyped(ORDER, Order.class);
      log.info("→ Reserving inventory for order: {}", order.getOrderId());

      List<String> reservationIds = new ArrayList<>();

      // Reserve each item
      for (OrderItem item : order.getItems()) {
        String reservationId =
            "RES-"
                + item.getProductId()
                + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        reservationIds.add(reservationId);

        log.info(
            "   Reserved {} x {} (ID: {})",
            item.getQuantity(),
            item.getProductName(),
            reservationId);
      }

      context.put(RESERVATION_IDS, reservationIds);
      log.info("✓ Inventory reserved: {} reservation(s)", reservationIds.size());
    }
  }

  /** Processes payment for the order. */
  static class ProcessPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Order order = context.getTyped(ORDER, Order.class);
      BigDecimal amount = order.getTotalAmount();

      log.info("→ Processing payment of ${} for customer {}", amount, order.getCustomerId());

      // Simulate payment failure
      if (Boolean.TRUE.equals(context.get("simulatePaymentFailure"))) {
        throw new TaskExecutionException(
            "Payment declined: Card expired for customer " + order.getCustomerId());
      }

      // Simulate payment gateway call
      String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
      context.put(PAYMENT_TRANSACTION_ID, transactionId);

      log.info("✓ Payment successful: {} (${} charged)", transactionId, amount);
    }
  }

  /** Creates shipping label and tracking number. */
  static class CreateShippingLabelTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Order order = context.getTyped(ORDER, Order.class);
      log.info("→ Creating shipping label for order: {}", order.getOrderId());

      // Simulate shipping failure
      if (Boolean.TRUE.equals(context.get("simulateShippingFailure"))) {
        throw new TaskExecutionException(
            "Shipping service unavailable: Unable to create label for address "
                + order.getShippingAddress());
      }

      // Simulate shipping service call
      String labelId = "LBL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

      context.put(SHIPPING_LABEL_ID, labelId);
      context.put(TRACKING_NUMBER, trackingNumber);

      log.info("✓ Shipping label created: {} (Tracking: {})", labelId, trackingNumber);
    }
  }

  /** Allocates loyalty points for the purchase. */
  static class AllocateLoyaltyPointsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Order order = context.getTyped(ORDER, Order.class);
      BigDecimal amount = order.getTotalAmount();

      // Calculate points: 1 point per dollar
      int points = amount.intValue();

      log.info("→ Allocating {} loyalty points to customer {}", points, order.getCustomerId());

      // Simulate loyalty service call
      String loyaltyTxnId = "LYL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(LOYALTY_TRANSACTION_ID, loyaltyTxnId);
      context.put("loyaltyPoints", points);

      log.info("✓ Loyalty points allocated: {} points (Transaction: {})", points, loyaltyTxnId);
    }
  }

  /** Sends order confirmation notifications. */
  static class SendNotificationsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Order order = context.getTyped(ORDER, Order.class);
      String trackingNumber = context.getTyped(TRACKING_NUMBER, String.class);

      log.info(
          "→ Sending notifications for order: {}, trackingNumber: {}",
          order.getOrderId(),
          trackingNumber);

      List<String> notificationIds = new ArrayList<>();

      // Email notification
      String emailId = "EMAIL-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
      notificationIds.add(emailId);
      log.info("   Email sent to {} (ID: {})", order.getCustomerEmail(), emailId);

      // SMS notification
      String smsId = "SMS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
      notificationIds.add(smsId);
      log.info("   SMS sent to customer (ID: {})", smsId);

      // Push notification
      String pushId = "PUSH-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
      notificationIds.add(pushId);
      log.info("   Push notification sent (ID: {})", pushId);

      context.put(NOTIFICATION_IDS, notificationIds);
      log.info("✓ Notifications sent: {} notification(s)", notificationIds.size());
    }
  }

  // ==================== Compensation Tasks ====================

  /** Releases inventory reservations. */
  static class ReleaseInventoryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      @SuppressWarnings("unchecked")
      List<String> reservationIds = (List<String>) context.get(RESERVATION_IDS);

      if (reservationIds != null && !reservationIds.isEmpty()) {
        log.warn("↩ Releasing inventory reservations");

        for (String reservationId : reservationIds) {
          log.info("   Released reservation: {}", reservationId);
        }

        context.remove(RESERVATION_IDS);
        log.info("✓ All inventory reservations released");
      }
    }
  }

  /** Refunds payment. */
  static class RefundPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String transactionId = context.getTyped(PAYMENT_TRANSACTION_ID, String.class);

      if (transactionId != null) {
        Order order = context.getTyped(ORDER, Order.class);
        BigDecimal amount = order.getTotalAmount();

        log.warn("↩ Refunding payment: {} (Amount: ${})", transactionId, amount);

        // Simulate refund failure scenario
        if (Boolean.TRUE.equals(context.get("simulateCompensationFailure"))) {
          throw new TaskExecutionException(
              "Refund failed: Payment gateway timeout for transaction " + transactionId);
        }

        // Simulate payment gateway refund call
        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        context.put("refundId", refundId);
        context.remove(PAYMENT_TRANSACTION_ID);

        log.info("✓ Payment refunded: {} (Refund ID: {})", transactionId, refundId);
      }
    }
  }

  /** Cancels shipping label. */
  static class CancelShippingLabelTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String labelId = context.getTyped(SHIPPING_LABEL_ID, String.class);

      if (labelId != null) {
        log.warn("↩ Canceling shipping label: {}", labelId);

        // Simulate shipping service cancellation call
        context.remove(SHIPPING_LABEL_ID);
        context.remove(TRACKING_NUMBER);

        log.info("✓ Shipping label canceled: {}", labelId);
      }
    }
  }

  /** Reverses loyalty points allocation. */
  static class ReverseLoyaltyPointsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String loyaltyTxnId = context.getTyped(LOYALTY_TRANSACTION_ID, String.class);

      if (loyaltyTxnId != null) {
        Integer points = context.getTyped("loyaltyPoints", Integer.class);

        log.warn("↩ Reversing loyalty points: {} points (Transaction: {})", points, loyaltyTxnId);

        // Simulate loyalty service reversal call
        String reversalId = "LYR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        context.put("loyaltyReversalId", reversalId);
        context.remove(LOYALTY_TRANSACTION_ID);

        log.info("✓ Loyalty points reversed: {} points (Reversal ID: {})", points, reversalId);
      }
    }
  }
}
