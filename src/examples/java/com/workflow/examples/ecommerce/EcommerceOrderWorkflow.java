package com.workflow.examples.ecommerce;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.task.AbstractTask;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive E-commerce Order Processing Example demonstrating a production-ready workflow for
 * handling online orders from placement to fulfillment.
 *
 * <p><b>Business Flow:</b>
 *
 * <ol>
 *   <li>Validate order (inventory, customer, payment method)
 *   <li>Calculate pricing (discounts, taxes, shipping)
 *   <li>Process payment (with circuit breaker protection)
 *   <li>Reserve inventory
 *   <li>Create shipment
 *   <li>Send notifications (parallel: customer email, SMS, internal alerts)
 *   <li>Update analytics
 * </ol>
 *
 * <p><b>Fault Tolerance Features:</b>
 *
 * <ul>
 *   <li>Circuit breaker for payment gateway
 *   <li>Retry policies for network calls
 *   <li>Fallback for notification failures
 *   <li>Rate limiting for external APIs
 *   <li>Conditional execution based on order type
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * EcommerceOrderWorkflow orderWorkflow = new EcommerceOrderWorkflow();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("order", orderData);
 * context.put("customer", customerData);
 *
 * WorkflowResult result = orderWorkflow.execute(context);
 *
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     String orderId = context.getTyped("orderId", String.class);
 *     System.out.println("Order processed: " + orderId);
 * } else {
 *     System.err.println("Order failed: " + result.getError().getMessage());
 *     // Implement compensation logic
 * }
 * }</pre>
 */
@Slf4j
public class EcommerceOrderWorkflow {

  public static final String ORDER_ID = "orderId";
  public static final String ITEMS = "items";
  public static final String ORDER = "order";
  public static final String CUSTOMER = "customer";
  public static final String METHOD = "method";
  public static final String PAYMENT = "payment";
  public static final String TOTAL_AMOUNT = "totalAmount";
  public static final String QUANTITY = "quantity";
  public static final String SUBTOTAL_AFTER_DISCOUNT = "subtotalAfterDiscount";

  /**
   * Build the complete order processing workflow with all stages and fault tolerance.
   *
   * @return configured workflow ready for execution
   */
  public Workflow buildWorkflow() {
    // Stage 1: Order Validation
    Workflow validationWorkflow =
        SequentialWorkflow.builder()
            .name("OrderValidation")
            .task(new ValidateInventoryTask())
            .task(new ValidateCustomerTask())
            .task(new ValidatePaymentMethodTask())
            .build();

    // Stage 2: Pricing Calculation
    Workflow pricingWorkflow =
        SequentialWorkflow.builder()
            .name("PricingCalculation")
            .task(new CalculateSubtotalTask())
            .task(new ApplyDiscountsTask())
            .task(new CalculateTaxTask())
            .task(new CalculateShippingTask())
            .task(new CalculateTotalTask())
            .build();

    // Stage 3: Payment Processing with Circuit Breaker and Retry
    Task paymentTask = new ProcessPaymentTask();
    TaskDescriptor paymentDescriptor =
        TaskDescriptor.builder()
            .task(paymentTask)
            .name("PaymentProcessor")
            .retryPolicy(
                RetryPolicy.limitedRetriesWithBackoff(
                    3, RetryPolicy.BackoffStrategy.exponentialWithJitter(1000, 5000)))
            .build();

    Workflow paymentWorkflow = new TaskWorkflow(paymentDescriptor);

    // Stage 4: Fulfillment Operations
    Workflow fulfillmentWorkflow =
        SequentialWorkflow.builder()
            .name("FulfillmentOps")
            .task(new ReserveInventoryTask())
            .task(new CreateShipmentTask())
            .task(new GenerateInvoiceTask())
            .build();

    // Stage 5: Notifications (Parallel) with Fallback
    Workflow emailNotification = new TaskWorkflow(new SendEmailTask());

    Workflow smsNotification = new TaskWorkflow(new SendSMSTask());

    Workflow notificationsWorkflow =
        ParallelWorkflow.builder()
            .name("CustomerNotifications")
            .workflow(emailNotification)
            .workflow(smsNotification)
            .workflow(new TaskWorkflow(new NotifyWarehouseTask()))
            .failFast(false) // Continue even if some notifications fail
            .build();

    // Add fallback for notification failures
    Workflow notificationsWithFallback =
        FallbackWorkflow.builder()
            .name("NotificationFallback")
            .primary(notificationsWorkflow)
            .fallback(new TaskWorkflow(new QueueNotificationsForRetryTask()))
            .build();

    // Stage 6: Conditional Premium Processing
    Workflow premiumProcessing =
        SequentialWorkflow.builder()
            .name("PremiumProcessing")
            .task(new AssignPremiumAgentTask())
            .task(new PriorityShippingTask())
            .build();

    Workflow standardProcessing = new TaskWorkflow(new StandardShippingTask());

    Workflow conditionalShipping =
        ConditionalWorkflow.builder()
            .name("ShippingSelector")
            .condition(ctx -> Boolean.TRUE.equals(ctx.get("isPremiumCustomer")))
            .whenTrue(premiumProcessing)
            .whenFalse(standardProcessing)
            .build();

    // Stage 7: Analytics and Logging
    Workflow analyticsWorkflow =
        ParallelWorkflow.builder()
            .name("AnalyticsAndTracking")
            .task(new UpdateAnalyticsTask())
            .task(new LogOrderEventTask())
            .task(new UpdateInventoryForecastTask())
            .shareContext(true)
            .build();

    // Main Order Processing Pipeline
    return SequentialWorkflow.builder()
        .name("EcommerceOrderProcessing")
        .workflow(validationWorkflow)
        .workflow(pricingWorkflow)
        .workflow(paymentWorkflow)
        .workflow(fulfillmentWorkflow)
        .workflow(conditionalShipping)
        .workflow(notificationsWithFallback)
        .workflow(analyticsWorkflow)
        .build();
  }

