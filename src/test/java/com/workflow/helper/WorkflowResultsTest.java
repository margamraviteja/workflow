package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkflowResultsTest {

  @Test
  void success_createsSuccessResultWithTimestamps() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusSeconds(5);

    WorkflowResult result = WorkflowResults.success(startedAt, completedAt);

    assertNotNull(result);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(startedAt, result.getStartedAt());
    assertEquals(completedAt, result.getCompletedAt());
    assertNull(result.getError());
  }

  @Test
  void success_withSameTimestamps_createsValidResult() {
    Instant now = Instant.now();

    WorkflowResult result = WorkflowResults.success(now, now);

    assertNotNull(result);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(result.isSuccess());
  }

  @Test
  void failure_createsFailedResultWithErrorAndTimestamps() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusSeconds(3);
    Throwable error = new RuntimeException("Test error");

    WorkflowResult result = WorkflowResults.failure(startedAt, completedAt, error);

    assertNotNull(result);
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertEquals(startedAt, result.getStartedAt());
    assertEquals(completedAt, result.getCompletedAt());
    assertEquals(error, result.getError());
    assertTrue(result.isFailure());
  }

  @Test
  void failure_withNullError_createsResultWithNullError() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusSeconds(1);

    WorkflowResult result = WorkflowResults.failure(startedAt, completedAt, null);

    assertNotNull(result);
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNull(result.getError());
  }

  @Test
  void failure_preservesExceptionDetails() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusSeconds(2);
    Exception error = new IllegalArgumentException("Invalid input");

    WorkflowResult result = WorkflowResults.failure(startedAt, completedAt, error);

    assertEquals(error, result.getError());
    assertEquals("Invalid input", result.getError().getMessage());
  }

  @Test
  void success_calculatesExecutionDurationCorrectly() {
    Instant startedAt = Instant.parse("2024-01-01T10:00:00Z");
    Instant completedAt = Instant.parse("2024-01-01T10:00:05.500Z");

    WorkflowResult result = WorkflowResults.success(startedAt, completedAt);

    assertEquals("5.500 seconds", result.getExecutionDuration());
  }

  @Test
  void failure_calculatesExecutionDurationCorrectly() {
    Instant startedAt = Instant.parse("2024-01-01T10:00:00Z");
    Instant completedAt = Instant.parse("2024-01-01T10:00:02.250Z");
    Exception error = new RuntimeException("Failed");

    WorkflowResult result = WorkflowResults.failure(startedAt, completedAt, error);

    assertEquals("2.250 seconds", result.getExecutionDuration());
  }
}
