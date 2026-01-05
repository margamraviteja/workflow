package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Executes a shell command and captures its output in the workflow context.
 *
 * <p><b>Purpose:</b> Enables integration with external shell commands and scripts. Useful for
 * invoking system utilities, running scripts, and accessing tools not available as Java libraries.
 *
 * <p><b>Command Execution:</b>
 *
 * <ul>
 *   <li>Accepts command as array of strings (first element is command, rest are arguments)
 *   <li>Executes using ProcessBuilder for cross-platform compatibility
 *   <li>Captures standard output (stdout)
 *   <li>Joins output lines with newline characters
 *   <li>Stores output in context under specified key
 * </ul>
 *
 * <p><b>Exit Code Handling:</b>
 *
 * <ul>
 *   <li><b>Exit Code 0:</b> Command succeeded, output stored normally
 *   <li><b>Non-zero Exit Code:</b> Command failed, throws TaskExecutionException with exit code
 *   <li>Standard error (stderr) is NOT captured (merged with parent process)
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. Multiple threads can execute different
 * commands concurrently.
 *
 * <p><b>Context Mutation:</b> Writes command output to the specified output key. Does not read from
 * context.
 *
 * <p><b>Limitations:</b>
 *
 * <ul>
 *   <li>Does not capture stderr separately
 *   <li>Output is loaded entirely into memory (large outputs may cause memory issues)
 *   <li>No timeout mechanism built-in (use TimedTask for timeout protection)
 *   <li>Interactive commands are not supported
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Execute system utilities (ls, grep, awk, etc.)
 *   <li>Run shell scripts
 *   <li>Version management (git commands)
 *   <li>File system operations
 *   <li>System administration tasks
 * </ul>
 *
 * <p><b>Example Usage - List Files:</b>
 *
 * <pre>{@code
 * Task listTask = new ShellCommandTask(
 *     new String[]{"ls", "-la", "/home/user"},
 *     "fileList"
 * );
 *
 * WorkflowContext context = new WorkflowContext();
 * listTask.execute(context);
 *
 * String output = context.getTyped("fileList", String.class);
 * System.out.println("Files:\n" + output);
 * }</pre>
 *
 * <p><b>Example Usage - Git Commands:</b>
 *
 * <pre>{@code
 * // Get current git branch
 * Task gitBranch = new ShellCommandTask(
 *     new String[]{"git", "branch", "--show-current"},
 *     "currentBranch"
 * );
 *
 * // Get git log
 * Task gitLog = new ShellCommandTask(
 *     new String[]{"git", "log", "--oneline", "-n", "5"},
 *     "recentCommits"
 * );
 * }</pre>
 *
 * <p><b>Example Usage - With Exit Code Error:</b>
 *
 * <pre>{@code
 * Task grepTask = new ShellCommandTask(
 *     new String[]{"grep", "ERROR", "logfile.txt"},
 *     "errorLines"
 * );
 *
 * try {
 *     grepTask.execute(context);
 *     String errors = context.getTyped("errorLines", String.class);
 * } catch (TaskExecutionException e) {
 *     // Exits with non-zero if "ERROR" not found
 *     System.err.println("Command failed: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Workflow:</b>
 *
 * <pre>{@code
 * SequentialWorkflow buildWorkflow = SequentialWorkflow.builder()
 *     .name("BuildPipeline")
 *     .task(new ShellCommandTask(
 *         new String[]{"./gradlew", "clean"},
 *         "cleanOutput"
 *     ))
 *     .task(new ShellCommandTask(
 *         new String[]{"./gradlew", "build"},
 *         "buildOutput"
 *     ))
 *     .task(new ShellCommandTask(
 *         new String[]{"./gradlew", "test"},
 *         "testOutput"
 *     ))
 *     .build();
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 * @see TimedTask (for adding timeout protection)
 * @see java.lang.ProcessBuilder
 */
public class ShellCommandTask extends AbstractTask {
  private final String[] command;
  private final String outputKey;

  /**
   * @param command The shell command to run as an array of strings
   * @param outputKey The key to store the command output in the context
   */
  public ShellCommandTask(String[] command, String outputKey) {
    this.command = command;
    this.outputKey = outputKey;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      Process p = pb.start();
      int exit = p.waitFor();
      String output;
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        output = r.lines().collect(Collectors.joining("\n"));
      }
      context.put(outputKey, output);
      if (exit != 0) {
        throw new TaskExecutionException("Command exited with code " + exit);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("Interrupted", ie);
    } catch (Exception e) {
      throw new TaskExecutionException("Failed to run shell command", e);
    }
  }

  @Override
  public String getName() {
    return "ShellCommandTask(" + String.join(" ", command) + ")";
  }
}
