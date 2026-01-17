package com.workflow.examples.annotation;

import com.workflow.Workflow;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowRef;
import com.workflow.annotation.java.JavaAnnotationWorkflowProcessor;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive example demonstrating workflow composition using the @WorkflowRef annotation.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Create reusable workflow components annotated with @WorkflowAnnotation
 *   <li>Compose multiple workflows into a larger workflow using @WorkflowRef
 *   <li>Control execution order with the order parameter
 *   <li>Create multi-level composite workflows
 *   <li>Share context between parent and included workflows
 * </ul>
 *
 * <p>This approach follows the Composite Pattern, allowing workflows to be built from other
 * workflows while maintaining clean separation of concerns.
 */
@Slf4j
@UtilityClass
public class CompositeWorkflowExample {

  public static final String INPUT_DATA = "input.data";

  /** Demonstration of building and executing a composite workflow. */
  static void main() {
    log.info("Starting Composite Workflow Example");

    JavaAnnotationWorkflowProcessor processor = new JavaAnnotationWorkflowProcessor();

    // Build and execute the composite workflow
    Workflow compositeWorkflow = processor.buildWorkflow(DataProcessingPipelineWorkflow.class);

    WorkflowContext context = new WorkflowContext();
    context.put(INPUT_DATA, "sample data to process");

    log.info("Executing: {}", compositeWorkflow.getName());
    var result = compositeWorkflow.execute(context);

    log.info("Workflow Status: {}", result.getStatus());
    log.info("Execution Time: {}ms", result.getDuration().toMillis());
    log.info("Output Data: {}", context.get("output.data"));
  }

  // ============================================================================
  // Reusable Workflow Components
  // ============================================================================

  /** First workflow component: Data Validation. This workflow validates the incoming data. */
  @WorkflowAnnotation(
      name = "DataValidationWorkflow",
      description = "Validates incoming data against schema and business rules")
  public static class DataValidationWorkflowDef {

    @TaskMethod(name = "ValidateSchema", order = 1, description = "Validate data schema")
    public Task validateSchema() {
      return context -> {
        log.info("Validating data schema...");
        String data = (String) context.get(INPUT_DATA);
        if (data == null || data.isEmpty()) {
          throw new IllegalArgumentException("Input data cannot be empty");
        }
        context.put("schema.valid", true);
        context.put("validation.timestamp", System.currentTimeMillis());
      };
    }

    @TaskMethod(name = "ValidateQuality", order = 2, description = "Validate data quality")
    public Task validateQuality() {
      return context -> {
        log.info("Validating data quality...");
        context.put("quality.score", 0.95);
        context.put("quality.valid", true);
      };
    }
  }

  /**
   * Second workflow component: Data Transformation. This workflow transforms the validated data.
   */
  @WorkflowAnnotation(
      name = "DataTransformationWorkflow",
      description = "Transforms data into the required format")
  public static class DataTransformationWorkflowDef {

    @TaskMethod(name = "NormalizeData", order = 1, description = "Normalize data format")
    public Task normalizeData() {
      return context -> {
        log.info("Normalizing data...");
        String data = (String) context.get(INPUT_DATA);
        String normalized = data.toUpperCase().trim();
        context.put("normalized.data", normalized);
      };
    }

    @TaskMethod(name = "EnrichData", order = 2, description = "Enrich data with metadata")
    public Task enrichData() {
      return context -> {
        log.info("Enriching data with metadata...");
        String data = (String) context.get("normalized.data");
        context.put("enriched.data", data + " [ENRICHED]");
        context.put("metadata.added", true);
      };
    }
  }

  /** Third workflow component: Data Output. This workflow handles the final output processing. */
  @WorkflowAnnotation(
      name = "DataOutputWorkflow",
      description = "Handles output processing and persistence")
  public static class DataOutputWorkflowDef {

