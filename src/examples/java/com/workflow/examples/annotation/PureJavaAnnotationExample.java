package com.workflow.examples.annotation;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.java.JavaAnnotationWorkflowProcessor;
import com.workflow.context.WorkflowContext;
import com.workflow.task.DelayTask;
import com.workflow.task.FileReadTask;
import com.workflow.task.FileWriteTask;
import com.workflow.task.Task;
import java.nio.file.Path;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Example demonstrating pure Java annotation-based workflow definition without Spring framework
 * dependencies.
 */
@Slf4j
@UtilityClass
public class PureJavaAnnotationExample {

  public static final String FILE_CONTENT = "file.content";
  public static final String COMPLETED = "completed";

  /** Simple sequential data processing workflow defined using annotations. */
  @WorkflowAnnotation(
      name = "DataProcessingWorkflow",
      description = "Processes data files sequentially",
      tags = {"data", "etl"})
  public static class DataProcessingWorkflowDefinition {

    @WorkflowMethod(name = "ValidationPhase", order = 1, description = "Validate input data")
    public Workflow createValidationWorkflow() {
      return SequentialWorkflow.builder()
          .name("ValidationWorkflow")
          .task(
              context -> {
                log.info("Validating data schema...");
                context.put("schema.valid", true);
              })
          .task(
              context -> {
                log.info("Validating data quality...");
                context.put("quality.score", 0.95);
              })
          .build();
    }

    @TaskMethod(
        name = "ReadInputFile",
        order = 2,
        description = "Read data from input file",
        maxRetries = 3,
        timeoutMs = 5000)
    public Task createReadTask() {
      return new FileReadTask(Path.of("input.csv"), FILE_CONTENT);
    }

    @TaskMethod(name = "TransformData", order = 3, description = "Transform the data")
    public Task createTransformTask() {
      return context -> {
        log.info("Transforming data...");
        String data = context.getTyped(FILE_CONTENT, String.class);
        String transformed = data.toUpperCase();
        context.put("transformed.data", transformed);
      };
    }

    @TaskMethod(
        name = "WriteOutputFile",
        order = 4,
        description = "Write transformed data to output",
        maxRetries = 2)
    public Task createWriteTask() {
      return new FileWriteTask("transformed.data", Path.of("output.csv"));
    }

    @TaskMethod(name = "Cleanup", order = 5, description = "Cleanup temporary resources")
    public Task createCleanupTask() {
      return context -> {
        log.info("Cleaning up temporary files...");
        context.put("cleanup.done", true);
      };
    }
  }

  /** Parallel workflow example - tasks execute concurrently. */
  @WorkflowAnnotation(
      name = "ParallelDataProcessing",
      parallel = true,
      description = "Process multiple data sources in parallel")
  public static class ParallelWorkflowDefinition {

    @TaskMethod(name = "ProcessSource1", order = 1)
    public Task createProcessSource1() {
      return context -> {
        log.info("Processing data source 1...");
        new DelayTask(Duration.ofSeconds(2).toMillis()).execute(context);
        context.put("source1.status", COMPLETED);
      };
    }

    @TaskMethod(name = "ProcessSource2", order = 2)
    public Task createProcessSource2() {
      return context -> {
        log.info("Processing data source 2...");
        new DelayTask(Duration.ofSeconds(1).toMillis()).execute(context);
        context.put("source2.status", COMPLETED);
      };
    }

    @TaskMethod(name = "ProcessSource3", order = 3)
    public Task createProcessSource3() {
      return context -> {
        log.info("Processing data source 3...");
        new DelayTask(Duration.ofMillis(500).toMillis()).execute(context);
        context.put("source3.status", COMPLETED);
      };
    }

    @WorkflowMethod(name = "AggregateResults", order = 4)
    public Workflow createAggregationWorkflow() {
      return SequentialWorkflow.builder()
          .name("AggregationWorkflow")
          .task(
              context -> {
                log.info("Aggregating results from all sources...");
                boolean allCompleted =
                    COMPLETED.equals(context.get("source1.status"))
                        && COMPLETED.equals(context.get("source2.status"))
                        && COMPLETED.equals(context.get("source3.status"));
                context.put("aggregation.complete", allCompleted);
              })
          .build();
    }
  }

  /** Main method demonstrating usage of the pure Java annotation processor. */
  static void main() {
    log.info("=== Pure Java Annotation-Based Workflow Example ===\n");

    // Create the annotation processor
    JavaAnnotationWorkflowProcessor processor = new JavaAnnotationWorkflowProcessor();

    // Example 1: Sequential workflow
    log.info("Example 1: Sequential Data Processing Workflow");
    log.info("-----------------------------------------------");
    Workflow sequentialWorkflow = processor.buildWorkflow(DataProcessingWorkflowDefinition.class);
    WorkflowContext context1 = new WorkflowContext();
    context1.put(FILE_CONTENT, "sample data to process");

    var result1 = sequentialWorkflow.execute(context1);
    log.info("Workflow Status: {}", result1.getStatus());
    log.info("Execution Time: {}", result1.getExecutionDuration());
    log.info("Context: {}", context1);

    log.info("\n\nExample 2: Parallel Data Processing Workflow");
    log.info("----------------------------------------------");

    // Example 2: Parallel workflow
    Workflow parallelWorkflow = processor.buildWorkflow(ParallelWorkflowDefinition.class);
    WorkflowContext context2 = new WorkflowContext();

    var result2 = parallelWorkflow.execute(context2);
    log.info("Workflow Status: {}", result2.getStatus());
    log.info("Execution Time: {}", result2.getExecutionDuration());
    log.info("Context: {}", context2);

    log.info("\n=== Workflow Execution Complete ===");
  }
}
