package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import com.workflow.ratelimit.RateLimitStrategy;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Workflow wrapper that applies rate limiting to the execution of an inner workflow.
 *
 * <p>This workflow ensures that the inner workflow is executed at a rate controlled by the
 * configured {@link RateLimitStrategy}. When the rate limit is exceeded, execution blocks until
 * permission is granted by the rate limiter.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li><b>Transparent:</b> Wraps any workflow without modification
 *   <li><b>Pluggable:</b> Works with any {@link RateLimitStrategy} implementation
 *   <li><b>Blocking:</b> Blocks execution when rate limit is exceeded
 *   <li><b>Context Passthrough:</b> Passes context directly to inner workflow
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe if the rate limit strategy is thread-safe
 * (all built-in strategies are thread-safe).
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Throttling API calls to respect external rate limits
 *   <li>Controlling resource consumption in high-throughput scenarios
 *   <li>Preventing system overload
 *   <li>Fair sharing of resources among multiple workflows
 * </ul>
 *
 * <p><b>Example usage - Fixed Window:</b>
 *
 * <pre>{@code
 * // Create rate limiter: 100 requests per minute
 * RateLimitStrategy limiter = new FixedWindowRateLimiter(
 *     100,
 *     Duration.ofMinutes(1)
 * );
 *
 * // Wrap API workflow with rate limiting
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .name("RateLimitedAPI")
 *     .workflow(apiCallWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 *
 * // Execute - automatically rate limited
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = rateLimited.execute(context);
 * }</pre>
 *
 * <p><b>Example usage - Token Bucket with Burst:</b>
 *
 * <pre>{@code
 * // Allow 100 req/sec with burst capacity of 200
 * RateLimitStrategy limiter = new TokenBucketRateLimiter(
 *     100,                        // Tokens per second
 *     200,                        // Bucket capacity (burst)
 *     Duration.ofSeconds(1)
 * );
 *
 * Workflow workflow = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Parallel Workflows:</b>
 *
 * <pre>{@code
 * // Shared rate limiter for all parallel workflows
 * RateLimitStrategy sharedLimiter = new SlidingWindowRateLimiter(
 *     1000,
 *     Duration.ofMinutes(1)
 * );
 *
 * // Multiple workflows sharing same rate limit
 * Workflow workflow1 = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow1)
 *     .rateLimitStrategy(sharedLimiter)
 *     .build();
 *
 * Workflow workflow2 = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow2)
 *     .rateLimitStrategy(sharedLimiter)
 *     .build();
 *
 * // Execute in parallel - share rate limit
 * ParallelWorkflow.builder()
 *     .workflow(workflow1)
 *     .workflow(workflow2)
 *     .build()
 *     .execute(context);
 * }</pre>
 *
 * <p><b>Example usage - Sequential Workflow:</b>
 *
 * <pre>{@code
 * // Rate limit each step independently
 * Workflow pipeline = SequentialWorkflow.builder()
 *     .name("RateLimitedPipeline")
 *     .workflow(RateLimitedWorkflow.builder()
 *         .workflow(fetchWorkflow)
 *         .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofSeconds(1)))
 *         .build())
 *     .workflow(processWorkflow)  // No rate limit
 *     .workflow(RateLimitedWorkflow.builder()
 *         .workflow(saveWorkflow)
 *         .rateLimitStrategy(new FixedWindowRateLimiter(5, Duration.ofSeconds(1)))
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Interrupt Handling:</b> If the thread is interrupted while waiting for rate limit
 * permission, the workflow returns a FAILED result with the InterruptedException.
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Rate limiting adds minimal overhead when limit is not reached
 *   <li>Blocking occurs only when rate limit is exceeded
 *   <li>Choose appropriate rate limit strategy based on requirements
 *   <li>Consider using shared rate limiters for related workflows
 * </ul>
 *
 * @see RateLimitStrategy
 * @see com.workflow.ratelimit.FixedWindowRateLimiter
 * @see com.workflow.ratelimit.SlidingWindowRateLimiter
 * @see com.workflow.ratelimit.TokenBucketRateLimiter
 * @see com.workflow.ratelimit.LeakyBucketRateLimiter
 * @see com.workflow.ratelimit.Resilience4jRateLimiter
 * @see com.workflow.ratelimit.Bucket4jRateLimiter
 */
@Slf4j
@Builder
public class RateLimitedWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final Workflow workflow;
  private final RateLimitStrategy rateLimitStrategy;

  /**
   * Private constructor used by the Builder.
   *
   * @param builder the builder instance containing configuration
   */
  private RateLimitedWorkflow(RateLimitedWorkflowBuilder builder) {
    this.name = builder.name;
    this.workflow = builder.workflow;
    this.rateLimitStrategy = builder.rateLimitStrategy;
  }

  /**
   * Executes the inner workflow with rate limiting applied.
   *
   * <p>The implementation:
   *
   * <ol>
   *   <li>Acquires permission from the rate limit strategy (blocks if necessary)
   *   <li>Executes the inner workflow
   *   <li>Returns the result from the inner workflow
   *   <li>Wraps InterruptedException in a FAILED result
   * </ol>
   *
   * @param context the workflow context passed to the inner workflow
   * @param execContext execution context for building results
   * @return the result from the inner workflow, or FAILED if interrupted
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    Objects.requireNonNull(rateLimitStrategy, "rateLimitStrategy must not be null");
    Objects.requireNonNull(workflow, "workflow must not be null");
    try {
      // Acquire permission from rate limiter (blocks if necessary)
      log.debug("Acquiring rate limit permission for workflow: {}", getName());
      rateLimitStrategy.acquire();
      log.debug("Rate limit permission acquired for workflow: {}", getName());

      // Execute the inner workflow
      return workflow.execute(context);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore interrupt status
      log.error("Rate limited workflow {} was interrupted", getName(), e);
      return execContext.failure(e);
    }
  }

  /**
   * Returns the workflow name.
   *
   * @return the provided name or a generated default name
   */
  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Rate-Limited");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    // A RateLimitedWorkflow simply wraps one child workflow.
    // We return it in a list so the TreeRenderer can recurse into it.
    return workflow != null ? List.of(workflow) : List.of();
  }

  /**
   * Creates a new builder for {@link RateLimitedWorkflow}.
   *
   * @return a new builder instance
   */
  public static RateLimitedWorkflowBuilder builder() {
    return new RateLimitedWorkflowBuilder();
  }

  /**
   * Builder for {@link RateLimitedWorkflow}.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * RateLimitedWorkflow workflow = RateLimitedWorkflow.builder()
   * .name("ThrottledAPI")
   * .workflow(myApiWorkflow)
   * .rateLimitStrategy(new TokenBucketRateLimiter(10, 1, Duration.ofSeconds(1)))
   * .build();
   * }</pre>
   */
  public static class RateLimitedWorkflowBuilder {
    private String name;
    private Workflow workflow;
    private RateLimitStrategy rateLimitStrategy;

    /**
     * Sets the name of the rate-limited workflow.
     *
     * @param name a descriptive name for logging and debugging
     * @return this builder
     */
    public RateLimitedWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the inner workflow to be executed underrate limits.
     *
     * @param workflow the workflow to wrap; must not be null
     * @return this builder
     */
    public RateLimitedWorkflowBuilder workflow(Workflow workflow) {
      this.workflow = workflow;
      return this;
    }

    /**
     * Sets the strategy used to control execution rate.
     *
     * @param rateLimitStrategy the rate limiter implementation; must not be null
     * @return this builder
     */
    public RateLimitedWorkflowBuilder rateLimitStrategy(RateLimitStrategy rateLimitStrategy) {
      this.rateLimitStrategy = rateLimitStrategy;
      return this;
    }

    /**
     * Builds and returns a new {@link RateLimitedWorkflow}.
     *
     * @return a configured RateLimitedWorkflow instance
     * @throws NullPointerException if workflow or rateLimitStrategy are null
     */
    public RateLimitedWorkflow build() {
      ValidationUtils.requireNonNull(rateLimitStrategy, "rateLimitStrategy");
      ValidationUtils.requireNonNull(workflow, "workflow");
      return new RateLimitedWorkflow(this);
    }
  }
}
