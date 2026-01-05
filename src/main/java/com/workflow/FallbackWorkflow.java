package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.TreeRenderer;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a primary workflow and falls back to an alternative workflow if the primary fails or
 * returns a non-success status.
 *
 * <p><b>Purpose:</b> Implements graceful degradation patterns. Attempts an optimized or preferred
 * execution path (primary), and if it fails for any reason, automatically falls back to an
 * alternative execution path. Useful for scenarios where multiple strategies are available with
 * different cost/performance/reliability tradeoffs.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Primary Execution:</b> Primary workflow is executed first
 *   <li><b>Success Check:</b> If primary returns SUCCESS status, that result is returned
 *   <li><b>Failure Handling:</b> If primary returns FAILED or SKIPPED, or throws any exception,
 *       fallback is executed
 *   <li><b>Fallback Result:</b> The fallback result is returned as-is, whether SUCCESS or FAILED
 *   <li><b>Context Sharing:</b> Both primary and fallback receive the same context instance,
 *       allowing primary to establish state that fallback can use
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. Child workflows must also be thread-safe.
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li>Cache-then-database pattern (try fast cache, fall back to DB)
 *   <li>Local-then-remote pattern (try local service, fall back to remote)
 *   <li>Optimized-then-baseline pattern (try new optimized path, fall back to proven path)
 *   <li>Free-tier-then-premium pattern (try free option, escalate to premium on failure)
 * </ul>
 *
 * <p><b>Example Usage - Cache with Database Fallback:</b>
 *
 * <pre>{@code
 * Workflow cacheWorkflow = new TaskWorkflow(new ReadFromCacheTask());
 * Workflow databaseWorkflow = new TaskWorkflow(new ReadFromDatabaseTask());
 * Workflow fallbackWorkflow = FallbackWorkflow.builder()
 *     .name("CacheThenDatabase")
 *     .primary(cacheWorkflow)
 *     .fallback(databaseWorkflow)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("userId", 12345);
 * WorkflowResult result = fallbackWorkflow.execute(context);
 *
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     Object userData = context.get("userData");
 *     System.out.println("User data retrieved successfully");
 * } else {
 *     System.err.println("Both cache and database lookups failed");
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Progressive Fallback Chain:</b>
 *
 * <pre>{@code
 * // Build fallback chain: fast → medium → slow
 * Workflow slowPath = new DatabaseWorkflow();
 * Workflow mediumPath = FallbackWorkflow.builder().name("MediumThenSlow")
 *     .primary(new LocalCacheWorkflow()).fallback(slowPath).build();
 * Workflow fastThenRest = FallbackWorkflow.builder().name("ProgressiveFallback")
 *     .primary(new DistributedCacheWorkflow()).fallback(mediumPath).build();
 *
 * WorkflowResult result = fastThenRest.execute(context);
 * }</pre>
 *
 * @see Workflow
 * @see AbstractWorkflow
 * @see ConditionalWorkflow
 */
@Slf4j
public class FallbackWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final Workflow primary;
  private final Workflow fallback;

  /** Private constructor for builder. */
  private FallbackWorkflow(FallbackWorkflowBuilder builder) {
    this.name = builder.name;
    this.primary = builder.primary;
    this.fallback = builder.fallback;
  }

  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    log.debug("Executing primary workflow: {}", primary.getName());
    WorkflowResult primaryResult;

    try {
      primaryResult = primary.execute(context);
    } catch (Exception e) {
      log.warn(
          "Primary workflow {} threw exception, trying fallback: {}",
          primary.getName(),
          e.getMessage());
      return executeFallback(context, execContext);
    }

    // Check if primary succeeded
    if (primaryResult != null && primaryResult.getStatus() == WorkflowStatus.SUCCESS) {
      log.debug("Primary workflow succeeded");
      return primaryResult;
    }

    log.warn(
        "Primary workflow failed or returned non-success status: {}",
        primaryResult != null ? primaryResult.getStatus() : "null");
    return executeFallback(context, execContext);
  }

  private WorkflowResult executeFallback(WorkflowContext context, ExecutionContext execContext) {
    log.debug("Executing fallback workflow: {}", fallback.getName());

    try {
      WorkflowResult fallbackResult = fallback.execute(context);

      if (fallbackResult != null && fallbackResult.getStatus() == WorkflowStatus.SUCCESS) {
        log.info("Fallback workflow succeeded");
        return fallbackResult;
      }

      // Fallback also failed
      Throwable error =
          fallbackResult != null && fallbackResult.getError() != null
              ? fallbackResult.getError()
              : new RuntimeException("Fallback workflow failed with no error details");

      log.error("Both primary and fallback workflows failed");
      return execContext.failure(error);

    } catch (Exception e) {
      log.error("Fallback workflow threw exception: {}", e.getMessage(), e);
      return execContext.failure(e);
    }
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Fallback");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    return List.of(
        new TreeRenderer.TreeLabelWrapper("TRY (Primary) ->", primary),
        new TreeRenderer.TreeLabelWrapper("ON FAILURE ->", fallback));
  }

  /**
   * Creates a new builder for FallbackWorkflow.
   *
   * @return a new builder instance
   */
  public static FallbackWorkflowBuilder builder() {
    return new FallbackWorkflowBuilder();
  }

  /** Builder for creating fallback workflows with fluent API. */
  public static class FallbackWorkflowBuilder {
    private String name;
    private Workflow primary;
    private Workflow fallback;

    /**
     * Sets the name of this fallback workflow.
     *
     * @param name the workflow name
     * @return this builder
     */
    public FallbackWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the primary workflow to execute first.
     *
     * @param primary the primary workflow
     * @return this builder
     */
    public FallbackWorkflowBuilder primary(Workflow primary) {
      this.primary = primary;
      return this;
    }

    /**
     * Sets the fallback workflow to execute if primary fails.
     *
     * @param fallback the fallback workflow
     * @return this builder
     */
    public FallbackWorkflowBuilder fallback(Workflow fallback) {
      this.fallback = fallback;
      return this;
    }

    /**
     * Builds the fallback workflow.
     *
     * @return the configured fallback workflow
     * @throws NullPointerException if primary or fallback is null
     */
    public FallbackWorkflow build() {
      ValidationUtils.requireNonNull(primary, "primary workflow");
      ValidationUtils.requireNonNull(fallback, "fallback workflow");
      return new FallbackWorkflow(this);
    }
  }
}
