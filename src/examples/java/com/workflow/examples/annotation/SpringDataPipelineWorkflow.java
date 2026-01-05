package com.workflow.examples.annotation;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.task.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Example demonstrating Spring-based annotation workflow with dependency injection and conditional
 * execution.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Use {@code @Autowired} to inject dependencies into workflow/task methods
 *   <li>Use {@code @ConditionalOnProperty} to conditionally enable workflows
 *   <li>Integrate with Spring's component scanning
 * </ul>
 *
 * <p>To run this example in a Spring Boot application:
 *
 * <pre>{@code
 * @SpringBootApplication
 * public class WorkflowApplication {
 *   public static void main(String[] args) {
 *     SpringApplication.run(WorkflowApplication.class, args);
 *   }
 *
 *   @Bean
 *   public CommandLineRunner runWorkflow(
 *       SpringAnnotationWorkflowProcessor processor,
 *       SpringDataPipelineWorkflow workflowDef) {
 *     return args -> {
 *       Workflow workflow = processor.buildWorkflow(workflowDef);
 *       workflow.execute(new WorkflowContext());
 *     };
 *   }
 * }
 * }</pre>
 *
 * <p>application.properties:
 *
 * <pre>
 * data.validation.enabled=true
 * data.enrichment.enabled=false
 * data.output.enabled=true
 * </pre>
 */
@Slf4j
@Component
@WorkflowAnnotation(
    name = "SpringDataPipeline",
    description = "Spring-enabled data pipeline with conditional steps",
    tags = {"spring", "data-pipeline", "etl"})
public class SpringDataPipelineWorkflow {

  public static final String RAW_DATA = "raw.data";
  public static final String ENRICHED_DATA = "enriched.data";

  // These would be real Spring beans in a production application
  public static class DataSource {
    public String fetchData() {
      return "raw_data_from_source";
    }
  }

  public static class ValidationService {
    public boolean validate(String data) {
      log.info("Validating data: {}", data);
      return data != null && !data.isEmpty();
    }
  }

  public static class EnrichmentService {
    public String enrich(String data) {
      log.info("Enriching data: {}", data);
      return data + "_enriched";
    }
  }

  public static class OutputService {
    public void write(String data) {
      log.info("Writing data to output: {}", data);
    }
  }

  /**
   * Workflow method that uses dependency injection. This workflow only executes if the property
   * "data.validation.enabled" is set to "true".
   */
  @WorkflowMethod(name = "ValidationWorkflow", order = 1, description = "Validates incoming data")
  @ConditionalOnProperty(name = "data.validation.enabled", havingValue = "true")
  public Workflow createValidationWorkflow(@Autowired ValidationService validationService) {
    log.info("Creating validation workflow with injected ValidationService");

    return SequentialWorkflow.builder()
        .name("DataValidation")
        .task(
            context -> {
              String data = context.getTyped(RAW_DATA, String.class);
              boolean isValid = validationService.validate(data);
              context.put("validation.passed", isValid);

              if (!isValid) {
                throw new IllegalStateException("Data validation failed");
              }
            })
        .task(
            context -> {
              log.info("Validation completed successfully");
              context.put("validation.timestamp", System.currentTimeMillis());
            })
        .build();
  }

  /** Task method with dependency injection. Reads data from the configured data source. */
  @TaskMethod(
      name = "IngestData",
      order = 2,
      description = "Ingests data from source",
      maxRetries = 3,
      timeoutMs = 10000)
  public Task createIngestTask(@Autowired DataSource dataSource) {
    log.info("Creating ingest task with injected DataSource");

    return context -> {
      log.info("Ingesting data from source");
      String data = dataSource.fetchData();
      context.put(RAW_DATA, data);
      context.put("ingest.timestamp", System.currentTimeMillis());
    };
  }

  /**
   * Conditional enrichment step - only runs if enrichment is enabled. This demonstrates how Spring
   * conditional annotations work with workflow methods.
   */
  @TaskMethod(
      name = "EnrichData",
      order = 3,
      description = "Enriches data with additional information")
  @ConditionalOnProperty(name = "data.enrichment.enabled", havingValue = "true")
  public Task createEnrichmentTask(@Autowired EnrichmentService enrichmentService) {
    log.info("Creating enrichment task with injected EnrichmentService");

    return context -> {
      String rawData = context.getTyped(RAW_DATA, String.class);
      String enrichedData = enrichmentService.enrich(rawData);
      context.put(ENRICHED_DATA, enrichedData);
      log.info("Data enriched successfully");
    };
  }

  /** Transform task without conditional execution - always runs. */
  @TaskMethod(
      name = "TransformData",
      order = 4,
      description = "Transforms data to target format",
      maxRetries = 2)
  public Task createTransformTask() {
    return context -> {
      String data =
          context.containsKey(ENRICHED_DATA)
              ? context.getTyped(ENRICHED_DATA, String.class)
              : context.getTyped(RAW_DATA, String.class);

      String transformed = data.toUpperCase().replace("_", "-");
      context.put("transformed.data", transformed);
      log.info("Data transformed: {}", transformed);
    };
  }

  /** Output workflow with dependency injection for writing results. */
  @WorkflowMethod(
      name = "OutputWorkflow",
      order = 5,
      description = "Writes processed data to output")
  @ConditionalOnProperty(name = "data.output.enabled", havingValue = "true", matchIfMissing = true)
  public Workflow createOutputWorkflow(@Autowired OutputService outputService) {
    log.info("Creating output workflow with injected OutputService");

    return SequentialWorkflow.builder()
        .name("OutputData")
        .task(
            context -> {
              String data = context.getTyped("transformed.data", String.class);
              outputService.write(data);
              context.put("output.timestamp", System.currentTimeMillis());
            })
        .task(
            context -> {
              log.info("Pipeline execution completed");
              long ingestTime = context.getTyped("ingest.timestamp", Long.class);
              long outputTime = context.getTyped("output.timestamp", Long.class);
              long duration = outputTime - ingestTime;
              context.put("pipeline.duration.ms", duration);
              log.info("Total pipeline duration: {} ms", duration);
            })
        .build();
  }

  /** Cleanup task - always runs at the end. */
  @TaskMethod(name = "Cleanup", order = 6, description = "Cleanup temporary resources")
  public Task createCleanupTask() {
    return context -> {
      log.info("Performing cleanup operations");
      context.put("cleanup.completed", true);
    };
  }
}
