package com.workflow.examples.annotation;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.java.JavaAnnotationWorkflowProcessor;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.task.DelayTask;
import com.workflow.task.Task;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive example demonstrating various annotation-based workflow patterns and comparing them
 * with programmatic workflow creation.
 */
@Slf4j
public class ComprehensiveAnnotationExamples {
  public static final String FILENAME = "filename";
  public static final String FILE_CONTENT = "file.content";
  public static final String PARSED_LINES = "parsed.lines";
  public static final String AUDIT_LOGGED = "audit.logged";
  public static final String NOTIFICATION_SENT = "notification.sent";

  // ============================================================================
  // Example 1: Basic Sequential Workflow with Tasks
  // ============================================================================

  @WorkflowAnnotation(
      name = "FileProcessingWorkflow",
      description = "Processes files through multiple stages")
  public static class FileProcessingWorkflow {

    @TaskMethod(name = "ValidateFile", order = 1, description = "Validate file format")
    public Task validateFile() {
      return context -> {
        log.info("Validating file format...");
        String filename = context.getTyped(FILENAME, String.class);
        if (filename == null || !filename.endsWith(".csv")) {
          throw new IllegalArgumentException("Invalid file format");
        }
        context.put("validation.passed", true);
      };
    }

    @TaskMethod(
        name = "ReadFile",
        order = 2,
        description = "Read file contents",
        maxRetries = 3,
        timeoutMs = 5000)
    public Task readFile() {
      return context -> {
        log.info("Reading file...");
        String filename = context.getTyped(FILENAME, String.class);
        String content = "Sample content from " + filename;
        context.put(FILE_CONTENT, content);
      };
    }

    @TaskMethod(name = "ParseData", order = 3, description = "Parse file data")
    public Task parseData() {
      return context -> {
        log.info("Parsing data...");
        String content = context.getTyped(FILE_CONTENT, String.class);
        String[] lines = content.split("\n");
        context.put(PARSED_LINES, lines.length);
      };
    }

    @TaskMethod(name = "GenerateReport", order = 4, description = "Generate processing report")
    public Task generateReport() {
      return context -> {
        log.info("Generating report...");
        int lines = context.getTyped(PARSED_LINES, Integer.class);
        String report = String.format("Processed %d lines from %s", lines, context.get(FILENAME));
        context.put("report", report);
        log.info("Report: {}", report);
      };
    }
  }

  // Equivalent programmatic workflow for comparison
  public static Workflow createFileProcessingWorkflowProgrammatic() {
    return SequentialWorkflow.builder()
        .name("FileProcessingWorkflow")
        .task(
            context -> {
              String filename = context.getTyped(FILENAME, String.class);
              if (filename == null || !filename.endsWith(".csv")) {
                throw new IllegalArgumentException("Invalid file format");
              }
              context.put("validation.passed", true);
            })
        .task(
            context -> {
              String filename = context.getTyped(FILENAME, String.class);
              context.put(FILE_CONTENT, "Sample content from " + filename);
            })
        .task(
            context -> {
              String content = context.getTyped(FILE_CONTENT, String.class);
              context.put(PARSED_LINES, content.split("\n").length);
            })
        .task(
            context -> {
              int lines = context.getTyped(PARSED_LINES, Integer.class);
              String report =
                  String.format("Processed %d lines from %s", lines, context.get(FILENAME));
              context.put("report", report);
            })
        .build();
  }

  // ============================================================================
  // Example 2: Parallel Workflow with Mixed Tasks and Sub-Workflows
  // ============================================================================

  @WorkflowAnnotation(
      name = "DataAggregationWorkflow",
      parallel = true,
      description = "Aggregate data from multiple sources in parallel")
  public static class DataAggregationWorkflow {

    @WorkflowMethod(name = "FetchDatabaseData", order = 1)
    public Workflow fetchDatabaseData() {
      return SequentialWorkflow.builder()
          .name("DatabaseFetch")
          .task(
              context -> {
                log.info("Connecting to database...");
                new DelayTask(Duration.ofMillis(500).toMillis()).execute(context);
              })
          .task(
              context -> {
                log.info("Fetching database records...");
                context.put("db.records", 100);
              })
          .build();
    }

    @TaskMethod(name = "FetchAPIData", order = 2)
    public Task fetchAPIData() {
      return context -> {
        log.info("Calling external API...");
        new DelayTask(Duration.ofMillis(300).toMillis()).execute(context);
        context.put("api.records", 50);
      };
    }

    @TaskMethod(name = "ReadCachedData", order = 3)
    public Task readCachedData() {
      return context -> {
        log.info("Reading cached data...");
        new DelayTask(Duration.ofMillis(100).toMillis()).execute(context);
        context.put("cache.records", 25);
      };
    }