    @TaskMethod(name = "FormatOutput", order = 1, description = "Format output")
    public Task formatOutput() {
      return context -> {
        log.info("Formatting output...");
        String data = (String) context.get("enriched.data");
        context.put("formatted.output", "Output: " + data);
      };
    }

    @TaskMethod(name = "PersistOutput", order = 2, description = "Persist output data")
    public Task persistOutput() {
      return context -> {
        log.info("Persisting output data...");
        String output = (String) context.get("formatted.output");
        context.put("output.data", output);
        context.put("persistence.complete", true);
      };
    }
  }

  // ============================================================================
  // Composite Workflows
  // ============================================================================

  /**
   * Main composite workflow that combines all three workflow components in sequence.
   *
   * <p>This workflow demonstrates:
   *
   * <ul>
   *   <li>Composition using @WorkflowRef annotations
   *   <li>Sequential execution with explicit ordering via order parameter
   *   <li>Context sharing between parent and included workflows
   * </ul>
   */
  @WorkflowAnnotation(
      name = "DataProcessingPipeline",
      description =
          "Complete data processing pipeline combining validation, transformation, and output")
  public static class DataProcessingPipelineWorkflow {

    @WorkflowRef(
        workflowClass = DataValidationWorkflowDef.class,
        description = "First step: Validate incoming data")
    private Workflow validationStep;

    @WorkflowRef(
        workflowClass = DataTransformationWorkflowDef.class,
        description = "Second step: Transform validated data")
    private Workflow transformationStep;

    @WorkflowRef(
        workflowClass = DataOutputWorkflowDef.class,
        description = "Third step: Process and persist output")
    private Workflow outputStep;
  }

  /**
   * Alternative composite workflow demonstrating conditional composition.
   *
   * <p>This workflow adds additional steps that can be included conditionally.
   */
  @WorkflowAnnotation(
      name = "EnhancedDataProcessingPipeline",
      description = "Enhanced data processing pipeline with audit logging")
  public static class EnhancedDataProcessingPipelineWorkflow {

    @WorkflowRef(workflowClass = DataValidationWorkflowDef.class)
    private Workflow validation;

    @WorkflowRef(workflowClass = DataTransformationWorkflowDef.class)
    private Workflow transformation;

    @WorkflowRef(workflowClass = DataOutputWorkflowDef.class)
    private Workflow output;

    @TaskMethod(name = "AuditLog", order = 4, description = "Log audit trail")
    public Task auditLog() {
      return context -> {
        log.info("Recording audit trail...");
        context.put("audit.logged", true);
        context.put("audit.timestamp", System.currentTimeMillis());
      };
    }
  }

  /**
   * Example showing how to create nested composite workflows (workflows that include other
   * composite workflows).
   */
  @WorkflowAnnotation(
      name = "NestedCompositeWorkflow",
      description = "Demonstrates nested workflow composition")
  public static class NestedCompositeWorkflowDef {
    @WorkflowRef(
        workflowClass = DataProcessingPipelineWorkflow.class,
        description = "Main data processing pipeline")
    private Workflow mainPipeline;

    @TaskMethod(name = "PostProcessing", order = 2, description = "Post-processing steps")
    public Task postProcessing() {
      return context -> {
        log.info("Performing post-processing...");
        context.put("post.processing.done", true);
      };
    }
  }

  /**
   * Example workflow demonstrating parallel execution of included workflows (when supported).
   *
   * <p>Note: Current implementation executes sequentially; parallel execution is a future
   * enhancement.
   */
  @WorkflowAnnotation(
      name = "ParallelProcessingWorkflow",
      parallel = true,
      description = "Demonstrates parallel workflow composition")
  public static class ParallelProcessingWorkflowDef {
    @WorkflowRef(
        workflowClass = DataValidationWorkflowDef.class,
        description = "Run validation in parallel")
    private Workflow validation;

    @WorkflowRef(
        workflowClass = DataTransformationWorkflowDef.class,
        description = "Run transformation in parallel")
    private Workflow transformation;
  }
}
