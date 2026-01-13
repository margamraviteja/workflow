package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.TreeRenderer;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * A workflow that evaluates a predicate against the provided {@link WorkflowContext} and
 * conditionally executes one of two child workflows based on the evaluation result.
 *
 * <p><b>Purpose:</b> Enables branching logic within workflows. Evaluates a condition function and
 * executes either the {@code whenTrue} or {@code whenFalse} workflow. If the selected branch is
 * {@code null}, the workflow is marked as SKIPPED.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Condition Evaluation:</b> The predicate is evaluated against the provided context
 *   <li><b>Branch Selection:</b> If true, execute whenTrue; if false, execute whenFalse
 *   <li><b>Null Branch:</b> If selected branch is null, workflow returns SKIPPED status
 *   <li><b>Context Sharing:</b> Both branches receive the same context instance
 *   <li><b>Condition Exception:</b> Exceptions during condition evaluation result in FAILED status
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. Child workflows must also be thread-safe.
 *
 * <p><b>Validation:</b> At least one of {@code whenTrue} or {@code whenFalse} must be non-null. The
 * condition predicate must not be null. These validations are performed at build time.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Feature flags (enable/disable features based on context)
 *   <li>Environment-specific execution (dev vs. prod workflows)
 *   <li>Input validation (different paths for valid/invalid data)
 *   <li>A/B testing workflows
 * </ul>
 *
 * <p><b>Example Usage - Feature Flag:</b>
 *
 * <pre>{@code
 * Workflow premiumWorkflow = new TaskWorkflow(new PremiumFeatureTask());
 * Workflow basicWorkflow = new TaskWorkflow(new BasicFeatureTask());
 * Workflow conditionalWorkflow = ConditionalWorkflow.builder()
 *    .name("PremiumFeatureGate")
 *    .condition(ctx -> ctx.getTyped("isPremiumUser", Boolean.class))
 *    .whenTrue(premiumWorkflow)
 *    .whenFalse(basicWorkflow)
 *    .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("isPremiumUser", true);
 * WorkflowResult result = conditionalWorkflow.execute(context);
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     System.out.println("Feature execution completed");
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Optional Branch:</b>
 *
 * <pre>{@code
 * // Only execute enrichment if mode is 'detailed'; skip otherwise
 * Workflow enrichmentWorkflow = new DataEnrichmentWorkflow();
 * ConditionalWorkflow conditionalWorkflow = ConditionalWorkflow.builder()
 *    .name("OptionalEnrichment")
 *    .condition(ctx -> "detailed".equals(ctx.get("processingMode")))
 *    .whenTrue(enrichmentWorkflow)
 *    .whenFalse(null)  // Skip if condition is false
 *    .build();
 * }</pre>
 *
 * @see Workflow
 * @see AbstractWorkflow
 * @see DynamicBranchingWorkflow
 */
@Slf4j
public class ConditionalWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final Predicate<WorkflowContext> condition;
  private final Workflow whenTrue;
  private final Workflow whenFalse;

  private ConditionalWorkflow(ConditionalWorkflowBuilder builder) {
    this.name = builder.name;
    this.condition = builder.condition;
    this.whenTrue = builder.whenTrue;
    this.whenFalse = builder.whenFalse;
  }

  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    boolean conditionResult;
    try {
      conditionResult = condition.test(context);
      log.info(
          "Condition evaluated to: {} for ConditionalWorkflow: {}", conditionResult, getName());
    } catch (Exception e) {
      log.error("Condition evaluation failed for ConditionalWorkflow: {}", getName(), e);
      return execContext.failure(
          new IllegalStateException("Condition evaluation failed: " + e.getMessage(), e));
    }

    Workflow selectedBranch = conditionResult ? whenTrue : whenFalse;
    if (selectedBranch == null) {
      log.info("Selected branch is null, returning SKIPPED");
      return execContext.skipped();
    }

    log.debug("Executing {} branch", conditionResult ? "true" : "false");
    return selectedBranch.execute(context);
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Conditional");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    List<Workflow> subWorkflows = new ArrayList<>();
    subWorkflows.add(new TreeRenderer.TreeLabelWrapper("When True ->", whenTrue));
    if (whenFalse != null) {
      subWorkflows.add(new TreeRenderer.TreeLabelWrapper("When False ->", whenFalse));
    }
    return subWorkflows;
  }

  /** Builder for creating conditional workflows with fluent API. */
  public static ConditionalWorkflowBuilder builder() {
    return new ConditionalWorkflowBuilder();
  }

  /** Builder class for {@link ConditionalWorkflow}. */
  public static class ConditionalWorkflowBuilder {
    private String name;
    private Predicate<WorkflowContext> condition;
    private Workflow whenTrue;
    private Workflow whenFalse;

    /**
     * Sets the name of this conditional workflow.
     *
     * @param name the workflow name
     * @return this builder
     */
    public ConditionalWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the condition predicate to evaluate.
     *
     * @param condition the condition predicate
     * @return this builder
     */
    public ConditionalWorkflowBuilder condition(Predicate<WorkflowContext> condition) {
      this.condition = condition;
      return this;
    }

    /**
     * Sets the workflow to execute when condition is true.
     *
     * @param workflow the workflow for true branch
     * @return this builder
     */
    public ConditionalWorkflowBuilder whenTrue(Workflow workflow) {
      this.whenTrue = workflow;
      return this;
    }

    /**
     * Sets the workflow to execute when condition is false.
     *
     * @param workflow the workflow for false branch
     * @return this builder
     */
    public ConditionalWorkflowBuilder whenFalse(Workflow workflow) {
      this.whenFalse = workflow;
      return this;
    }

    /**
     * Builds the conditional workflow.
     *
     * @return the configured conditional workflow
     * @throws NullPointerException if condition or whenTrue is null
     */
    public ConditionalWorkflow build() {
      ValidationUtils.requireNonNull(condition, "condition");
      ValidationUtils.requireNonNull(whenTrue, "whenTrue");
      return new ConditionalWorkflow(this);
    }
  }
}
