package com.workflow.task;

import com.workflow.context.WorkflowContext;
import java.util.Map;
import lombok.Builder;

/**
 * Selects and executes one of several tasks based on a value in the workflow context.
 *
 * <p><b>Purpose:</b> Implements multi-way branching at the task level. Reads a value from the
 * context, uses it as a key to look up a task from a cases map, and executes the selected task.
 * Provides a fallback task if the key is not found.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Key Lookup:</b> Retrieves the value of the switch key from context
 *   <li><b>Task Selection:</b> Looks up the value in the cases map
 *   <li><b>Default Fallback:</b> If key not found and defaultTask is provided, executes it
 *   <li><b>No-op on Missing:</b> If key not found and no defaultTask, execution succeeds with no-op
 *   <li><b>Context Sharing:</b> The selected task receives the same context instance
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe if all case tasks are thread-safe.
 *
 * <p><b>Context Mutation:</b> The selected task may read and modify the context. The switch key
 * itself is only read, not modified.
 *
 * <p><b>Exception Handling:</b> Any exception thrown by the selected task is propagated to the
 * caller.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Processing types (handle different document types differently)
 *   <li>Operation routing (execute different operation handlers)
 *   <li>Mode selection (strict, lenient, debug, production modes)
 *   <li>Content-type based processing (JSON vs XML vs CSV)
 * </ul>
 *
 * <p><b>Example Usage - Document Type Processing:</b>
 *
 * <pre>{@code
 * SwitchTask documentProcessor = SwitchTask.builder()
 *     .switchKey("documentType")
 *     .cases(Map.of(
 *         "pdf", new PdfProcessingTask(),
 *         "docx", new DocxProcessingTask(),
 *         "txt", new TextProcessingTask(),
 *         "json", new JsonProcessingTask()
 *     ))
 *     .defaultTask(new UnknownDocumentTask())  // Handle unexpected types
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("documentType", "pdf");
 * context.put("document", pdfDocument);
 *
 * documentProcessor.execute(context);
 * Object result = context.get("processingResult");
 * }</pre>
 *
 * <p><b>Example Usage - Mode-Based Execution:</b>
 *
 * <pre>{@code
 * SwitchTask modeSelector = SwitchTask.builder()
 *     .switchKey("executionMode")
 *     .cases(Map.of(
 *         "strict", new StrictValidationTask(),
 *         "lenient", new LenientValidationTask(),
 *         "debug", new DebugValidationTask()
 *     ))
 *     .defaultTask(new StrictValidationTask())  // Default to strict
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - With Null Key Handling:</b>
 *
 * <pre>{@code
 * SwitchTask safeSwitchTask = SwitchTask.builder()
 *     .switchKey("processingStrategy")
 *     .cases(Map.of(
 *         "fast", new FastProcessingTask(),
 *         "accurate", new AccurateProcessingTask()
 *     ))
 *     .defaultTask(new NoOpTask())  // Do nothing if key not found or not recognized
 *     .build();
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 * @see ConditionalTask
 * @see com.workflow.DynamicBranchingWorkflow
 */
@Builder
public class SwitchTask extends AbstractTask {
  private final String switchKey;
  private final Map<Object, Task> cases;
  private final Task defaultTask;

  @Override
  protected void doExecute(WorkflowContext context) {
    Object value = context.get(switchKey);
    Task task = cases.getOrDefault(value, defaultTask);
    if (task != null) {
      task.execute(context);
    }
  }

  @Override
  public String getName() {
    return "SwitchTask[" + switchKey + "]";
  }
}
