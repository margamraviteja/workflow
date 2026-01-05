package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileWriteTaskTest {

  @TempDir Path tempDir;

  @Test
  void execute_writesContentFromContextToFile_andUpdatesContext() throws Exception {
    Path file = tempDir.resolve("out.txt");
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(ctx.get("key")).thenReturn("hello world");

    FileWriteTask task = new FileWriteTask("key", file);
    task.execute(ctx);

    // file content written
    String written = Files.readString(file, StandardCharsets.UTF_8);
    assertEquals("hello world", written);

    // context updated with lastWrittenFile
    verify(ctx, times(1)).put("lastWrittenFile", file.toString());
  }

  @Test
  void execute_withNullContentKey_readsNullAndWritesStringNull() throws Exception {
    Path file = tempDir.resolve("out-nullkey.txt");
    WorkflowContext ctx = mock(WorkflowContext.class);

    // context.get(null) returns null
    when(ctx.get((String) null)).thenReturn(null);

    FileWriteTask task = new FileWriteTask(null, file);
    task.execute(ctx);

    String written = Files.readString(file, StandardCharsets.UTF_8);
    // String.valueOf(null) -> "null"
    assertEquals("null", written);

    verify(ctx).put("lastWrittenFile", file.toString());
  }

  @Test
  void execute_whenContextGetThrows_wrappedInTaskExecutionException() {
    Path file = tempDir.resolve("should-not-exist.txt");
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(ctx.get("k")).thenThrow(new RuntimeException("get failed"));

    FileWriteTask task = new FileWriteTask("k", file);

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertEquals("get failed", ex.getCause().getMessage());
    assertTrue(ex.getMessage().contains(file.toString()));
  }

  @Test
  void execute_whenFilesWriteFails_throwsTaskExecutionExceptionWithIOExceptionCause()
      throws Exception {
    // create a directory and attempt to write to it (Files.writeString to a directory should fail)
    Path dir = tempDir.resolve("a_dir");
    Files.createDirectory(dir);

    WorkflowContext ctx = mock(WorkflowContext.class);
    when(ctx.get("k")).thenReturn("payload");

    FileWriteTask task = new FileWriteTask("k", dir);

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertInstanceOf(IOException.class, ex.getCause());
    assertTrue(ex.getMessage().contains(dir.toString()));
  }

  @Test
  void execute_whenContextPutThrows_wrappedInTaskExecutionException() throws Exception {
    Path file = tempDir.resolve("out-put-exception.txt");
    WorkflowContext ctx = mock(WorkflowContext.class);

    when(ctx.get("k")).thenReturn("payload");
    doThrow(new RuntimeException("put failed")).when(ctx).put("lastWrittenFile", file.toString());

    FileWriteTask task = new FileWriteTask("k", file);

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertEquals("put failed", ex.getCause().getMessage());

    // file should still have been written before put failed
    String written = Files.readString(file, StandardCharsets.UTF_8);
    assertEquals("payload", written);
  }

  @Test
  void execute_withNullPath_throwsTaskExecutionExceptionWrappingNpe() {
    WorkflowContext ctx = mock(WorkflowContext.class);
    when(ctx.get("k")).thenReturn("x");

    FileWriteTask task = new FileWriteTask("k", null);

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertInstanceOf(NullPointerException.class, ex.getCause());
    assertTrue(ex.getMessage().contains("null"));
  }
}
