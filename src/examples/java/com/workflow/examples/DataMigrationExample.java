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
import java.util.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example: Data Migration Pipeline
 *
 * <p>Demonstrates a production-grade data migration workflow with database validation, schema
 * verification, data extraction, transformation, parallel loading, validation, and comprehensive
 * reporting.
 */
@UtilityClass
@Slf4j
public class DataMigrationExample {

  public static final String USERS = "users";
  public static final String ORDERS = "orders";
  public static final String PRODUCTS = "products";
  public static final String TRANSACTIONS = "transactions";
  public static final String EXTRACTED_RECORDS = "extractedRecords";

  static class ValidateSourceDatabaseTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String sourceDb = (String) context.get("sourceDatabase");
      log.info("Validating source database: {}", sourceDb);

      boolean isAccessible = Math.random() > 0.05;
      int recordCount = 50000;

      if (!isAccessible) {
        throw new IllegalStateException("Source database is not accessible");
      }

      context.put("sourceAccessible", true);
      context.put("sourceRecordCount", recordCount);
      log.info("Source database validation passed. Records: {}", recordCount);
    }

    @Override
    public String getName() {
      return "validate-source-db";
    }
  }

  static class ValidateDestinationDatabaseTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String destDb = (String) context.get("destinationDatabase");
      log.info("Validating destination database: {}", destDb);

      boolean isAccessible = Math.random() > 0.05;

      if (!isAccessible) {
        throw new IllegalStateException("Destination database is not accessible");
      }

      context.put("destinationAccessible", true);
      log.info("Destination database validation passed");
    }

    @Override
    public String getName() {
      return "validate-dest-db";
    }
  }

  static class VerifySchemaTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("Verifying schema compatibility...");

      List<String> tables = new ArrayList<>();
      tables.add(USERS);
      tables.add(ORDERS);
      tables.add(PRODUCTS);
      tables.add(TRANSACTIONS);

      context.put("schemaTables", tables);
      context.put("schemaVerified", true);
      log.info("Schema verification passed. Tables: {}", tables.size());
    }

    @Override
    public String getName() {
      return "verify-schema";
    }
  }

  static class ExtractDataTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      Integer recordCount = (Integer) context.get("sourceRecordCount");
      log.info("Extracting {} records from source...", recordCount);

      long extractedRecords = recordCount;
      String extractTimestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

      context.put(EXTRACTED_RECORDS, extractedRecords);
      context.put("extractTimestamp", extractTimestamp);
      log.info("Data extraction completed. Extracted: {} records", extractedRecords);
    }

    @Override
    public String getName() {
      return "extract-data";
    }
  }

  static class TransformDataTask implements Task {
    private final String tableName;
    private final Random random = new Random();

    TransformDataTask(String tableName) {
      this.tableName = tableName;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Transforming data for table: {}", tableName);

      try {
        Thread.sleep(100);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
      int transformedRecords = (random.nextInt() * 5000) + 5000;

      String key = "transformed_" + tableName;
      context.put(key, transformedRecords);
      log.info("Data transformation for {} completed", tableName);
    }

    @Override
    public String getName() {
      return "transform-" + tableName;
    }
  }

  static class LoadDataBatchTask implements Task {
    private final int batchNumber;
    private final String tableName;

    LoadDataBatchTask(int batchNumber, String tableName) {
      this.batchNumber = batchNumber;
      this.tableName = tableName;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Loading batch {} to table {}", batchNumber, tableName);

      boolean loadSuccess = Math.random() > 0.1;

      if (!loadSuccess) {
        throw new IllegalStateException("Batch load failed for table " + tableName);
      }

      String batchKey = tableName + "_batch_" + batchNumber;
      context.put(batchKey, 1000);

      log.info("Batch {} loaded successfully", batchNumber);
    }

    @Override
    public String getName() {
      return "load-" + tableName + "-batch-" + batchNumber;
    }
  }

  static class ValidateMigratedDataTask implements Task {
    private final String tableName;

    ValidateMigratedDataTask(String tableName) {
      this.tableName = tableName;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Validating migrated data for table: {}", tableName);

      boolean integrityCheck = Math.random() > 0.05;
      if (!integrityCheck) {
        throw new IllegalStateException("Data integrity check failed for table: " + tableName);
      }

      String validationKey = "validated_" + tableName;
      context.put(validationKey, true);
      log.info("Data validation for {} passed", tableName);
    }

    @Override
    public String getName() {
      return "validate-" + tableName;
    }
  }

  static class GenerateMigrationReportTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("Generating migration report...");

      Long extractedRecords = (Long) context.get(EXTRACTED_RECORDS);
      String extractTimestamp = (String) context.get("extractTimestamp");
      String endTimestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

      Map<String, Object> report = new HashMap<>();
      report.put("recordsExtracted", extractedRecords);
      report.put("startTime", extractTimestamp);
      report.put("endTime", endTimestamp);
      report.put("migrationStatus", "SUCCESS");

      context.put("migrationReport", report);
      log.info("Migration report generated. Total records: {}", extractedRecords);
    }

    @Override
    public String getName() {
      return "generate-report";
    }
  }

  static void executeMigration() {
    log.info("\n=== Data Migration Pipeline Workflow Example ===\n");

    WorkflowContext context = new WorkflowContext();
    context.put("sourceDatabase", "legacy-db.example.com");
    context.put("destinationDatabase", "new-db.example.com");

    RetryPolicy dbRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 4;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.exponential(300);
          }
        };

    RetryPolicy loadRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 3;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.linear(500);
          }
        };

    TaskWorkflow validateSource =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ValidateSourceDatabaseTask())
                .retryPolicy(dbRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    TaskWorkflow validateDest =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ValidateDestinationDatabaseTask())
                .retryPolicy(dbRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    ParallelWorkflow validateDatabases =
        ParallelWorkflow.builder()
            .name("validate-databases")
            .workflow(validateSource)
            .workflow(validateDest)
            .shareContext(true)
            .failFast(true)
            .build();

    TaskWorkflow verifySchema =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new VerifySchemaTask())
                .retryPolicy(dbRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow extractData =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ExtractDataTask())
                .retryPolicy(dbRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(60000))
                .build());

    List<com.workflow.Workflow> transformWorkflows = new ArrayList<>();
    for (String table : new String[] {USERS, ORDERS, PRODUCTS, TRANSACTIONS}) {
      transformWorkflows.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new TransformDataTask(table))
                  .retryPolicy(RetryPolicy.NONE)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(30000))
                  .build()));
    }

    ParallelWorkflow transformData =
        ParallelWorkflow.builder()
            .name("transform-data")
            .workflows(transformWorkflows)
            .shareContext(true)
            .failFast(true)
            .build();

    List<com.workflow.Workflow> loadWorkflows = new ArrayList<>();
    for (String table : new String[] {USERS, ORDERS, PRODUCTS, TRANSACTIONS}) {
      for (int batch = 1; batch <= 5; batch++) {
        loadWorkflows.add(
            new TaskWorkflow(
                TaskDescriptor.builder()
                    .task(new LoadDataBatchTask(batch, table))
                    .retryPolicy(loadRetry)
                    .timeoutPolicy(TimeoutPolicy.ofMillis(20000))
                    .build()));
      }
    }

    ParallelWorkflow loadData =
        ParallelWorkflow.builder()
            .name("load-data-batches")
            .workflows(loadWorkflows)
            .shareContext(true)
            .failFast(false)
            .build();

    List<com.workflow.Workflow> validateWorkflows = new ArrayList<>();
    for (String table : new String[] {USERS, ORDERS, PRODUCTS, TRANSACTIONS}) {
      validateWorkflows.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new ValidateMigratedDataTask(table))
                  .retryPolicy(dbRetry)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(15000))
                  .build()));
    }

    ParallelWorkflow validateMigration =
        ParallelWorkflow.builder()
            .name("validate-migration")
            .workflows(validateWorkflows)
            .shareContext(true)
            .failFast(true)
            .build();

    TaskWorkflow generateReport =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new GenerateMigrationReportTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    SequentialWorkflow migrationPipeline =
        SequentialWorkflow.builder()
            .name("data-migration-pipeline")
            .workflow(validateDatabases)
            .workflow(verifySchema)
            .workflow(extractData)
            .workflow(transformData)
            .workflow(loadData)
            .workflow(validateMigration)
            .workflow(generateReport)
            .build();

    WorkflowResult result = migrationPipeline.execute(context);

    log.info("\n=== Migration Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration());
    log.info("Source Records: {}", context.get(EXTRACTED_RECORDS));

    @SuppressWarnings("unchecked")
    Map<String, Object> report = (Map<String, Object>) context.get("migrationReport");
    if (report != null) {
      log.info("Migration Report:");
      report.forEach((k, v) -> log.info("  {}: {}", k, v));
    }

    if (result.getError() != null) {
      log.error("Error: {}", result.getError().getMessage());
    }
  }

  public static void main(String[] args) {
    executeMigration();
  }
}
