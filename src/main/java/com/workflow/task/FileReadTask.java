package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a file from the filesystem and stores its content as a string in the workflow context.
 *
 * <p><b>Purpose:</b> Provides file input operations for workflows. Reads the entire file as UTF-8
 * text and stores it in the context for downstream tasks to process.
 *
 * <p><b>File I/O Semantics:</b>
 *
 * <ul>
 *   <li><b>Encoding:</b> Files are read as UTF-8 text
 *   <li><b>Buffering:</b> Uses Java NIO for efficient reading
 *   <li><b>Newlines:</b> Preserves original line endings in the file
 *   <li><b>File Size:</b> No limit on file size (entire file is loaded into memory)
 *   <li><b>Missing File:</b> Throws TaskExecutionException if file does not exist
 *   <li><b>Permission Errors:</b> Throws TaskExecutionException if file cannot be read
 * </ul>
 *
 * <p><b>Context Mutation:</b> Writes the file content as a string under the specified {@code
 * targetKey}. Does not modify other context values.
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. Multiple threads can safely read different
 * files concurrently.
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Large files are loaded entirely into memory
 *   <li>Reading is synchronous and blocking
 *   <li>Network filesystems may have significant latency
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Load configuration files for processing
 *   <li>Read input data from files
 *   <li>Load templates or reference data
 *   <li>Process uploaded files
 *   <li>Data pipeline source stage
 * </ul>
 *
 * <p><b>Example Usage - Basic File Read:</b>
 *
 * <pre>{@code
 * Path inputFile = Path.of("data/input.json");
 * Task fileReadTask = new FileReadTask(inputFile, "jsonContent");
 *
 * WorkflowContext context = new WorkflowContext();
 * fileReadTask.execute(context);
 *
 * String jsonContent = context.getTyped("jsonContent", String.class);
 * System.out.println("Loaded JSON: " + jsonContent);
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Pipeline:</b>
 *
 * <pre>{@code
 * // Read file → Parse JSON → Validate → Save to database
 * SequentialWorkflow pipeline = SequentialWorkflow.builder()
 *     .name("JsonImportPipeline")
 *     .task(new FileReadTask(
 *         Path.of("data/users.json"),
 *         "rawJsonContent"
 *     ))
 *     .task(new JsonParseTask("rawJsonContent", "parsedUsers"))
 *     .task(new UserValidationTask("parsedUsers", "validUsers"))
 *     .task(new DatabaseInsertTask("validUsers"))
 *     .build();
 *
 * WorkflowResult result = pipeline.execute(new WorkflowContext());
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     System.out.println("Import completed successfully");
 * }
 * }</pre>
 *
 * @see FileWriteTask
 * @see Task
 * @see AbstractTask
 * @see java.nio.file.Files#readString(Path, java.nio.charset.Charset)
 */
public class FileReadTask extends AbstractTask {
  private final Path path;
  private final String targetKey;

  /**
   * Constructs a FileReadTask.
   *
   * @param path the path to the file
   * @param targetKey the key under which the file content will be stored in the context
   */
  public FileReadTask(Path path, String targetKey) {
    this.path = path;
    this.targetKey = targetKey;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    try {
      String content = Files.readString(path, StandardCharsets.UTF_8);
      context.put(targetKey, content);
    } catch (Exception e) {
      throw new TaskExecutionException("Failed to read file: " + path, e);
    }
  }

  @Override
  public String getName() {
    return "FileReadTask[" + path + "]";
  }
}