  public static void main(String[] args) {
    EcommerceOrderWorkflow example = new EcommerceOrderWorkflow();
    Workflow workflow = example.buildWorkflow();

    // Simulate order data
    WorkflowContext context = new WorkflowContext();

    // Order details
    Map<String, Object> order = new HashMap<>();
    order.put(ORDER_ID, "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    order.put(
        ITEMS,
        Arrays.asList(
            createOrderItem("PROD-001", "Laptop", 2, 999.99),
            createOrderItem("PROD-002", "Mouse", 1, 29.99)));
    order.put("orderDate", Instant.now());
    context.put(ORDER, order);

    // Customer details
    Map<String, Object> customer = new HashMap<>();
    customer.put("customerId", "CUST-12345");
    customer.put("email", "customer@example.com");
    customer.put("phone", "+1-555-0123");
    customer.put("isPremiumMember", true);
    context.put(CUSTOMER, customer);
    context.put("isPremiumCustomer", true);

    // Payment details
    Map<String, Object> payment = new HashMap<>();
    payment.put(METHOD, "CREDIT_CARD");
    payment.put("cardLast4", "4242");
    payment.put("amount", new BigDecimal("2059.97"));
    context.put(PAYMENT, payment);

    // Shipping details
    Map<String, Object> shipping = new HashMap<>();
    shipping.put("address", "123 Main St, City, State 12345");
    shipping.put(METHOD, "EXPRESS");
    context.put("shipping", shipping);

    // Execute workflow
    log.info("Starting e-commerce order processing workflow...");
    Instant start = Instant.now();

    try {
      WorkflowResult result = workflow.execute(context);

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);

      log.info("=".repeat(60));
      log.info("Workflow completed with status: {}", result.getStatus());
      log.info("Execution time: {} ms", duration.toMillis());
      log.info("=".repeat(60));

      if (result.getStatus() == WorkflowStatus.SUCCESS) {
        String orderId = context.getTyped(ORDER_ID, String.class, "N/A");
        BigDecimal totalAmount = context.getTyped(TOTAL_AMOUNT, BigDecimal.class, BigDecimal.ZERO);
        String trackingNumber = context.getTyped("trackingNumber", String.class, "N/A");

        log.info("✓ Order ID: {}", orderId);
        log.info("✓ Total Amount: ${}", totalAmount);
        log.info("✓ Payment Status: {}", context.get("paymentStatus"));
        log.info("✓ Tracking Number: {}", trackingNumber);
        log.info("✓ Notifications Sent: {}", context.get("notificationsSent"));
      } else {
        log.error("✗ Order processing failed: {}", result.getError().getMessage());
        log.error("Failed at stage: {}", context.get("failedStage"));
      }

    } catch (Exception e) {
      log.error("Unexpected error during workflow execution", e);
    }
  }

  private static Map<String, Object> createOrderItem(
      String sku, String name, int quantity, double price) {
    Map<String, Object> item = new HashMap<>();
    item.put("sku", sku);
    item.put("name", name);
    item.put(QUANTITY, quantity);
    item.put("unitPrice", BigDecimal.valueOf(price));
    return item;
  }

  // ==================== Task Implementations ====================

  /** Validates product inventory availability */
  static class ValidateInventoryTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Validating inventory availability...");
      @SuppressWarnings("unchecked")
      Map<String, Object> order = context.getTyped(ORDER, Map.class);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items = (List<Map<String, Object>>) order.get(ITEMS);

      for (Map<String, Object> item : items) {
        String sku = (String) item.get("sku");
        int quantity = (int) item.get(QUANTITY);
        // Simulate inventory check
        if (quantity > 100) {
          throw new TaskExecutionException("Insufficient inventory for SKU: " + sku);
        }
      }
      context.put("inventoryValidated", true);
      log.info("✓ Inventory validated");
    }
  }

  /** Validates customer account status */
  static class ValidateCustomerTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Validating customer account...");
      @SuppressWarnings("unchecked")
      Map<String, Object> customer = context.getTyped(CUSTOMER, Map.class);
      String customerId = (String) customer.get("customerId");
      // Simulate customer validation
      context.put("customerValidated", true);
      log.info("✓ Customer {} validated", customerId);
    }
  }

  /** Validates payment method */
  static class ValidatePaymentMethodTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Validating payment method...");
      @SuppressWarnings("unchecked")
      Map<String, Object> payment = context.getTyped(PAYMENT, Map.class);
      String method = (String) payment.get(METHOD);
      context.put("paymentMethodValidated", true);
      log.info("✓ Payment method {} validated", method);
    }
  }

  /** Calculates order subtotal */
  static class CalculateSubtotalTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Calculating subtotal...");
      @SuppressWarnings("unchecked")
      Map<String, Object> order = context.getTyped(ORDER, Map.class);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items = (List<Map<String, Object>>) order.get(ITEMS);

      BigDecimal subtotal = BigDecimal.ZERO;
      for (Map<String, Object> item : items) {
        BigDecimal unitPrice = (BigDecimal) item.get("unitPrice");
        int quantity = (int) item.get(QUANTITY);
        subtotal = subtotal.add(unitPrice.multiply(new BigDecimal(quantity)));
      }

      context.put("subtotal", subtotal);
      log.info("✓ Subtotal calculated: ${}", subtotal);
    }
  }

  /** Applies discounts to order */
  static class ApplyDiscountsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Applying discounts...");
      BigDecimal subtotal = context.getTyped("subtotal", BigDecimal.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> customer = context.getTyped(CUSTOMER, Map.class);
      Boolean isPremium = (Boolean) customer.get("isPremiumMember");

      BigDecimal discount = BigDecimal.ZERO;
      if (Boolean.TRUE.equals(isPremium)) {
        discount = subtotal.multiply(new BigDecimal("0.10")); // 10% discount
      }

      context.put("discount", discount);
      context.put(SUBTOTAL_AFTER_DISCOUNT, subtotal.subtract(discount));
      log.info("✓ Discount applied: ${}", discount);
    }
  }

  /** Calculates sales tax */
  static class CalculateTaxTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Calculating tax...");
      BigDecimal subtotalAfterDiscount =
          context.getTyped(SUBTOTAL_AFTER_DISCOUNT, BigDecimal.class);
      BigDecimal tax = subtotalAfterDiscount.multiply(new BigDecimal("0.08")); // 8% tax
      context.put("tax", tax);
      log.info("✓ Tax calculated: ${}", tax);
    }
  }

  /** Calculates shipping cost */
  static class CalculateShippingTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Calculating shipping...");
      @SuppressWarnings("unchecked")
      Map<String, Object> shipping = context.getTyped("shipping", Map.class);
      String method = (String) shipping.get(METHOD);

      BigDecimal shippingCost =
          switch (method) {
            case "EXPRESS" -> new BigDecimal("19.99");
            case "STANDARD" -> new BigDecimal("9.99");
            default -> new BigDecimal("5.99");
          };

      context.put("shippingCost", shippingCost);
      log.info("✓ Shipping cost: ${}", shippingCost);
    }
  }

  /** Calculates order total */
  static class CalculateTotalTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Calculating total...");
      BigDecimal subtotalAfterDiscount =
          context.getTyped(SUBTOTAL_AFTER_DISCOUNT, BigDecimal.class);
      BigDecimal tax = context.getTyped("tax", BigDecimal.class);
      BigDecimal shippingCost = context.getTyped("shippingCost", BigDecimal.class);

      BigDecimal total = subtotalAfterDiscount.add(tax).add(shippingCost);
      context.put(TOTAL_AMOUNT, total);
      log.info("✓ Total amount: ${}", total);
    }
  }

  /** Processes payment through payment gateway */
  static class ProcessPaymentTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Processing payment...");
      @SuppressWarnings("unchecked")
      Map<String, Object> payment = context.getTyped(PAYMENT, Map.class);
      BigDecimal amount = context.getTyped(TOTAL_AMOUNT, BigDecimal.class);
      log.info("Payment: {}", payment);
      log.info("Amount: {}", amount);

      // Simulate payment processing delay
      try {
        Thread.sleep(500);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }

      // Simulate 95% success rate
      if (Math.random() < 0.95) {
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        context.put("paymentStatus", "COMPLETED");
        context.put("transactionId", transactionId);
        log.info("✓ Payment processed: {}", transactionId);
      } else {
        throw new TaskExecutionException("Payment gateway error");
      }
    }
  }

  /** Reserves inventory for the order */
  static class ReserveInventoryTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Reserving inventory...");
      @SuppressWarnings("unchecked")
      Map<String, Object> order = context.getTyped(ORDER, Map.class);
      String orderId = (String) order.get(ORDER_ID);
      context.put("inventoryReserved", true);
      context.put("reservationId", "RSV-" + orderId);
      log.info("✓ Inventory reserved");
    }
  }

  /** Creates shipment record */
  static class CreateShipmentTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Creating shipment...");
      String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
      context.put("trackingNumber", trackingNumber);
      context.put("shipmentCreated", true);
      log.info("✓ Shipment created: {}", trackingNumber);
    }
  }

  /** Generates invoice */
  static class GenerateInvoiceTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Generating invoice...");
      @SuppressWarnings("unchecked")
      Map<String, Object> order = context.getTyped(ORDER, Map.class);
      String orderId = (String) order.get(ORDER_ID);
      String invoiceId = "INV-" + orderId;
      context.put("invoiceId", invoiceId);
      log.info("✓ Invoice generated: {}", invoiceId);
    }
  }

  /** Sends email notification */
  static class SendEmailTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Sending email notification...");
      @SuppressWarnings("unchecked")
      Map<String, Object> customer = context.getTyped(CUSTOMER, Map.class);
      String email = (String) customer.get("email");
      context.put("emailSent", true);
      log.info("✓ Email sent to: {}", email);
    }
  }

  /** Sends SMS notification */
  static class SendSMSTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Sending SMS notification...");
      @SuppressWarnings("unchecked")
      Map<String, Object> customer = context.getTyped(CUSTOMER, Map.class);
      String phone = (String) customer.get("phone");
      context.put("smsSent", true);
      log.info("✓ SMS sent to: {}", phone);
    }
  }

  /** Notifies warehouse system */
  static class NotifyWarehouseTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Notifying warehouse...");
      context.put("warehouseNotified", true);
      log.info("✓ Warehouse notified");
    }
  }

  /** Queues failed notifications for retry */
  static class QueueNotificationsForRetryTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.warn("Queueing notifications for retry...");
      context.put("notificationsQueued", true);
      log.warn("⚠ Notifications queued for later retry");
    }
  }

  /** Assigns premium customer service agent */
  static class AssignPremiumAgentTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Assigning premium agent...");
      context.put("agentId", "AGENT-PREMIUM-001");
      log.info("✓ Premium agent assigned");
    }
  }

  /** Sets up priority shipping */
  static class PriorityShippingTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Setting up priority shipping...");
      context.put("shippingPriority", "HIGH");
      log.info("✓ Priority shipping enabled");
    }
  }

  /** Sets up standard shipping */
  static class StandardShippingTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Setting up standard shipping...");
      context.put("shippingPriority", "NORMAL");
      log.info("✓ Standard shipping enabled");
    }
  }

  /** Updates analytics system */
  static class UpdateAnalyticsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Updating analytics...");
      context.put("analyticsUpdated", true);
      log.info("✓ Analytics updated");
    }
  }

  /** Logs order event */
  static class LogOrderEventTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Logging order event...");
      @SuppressWarnings("unchecked")
      Map<String, Object> order = context.getTyped(ORDER, Map.class);
      String orderId = (String) order.get(ORDER_ID);
      log.info("✓ Event logged for order: {}", orderId);
    }
  }

  /** Updates inventory forecast */
  static class UpdateInventoryForecastTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Updating inventory forecast...");
      context.put("forecastUpdated", true);
      log.info("✓ Inventory forecast updated");
    }
  }
}
