package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * Writes a string value from the workflow context to a file on the filesystem.
 *
 * <p><b>Purpose:</b> Provides file output operations for workflows. Retrieves a string value from
 * the context and writes it to the specified file, making results persistent.
 *
 * <p><b>File I/O Semantics:</b>
 *
 * <ul>
 *   <li><b>Encoding:</b> Files are written as UTF-8 text
 *   <li><b>Creation:</b> Creates file if it does not exist
 *   <li><b>Truncation:</b> Overwrites existing file content completely
 *   <li><b>Buffering:</b> Uses Java NIO for efficient writing
 *   <li><b>Parent Directories:</b> Does NOT create parent directories (must exist)
 * </ul>
 *
 * <p><b>Content Retrieval:</b> The content value is retrieved from the context using the specified
 * {@code contentKey}. If the value is null or missing, it is converted to the string "null".
 *
 * <p><b>Context Mutation:</b>
 *
 * <ul>
 *   <li>Reads the content from {@code contentKey}
 *   <li>Writes a "lastWrittenFile" key with the file path as string
 *   <li>Does not modify other context values
 * </ul>
 *
 * <p><b>Exception Handling:</b>
 *
 * <ul>
 *   <li>Parent directory does not exist: Throws TaskExecutionException
 *   <li>Permission denied: Throws TaskExecutionException
 *   <li>Content key not found: Writes the string "null" to the file
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. Multiple threads can safely write to different
 * files concurrently. Writing to the same file concurrently will result in undefined behavior.
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Writing is synchronous and blocking
 *   <li>Network filesystems may have significant latency
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Save processed results to files
 *   <li>Export data in text/JSON/XML formats
 *   <li>Write logs or reports
 *   <li>Persist intermediate workflow state
 *   <li>Data pipeline sink stage
 * </ul>
 *
 * <p><b>Example Usage - Basic File Write:</b>
 *
 * <pre>{@code
 * Path outputFile = Path.of("results/output.txt");
 * Task fileWriteTask = new FileWriteTask("processedData", outputFile);
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("processedData", "Hello, World! This is the processed output.");
 * fileWriteTask.execute(context);
 *
 * String lastFile = context.getTyped("lastWrittenFile", String.class);
 * System.out.println("File written to: " + lastFile);
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Pipeline:</b>
 *
 * <pre>{@code
 * // Read → Transform → Write
 * SequentialWorkflow pipeline = SequentialWorkflow.builder()
 *     .name("DataTransformationPipeline")
 *     .task(new FileReadTask(
 *         Path.of("input/data.txt"),
 *         "rawContent"
 *     ))
 *     .task(new TextTransformationTask("rawContent", "transformedContent"))
 *     .task(new FileWriteTask("transformedContent", Path.of("output/result.txt")))
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = pipeline.execute(context);
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     System.out.println("Processing complete. Output written.");
 * }
 * }</pre>
 *
 * @see FileReadTask
 * @see Task
 * @see AbstractTask
 * @see java.nio.file.Files#writeString(Path, CharSequence, OpenOption...)
 */
public class FileWriteTask extends AbstractTask {
  private final String contentKey;
  private final Path path;

  /**
   * Constructs a FileWriteTask with the specified content key and file path.
   *
   * @param contentKey the key of the content in the workflow context
   * @param path the path to the file where the content will be written
   */
  public FileWriteTask(String contentKey, Path path) {
    this.contentKey = contentKey;
    this.path = path;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    try {
      String content = String.valueOf(context.get(contentKey));
      Files.writeString(path, content);
      context.put("lastWrittenFile", path.toString());
    } catch (Exception e) {
      throw new TaskExecutionException("Failed to write file: " + path, e);
    }
  }
}
