package com.workflow.examples.datapipeline;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.task.AbstractTask;
import com.workflow.task.TaskDescriptor;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive Data Pipeline Processing Example demonstrating an ETL (Extract, Transform, Load)
 * workflow for big data processing.
 *
 * <p><b>Business Scenario:</b> E-commerce analytics data pipeline that:
 *
 * <ul>
 *   <li>Extracts data from multiple sources (Database, S3, APIs)
 *   <li>Transforms and enriches the data
 *   <li>Validates data quality
 *   <li>Loads into data warehouse
 *   <li>Updates materialized views and caches
 * </ul>
 *
 * <p><b>Pipeline Stages:</b>
 *
 * <pre>
 * 1. Data Extraction (Parallel)
 *    - Extract from Database (orders, customers)
 *    - Extract from S3 (clickstream logs)
 *    - Extract from APIs (product catalog)
 *
 * 2. Data Validation
 *    - Schema validation
 *    - Data quality checks
 *    - Duplicate detection
 *
 * 3. Data Transformation (Parallel)
 *    - Clean and normalize
 *    - Enrich with additional data
 *    - Aggregate metrics
 *
 * 4. Data Loading (Sequential)
 *    - Load to staging tables
 *    - Apply transformations
 *    - Load to production tables
 *
 * 5. Post-Processing
 *    - Update materialized views
 *    - Refresh caches
 *    - Generate reports
 *    - Send alerts
 * </pre>
 *
 * <p><b>Features Demonstrated:</b>
 *
 * <ul>
 *   <li>Parallel data extraction for performance
 *   <li>Rate limiting for API calls
 *   <li>Retry policies for transient failures
 *   <li>Circuit breakers for external dependencies
 *   <li>Conditional processing based on data volume
 *   <li>Fallback strategies for non-critical operations
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * DataPipelineWorkflow pipeline = new DataPipelineWorkflow();
 * WorkflowContext context = new WorkflowContext();
 *
 * context.put("processingDate", "2025-01-15");
 * context.put("batchId", "BATCH-12345");
 *
 * WorkflowResult result = pipeline.execute(context);
 *
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     int recordsProcessed = context.getTyped("recordsProcessed", Integer.class);
 *     System.out.println("Pipeline completed: " + recordsProcessed + " records");
 * }
 * }</pre>
 */
@Slf4j
public class DataPipelineWorkflow {

  public static final String LOAD_TYPE = "loadType";
  public static final String BATCH_ID = "batchId";
  public static final String DATA_QUALITY_SCORE = "dataQualityScore";

  /**
   * Build the complete data pipeline workflow with fault tolerance and optimization.
   *
   * @return configured workflow
   */
  public Workflow buildWorkflow() {
    // Stage 1: Data Extraction (Parallel for performance)
    Workflow extractionWorkflow = buildExtractionWorkflow();

    // Stage 2: Data Validation
    Workflow validationWorkflow =
        SequentialWorkflow.builder()
            .name("DataValidation")
            .task(new SchemaValidationTask())
            .task(new DataQualityCheckTask())
            .task(new DuplicateDetectionTask())
            .build();

    // Stage 3: Data Transformation (Parallel processing)
    Workflow transformationWorkflow = buildTransformationWorkflow();

    // Stage 4: Data Loading (Sequential with checkpoints)
    Workflow loadingWorkflow = buildLoadingWorkflow();

    // Stage 5: Post-Processing
    Workflow postProcessingWorkflow = buildPostProcessingWorkflow();

    // Conditional: Different paths for full vs incremental loads
    Workflow pipelineWorkflow =
        ConditionalWorkflow.builder()
            .name("LoadTypeRouter")
            .condition(ctx -> "FULL".equals(ctx.get(LOAD_TYPE)))
            .whenTrue(buildFullLoadPipeline())
            .whenFalse(buildIncrementalLoadPipeline())
            .build();

    // Main Pipeline
    return SequentialWorkflow.builder()
        .name("DataPipeline")
        .workflow(extractionWorkflow)
        .workflow(validationWorkflow)
        .workflow(transformationWorkflow)
        .workflow(loadingWorkflow)
        .workflow(postProcessingWorkflow)
        .workflow(pipelineWorkflow)
        .build();
  }

