package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.TreeRenderer;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Iterates over a collection from the workflow context and executes a child workflow for each item.
 *
 * <p><b>Purpose:</b> Enables batch processing patterns where the same workflow logic needs to be
 * applied to each element in a collection. Useful for processing lists of users, orders, files, or
 * any iterable data.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Collection Retrieval:</b> Retrieves the collection from context using {@code itemsKey}
 *   <li><b>Item Binding:</b> For each item, sets it in context using {@code itemVariable}
 *   <li><b>Index Binding:</b> Optionally sets the current index using {@code indexVariable}
 *   <li><b>Sequential Execution:</b> Executes the child workflow for each item in order
 *   <li><b>Fail Fast:</b> Stops iteration on first failure and returns that result
 *   <li><b>Empty Collection:</b> Returns SUCCESS immediately if collection is null or empty
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. The child workflow must also be
 * thread-safe.
 *
 * <p><b>Example Usage - Processing Users:</b>
 *
 * <pre>{@code
 * Workflow processUser = new TaskWorkflow(new ProcessUserTask());
 * Workflow forEach = ForEachWorkflow.builder()
 *     .name("ProcessAllUsers")
 *     .itemsKey("userList")
 *     .itemVariable("currentUser")
 *     .indexVariable("userIndex")
 *     .workflow(processUser)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("userList", List.of(user1, user2, user3));
 * WorkflowResult result = forEach.execute(context);
 * }</pre>
 *
 * @see Workflow
 * @see AbstractWorkflow
 * @see RepeatWorkflow
 */
@Slf4j
public class ForEachWorkflow extends AbstractWorkflow implements WorkflowContainer {

  private final String name;
  private final String itemsKey;
  private final String itemVariable;
  private final String indexVariable;
  private final Workflow workflow;

  @Builder
  private ForEachWorkflow(
      String name, String itemsKey, String itemVariable, String indexVariable, Workflow workflow) {
    ValidationUtils.requireNonBlank(itemsKey, "itemsKey");
    ValidationUtils.requireNonBlank(itemVariable, "itemVariable");
    ValidationUtils.requireNonNull(workflow, "workflow");

    this.name = name;
    this.itemsKey = itemsKey;
    this.itemVariable = itemVariable;
    this.indexVariable = indexVariable;
    this.workflow = workflow;
  }

  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    Object itemsObj = context.get(itemsKey);

    if (itemsObj == null) {
      log.debug("Items key '{}' is null, skipping iteration", itemsKey);
      return execContext.success();
    }

    Collection<?> items;
    if (itemsObj instanceof Collection) {
      items = (Collection<?>) itemsObj;
    } else if (itemsObj.getClass().isArray()) {
      items = List.of((Object[]) itemsObj);
    } else {
      log.warn("Items key '{}' is not a collection or array: {}", itemsKey, itemsObj.getClass());
      return execContext.failure(
          new IllegalArgumentException(
              "Items key '" + itemsKey + "' must be a Collection or array"));
    }

    if (items.isEmpty()) {
      log.debug("Items collection is empty, skipping iteration");
      return execContext.success();
    }

    log.debug("Starting iteration over {} items", items.size());
    int index = 0;

    for (Object item : items) {
      log.debug("Processing item {} of {}: {}", index + 1, items.size(), item);

      // Set the current item in context
      context.put(itemVariable, item);

      // Optionally set the index
      if (indexVariable != null && !indexVariable.isBlank()) {
        context.put(indexVariable, index);
      }

      // Execute the child workflow
      WorkflowResult result = workflow.execute(context);

      if (result.getStatus() == WorkflowStatus.FAILED) {
        log.warn("Iteration {} failed, stopping foreach execution", index);
        return result;
      }

      index++;
    }

    log.debug("Completed iteration over all {} items", items.size());
    return execContext.success();
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("ForEach");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    String label = String.format("FOR EACH (%s IN %s) ->", itemVariable, itemsKey);
    return List.of(new TreeRenderer.TreeLabelWrapper(label, workflow));
  }
}
