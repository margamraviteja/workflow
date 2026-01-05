package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileReadTaskTest {

  @TempDir Path tempDir;

  @Test
  void execute_readsFileAndStoresContentInContext() throws Exception {
    // Arrange
    Path file = tempDir.resolve("sample.txt");
    String content = "hello world\nline2";
    Files.writeString(file, content, StandardCharsets.UTF_8);

    WorkflowContext ctx = mock(WorkflowContext.class);
    FileReadTask task = new FileReadTask(file, "myKey");

    // Act
    task.execute(ctx);

    // Assert
    verify(ctx, times(1)).put("myKey", content);
  }

  @Test
  void execute_withNullTargetKey_storesContentWithNullKey() throws Exception {
    Path file = tempDir.resolve("sample2.txt");
    String content = "abc";
    Files.writeString(file, content, StandardCharsets.UTF_8);

    WorkflowContext ctx = mock(WorkflowContext.class);
    FileReadTask task = new FileReadTask(file, null);

    task.execute(ctx);

    verify(ctx, times(1)).put((String) null, content);
  }

  @Test
  void execute_nonExistentFile_throwsTaskExecutionExceptionWithCause() {
    Path nonExistent = tempDir.resolve("does-not-exist.txt");
    WorkflowContext ctx = mock(WorkflowContext.class);
    FileReadTask task = new FileReadTask(nonExistent, "k");

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertInstanceOf(NoSuchFileException.class, ex.getCause());
    assertTrue(ex.getMessage().contains(nonExistent.toString()));
  }

  @Test
  void execute_whenContextPutThrows_exceptionIsWrappedInTaskExecutionException() throws Exception {
    Path file = tempDir.resolve("sample3.txt");
    String content = "payload";
    Files.writeString(file, content, StandardCharsets.UTF_8);

    WorkflowContext ctx = mock(WorkflowContext.class);
    doThrow(new RuntimeException("put failed")).when(ctx).put("k", content);

    FileReadTask task = new FileReadTask(file, "k");

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertEquals("put failed", ex.getCause().getMessage());
    assertTrue(ex.getMessage().contains(file.toString()));
  }

  @Test
  void execute_withNullPath_throwsTaskExecutionExceptionWrappingNpe() {
    WorkflowContext ctx = mock(WorkflowContext.class);
    FileReadTask task = new FileReadTask(null, "k");

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertNotNull(ex.getCause());
    assertInstanceOf(NullPointerException.class, ex.getCause());
    assertTrue(ex.getMessage().contains("null"));
  }

  @Test
  void getName_includesPathToString() {
    Path file = tempDir.resolve("name-test.txt");
    FileReadTask task = new FileReadTask(file, "k");
    String name = task.getName();
    assertTrue(name.contains(file.toString()));
    assertTrue(name.startsWith("FileReadTask["));
    assertTrue(name.endsWith("]"));
  }
}