  private Workflow buildExtractionWorkflow() {
    // Database extraction with retry
    TaskDescriptor dbExtractor =
        TaskDescriptor.builder()
            .task(new ExtractFromDatabaseTask())
            .name("DatabaseExtractor")
            .retryPolicy(
                RetryPolicy.limitedRetriesWithBackoff(
                    3, RetryPolicy.BackoffStrategy.exponentialWithJitter(1000, 10000)))
            .build();

    // S3 extraction
    Workflow s3Extractor = new TaskWorkflow(new ExtractFromS3Task());

    // API extraction
    Workflow apiExtractor = new TaskWorkflow(new ExtractFromAPITask());

    return ParallelWorkflow.builder()
        .name("DataExtraction")
        .workflow(new TaskWorkflow(dbExtractor))
        .workflow(s3Extractor)
        .workflow(apiExtractor)
        .failFast(false) // Collect all available data even if one source fails
        .shareContext(true)
        .build();
  }

  private Workflow buildTransformationWorkflow() {
    return ParallelWorkflow.builder()
        .name("DataTransformation")
        .task(new CleanAndNormalizeTask())
        .task(new EnrichDataTask())
        .task(new AggregateMetricsTask())
        .task(new CalculateDerivedFieldsTask())
        .shareContext(true)
        .build();
  }

  private Workflow buildLoadingWorkflow() {
    return SequentialWorkflow.builder()
        .name("DataLoading")
        .task(new LoadToStagingTask())
        .task(new ValidateStagingDataTask())
        .task(new ApplyTransformationsTask())
        .task(new LoadToProductionTask())
        .task(new CreateCheckpointTask())
        .build();
  }

  private Workflow buildPostProcessingWorkflow() {
    // Materialized view refresh with fallback
    Workflow viewRefresh =
        FallbackWorkflow.builder()
            .name("MaterializedViewRefresh")
            .primary(new TaskWorkflow(new RefreshMaterializedViewsTask()))
            .fallback(new TaskWorkflow(new ScheduleViewRefreshTask()))
            .build();

    return SequentialWorkflow.builder()
        .name("PostProcessing")
        .workflow(viewRefresh)
        .task(new RefreshCachesTask())
        .task(new GenerateReportsTask())
        .task(new SendCompletionNotificationTask())
        .build();
  }

  private Workflow buildFullLoadPipeline() {
    return SequentialWorkflow.builder()
        .name("FullLoadPipeline")
        .task(new TruncateTargetTablesTask())
        .workflow(buildExtractionWorkflow())
        .workflow(buildTransformationWorkflow())
        .workflow(buildLoadingWorkflow())
        .build();
  }

  private Workflow buildIncrementalLoadPipeline() {
    return SequentialWorkflow.builder()
        .name("IncrementalLoadPipeline")
        .task(new DetermineIncrementalRangeTask())
        .workflow(buildExtractionWorkflow())
        .workflow(buildTransformationWorkflow())
        .task(new MergeIncrementalDataTask())
        .build();
  }

  public static void main(String[] args) {
    DataPipelineWorkflow pipeline = new DataPipelineWorkflow();
    Workflow workflow = pipeline.buildWorkflow();

    // Setup context
    WorkflowContext context = new WorkflowContext();

    // Pipeline configuration
    context.put(BATCH_ID, "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    context.put("processingDate", "2025-01-15");
    context.put(LOAD_TYPE, "FULL"); // FULL or INCREMENTAL
    context.put("environment", "PRODUCTION");

    // Data sources
    Map<String, String> dataSources = new HashMap<>();
    dataSources.put("database", "postgresql://analytics-db:5432");
    dataSources.put("s3Bucket", "s3://analytics-data/clickstream/");
    dataSources.put("apiEndpoint", "https://api.example.com/products");
    context.put("dataSources", dataSources);

    // Processing parameters
    context.put("chunkSize", 10000);
    context.put("maxConcurrency", 4);
    context.put("enableDataQuality", true);

    // Execute pipeline
    log.info("=".repeat(70));
    log.info("Starting Data Pipeline Workflow");
    log.info("Batch ID: {}", context.get(BATCH_ID));
    log.info("Processing Date: {}", context.get("processingDate"));
    log.info("Load Type: {}", context.get(LOAD_TYPE));
    log.info("=".repeat(70));

    Instant start = Instant.now();

    try {
      WorkflowResult result = workflow.execute(context);

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);

      log.info("=".repeat(70));
      log.info("Pipeline Status: {}", result.getStatus());
      log.info("Execution Time: {} seconds", duration.getSeconds());
      log.info("=".repeat(70));

      if (result.getStatus() == WorkflowStatus.SUCCESS) {
        log.info("✓ Records Extracted: {}", context.get("recordsExtracted"));
        log.info("✓ Records Transformed: {}", context.get("recordsTransformed"));
        log.info("✓ Records Loaded: {}", context.get("recordsLoaded"));
        log.info("✓ Data Quality Score: {}%", context.get(DATA_QUALITY_SCORE));
        log.info("✓ Checkpoint: {}", context.get("checkpointId"));
        log.info("✓ Reports Generated: {}", context.get("reportsGenerated"));
      } else {
        log.error("✗ Pipeline Failed: {}", result.getError().getMessage());
        log.error("✗ Failed Stage: {}", context.get("failedStage"));
        log.error("✗ Rollback Status: {}", context.get("rollbackStatus"));
      }

    } catch (Exception e) {
      log.error("Unexpected error during pipeline execution", e);
    }
  }

