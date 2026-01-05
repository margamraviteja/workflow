package com.workflow.examples;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;
import java.util.*;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Example demonstrating batch processing with the workflow engine.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Process large datasets in batches
 *   <li>Use parallel processing for efficiency
 *   <li>Handle partial failures gracefully
 *   <li>Aggregate results from multiple batches
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>ETL (Extract, Transform, Load) operations
 *   <li>Bulk data migration
 *   <li>Report generation for large datasets
 *   <li>Batch email/notification sending
 * </ul>
 */
@Slf4j
public class BatchProcessingWorkflowExample {

  record DataRecord(int id, String data, boolean processed) {}

  static class DataLoader implements Task {
    private final int totalRecords;

    DataLoader(int totalRecords) {
      this.totalRecords = totalRecords;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Loading {} records from data source", totalRecords);

      List<DataRecord> records =
          IntStream.range(0, totalRecords)
              .mapToObj(i -> new DataRecord(i, "Data-" + i, false))
              .toList();

      context.put("allRecords", records);
      log.info("Loaded {} records successfully", records.size());
    }
  }

  static class BatchCreator implements Task {
    private final int batchSize;

    BatchCreator(int batchSize) {
      this.batchSize = batchSize;
    }

    @Override
    public void execute(WorkflowContext context) {
      @SuppressWarnings("unchecked")
      List<DataRecord> records = (List<DataRecord>) context.get("allRecords");

      List<List<DataRecord>> batches = new ArrayList<>();
      for (int i = 0; i < records.size(); i += batchSize) {
        int end = Math.min(i + batchSize, records.size());
        batches.add(records.subList(i, end));
      }

      context.put("batches", batches);
      log.info("Created {} batches of size {}", batches.size(), batchSize);
    }
  }

  static class BatchProcessor implements Task {
    private final int batchNumber;

    BatchProcessor(int batchNumber) {
      this.batchNumber = batchNumber;
    }

    @Override
    public void execute(WorkflowContext context) {
      @SuppressWarnings("unchecked")
      List<List<DataRecord>> batches = (List<List<DataRecord>>) context.get("batches");

      if (batchNumber >= batches.size()) {
        return;
      }

      List<DataRecord> batch = batches.get(batchNumber);
      log.info("Processing batch {} with {} records", batchNumber, batch.size());

      // Simulate processing
      List<DataRecord> processed =
          batch.stream()
              .map(
                  dataRecord -> {
                    // Simulate processing time
                    try {
                      Thread.sleep(10);
                    } catch (InterruptedException _) {
                      Thread.currentThread().interrupt();
                    }
                    return new DataRecord(dataRecord.id(), dataRecord.data().toUpperCase(), true);
                  })
              .toList();

      context.put("batch-" + batchNumber + "-result", processed);
      log.info("Completed processing batch {}", batchNumber);
    }
  }

  static class ResultAggregator implements Task {
    private final int numBatches;

    ResultAggregator(int numBatches) {
      this.numBatches = numBatches;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Aggregating results from {} batches", numBatches);

      List<DataRecord> allProcessed = new ArrayList<>();
      for (int i = 0; i < numBatches; i++) {
        @SuppressWarnings("unchecked")
        List<DataRecord> batchResult = (List<DataRecord>) context.get("batch-" + i + "-result");
        if (batchResult != null) {
          allProcessed.addAll(batchResult);
        }
      }

      context.put("processedRecords", allProcessed);
      log.info("Aggregated {} processed records", allProcessed.size());

      // Calculate statistics
      long successfullyProcessed = allProcessed.stream().filter(DataRecord::processed).count();
      context.put("successCount", successfullyProcessed);

      log.info(
          "Processing complete: {} of {} records successful",
          successfullyProcessed,
          allProcessed.size());
    }
  }

  public static void main(String[] args) {
    log.info("=== Batch Processing Workflow Example ===\n");

    int totalRecords = 1000;
    int batchSize = 100;
    int numBatches = (totalRecords + batchSize - 1) / batchSize;

    // Step 1: Load data
    Workflow loadWorkflow =
        SequentialWorkflow.builder()
            .name("DataLoader")
            .task(new DataLoader(totalRecords))
            .task(new BatchCreator(batchSize))
            .build();

    // Step 2: Process batches in parallel
    ParallelWorkflow.ParallelWorkflowBuilder parallelBuilder =
        ParallelWorkflow.builder().name("ParallelBatchProcessor");

    for (int i = 0; i < numBatches; i++) {
      parallelBuilder.task(new BatchProcessor(i));
    }

    Workflow processingWorkflow = parallelBuilder.failFast(false).build();

    // Step 3: Aggregate results
    Workflow aggregateWorkflow =
        SequentialWorkflow.builder()
            .name("ResultAggregator")
            .task(new ResultAggregator(numBatches))
            .build();

    // Complete workflow
    Workflow completeWorkflow =
        SequentialWorkflow.builder()
            .name("BatchProcessingPipeline")
            .workflow(loadWorkflow)
            .workflow(processingWorkflow)
            .workflow(aggregateWorkflow)
            .build();

    // Execute
    WorkflowContext context = new WorkflowContext();
    long startTime = System.currentTimeMillis();

    WorkflowResult result = completeWorkflow.execute(context);

    long duration = System.currentTimeMillis() - startTime;

    log.info("\n=== Batch Processing Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {} ms", duration);
    log.info("Records Processed: {}", context.get("successCount"));

    @SuppressWarnings("unchecked")
    List<DataRecord> processedRecords = (List<DataRecord>) context.get("processedRecords");
    if (processedRecords != null) {
      log.info("Sample processed records:");
      processedRecords.stream().limit(5).forEach(r -> log.info("  {}", r));
    }

    log.info("\n=== Batch Processing Example Complete ===");
  }
}
