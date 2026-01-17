package com.workflow.examples;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.context.WorkflowContext;
import com.workflow.database.DatabaseWorkflowProcessor;
import com.workflow.exception.TaskExecutionException;
import com.workflow.registry.WorkflowRegistry;
import com.workflow.task.Task;
import javax.sql.DataSource;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive examples demonstrating failFast and shareContext options for parallel workflows.
 *
 * <p>This class showcases various scenarios where these options are useful, including:
 *
 * <ul>
 *   <li>Data aggregation with fail-fast enabled
 *   <li>Independent processing with isolated contexts
 *   <li>Partial result collection without fail-fast
 *   <li>Annotation-based workflow configuration
 *   <li>Database-driven workflow configuration
 * </ul>
 */
@Slf4j
@UtilityClass
public class ParallelWorkflowOptionsExample {

  /**
   * Example 1: Fail-Fast for Critical Dependencies
   *
   * <p>When fetching data from multiple services and ANY failure invalidates the entire operation,
   * use failFast=true to stop immediately and save resources.
   */
  public static void example1FailFastForCriticalServices() {
    log.info("=== Example 1: Fail-Fast for Critical Dependencies ===");

    // Create workflows for fetching critical data
    Workflow fetchUserData =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchUserData")
            .task(
                context -> {
                  log.info("Fetching user data...");
                  context.put("userData", "{ userId: 123, name: 'John' }");
                })
            .build();

    Workflow fetchPermissions =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchPermissions")
            .task(
                _ -> {
                  log.info("Fetching permissions...");
                  // Simulate failure
                  throw new TaskExecutionException("Permission service unavailable");
                })
            .build();

    Workflow fetchSettings =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchSettings")
            .task(
                context -> {
                  log.info("Fetching settings...");
                  try {
                    Thread.sleep(2000); // This should be canceled
                  } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                  }
                  context.put("settings", "{ theme: 'dark' }");
                })
            .build();

    // Build parallel workflow with fail-fast enabled
    Workflow criticalDataFetch =
        ParallelWorkflow.builder()
            .name("CriticalDataFetch")
            .workflow(fetchUserData)
            .workflow(fetchPermissions)
            .workflow(fetchSettings)
            .failFast(true) // Stop all workflows on first failure
            .shareContext(true) // Share context for aggregated results
            .build();

    // Execute
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = criticalDataFetch.execute(context);

