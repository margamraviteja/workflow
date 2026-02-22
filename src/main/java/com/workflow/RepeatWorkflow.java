package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.TreeRenderer;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a child workflow a fixed number of times.
 *
 * <p><b>Purpose:</b> Enables repeat patterns where the same workflow logic needs to be executed
 * multiple times. Useful for retry loops, polling operations, or any scenario requiring fixed
 * iterations.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Fixed Iterations:</b> Executes the child workflow exactly {@code times} times
 *   <li><b>Index Binding:</b> Sets the current iteration index (0-based) using {@code
 *       indexVariable}
 *   <li><b>Sequential Execution:</b> Executes iterations in order
 *   <li><b>Fail Fast:</b> Stops iteration on first failure and returns that result
 *   <li><b>Zero Times:</b> Returns SUCCESS immediately if {@code times <= 0}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. The child workflow must also be
 * thread-safe.
 *
 * <p><b>Example Usage - Retry Pattern:</b>
 *
 * <pre>{@code
 * Workflow retryableTask = new TaskWorkflow(new HttpCallTask());
 * Workflow repeat = RepeatWorkflow.builder()
 *     .name("RetryLoop")
 *     .times(3)
 *     .indexVariable("attempt")
 *     .workflow(retryableTask)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = repeat.execute(context);
 * }</pre>
 *
 * @see Workflow
 * @see AbstractWorkflow
 * @see ForEachWorkflow
 */
@Slf4j
public class RepeatWorkflow extends AbstractWorkflow implements WorkflowContainer {

  private static final String DEFAULT_INDEX_VARIABLE = "iteration";

  private final String name;
  private final int times;
  private final String indexVariable;
  private final Workflow workflow;

  @Builder
  private RepeatWorkflow(String name, int times, String indexVariable, Workflow workflow) {
    ValidationUtils.requireNonNull(workflow, "workflow");

    this.name = name;
    this.times = times;
    this.indexVariable =
        indexVariable != null && !indexVariable.isBlank() ? indexVariable : DEFAULT_INDEX_VARIABLE;
    this.workflow = workflow;
  }

  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    if (times <= 0) {
      log.debug("Times is {}, skipping repeat execution", times);
      return execContext.success();
    }

    log.debug("Starting repeat workflow for {} iterations", times);

    for (int i = 0; i < times; i++) {
      log.debug("Executing iteration {} of {}", i + 1, times);

      // Set the iteration index in context
      context.put(indexVariable, i);

      // Execute the child workflow
      WorkflowResult result = workflow.execute(context);

      if (result.getStatus() == WorkflowStatus.FAILED) {
        log.warn("Iteration {} failed, stopping repeat execution", i);
        return result;
      }
    }

    log.debug("Completed all {} iterations", times);
    return execContext.success();
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Repeat");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    // Descriptive label: REPEAT 3 TIMES (index: attempt) ->
    String label = String.format("REPEAT %d TIMES (index: %s) ->", times, indexVariable);
    return List.of(new TreeRenderer.TreeLabelWrapper(label, workflow));
  }
}
