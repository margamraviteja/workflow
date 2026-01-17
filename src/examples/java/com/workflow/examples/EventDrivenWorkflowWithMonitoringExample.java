package com.workflow.examples;

import com.workflow.ConditionalWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.listener.WorkflowListener;
import com.workflow.task.Task;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Example demonstrating event-driven workflows with comprehensive monitoring.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Event-based workflow triggering
 *   <li>Real-time metrics collection
 *   <li>Performance monitoring
 *   <li>Custom workflow listeners
 * </ul>
 *
 * <p><b>Monitoring Capabilities:</b>
 *
 * <ul>
 *   <li>Execution time tracking
 *   <li>Success/failure rates
 *   <li>Event statistics
 *   <li>Performance metrics
 * </ul>
 */
@Slf4j
public class EventDrivenWorkflowWithMonitoringExample {

  public static final String ORDER_ID = "orderId";
  public static final String ORD_001 = "ORD-001";
  public static final String AMOUNT = "amount";

  enum EventType {
    ORDER_PLACED,
    PAYMENT_RECEIVED,
    INVENTORY_UPDATED,
    SHIPMENT_REQUESTED,
    CUSTOMER_NOTIFICATION
  }

  record WorkflowEvent(EventType type, Map<String, Object> payload, Instant timestamp) {
    public WorkflowEvent(EventType type, Map<String, Object> payload) {
      this(type, payload, Instant.now());
    }
  }

  static class MetricsCollector implements WorkflowListener {
    private final Map<String, Long> executionCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> successCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> totalExecutionTime = new ConcurrentHashMap<>();