  // ==================== Task Implementations ====================

  // Extraction Tasks

  static class ExtractFromDatabaseTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Extracting data from database...");
      simulateDataExtraction(2000);

      List<Map<String, Object>> records = generateMockRecords(5000);
      context.put("dbRecords", records);
      context.put("dbRecordsCount", records.size());

      log.info("✓ Extracted {} records from database", records.size());
    }
  }

  static class ExtractFromS3Task extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Extracting data from S3...");
      simulateDataExtraction(1500);

      List<Map<String, Object>> records = generateMockRecords(10000);
      context.put("s3Records", records);
      context.put("s3RecordsCount", records.size());

      log.info("✓ Extracted {} records from S3", records.size());
    }
  }

  static class ExtractFromAPITask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Extracting data from API...");
      simulateDataExtraction(1000);

      List<Map<String, Object>> records = generateMockRecords(2000);
      context.put("apiRecords", records);
      context.put("apiRecordsCount", records.size());

      log.info("✓ Extracted {} records from API", records.size());
    }
  }

  // Validation Tasks

  static class SchemaValidationTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Validating data schema...");
      simulateProcessing(500);

      int totalRecords = getTotalRecordCount(context);
      int invalidRecords = (int) (totalRecords * 0.02); // 2% invalid

      if (invalidRecords > totalRecords * 0.05) {
        throw new TaskExecutionException("Too many schema validation errors: " + invalidRecords);
      }

      context.put("schemaValidationPassed", true);
      context.put("invalidSchemaRecords", invalidRecords);
      log.info("✓ Schema validation passed ({} invalid records filtered)", invalidRecords);
    }
  }

  static class DataQualityCheckTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Performing data quality checks...");
      simulateProcessing(800);

      // Simulate quality metrics
      double qualityScore = 92.5 + (Math.random() * 7); // 92.5-99.5%
      context.put(DATA_QUALITY_SCORE, Math.round(qualityScore * 10) / 10.0);
      context.put("dataQualityPassed", qualityScore > 90);

      log.info("✓ Data quality score: {}%", context.get(DATA_QUALITY_SCORE));
    }
  }

  static class DuplicateDetectionTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Detecting duplicates...");
      simulateProcessing(600);

      int totalRecords = getTotalRecordCount(context);
      int duplicates = (int) (totalRecords * 0.01); // 1% duplicates

      context.put("duplicatesFound", duplicates);
      context.put("duplicatesRemoved", duplicates);

      log.info("✓ Detected and removed {} duplicates", duplicates);
    }
  }

  // Transformation Tasks

  static class CleanAndNormalizeTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Cleaning and normalizing data...");
      simulateProcessing(1200);

      int totalRecords = getTotalRecordCount(context);
      context.put("recordsCleaned", totalRecords);

      log.info("✓ Cleaned and normalized {} records", totalRecords);
    }
  }

  static class EnrichDataTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Enriching data with additional information...");
      simulateProcessing(1500);

      int totalRecords = getTotalRecordCount(context);
      int enriched = (int) (totalRecords * 0.85); // 85% enriched

      context.put("recordsEnriched", enriched);
      log.info("✓ Enriched {} records", enriched);
    }
  }

  static class AggregateMetricsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Aggregating metrics...");
      simulateProcessing(800);

      Map<String, Object> metrics = new HashMap<>();
      metrics.put("totalSales", 1250000.0);
      metrics.put("totalOrders", 5432);
      metrics.put("avgOrderValue", 230.15);
      metrics.put("uniqueCustomers", 3210);

      context.put("aggregatedMetrics", metrics);
      log.info("✓ Calculated aggregated metrics");
    }
  }

  static class CalculateDerivedFieldsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Calculating derived fields...");
      simulateProcessing(1000);

      int totalRecords = getTotalRecordCount(context);
      context.put("derivedFieldsCalculated", totalRecords);

      log.info("✓ Calculated derived fields for {} records", totalRecords);
    }
  }

  // Loading Tasks

  static class LoadToStagingTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Loading data to staging tables...");
      simulateProcessing(2000);

      int totalRecords = getTotalRecordCount(context);
      context.put("recordsInStaging", totalRecords);

      log.info("✓ Loaded {} records to staging", totalRecords);
    }
  }

  static class ValidateStagingDataTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Validating staging data...");
      simulateProcessing(800);

      context.put("stagingValidationPassed", true);
      log.info("✓ Staging data validated");
    }
  }

  static class ApplyTransformationsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Applying final transformations...");
      simulateProcessing(1500);

      context.put("transformationsApplied", true);
      log.info("✓ Transformations applied");
    }
  }

  static class LoadToProductionTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Loading data to production tables...");
      simulateProcessing(2500);

      int totalRecords = getTotalRecordCount(context);
      context.put("recordsLoaded", totalRecords);
      context.put("recordsExtracted", totalRecords);
      context.put("recordsTransformed", totalRecords);

      log.info("✓ Loaded {} records to production", totalRecords);
    }
  }

  static class CreateCheckpointTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Creating checkpoint...");
      simulateProcessing(300);

      String checkpointId = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put("checkpointId", checkpointId);

      log.info("✓ Checkpoint created: {}", checkpointId);
    }
  }

  // Post-Processing Tasks

  static class RefreshMaterializedViewsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Refreshing materialized views...");
      simulateProcessing(3000);

      context.put("materializedViewsRefreshed", true);
      context.put("viewsRefreshed", 15);

      log.info("✓ Refreshed 15 materialized views");
    }
  }

  static class ScheduleViewRefreshTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.warn("Scheduling deferred view refresh...");
      context.put("viewRefreshScheduled", true);
      log.warn("⚠ View refresh scheduled for later");
    }
  }

  static class RefreshCachesTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Refreshing caches...");
      simulateProcessing(500);

      context.put("cachesRefreshed", true);
      log.info("✓ Caches refreshed");
    }
  }

  static class GenerateReportsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Generating reports...");
      simulateProcessing(1000);

      List<String> reports =
          Arrays.asList("DailySalesReport", "CustomerAnalytics", "InventoryStatus");

      context.put("reportsGenerated", reports);
      log.info("✓ Generated {} reports", reports.size());
    }
  }

  static class SendCompletionNotificationTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Sending completion notification...");
      simulateProcessing(200);

      String batchId = context.getTyped(BATCH_ID, String.class);
      context.put("notificationSent", true);

      log.info("✓ Notification sent for batch: {}", batchId);
    }
  }

  // Full Load Tasks

  static class TruncateTargetTablesTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Truncating target tables...");
      simulateProcessing(500);

      context.put("tablesTruncated", true);
      log.info("✓ Target tables truncated");
    }
  }

  // Incremental Load Tasks

  static class DetermineIncrementalRangeTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Determining incremental range...");
      simulateProcessing(300);

      context.put("incrementalFromDate", "2025-01-14 00:00:00");
      context.put("incrementalToDate", "2025-01-15 00:00:00");

      log.info("✓ Incremental range determined");
    }
  }

  static class MergeIncrementalDataTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Merging incremental data...");
      simulateProcessing(2000);

      int totalRecords = getTotalRecordCount(context);
      context.put("recordsMerged", totalRecords);

      log.info("✓ Merged {} incremental records", totalRecords);
    }
  }

  // Helper methods

  private static void simulateDataExtraction(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  private static void simulateProcessing(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  private static List<Map<String, Object>> generateMockRecords(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            _ -> {
              Map<String, Object> data = new HashMap<>();
              data.put("id", UUID.randomUUID().toString());
              data.put("value", Math.random() * 1000);
              data.put("timestamp", Instant.now());
              return data;
            })
        .toList();
  }

  private static int getTotalRecordCount(WorkflowContext context) {
    int dbCount = context.getTyped("dbRecordsCount", Integer.class, 0);
    int s3Count = context.getTyped("s3RecordsCount", Integer.class, 0);
    int apiCount = context.getTyped("apiRecordsCount", Integer.class, 0);
    return dbCount + s3Count + apiCount;
  }
}
