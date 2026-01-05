package com.workflow.listener;

import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry and notifier for {@link WorkflowListener} instances.
 *
 * <p>This class maintains a thread-safe list of listeners and provides convenience methods to
 * broadcast workflow lifecycle events to all registered listeners. It uses a {@link
 * CopyOnWriteArrayList} so listeners can be registered or removed concurrently while notifications
 * are in progress without requiring external synchronization.
 *
 * <h3>Threading and behavior</h3>
 *
 * <ul>
 *   <li>All {@code notify*} methods iterate over the current snapshot of listeners and invoke the
 *       corresponding callback on each listener.
 *   <li>Because {@link CopyOnWriteArrayList} is used, registration is safe from concurrent threads
 *       and iteration is not blocked by modifications.
 *   <li>Listener callbacks are invoked on the calling thread. Implementations of {@link
 *       WorkflowListener} should therefore be thread-safe and avoid long-running or blocking
 *       operations. If heavy work is required, offload it to a dedicated executor to avoid delaying
 *       the caller.
 *   <li>Exceptions thrown by a listener will propagate to the caller of the notify method. If you
 *       want to isolate listeners from each other, wrap invocations in try/catch blocks or provide
 *       a decorator that swallows or logs exceptions.
 * </ul>
 *
 * <h3>Usage notes</h3>
 *
 * <ul>
 *   <li>Register listeners early in application startup so they receive lifecycle events.
 *   <li>Use lightweight listeners for metrics, logging, or tracing. For heavier tasks, schedule
 *       work asynchronously.
 *   <li>To avoid leaking listeners, keep a reference to the {@code WorkflowListeners} instance and
 *       provide an unregister method if dynamic removal is required.
 * </ul>
 *
 * <h3>Example usage</h3>
 *
 * <pre>{@code
 * // Create registry and a simple listener
 * WorkflowListeners registry = new WorkflowListeners();
 *
 * WorkflowListener loggingListener = new WorkflowListener() {
 *     @Override
 *     public void onStart(String workflowName, WorkflowContext context) {
 *         System.out.println("Started: " + workflowName + " id=" + context.getId());
 *     }
 *
 *     @Override
 *     public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult workflowResult) {
 *         System.out.println("Succeeded: " + workflowName + " result=" + workflowResult);
 *     }
 *
 *     @Override
 *     public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
 *         System.err.println("Failed: " + workflowName + " error=" + error.getMessage());
 *     }
 * };
 *
 * // Register and notify
 * registry.register(loggingListener);
 *
 * WorkflowContext ctx = WorkflowContext.builder().id("wf-1").build();
 * registry.notifyStart("my-workflow", ctx);
 *
 * // Later, when workflow finishes:
 * WorkflowResult result = new WorkflowResult(...);
 * registry.notifySuccess("my-workflow", ctx, result);
 * }</pre>
 *
 * @see WorkflowListener
 */
@Slf4j
public class WorkflowListeners {

  /** Thread-safe list of registered listeners. */
  private final List<WorkflowListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * Register a {@link WorkflowListener} to receive lifecycle callbacks.
   *
   * @param workflowListener the listener to register; must not be null
   * @throws IllegalArgumentException if {@code workflowListener} is null
   */
  public void register(WorkflowListener workflowListener) {
    if (workflowListener == null) {
      throw new IllegalArgumentException("workflowListener must not be null");
    }
    listeners.add(workflowListener);
  }

  /**
   * Unregister a {@link WorkflowListener} so it no longer receives callbacks.
   *
   * @param workflowListener the listener to unregister
   * @return true if the listener was removed, false if it was not registered
   */
  public boolean unregister(WorkflowListener workflowListener) {
    return listeners.remove(workflowListener);
  }

  /** Remove all registered listeners. */
  public void clear() {
    listeners.clear();
  }

  /**
   * Notify all registered listeners that a workflow is starting.
   *
   * @param workflowName the logical name of the workflow (not null)
   * @param context the workflow execution context (not null)
   */
  public void notifyStart(String workflowName, WorkflowContext context) {
    listeners.forEach(
        l -> {
          try {
            l.onStart(workflowName, context);
          } catch (Exception e) {
            // log and continue; avoid swallowing silently
            log.error("Error while executing the listener: {}.onStart", l.getClass().getName(), e);
          }
        });
  }

  /**
   * Notify all registered listeners that a workflow completed successfully.
   *
   * @param workflowName the logical name of the workflow (not null)
   * @param context the workflow execution context (not null)
   * @param workflowResult the result produced by the workflow; may be null
   */
  public void notifySuccess(
      String workflowName, WorkflowContext context, WorkflowResult workflowResult) {
    listeners.forEach(
        l -> {
          try {
            l.onSuccess(workflowName, context, workflowResult);
          } catch (Exception e) {
            // log and continue; avoid swallowing silently
            log.error(
                "Error while executing the listener: {}.onSuccess", l.getClass().getName(), e);
          }
        });
  }

  /**
   * Notify all registered listeners that a workflow failed.
   *
   * @param workflowName the logical name of the workflow (not null)
   * @param context the workflow execution context (not null)
   * @param error the throwable that caused the failure (not null)
   */
  public void notifyFailure(String workflowName, WorkflowContext context, Throwable error) {
    listeners.forEach(
        l -> {
          try {
            l.onFailure(workflowName, context, error);
          } catch (Exception e) {
            // log and continue; avoid swallowing silently
            log.error(
                "Error while executing the listener: {}.onFailure", l.getClass().getName(), e);
          }
        });
  }
}
