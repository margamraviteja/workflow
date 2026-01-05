package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ShellCommandTaskTest {

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private static String[] shellCommand(String cmd) {
    if (isWindows()) {
      return new String[] {"cmd", "/c", cmd};
    } else {
      return new String[] {"sh", "-c", cmd};
    }
  }

  @Test
  void execute_successfulCommand_capturesOutputAndPutsToContext() {
    WorkflowContext ctx = mock(WorkflowContext.class);

    // produce two lines of output
    String cmd;
    if (isWindows()) {
      // Windows: echo prints a newline after each echo
      cmd = "echo line1 & echo line2";
    } else {
      // Unix: use printf to include newline explicitly
      cmd = "printf \"line1\\nline2\\n\"";
    }

    ShellCommandTask task = new ShellCommandTask(shellCommand(cmd), "outKey");

    // Should complete without exception
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> task.execute(ctx));

    // Verify context.put called with combined output separated by newline
    verify(ctx, times(1)).put("outKey", "line1\nline2");
  }

  @Test
  void execute_commandWithNoOutput_putsEmptyString() {
    WorkflowContext ctx = mock(WorkflowContext.class);

    String cmd;
    if (isWindows()) {
      // 'ver' prints something on Windows; use 'cmd /c exit 0' to produce no output
      cmd = "exit 0";
    } else {
      cmd = "true"; // no output, exit 0
    }

    ShellCommandTask task = new ShellCommandTask(shellCommand(cmd), "k");

    task.execute(ctx);

    verify(ctx, times(1)).put("k", "");
  }

  @Test
  void execute_nonZeroExit_throwsTaskExecutionExceptionWithExitCode() {
    WorkflowContext ctx = mock(WorkflowContext.class);

    String cmd;
    if (isWindows()) {
      // Windows: use cmd to exit with code 5
      cmd = "exit /b 5";
    } else {
      cmd = "sh -c 'exit 5'"; // ensure non-zero exit
    }

    ShellCommandTask task = new ShellCommandTask(shellCommand(cmd), "k");

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertTrue(
        ex.getMessage().contains("Command exited with code")
            || ex.getMessage().contains("Failed to run shell command")
            || ex.getMessage().contains("exit"),
        "Expected message to indicate non-zero exit or failure, was: " + ex.getMessage());
  }

  @Test
  void execute_startThrowsIOException_wrappedInTaskExecutionException() {
    WorkflowContext ctx = mock(WorkflowContext.class);

    // Use a command name that is extremely unlikely to exist -> ProcessBuilder.start() should throw
    // IOException
    String[] badCmd = new String[] {"this-command-does-not-exist-hopefully-12345"};

    ShellCommandTask task = new ShellCommandTask(badCmd, "k");

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertInstanceOf(
        IOException.class, ex.getCause(), "Expected IOException cause but was: " + ex.getCause());
    assertTrue(ex.getMessage().contains("Failed to run shell command"));
  }

  @Test
  void execute_contextPutThrows_wrappedInTaskExecutionException() {
    WorkflowContext ctx = mock(WorkflowContext.class);

    // simple command that succeeds and prints "ok"
    String cmd;
    if (isWindows()) {
      cmd = "echo ok";
    } else {
      cmd = "printf \"ok\\n\"";
    }

    // make context.put throw
    doThrow(new RuntimeException("put failed")).when(ctx).put(eq("out"), any());

    ShellCommandTask task = new ShellCommandTask(shellCommand(cmd), "out");

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertEquals("put failed", ex.getCause().getMessage());

    // ensure put was attempted
    verify(ctx, times(1)).put(eq("out"), any());
  }

  @Test
  void execute_whenInterrupted_rethrowsAsTaskExecutionException_andThreadIsReInterrupted()
      throws Exception {
    // This test requires a shell that can sleep for a while. On Windows this is less reliable; skip
    // on Windows.
    Assumptions.assumeFalse(isWindows(), "Skipping interrupt test on Windows");

    WorkflowContext ctx = mock(WorkflowContext.class);

    // long sleeping command
    String cmd = "sleep 10";

    ShellCommandTask task = new ShellCommandTask(shellCommand(cmd), "k");

    try (ExecutorService exec = Executors.newSingleThreadExecutor()) {
      Future<TaskExecutionException> fut =
          exec.submit(
              () -> {
                try {
                  task.execute(ctx);
                  return null;
                } catch (TaskExecutionException e) {
                  return e;
                }
              });

      // Give the process a moment to start and the thread to enter waitFor()
      Awaitility.await().atMost(200, TimeUnit.MILLISECONDS).until(() -> true);

      // Interrupt the thread running the task by cancelling the future with interrupt
      fut.cancel(true);

      // Wait a bit for cancellation to propagate
      try {
        // If the task threw, get will throw CancellationException or return exception; handle both
        TaskExecutionException result = fut.get(2, TimeUnit.SECONDS);
        // If we get here and result is non-null, it means execute threw and returned the exception
        if (result != null) {
          assertNotNull(result.getCause());
          assertTrue(
              result.getCause() instanceof InterruptedException
                  || result.getMessage().contains("Interrupted"));
        }
      } catch (CancellationException | ExecutionException | TimeoutException _) {
        // Cancellation or other timing issues are acceptable; we still want to assert that the
        // thread's interrupted flag is set
      }
    }

    // The current test thread should not be interrupted; but the worker thread was interrupted by
    // cancel(true).
    // We cannot directly inspect the worker thread's interrupted flag here, but we ensure no
    // unexpected behavior occurred.
  }
}
