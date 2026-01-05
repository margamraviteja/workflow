package com.workflow.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExceptionsTest {

  @Test
  void circularDependencyException_withMessage_createsException() {
    String message = "Circular dependency detected";
    CircularDependencyException exception = new CircularDependencyException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void circularDependencyException_withMessageAndCause_createsException() {
    String message = "Circular dependency detected";
    RuntimeException cause = new RuntimeException("Root cause");
    CircularDependencyException exception = new CircularDependencyException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void httpResponseProcessingException_withMessage_createsException() {
    String message = "HTTP 500 error";
    HttpResponseProcessingException exception = new HttpResponseProcessingException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void httpResponseProcessingException_withMessageAndCause_createsException() {
    String message = "HTTP processing failed";
    RuntimeException cause = new RuntimeException("Network error");
    HttpResponseProcessingException exception = new HttpResponseProcessingException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void httpResponseProcessingException_withCause_createsException() {
    RuntimeException cause = new RuntimeException("Network timeout");
    HttpResponseProcessingException exception = new HttpResponseProcessingException(cause);

    assertEquals(cause, exception.getCause());
    assertNotNull(exception.getMessage());
  }

  @Test
  void jsonProcessingException_withMessage_createsException() {
    String message = "Invalid JSON";
    JsonProcessingException exception = new JsonProcessingException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void jsonProcessingException_withMessageAndCause_createsException() {
    String message = "JSON parsing failed";
    RuntimeException cause = new RuntimeException("Malformed JSON");
    JsonProcessingException exception = new JsonProcessingException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void jsonProcessingException_withCause_createsException() {
    RuntimeException cause = new RuntimeException("Parse error");
    JsonProcessingException exception = new JsonProcessingException(cause);

    assertEquals(cause, exception.getCause());
    assertNotNull(exception.getMessage());
  }

  @Test
  void taskExecutionException_withMessage_createsException() {
    String message = "Task failed";
    TaskExecutionException exception = new TaskExecutionException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void taskExecutionException_withMessageAndCause_createsException() {
    String message = "Task execution failed";
    RuntimeException cause = new RuntimeException("Execution error");
    TaskExecutionException exception = new TaskExecutionException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void taskExecutionException_withCause_createsException() {
    RuntimeException cause = new RuntimeException("Task error");
    TaskExecutionException exception = new TaskExecutionException(cause);

    assertEquals(cause, exception.getCause());
    assertNotNull(exception.getMessage());
  }

  @Test
  void taskTimeoutException_withMessage_createsException() {
    String message = "Task timed out";
    TaskTimeoutException exception = new TaskTimeoutException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void taskTimeoutException_withMessageAndCause_createsException() {
    String message = "Task exceeded timeout";
    RuntimeException cause = new RuntimeException("Timeout occurred");
    TaskTimeoutException exception = new TaskTimeoutException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void workflowBuildException_withMessage_createsException() {
    String message = "Workflow build failed";
    WorkflowBuildException exception = new WorkflowBuildException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void workflowBuildException_withMessageAndCause_createsException() {
    String message = "Failed to build workflow";
    RuntimeException cause = new RuntimeException("Configuration error");
    WorkflowBuildException exception = new WorkflowBuildException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void workflowCompositionException_withMessage_createsException() {
    String message = "Workflow composition error";
    WorkflowCompositionException exception = new WorkflowCompositionException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void workflowCompositionException_withMessageAndCause_createsException() {
    String message = "Failed to compose workflow";
    RuntimeException cause = new RuntimeException("Composition error");
    WorkflowCompositionException exception = new WorkflowCompositionException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }
}