    @Override
    public void onStart(String name, WorkflowContext ctx) {
      executionCounts.merge(name, 1L, Long::sum);
      log.debug("Workflow started: {}", name);
    }

    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
      successCounts.merge(name, 1L, Long::sum);
      totalExecutionTime.merge(name, result.getDuration().toMillis(), Long::sum);
      log.debug("Workflow succeeded: {} in {}ms", name, result.getDuration().toMillis());
    }

    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
      failureCounts.merge(name, 1L, Long::sum);
      log.debug("Workflow failed: {} - {}", name, error.getMessage());
    }

    public void printMetrics() {
      log.info("\n=== Workflow Metrics ===");
      executionCounts.forEach(
          (name, count) -> {
            long successes = successCounts.getOrDefault(name, 0L);
            long failures = failureCounts.getOrDefault(name, 0L);
            long avgTime =
                successes > 0 ? totalExecutionTime.getOrDefault(name, 0L) / successes : 0;

            log.info(
                "Workflow: {} | Executions: {} | Successes: {} | Failures: {} | Avg Time: {}ms",
                name,
                count,
                successes,
                failures,
                avgTime);
          });
    }
  }

  static class EventProcessor implements Task {
    private final EventType expectedType;
    private final Random random = new Random();

    EventProcessor(EventType expectedType) {
      this.expectedType = expectedType;
    }

    @Override
    public void execute(WorkflowContext context) {
      WorkflowEvent event = context.getTyped("event", WorkflowEvent.class);
      log.info("Processing event: {} at {}", event.type(), event.timestamp());

      if (event.type() != expectedType) {
        throw new IllegalStateException(
            "Unexpected event type: " + event.type() + ", expected: " + expectedType);
      }

      // Simulate event processing
      try {
        Thread.sleep(50 + (long) random.nextInt(100));
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }

      context.put("processed-" + expectedType.name(), true);
    }
  }

  static class EventRouter {
    private final Map<EventType, Workflow> workflows;

    EventRouter() {
      this.workflows = new EnumMap<>(EventType.class);
      initializeWorkflows();
    }

    private void initializeWorkflows() {
      // Order placement workflow
      workflows.put(
          EventType.ORDER_PLACED,
          SequentialWorkflow.builder()
              .name("OrderPlacementWorkflow")
              .task(new EventProcessor(EventType.ORDER_PLACED))
              .task(
                  context -> {
                    log.info("Validating order details");
                    context.put("orderValidated", true);
                  })
              .task(
                  context -> {
                    log.info("Reserving inventory");
                    context.put("inventoryReserved", true);
                  })
              .build());

      // Payment workflow
      workflows.put(
          EventType.PAYMENT_RECEIVED,
          SequentialWorkflow.builder()
              .name("PaymentWorkflow")
              .task(new EventProcessor(EventType.PAYMENT_RECEIVED))
              .task(
                  context -> {
                    log.info("Processing payment");
                    context.put("paymentProcessed", true);
                  })
              .build());

      // Inventory workflow
      workflows.put(
          EventType.INVENTORY_UPDATED,
          SequentialWorkflow.builder()
              .name("InventoryWorkflow")
              .task(new EventProcessor(EventType.INVENTORY_UPDATED))
              .task(
                  context -> {
                    log.info("Updating inventory records");
                    context.put("inventoryUpdated", true);
                  })
              .build());

      // Shipment workflow
      workflows.put(
          EventType.SHIPMENT_REQUESTED,
          ConditionalWorkflow.builder()
              .name("ShipmentWorkflow")
              .condition(ctx -> ctx.getTyped("paymentProcessed", Boolean.class) == Boolean.TRUE)
              .whenTrue(
                  SequentialWorkflow.builder()
                      .task(new EventProcessor(EventType.SHIPMENT_REQUESTED))
                      .task(
                          context -> {
                            log.info("Arranging shipment");
                            context.put("shipmentArranged", true);
                          })
                      .build())
              .whenFalse(
                  SequentialWorkflow.builder()
                      .task(_ -> log.warn("Cannot ship - payment not processed"))
                      .build())
              .build());

      // Notification workflow
      workflows.put(
          EventType.CUSTOMER_NOTIFICATION,
          SequentialWorkflow.builder()
              .name("NotificationWorkflow")
              .task(new EventProcessor(EventType.CUSTOMER_NOTIFICATION))
              .task(
                  context -> {
                    log.info("Sending customer notification");
                    context.put("notificationSent", true);
                  })
              .build());
    }

    public Workflow route(WorkflowEvent event) {
      Workflow workflow = workflows.get(event.type());
      if (workflow == null) {
        log.warn("No workflow registered for event type: {}", event.type());
      }
      return workflow;
    }
  }

  static void main() {
    log.info("=== Event-Driven Workflow with Monitoring Example ===\n");

    MetricsCollector metricsCollector = new MetricsCollector();
    EventRouter router = new EventRouter();

    // Simulate various events
    List<WorkflowEvent> events =
        Arrays.asList(
            new WorkflowEvent(EventType.ORDER_PLACED, Map.of(ORDER_ID, ORD_001, AMOUNT, 100.0)),
            new WorkflowEvent(EventType.PAYMENT_RECEIVED, Map.of(ORDER_ID, ORD_001, AMOUNT, 100.0)),
            new WorkflowEvent(
                EventType.INVENTORY_UPDATED, Map.of("productId", "PROD-123", "qty", 10)),
            new WorkflowEvent(EventType.SHIPMENT_REQUESTED, Map.of(ORDER_ID, ORD_001)),
            new WorkflowEvent(EventType.CUSTOMER_NOTIFICATION, Map.of("customerId", "CUST-456")),
            new WorkflowEvent(EventType.ORDER_PLACED, Map.of(ORDER_ID, "ORD-002", AMOUNT, 250.0)),
            new WorkflowEvent(
                EventType.PAYMENT_RECEIVED, Map.of(ORDER_ID, "ORD-002", AMOUNT, 250.0)));

    // Process each event
    for (WorkflowEvent event : events) {
      log.info("\n--- Processing Event: {} ---", event.type());

      Workflow workflow = router.route(event);
      if (workflow != null) {
        WorkflowContext context = new WorkflowContext();
        context.put("event", event);
        context.getListeners().register(metricsCollector);

        WorkflowResult result = workflow.execute(context);
        log.info("Event processing result: {}", result.getStatus());
      }

      // Small delay between events
      try {
        Thread.sleep(100);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    }

    // Print collected metrics
    log.info("\n{}", "=".repeat(50));
    metricsCollector.printMetrics();
    log.info("=".repeat(50));

    log.info("\n=== Event-Driven Workflow Example Complete ===");
  }
}
