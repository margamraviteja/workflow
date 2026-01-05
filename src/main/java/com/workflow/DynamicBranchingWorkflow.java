package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.TreeRenderer;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.*;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Selects and executes one of several child workflows at runtime based on a selector function.
 *
 * <p><b>Purpose:</b> Enables multi-way branching logic when the number of branches or branch keys
 * are determined dynamically. The selector function computes a key from the context, and that key
 * is used to look up the appropriate workflow from a map of branches.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Selector Evaluation:</b> The selector function is called with the context to compute a
 *       branch key
 *   <li><b>Branch Lookup:</b> The returned key is used to look up a workflow in the branches map.
 *       An exact match is attempted first, followed by a case-insensitive search.
 *   <li><b>Default Fallback:</b> If key is not found and defaultBranch is provided, use it
 *   <li><b>Missing Key:</b> If key not found and no defaultBranch, workflow is marked as skipped
 *   <li><b>Context Sharing:</b> The selected workflow receives the same context instance
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. Child workflows must also be thread-safe.
 *
 * <p><b>Validation:</b> The {@code branches} map must not be empty and {@code selector} function
 * must not be null. The {@code defaultBranch} is optional.
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li>Routing based on order type (standard, express, overnight)
 *   <li>Processing pipelines based on data source (API, database, file)
 *   <li>Multi-tenant workflows (tenant-specific processing)
 *   <li>Load balancing across several processing strategies
 *   <li>State machine transitions (state → next action mapping)
 * </ul>
 *
 * <p><b>Example Usage - Order Type Routing:</b>
 *
 * <pre>{@code
 * Map<String, Workflow> routes = Map.of(
 *     "standard", new StandardShippingWorkflow(),
 *     "express", new ExpressShippingWorkflow(),
 *     "overnight", new OvernightShippingWorkflow()
 * );
 * DynamicBranchingWorkflow orderRouter = DynamicBranchingWorkflow.builder()
 *     .name("OrderRouter")
 *     .branches(routes)
 *     .selector(ctx -> ctx.getTyped("shippingType", String.class))
 *     .defaultBranch(new DefaultShippingWorkflow())  // Fallback if type not recognized
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("shippingType", "express");
 * WorkflowResult result = orderRouter.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Multi-Tenant Processing:</b>
 *
 * <pre>{@code
 * Map<String, Workflow> tenantWorkflows = Map.of(
 *     "tenant-a", new TenantAProcessingWorkflow(),
 *     "tenant-b", new TenantBProcessingWorkflow(),
 *     "tenant-c", new TenantCProcessingWorkflow()
 * );
 * DynamicBranchingWorkflow multiTenantWorkflow = DynamicBranchingWorkflow.builder()
 *     .name("MultiTenantProcessor")
 *     .branches(tenantWorkflows)
 *     .selector(ctx -> ctx.getTyped("tenantId", String.class))
 *     .build();
 * }</pre>
 *
 * @see Workflow
 * @see AbstractWorkflow
 * @see ConditionalWorkflow
 */
@Slf4j
public class DynamicBranchingWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final Map<String, Workflow> branches;
  private final Function<WorkflowContext, String> selector;
  private final Workflow defaultBranch;

  /** Internal constructor used by the Builder. */
  private DynamicBranchingWorkflow(DynamicBranchingWorkflowBuilder builder) {
    this.name = builder.name;
    this.branches = builder.branches;
    this.selector = builder.selector;
    this.defaultBranch = builder.defaultBranch;
  }

  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    String key;
    try {
      key = selector.apply(context);
      log.debug("Selector returned key: {} for DynamicBranchingWorkflow: {}", key, getName());
    } catch (Exception e) {
      log.error("Selector function failed for DynamicBranchingWorkflow: {}", getName(), e);
      return execContext.failure(
          new IllegalStateException("Branch selector evaluation failed: " + e.getMessage(), e));
    }

    // Case-insensitive lookup
    Workflow chosen = null;
    if (key != null) {
      chosen = branches.get(key);
      if (chosen == null) {
        // Try case-insensitive match
        for (Map.Entry<String, Workflow> entry : branches.entrySet()) {
          if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
            chosen = entry.getValue();
            log.debug("Matched key '{}' case-insensitively to '{}'", key, entry.getKey());
            break;
          }
        }
      }
    } else {
      log.debug("Selector returned null key; checking default branch for {}", getName());
    }

    if (chosen == null) {
      chosen = defaultBranch;
    }

    if (chosen == null) {
      String warnMsg =
          String.format("No branch found for key '%s' and no default branch configured", key);
      log.warn(warnMsg);
      return execContext.skipped();
    }

    log.debug("Executing chosen branch: {}", chosen.getName());
    return chosen.execute(context);
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Switch");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    List<Workflow> children = new ArrayList<>();
    branches.forEach(
        (key, workflow) ->
            children.add(new TreeRenderer.TreeLabelWrapper("CASE \"" + key + "\" ->", workflow)));
    if (defaultBranch != null) {
      children.add(new TreeRenderer.TreeLabelWrapper("DEFAULT ->", defaultBranch));
    }
    return children;
  }

  /**
   * Creates a new builder for FallbackWorkflow.
   *
   * @return a new builder instance
   */
  public static DynamicBranchingWorkflowBuilder builder() {
    return new DynamicBranchingWorkflowBuilder();
  }

  /** Builder for {@link DynamicBranchingWorkflow}. */
  public static class DynamicBranchingWorkflowBuilder {
    private String name;
    private final Map<String, Workflow> branches = new LinkedHashMap<>();
    private Function<WorkflowContext, String> selector;
    private Workflow defaultBranch;

    /**
     * Sets the name of the workflow.
     *
     * @param name a descriptive name for logging and debugging
     * @return this builder
     */
    public DynamicBranchingWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Adds a single branch to the workflow.
     *
     * @param key the lookup key (evaluated against the selector result)
     * @param workflow the workflow to execute for this key
     * @return this builder
     */
    public DynamicBranchingWorkflowBuilder branch(String key, Workflow workflow) {
      this.branches.put(key, workflow);
      return this;
    }

    /**
     * Adds multiple branches to the workflow.
     *
     * @param branches a map of keys to workflows
     * @return this builder
     */
    public DynamicBranchingWorkflowBuilder branches(Map<String, Workflow> branches) {
      this.branches.putAll(branches);
      return this;
    }

    /**
     * Sets the selector function used to determine which branch to execute.
     *
     * @param selector a function that extracts a String key from the {@link WorkflowContext}
     * @return this builder
     */
    public DynamicBranchingWorkflowBuilder selector(Function<WorkflowContext, String> selector) {
      this.selector = selector;
      return this;
    }

    /**
     * Sets the fallback workflow if the selector key does not match any existing branch.
     *
     * @param defaultBranch the fallback workflow; if null, the workflow will fail on missing keys
     * @return this builder
     */
    public DynamicBranchingWorkflowBuilder defaultBranch(Workflow defaultBranch) {
      this.defaultBranch = defaultBranch;
      return this;
    }

    /**
     * Builds and returns a new {@link DynamicBranchingWorkflow}.
     *
     * @return a configured DynamicBranchingWorkflow instance
     * @throws NullPointerException if branches or selector are null
     * @throws IllegalArgumentException if the branches is empty
     */
    public DynamicBranchingWorkflow build() {
      ValidationUtils.requireNonEmpty(branches, "branches");
      ValidationUtils.requireNonNull(selector, "selector");
      return new DynamicBranchingWorkflow(this);
    }
  }
}