    @WorkflowMethod(name = "AggregateResults", order = 4)
    public Workflow aggregateResults() {
      return SequentialWorkflow.builder()
          .name("Aggregation")
          .task(
              context -> {
                log.info("Aggregating all data sources...");
                int total =
                    context.getTyped("db.records", Integer.class)
                        + context.getTyped("api.records", Integer.class)
                        + context.getTyped("cache.records", Integer.class);
                context.put("total.records", total);
              })
          .task(
              context ->
                  log.info(
                      "Total records aggregated: {}", context.get("total.records", Integer.class)))
          .build();
    }
  }

  // ============================================================================
  // Example 3: Complex Workflow with Error Handling and Retries
  // ============================================================================

  @WorkflowAnnotation(name = "ResilientETLWorkflow", description = "ETL with error handling")
  public static class ResilientETLWorkflow {

    private int extractAttempt = 0;
    private int transformAttempt = 0;

    @TaskMethod(
        name = "ExtractWithRetry",
        order = 1,
        maxRetries = 3,
        timeoutMs = 2000,
        description = "Extract data with retry logic")
    public Task extractWithRetry() {
      return context -> {
        extractAttempt++;
        log.info("Extract attempt #{}", extractAttempt);

        // Simulate transient failure on first attempt
        if (extractAttempt == 1) {
          log.info("Simulating extraction failure...");
          throw new TaskExecutionException("Temporary extraction failure");
        }

        context.put("extracted.data", "Raw data extracted successfully");
        context.put("extract.attempts", extractAttempt);
      };
    }

    @TaskMethod(
        name = "TransformWithRetry",
        order = 2,
        maxRetries = 2,
        description = "Transform data with retry")
    public Task transformWithRetry() {
      return context -> {
        transformAttempt++;
        log.info("Transform attempt #{}", transformAttempt);

        String data = context.getTyped("extracted.data", String.class);
        context.put("transformed.data", data.toUpperCase());
        context.put("transform.attempts", transformAttempt);
      };
    }

    @WorkflowMethod(name = "ValidateAndLoad", order = 3)
    public Workflow validateAndLoad() {
      return SequentialWorkflow.builder()
          .name("ValidateLoad")
          .task(
              context -> {
                log.info("Validating transformed data...");
                String transformed = context.getTyped("transformed.data", String.class);
                if (transformed == null || transformed.isEmpty()) {
                  throw new IllegalStateException("Invalid transformed data");
                }
                context.put("validation.ok", true);
              })
          .task(
              context -> {
                log.info("Loading data to destination...");
                context.put("load.status", "success");
              })
          .build();
    }

    @TaskMethod(name = "GenerateSummary", order = 4)
    public Task generateSummary() {
      return context -> {
        log.info("\n=== ETL Summary ===");
        log.info("Extract attempts: {}", context.get("extract.attempts"));
        log.info("Transform attempts: {}", context.get("transform.attempts"));
        log.info("Load status: {}", context.get("load.status"));
        log.info("===================\n");
      };
    }
  }

  // ============================================================================
  // Example 4: Conditional Execution Pattern (Pure Java approach)
  // ============================================================================

  @WorkflowAnnotation(
      name = "ConditionalProcessingWorkflow",
      description = "Workflow with conditional execution")
  public static class ConditionalProcessingWorkflow {

    @TaskMethod(name = "Initialize", order = 1)
    public Task initialize() {
      return context -> {
        log.info("Initializing workflow...");
        context.put("mode", "development"); // Can be "development" or "production"
        context.put("enable.audit", true);
        context.put("enable.notification", false);
      };
    }

    @TaskMethod(name = "ProcessData", order = 2)
    public Task processData() {
      return context -> {
        log.info("Processing data...");
        context.put("processed.records", 100);
      };
    }

    @WorkflowMethod(name = "ConditionalAudit", order = 3)
    public Workflow conditionalAudit() {
      return SequentialWorkflow.builder()
          .name("AuditWorkflow")
          .task(
              context -> {
                Boolean enableAudit = context.getTyped("enable.audit", Boolean.class);
                if (enableAudit != null && enableAudit) {
                  log.info("Audit enabled - Recording audit trail...");
                  context.put(AUDIT_LOGGED, true);
                } else {
                  log.info("Audit disabled - Skipping audit...");
                  context.put(AUDIT_LOGGED, false);
                }
              })
          .build();
    }

    @WorkflowMethod(name = "ConditionalNotification", order = 4)
    public Workflow conditionalNotification() {
      return SequentialWorkflow.builder()
          .name("NotificationWorkflow")
          .task(
              context -> {
                Boolean enableNotification = context.getTyped("enable.notification", Boolean.class);
                if (enableNotification != null && enableNotification) {
                  log.info("Notifications enabled - Sending notification...");
                  context.put(NOTIFICATION_SENT, true);
                } else {
                  log.info("Notifications disabled - Skipping notification...");
                  context.put(NOTIFICATION_SENT, false);
                }
              })
          .build();
    }

    @TaskMethod(name = "Finalize", order = 5)
    public Task finalTask() {
      return context -> {
        log.info("Finalizing workflow...");
        log.info("Mode: {}", context.getTyped("mode", String.class));
        log.info("Audit logged: {}", context.getTyped(AUDIT_LOGGED, String.class));
        log.info("Notification sent: {}", context.getTyped(NOTIFICATION_SENT, String.class));
      };
    }
  }

