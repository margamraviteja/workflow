package com.workflow.saga;

import com.workflow.TaskWorkflow;
import com.workflow.Workflow;
import com.workflow.helper.ValidationUtils;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import lombok.Getter;

/**
 * Represents a single step in a Saga workflow, pairing a forward action with an optional
 * compensating action.
 *
 * <p><b>Purpose:</b> Encapsulates a transactional step that can be rolled back. The forward
 * workflow performs the main action, while the compensation workflow undoes that action if a later
 * step in the saga fails.
 *
 * <p><b>Compensation Semantics:</b>
 *
 * <ul>
 *   <li>Compensation is optional - steps without compensation are valid (e.g., read-only steps)
 *   <li>Compensation should be idempotent where possible
 *   <li>Compensation receives the same context as the forward action, plus failure information
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * // Step with compensation
 * SagaStep reserveInventory = SagaStep.builder()
 *     .name("ReserveInventory")
 *     .action(new TaskWorkflow(new ReserveInventoryTask()))
 *     .compensation(new TaskWorkflow(new ReleaseInventoryTask()))
 *     .build();
 *
 * // Step without compensation (read-only)
 * SagaStep validateOrder = SagaStep.builder()
 *     .name("ValidateOrder")
 *     .action(new TaskWorkflow(new ValidateOrderTask()))
 *     .build();
 * }</pre>
 *
 * @see com.workflow.SagaWorkflow
 */
@Getter
public class SagaStep {
  /** Human-readable name for logging and debugging. */
  private final String name;

  /** The forward workflow that performs the main action. */
  private final Workflow action;

  /** The compensation workflow that undoes the action (nullable). */
  private final Workflow compensation;

  private SagaStep(SagaStepBuilder builder) {
    this.name = builder.name;
    this.action = builder.action;
    this.compensation = builder.compensation;
  }

  /**
   * Returns true if this step has a compensation workflow defined.
   *
   * @return true if compensation is not null
   */
  public boolean hasCompensation() {
    return compensation != null;
  }

  /**
   * Returns the step name, falling back to the action's name if not explicitly set.
   *
   * @return the step name
   */
  public String getName() {
    if (name != null && !name.isBlank()) {
      return name;
    }
    return action != null ? action.getName() : "UnnamedSagaStep";
  }

  /**
   * Creates a new builder for {@link SagaStep}.
   *
   * @return a new builder instance
   */
  public static SagaStepBuilder builder() {
    return new SagaStepBuilder();
  }

  /**
   * Builder for {@link SagaStep} with convenience methods for tasks.
   *
   * <p>Provides fluent methods to set:
   *
   * <ul>
   *   <li>{@link #action(Workflow)} or {@link #action(Task)} - the forward action (required)
   *   <li>{@link #compensation(Workflow)} or {@link #compensation(Task)} - the rollback action
   *       (optional)
   *   <li>{@link #name(String)} - human-readable identifier (optional)
   * </ul>
   */
  public static class SagaStepBuilder {
    private String name;
    private Workflow action;
    private Workflow compensation;

    /**
     * Sets the name of this saga step.
     *
     * @param name a descriptive name for logging and debugging
     * @return this builder
     */
    public SagaStepBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the forward action workflow.
     *
     * @param action the workflow to execute; must not be null
     * @return this builder
     */
    public SagaStepBuilder action(Workflow action) {
      this.action = action;
      return this;
    }

    /**
     * Convenience method to set the forward action from a Task. The task is automatically wrapped
     * in a {@link TaskWorkflow}.
     *
     * @param task the task to execute; must not be null
     * @return this builder
     */
    public SagaStepBuilder action(Task task) {
      ValidationUtils.requireNonNull(task, "action task");
      this.action = new TaskWorkflow(task);
      return this;
    }

    /**
     * Convenience method to set the forward action from a TaskDescriptor. The descriptor is wrapped
     * in a {@link TaskWorkflow}.
     *
     * @param taskDescriptor the task descriptor; must not be null
     * @return this builder
     */
    public SagaStepBuilder action(TaskDescriptor taskDescriptor) {
      ValidationUtils.requireNonNull(taskDescriptor, "action taskDescriptor");
      this.action = new TaskWorkflow(taskDescriptor);
      return this;
    }

    /**
     * Sets the compensation workflow to execute on rollback.
     *
     * @param compensation the workflow to execute for compensation; may be null
     * @return this builder
     */
    public SagaStepBuilder compensation(Workflow compensation) {
      this.compensation = compensation;
      return this;
    }

    /**
     * Convenience method to set the compensation from a Task. The task is automatically wrapped in
     * a {@link TaskWorkflow}.
     *
     * @param task the compensation task; must not be null
     * @return this builder
     */
    public SagaStepBuilder compensation(Task task) {
      ValidationUtils.requireNonNull(task, "compensation task");
      this.compensation = new TaskWorkflow(task);
      return this;
    }

    /**
     * Convenience method to set the compensation from a TaskDescriptor. The descriptor is wrapped
     * in a {@link TaskWorkflow}.
     *
     * @param taskDescriptor the compensation task descriptor; must not be null
     * @return this builder
     */
    public SagaStepBuilder compensation(TaskDescriptor taskDescriptor) {
      ValidationUtils.requireNonNull(taskDescriptor, "compensation taskDescriptor");
      this.compensation = new TaskWorkflow(taskDescriptor);
      return this;
    }

    /**
     * Builds and returns a new {@link SagaStep}.
     *
     * @return a configured SagaStep instance
     * @throws NullPointerException if action is null
     */
    public SagaStep build() {
      ValidationUtils.requireNonNull(action, "action");
      return new SagaStep(this);
    }
  }
}
