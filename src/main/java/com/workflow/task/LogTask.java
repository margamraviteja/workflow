package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A task that logs messages using SLF4J at configurable log levels.
 *
 * <p>This task enables logging within workflows, supporting different log levels and dynamic
 * message and logging parameters from the workflow context. It provides a flexible way to
 * instrument workflows with logging at various execution stages.
 *
 * <p><b>Supported Log Levels:</b>
 *
 * <ul>
 *   <li>{@code TRACE} - Finest-grained informational messages
 *   <li>{@code DEBUG} - Detailed diagnostic information
 *   <li>{@code INFO} - Informational messages (default)
 *   <li>{@code WARN} - Warning messages for potentially harmful situations
 *   <li>{@code ERROR} - Error messages for error events, optionally with exceptions
 * </ul>
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Supports all SLF4J log levels: TRACE, DEBUG, INFO, WARN, ERROR
 *   <li>Configurable logger name (defaults to "workflow.log")
 *   <li>Optional exception/throwable logging for ERROR level
 *   <li>Efficient logging level checking to avoid unnecessary string formatting
 *   <li>Fluent builder API for easy task configuration
 * </ul>
 *
 * <p><b>Basic Usage Examples:</b>
 *
 * <pre>{@code
 * LogTask infoLog = LogTask.builder()
 *     .message("Workflow started")
 *     .level(LogLevel.INFO)
 *     .build();
 *
 * // Error log with exception
 * LogTask errorLog = LogTask.builder()
 *     .message("Error processing request")
 *     .level(LogLevel.ERROR)
 *     .throwable(exception)
 *     .build();
 * }</pre>
 *
 * <p><b>Advanced Usage:</b>
 *
 * <pre>{@code
 * // Log with multiple parameters and throwable
 * LogTask advancedLog = LogTask.builder()
 *     .message("Request {} failed with code {}: {}")
 *     .parameters("user-123", 500, "Internal Server Error")
 *     .level(LogLevel.ERROR)
 *     .throwable(new RuntimeException("Processing failed"))
 *     .loggerName("com.myapp.audit")
 *     .build();
 *
 * // Dynamic level and message from context
 * context.put("message", "Processing batch job");
 * context.put("logLevel", LogLevel.INFO);
 * task.execute(context);
 * }</pre>
 *
 * <p><b>Default Values:</b>
 *
 * <ul>
 *   <li>Log Level: {@link LogLevel#INFO}
 *   <li>Logger Name: {@code "workflow.log"}
 *   <li>Parameters: empty array
 *   <li>Throwable: {@code null}
 * </ul>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <p>The task performs level checking before logging to avoid unnecessary message formatting. This
 * ensures minimal performance impact when log levels are disabled.
 *
 * @see LogLevel
 * @see WorkflowContext
 * @see TaskExecutionException
 */
public class LogTask extends AbstractTask {

  private static final String DEFAULT_LOGGER_NAME = "workflow.log";

  /**
   * Supported log levels for the LogTask.
   *
   * <p>These levels correspond to standard SLF4J logging levels, allowing fine-grained control over
   * what messages are logged. The effective log level depends on the logger configuration in your
   * logging framework (e.g., log4j2, logback).
   *
   * @see org.slf4j.Logger
   */
  public enum LogLevel {
    /** Finest-grained informational messages, typically used for detailed diagnostics. */
    TRACE,
    /** Detailed diagnostic information, useful for debugging. */
    DEBUG,
    /** General informational messages about application progress. */
    INFO,
    /** Warning messages for potentially harmful situations that may need attention. */
    WARN,
    /** Error messages for error events with predictable recovery. */
    ERROR
  }

  private final String message;
  private final Object[] parameters;
  private final Throwable throwable;
  private final LogLevel level;
  private final Logger logger;

  /**
   * Constructs a LogTask from a builder.
   *
   * <p>This constructor initializes all fields from the builder configuration and validates that
   * the message is not null. The logger is obtained from the SLF4J LoggerFactory using the
   * configured or default logger name.
   *
   * @param builder the builder containing the configuration
   * @throws NullPointerException if the message is null
   */
  private LogTask(Builder builder) {
    this.message = Objects.requireNonNull(builder.message, "message must not be null");
    this.parameters = builder.parameters != null ? builder.parameters : new Object[0];
    this.throwable = builder.throwable;
    this.level = builder.level != null ? builder.level : LogLevel.INFO;
    if (builder.logger == null) {
      String loggerName = builder.loggerName != null ? builder.loggerName : DEFAULT_LOGGER_NAME;
      this.logger = LoggerFactory.getLogger(loggerName);
    } else {
      this.logger = builder.logger;
    }
  }

  /**
   * Executes the logging task.
   *
   * <p>This method performs level checking before logging to ensure that unnecessary message
   * formatting is avoided when the configured log level is disabled. If the log level is enabled,
   * it delegates to {@link #logMessage(WorkflowContext)} to perform the actual logging.
   *
   * @param context the workflow context containing variables and state
   * @throws TaskExecutionException if an error occurs during task execution
   */
  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    if (isLevelEnabled()) {
      logMessage(context);
    }
  }

  /**
   * Checks if the logger is configured to accept messages at the specified level.
   *
   * <p>This method uses the configured log level to determine if logging should occur. It delegates
   * to the appropriate SLF4J logger method based on the configured level.
   *
   * @return true if the logger is enabled for the configured level; false otherwise
   */
  private boolean isLevelEnabled() {
    return switch (level) {
      case TRACE -> logger.isTraceEnabled();
      case DEBUG -> logger.isDebugEnabled();
      case INFO -> logger.isInfoEnabled();
      case WARN -> logger.isWarnEnabled();
      case ERROR -> logger.isErrorEnabled();
    };
  }

  /**
   * Logs the message at the configured log level.
   *
   * <p>This method retrieves the message, parameters, and optional throwable from the workflow
   * context, with fallback to the builder-configured values if not found in the context. It then
   * delegates to the appropriate SLF4J logger method based on the configured level.
   *
   * <p>For ERROR level logs, the throwable is included if provided. For other levels, parameters
   * are used for message formatting.
   *
   * @param context the workflow context containing runtime variables
   */
  private void logMessage(WorkflowContext context) {
    String msg = context.getTyped("message", String.class, this.message);
    Object[] params = context.getTyped("parameters", Object[].class, this.parameters);
    Throwable exception = context.getTyped("throwable", Throwable.class, this.throwable);
    switch (level) {
      case TRACE -> logger.trace(msg, params);
      case DEBUG -> logger.debug(msg, params);
      case INFO -> logger.info(msg, params);
      case WARN -> logger.warn(msg, params);
      case ERROR -> logger.error(msg, params, exception);
    }
  }

  /**
   * Returns the name of this task.
   *
   * <p>The name includes the configured log level for easy identification in logs and debugging.
   *
   * @return a string representation in the format {@code "LogTask[LEVEL]"}
   */
  @Override
  public String getName() {
    return String.format("LogTask[%s]", level);
  }

  /**
   * Creates a new builder for LogTask.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for constructing LogTask instances.
   *
   * <p>This builder provides a fluent API for creating LogTask instances with customizable logging
   * configuration. All builder methods return the builder instance itself to enable method
   * chaining.
   *
   * <p><b>Required Configuration:</b> The {@code message} must be set before calling {@link
   * #build()}.
   *
   * <p><b>Builder Example:</b>
   *
   * <pre>{@code
   * LogTask task = LogTask.builder()
   *     .message("Processing user ${userId}")
   *     .level(LogLevel.DEBUG)
   *     .loggerName("com.myapp")
   *     .build();
   * }</pre>
   *
   * @see LogTask#builder()
   */
  public static class Builder {
    private String message;
    private Object[] parameters;
    private Throwable throwable;
    private LogLevel level;
    private String loggerName;
    private Logger logger;

    /**
     * Sets the log message.
     *
     * @param message the message template (required)
     * @return this builder for method chaining
     * @throws NullPointerException if message is null during build
     */
    public Builder message(String message) {
      this.message = message;
      return this;
    }

    /**
     * Sets the parameters for logging.
     *
     * <p>These parameters are used for message formatting and can be retrieved from the workflow
     * context at execution time.
     *
     * @param parameters the parameters for log message formatting (varargs)
     * @return this builder for method chaining
     */
    public Builder parameters(Object... parameters) {
      this.parameters = parameters;
      return this;
    }

    /**
     * Sets the throwable to log (optional). Used when logging exceptions at ERROR level.
     *
     * <p>When configured, the throwable is included in the log output at the ERROR level. For other
     * log levels, the throwable is ignored. If set to null, the throwable from the workflow context
     * (if available) is used instead.
     *
     * @param throwable the throwable/exception to log (optional)
     * @return this builder for method chaining
     */
    public Builder throwable(Throwable throwable) {
      this.throwable = throwable;
      return this;
    }

    /**
     * Sets the log level.
     *
     * <p>The log level determines which messages are actually logged based on your logging
     * framework configuration. If not set, defaults to {@link LogLevel#INFO}.
     *
     * @param level the log level (default: INFO)
     * @return this builder for method chaining
     */
    public Builder level(LogLevel level) {
      this.level = level;
      return this;
    }

    /**
     * Sets the logger name.
     *
     * <p>The logger name is used to obtain the SLF4J logger instance via {@link
     * org.slf4j.LoggerFactory#getLogger(String)}. Different logger names can be used to organize
     * logging by component or module. If not set, defaults to {@code "workflow.log"}.
     *
     * @param loggerName the logger name (default: "workflow.log")
     * @return this builder for method chaining
     */
    public Builder loggerName(String loggerName) {
      this.loggerName = loggerName;
      return this;
    }

    /**
     * Sets the logger instance directly.
     *
     * <p>Alternatively to setting a logger name, you can provide a pre-configured SLF4J Logger
     * instance. If both logger name and logger instance are set, the logger instance takes
     * precedence.
     *
     * @param logger the SLF4J Logger instance to use for logging
     * @return this builder for method chaining
     */
    public Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    /**
     * Builds the LogTask.
     *
     * @return the configured LogTask
     */
    public LogTask build() {
      return new LogTask(this);
    }
  }
}
