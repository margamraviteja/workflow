package com.workflow.listener;

import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;

/**
 * Listener interface for observing lifecycle events of a workflow execution.
 *
 * <p>Implementations of this interface can be registered with workflow orchestration components to
 * receive callbacks when a workflow starts, succeeds, fails
 *
 * <h3>Threading and safety</h3>
 *
 * <ul>
 *   <li>Callback methods may be invoked from workflow execution threads. Implementations should be
 *       thread-safe if they are shared across multiple workflows or executions.
 *   <li>Callbacks should avoid long-running or blocking operations. If heavy work is required,
 *       offload it to a dedicated executor to avoid delaying workflow progress.
 * </ul>
 *
 * <h3>Typical responsibilities</h3>
 *
 * <ul>
 *   <li>Logging workflow lifecycle events for observability.
 *   <li>Publishing metrics or tracing spans when workflows start, succeed, or fail.
 *   <li>Triggering downstream processes or cleanup actions on completion.
 * </ul>
 *
 * <h3>Example usage</h3>
 *
 * <pre>{@code
 * // A simple listener implementation that logs lifecycle events
 * public class LoggingWorkflowListener implements WorkflowListener {
 *     @Override
 *     public void onStart(String workflowName, WorkflowContext context) {
 *         System.out.println("Workflow started: " + workflowName + ", id=" + context.getId());
 *     }
 *
 *     @Override
 *     public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult workflowResult) {
 *         System.out.println("Workflow succeeded: " + workflowName + ", result=" + workflowResult);
 *     }
 *
 *     @Override
 *     public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
 *         System.err.println("Workflow failed: " + workflowName + ", error=" + error.getMessage());
 *     }
 * }
 *
 * // Example of invoking the listener from a workflow runner
 * WorkflowListener listener = new LoggingWorkflowListener();
 * WorkflowContext ctx = WorkflowContext.builder().id("wf-123").build();
 * listener.onStart("my-workflow", ctx);
 * try {
 *     WorkflowResult result = runWorkflow(ctx); // user-defined workflow execution
 *     listener.onSuccess("my-workflow", ctx, result);
 * } catch (Throwable t) {
 *     listener.onFailure("my-workflow", ctx, t);
 * }
 * }</pre>
 *
 * @see com.workflow.context.WorkflowContext
 * @see com.workflow.WorkflowResult
 */
public interface WorkflowListener {

  /**
   * Called when a workflow execution is starting.
   *
   * @param workflowName the logical name of the workflow (not null)
   * @param context the workflow execution context containing metadata and inputs (not null)
   */
  void onStart(String workflowName, WorkflowContext context);

  /**
   * Called when a workflow execution completes successfully.
   *
   * @param workflowName the logical name of the workflow (not null)
   * @param context the workflow execution context containing metadata and inputs (not null)
   * @param workflowResult the result produced by the workflow; may be null if the workflow produces
   *     no result
   */
  void onSuccess(String workflowName, WorkflowContext context, WorkflowResult workflowResult);

  /**
   * Called when a workflow execution fails with an error.
   *
   * @param workflowName the logical name of the workflow (not null)
   * @param context the workflow execution context containing metadata and inputs (not null)
   * @param error the throwable that caused the failure; never null when invoked for failure
   */
  void onFailure(String workflowName, WorkflowContext context, Throwable error);
}