    logResult(result);
    log.info("Error: {}", result.getError() != null ? result.getError().getMessage() : "None");
    log.info("Note: Settings workflow was cancelled due to fail-fast, saving processing time\n");
  }

  private static void logResult(WorkflowResult result) {
    log.info("Result status: {}", result.getStatus());
  }

  /**
   * Example 2: No Fail-Fast for Data Collection
   *
   * <p>When collecting metrics or logs from multiple sources, you want ALL results even if some
   * fail. Use failFast=false to wait for all workflows.
   */
  public static void example2CollectAllResultsWithoutFailFast() {
    log.info("=== Example 2: Collect All Results Without Fail-Fast ===");

    Workflow collectCpuMetrics =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("CollectCPU")
            .task(
                context -> {
                  log.info("Collecting CPU metrics...");
                  context.put("cpuUsage", 45.2);
                })
            .build();

    Workflow collectMemoryMetrics =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("CollectMemory")
            .task(
                _ -> {
                  log.info("Collecting memory metrics...");
                  throw new TaskExecutionException("Memory collector timeout");
                })
            .build();

    Workflow collectDiskMetrics =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("CollectDisk")
            .task(
                context -> {
                  log.info("Collecting disk metrics...");
                  context.put("diskUsage", 72.8);
                })
            .build();

    // Build parallel workflow without fail-fast
    Workflow metricsCollection =
        ParallelWorkflow.builder()
            .name("MetricsCollection")
            .workflow(collectCpuMetrics)
            .workflow(collectMemoryMetrics)
            .workflow(collectDiskMetrics)
            .failFast(false) // Wait for all even if some fail
            .shareContext(true) // Aggregate all available metrics
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = metricsCollection.execute(context);

    logResult(result);
    log.info("CPU Usage: {}", context.get("cpuUsage"));
    log.info("Disk Usage: {}", context.get("diskUsage"));
    log.info("Note: We collected partial metrics even though memory collection failed\n");
  }

  /**
   * Example 3: Isolated Contexts for Independent Processing
   *
   * <p>When processing completely independent items (like batch image processing), use
   * shareContext=false to avoid any potential interference between workflows.
   */
  public static void example3IsolatedContextsForIndependentProcessing() {
    log.info("=== Example 3: Isolated Contexts for Independent Processing ===");

    // Each image processing workflow should work independently
    Workflow processImage1 =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("ProcessImage1")
            .task(
                context -> {
                  log.info("Processing image1.jpg");
                  context.put("result", "image1_processed.jpg");
                  context.put("status", "success");
                })
            .build();

    Workflow processImage2 =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("ProcessImage2")
            .task(
                context -> {
                  log.info("Processing image2.jpg");
                  context.put("result", "image2_processed.jpg");
                  context.put("status", "success");
                })
            .build();

    Workflow processImage3 =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("ProcessImage3")
            .task(
                _ -> {
                  log.info("Processing image3.jpg");
                  throw new TaskExecutionException("Corrupted image");
                })
            .build();

    // Build parallel workflow with isolated contexts
    Workflow batchImageProcessing =
        ParallelWorkflow.builder()
            .name("BatchImageProcessing")
            .workflow(processImage1)
            .workflow(processImage2)
            .workflow(processImage3)
            .failFast(false) // Process all images
            .shareContext(false) // Each image gets isolated context
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("config", "{ quality: 'high', format: 'jpg' }");
    WorkflowResult result = batchImageProcessing.execute(context);

    logResult(result);
    log.info(
        "Note: Each image was processed in isolation, failures didn't affect others' context\n");
  }

  /**
   * Example 4: Shared Context for Data Aggregation
   *
   * <p>When multiple workflows need to write to a shared result structure, use shareContext=true to
   * aggregate results.
   */
  public static void example4SharedContextForDataAggregation() {
    log.info("=== Example 4: Shared Context for Data Aggregation ===");

    Workflow fetchOrders =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchOrders")
            .task(
                context -> {
                  log.info("Fetching orders...");
                  context.put("orders", java.util.List.of("Order1", "Order2", "Order3"));
                })
            .build();

    Workflow fetchCustomers =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchCustomers")
            .task(
                context -> {
                  log.info("Fetching customers...");
                  context.put("customers", java.util.List.of("Customer1", "Customer2"));
                })
            .build();

    Workflow fetchProducts =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchProducts")
            .task(
                context -> {
                  log.info("Fetching products...");
                  context.put("products", java.util.List.of("Product1", "Product2", "Product3"));
                })
            .build();

    // Build parallel workflow with shared context
    Workflow dataAggregation =
        ParallelWorkflow.builder()
            .name("DataAggregation")
            .workflow(fetchOrders)
            .workflow(fetchCustomers)
            .workflow(fetchProducts)
            .failFast(true) // Need all data, fail fast if any fails
            .shareContext(true) // All workflows write to same context
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = dataAggregation.execute(context);

    if (result.getStatus() == WorkflowStatus.SUCCESS) {
      log.info("Orders: {}", context.get("orders"));
      log.info("Customers: {}", context.get("customers"));
      log.info("Products: {}", context.get("products"));
      log.info("Note: All data aggregated in shared context\n");
    }
  }

  /**
   * Example 5: Annotation-Based Configuration
   *
   * <p>Use @WorkflowAnnotation with failFast and shareContext options.
   */
  @WorkflowAnnotation(
      name = "AnnotatedParallelWorkflow",
      parallel = true,
      failFast = true,
      shareContext = false,
      description = "Example workflow using annotations")
  public static class AnnotatedWorkflowExample {

    @WorkflowMethod(name = "Step1", order = 1)
    public Workflow createStep1() {
      return new SequentialWorkflow.SequentialWorkflowBuilder()
          .name("Step1")
          .task(_ -> log.info("Executing Step1 with isolated context"))
          .build();
    }

    @TaskMethod(name = "Step2", order = 2)
    public Task createStep2() {
      return _ -> log.info("Executing Step2 with isolated context");
    }

    @WorkflowMethod(name = "Step3", order = 3)
    public Workflow createStep3() {
      return new SequentialWorkflow.SequentialWorkflowBuilder()
          .name("Step3")
          .task(_ -> log.info("Executing Step3 with isolated context"))
          .build();
    }
  }

  public static void example5AnnotationBasedConfiguration() {
    log.info("=== Example 5: Annotation-Based Configuration ===");
    log.info("The AnnotatedWorkflowExample class demonstrates using @WorkflowAnnotation with:");
    log.info("  - parallel=true: Execute workflows in parallel");
    log.info("  - failFast=true: Stop on first failure");
    log.info("  - shareContext=false: Each workflow gets isolated context");
    log.info("This provides declarative configuration without programmatic builder code.\n");
  }

  /**
   * Example 6: Database-Driven Configuration
   *
   * <p>Database schema:
   *
   * <p>CREATE TABLE workflow ( id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255) NOT NULL
   * UNIQUE, description VARCHAR(255), is_parallel BOOLEAN DEFAULT FALSE, fail_fast BOOLEAN DEFAULT
   * FALSE, -- NEW share_context BOOLEAN DEFAULT TRUE -- NEW );
   *
   * <p>Example INSERT: INSERT INTO workflow (name, description, is_parallel, fail_fast,
   * share_context) VALUES ('DataPipeline', 'Process data in parallel', TRUE, TRUE, FALSE);
   *
   * <p>Configure failFast and shareContext options in database tables.
   */
  public static void example6DatabaseDrivenConfiguration(DataSource dataSource) {
    log.info("=== Example 6: Database-Driven Configuration ===");

    WorkflowRegistry registry = new WorkflowRegistry();
    // Register your workflows...

    DatabaseWorkflowProcessor processor = new DatabaseWorkflowProcessor(dataSource, registry);

    log.info("Database table now supports fail_fast and share_context columns");
    log.info("Example query:");
    log.info("  SELECT * FROM workflow WHERE name = 'DataPipeline'");
    log.info("  Result: is_parallel=TRUE, fail_fast=TRUE, share_context=FALSE");
    log.info("The processor will automatically configure parallel workflows with these options\n");
    try {
      processor.buildWorkflow("DataPipeline");
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Example 7: Combining Options for Real-World Scenario
   *
   * <p>A realistic microservices orchestration scenario using both options.
   */
  public static void example7MicroservicesOrchestration() {
    log.info("=== Example 7: Microservices Orchestration ===");

    // Phase 1: Fetch required data (fail-fast, shared context)
    Workflow fetchUserProfile =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchUserProfile")
            .task(context -> context.put("userProfile", "{ id: 123, name: 'John' }"))
            .build();

    Workflow fetchPaymentMethods =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("FetchPaymentMethods")
            .task(context -> context.put("paymentMethods", java.util.List.of("Visa", "PayPal")))
            .build();

    Workflow dataFetchPhase =
        ParallelWorkflow.builder()
            .name("DataFetchPhase")
            .workflow(fetchUserProfile)
            .workflow(fetchPaymentMethods)
            .failFast(true) // Need all data for checkout
            .shareContext(true) // Aggregate results
            .build();

    // Phase 2: Process order steps independently (no fail-fast, isolated contexts)
    Workflow sendEmail =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("SendEmail")
            .task(_ -> log.info("Sending confirmation email..."))
            .build();

    Workflow updateInventory =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("UpdateInventory")
            .task(_ -> log.info("Updating inventory..."))
            .build();

    Workflow logAnalytics =
        new SequentialWorkflow.SequentialWorkflowBuilder()
            .name("LogAnalytics")
            .task(
                _ -> {
                  log.info("Logging analytics...");
                  throw new TaskExecutionException("Analytics service down");
                })
            .build();

    Workflow postProcessingPhase =
        ParallelWorkflow.builder()
            .name("PostProcessingPhase")
            .workflow(sendEmail)
            .workflow(updateInventory)
            .workflow(logAnalytics)
            .failFast(false) // Continue even if analytics fails
            .shareContext(false) // Independent tasks
            .build();

    // Complete orchestration
    Workflow checkoutOrchestration =
        SequentialWorkflow.builder()
            .name("CheckoutOrchestration")
            .workflow(dataFetchPhase)
            .workflow(postProcessingPhase)
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = checkoutOrchestration.execute(context);

    log.info("Checkout result: {}", result.getStatus());
    log.info(
        "Note: Data fetch required all services (fail-fast), but post-processing continued despite analytics failure\n");
  }

  static void main() {
    try {
      example1FailFastForCriticalServices();
      example2CollectAllResultsWithoutFailFast();
      example3IsolatedContextsForIndependentProcessing();
      example4SharedContextForDataAggregation();
      example5AnnotationBasedConfiguration();
      // example6_DatabaseDrivenConfiguration(dataSource); // Requires DataSource
      example7MicroservicesOrchestration();

      log.info("=== Summary ===");
      log.info("failFast=true: Use when ANY failure invalidates the entire operation");
      log.info("failFast=false: Use when you want ALL results, even if some fail");
      log.info("shareContext=true: Use when workflows need to share/aggregate data");
      log.info("shareContext=false: Use for completely independent workflows");
    } catch (Exception e) {
      log.error("Error in examples", e);
    }
  }
}
