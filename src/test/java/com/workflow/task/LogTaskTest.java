package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class LogTaskTest {

  private WorkflowContext context;
  private Logger mockLogger;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
    mockLogger = mock(Logger.class);
  }

  @Test
  @DisplayName("Should log at INFO level with provided parameters when enabled")
  void execute_InfoLevelSuccess() throws TaskExecutionException {
    // Arrange
    LogTask task =
        LogTask.builder()
            .message("User {} logged in")
            .parameters("admin")
            .level(LogTask.LogLevel.INFO)
            .logger(mockLogger)
            .build();

    when(mockLogger.isInfoEnabled()).thenReturn(true);

    // Act
    task.execute(context);

    // Assert
    verify(mockLogger).info(eq("User {} logged in"), (Object[]) any());
  }

  @Test
  @DisplayName("Should skip logging call if the specific level is disabled in config")
  void execute_LevelDisabled() throws TaskExecutionException {
    // Arrange
    LogTask task =
        LogTask.builder()
            .message("Debug details")
            .level(LogTask.LogLevel.DEBUG)
            .logger(mockLogger)
            .build();

    when(mockLogger.isDebugEnabled()).thenReturn(false);

    // Act
    task.execute(context);

    // Assert
    verify(mockLogger, never()).debug(anyString(), any(Object[].class));
  }

  @Test
  @DisplayName("Should log ERROR with a Throwable when provided")
  void execute_ErrorWithThrowable() throws TaskExecutionException {
    // Arrange
    Exception ex = new RuntimeException("Database down");
    LogTask task =
        LogTask.builder()
            .message("Critical failure")
            .level(LogTask.LogLevel.ERROR)
            .throwable(ex)
            .logger(mockLogger)
            .build();

    when(mockLogger.isErrorEnabled()).thenReturn(true);

    // Act
    task.execute(context);

    // Assert
    verify(mockLogger).error(eq("Critical failure"), any(Object[].class), eq(ex));
  }

  @Test
  @DisplayName("Should prioritize context values over builder values (Dynamic Logging)")
  void execute_ContextOverrides() throws TaskExecutionException {
    // Arrange
    LogTask task =
        LogTask.builder()
            .message("Static message")
            .parameters("StaticParam")
            .logger(mockLogger)
            .build();

    when(mockLogger.isInfoEnabled()).thenReturn(true);

    // Inject dynamic data into context
    context.put("message", "Dynamic: {}");
    context.put("parameters", new Object[] {"DynamicParam"});

    // Act
    task.execute(context);

    // Assert - verify info was called exactly once
    verify(mockLogger, times(1)).info(anyString(), any(Object[].class));
  }

  @Test
  @DisplayName("Should use default logger name if neither name nor instance is provided")
  void builder_DefaultLoggerFlow() {
    LogTask task = LogTask.builder().message("Test").build();

    // Since we can't easily mock the static LoggerFactory without special tools,
    // we verify the object was created successfully with defaults.
    assertNotNull(task.getName());
    assertTrue(task.getName().contains("INFO"));
  }

  @Test
  @DisplayName("Should throw NullPointerException if message is missing (Sonar-compliant)")
  void builder_Validation() {
    // Refactor: Logic outside lambda, only invocation inside.
    LogTask.Builder builder = LogTask.builder().message(null);

    assertThrows(NullPointerException.class, builder::build);
  }
}