  // ============================================================================
  // Main Method - Demonstrates all examples
  // ============================================================================

  public static void main(String[] args) {
    JavaAnnotationWorkflowProcessor processor = new JavaAnnotationWorkflowProcessor();

    log.info("\n{}", "=".repeat(80));
    log.info("COMPREHENSIVE ANNOTATION-BASED WORKFLOW EXAMPLES");
    log.info("{}\n", "=".repeat(80));

    // Example 1: File Processing Workflow
    runExample(
        "Example 1: Basic Sequential File Processing",
        () -> {
          Workflow workflow = processor.buildWorkflow(FileProcessingWorkflow.class);
          WorkflowContext context = new WorkflowContext();
          context.put(FILENAME, "data.csv");
          return workflow.execute(context);
        });

    // Example 1b: Programmatic comparison
    runExample(
        "Example 1b: Same Workflow (Programmatic)",
        () -> {
          Workflow workflow = createFileProcessingWorkflowProgrammatic();
          WorkflowContext context = new WorkflowContext();
          context.put(FILENAME, "data.csv");
          return workflow.execute(context);
        });

    // Example 2: Parallel Data Aggregation
    runExample(
        "Example 2: Parallel Data Aggregation",
        () -> {
          Workflow workflow = processor.buildWorkflow(DataAggregationWorkflow.class);
          WorkflowContext context = new WorkflowContext();
          return workflow.execute(context);
        });

    // Example 3: Resilient ETL with Retries
    runExample(
        "Example 3: Resilient ETL with Retries",
        () -> {
          Workflow workflow = processor.buildWorkflow(ResilientETLWorkflow.class);
          WorkflowContext context = new WorkflowContext();
          return workflow.execute(context);
        });

    // Example 4: Conditional Processing
    runExample(
        "Example 4: Conditional Processing",
        () -> {
          Workflow workflow = processor.buildWorkflow(ConditionalProcessingWorkflow.class);
          WorkflowContext context = new WorkflowContext();
          return workflow.execute(context);
        });

    log.info("\n{}", "=".repeat(80));
    log.info("ALL EXAMPLES COMPLETED");
    log.info("=".repeat(80));
  }

  private static void runExample(String title, WorkflowRunner runner) {
    log.info("\n{}", "=".repeat(80));
    log.info(title);
    log.info("=".repeat(80));

    try {
      long startTime = System.currentTimeMillis();
      WorkflowResult result = runner.run();
      long endTime = System.currentTimeMillis();

      log.info("\n--- Execution Result ---");
      log.info("Status: {}", result.getStatus());
      log.info("Duration: {} ms", (endTime - startTime));
      log.info("Started at: {}", result.getStartedAt());
      log.info("Completed at: {}", result.getCompletedAt());

      if (result.getError() != null) {
        log.info("Error: {}", result.getError().getMessage());
      }
    } catch (Exception e) {
      log.error("Example failed with exception: {}", e.getMessage(), e);
    }
  }

  @FunctionalInterface
  interface WorkflowRunner {
    WorkflowResult run();
  }

  // ============================================================================
  // Key Takeaways
  // ============================================================================

  /*
   * ANNOTATION-BASED WORKFLOW BENEFITS:
   *
   * 1. DECLARATIVE SYNTAX
   *    - Clear separation of workflow structure from implementation
   *    - Self-documenting code with descriptions and metadata
   *    - Easy to understand workflow flow at a glance
   *
   * 2. AUTOMATIC ORDERING
   *    - Methods execute in specified order regardless of declaration order
   *    - Easy to insert new steps without refactoring
   *    - Clear execution sequence
   *
   * 3. BUILT-IN POLICIES
   *    - Retry and timeout policies configured via annotations
   *    - No boilerplate code for common patterns
   *    - Consistent error handling
   *
   * 4. MIXED COMPOSITION
   *    - Can combine individual tasks with sub-workflows
   *    - Both TaskMethod and WorkflowMethod in same definition
   *    - Flexible composition strategies
   *
   * 5. PARALLEL EXECUTION
   *    - Single annotation to enable parallel execution
   *    - No manual thread management
   *    - Automatic coordination
   *
   * 6. TESTABILITY
   *    - Individual methods can be tested in isolation
   *    - Workflow structure can be validated separately
   *    - Easy to mock dependencies (especially with Spring)
   *
   * WHEN TO USE ANNOTATIONS:
   * - Complex workflows with many steps
   * - Workflows that change frequently
   * - When you want clear documentation
   * - When using Spring Framework (for DI and conditionals)
   *
   * WHEN TO USE PROGRAMMATIC:
   * - Simple, one-off workflows
   * - Dynamic workflow construction at runtime
   * - When you need full control over construction logic
   * - Performance-critical scenarios (no reflection overhead)
   */
}
