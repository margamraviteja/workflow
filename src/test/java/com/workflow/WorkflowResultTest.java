package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for WorkflowResult covering all methods, edge cases, and boundary
 * conditions.
 */
class WorkflowResultTest {

  @Test
  void builder_createsResultWithAllFields() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusMillis(100);
    RuntimeException error = new RuntimeException("test error");

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(error)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertSame(error, result.getError());
    assertEquals(startedAt, result.getStartedAt());
    assertEquals(completedAt, result.getCompletedAt());
  }

  @Test
  void builder_allowsNullError() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .error(null)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertNull(result.getError());
  }

  @Test
  void isSuccess_returnsTrue_whenStatusIsSuccess() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertTrue(result.isSuccess());
    assertFalse(result.isFailure());
  }

  @Test
  void isSuccess_returnsFalse_whenStatusIsFailed() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new RuntimeException("error"))
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertFalse(result.isSuccess());
    assertTrue(result.isFailure());
  }

  @Test
  void isSuccess_returnsFalse_whenStatusIsSkipped() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SKIPPED)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertFalse(result.isSuccess());
    assertFalse(result.isFailure());
  }

  @Test
  void isFailure_returnsTrue_whenStatusIsFailed() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new RuntimeException())
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertTrue(result.isFailure());
    assertFalse(result.isSuccess());
  }

  @Test
  void isFailure_returnsFalse_whenStatusIsSuccess() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertFalse(result.isFailure());
    assertTrue(result.isSuccess());
  }

  @Test
  void getExecutionDuration_handlesZeroDuration() {
    Instant now = Instant.now();

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(now)
            .completedAt(now)
            .build();

    String duration = result.getExecutionDuration();
    assertEquals("0.000 seconds", duration);
  }

  @Test
  void getDuration_returnsCorrectDuration() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusMillis(1500);

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    Duration duration = result.getDuration();
    assertEquals(1500, duration.toMillis());
  }

  @Test
  void getDuration_handlesZeroDuration() {
    Instant now = Instant.now();

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(now)
            .completedAt(now)
            .build();

    Duration duration = result.getDuration();
    assertEquals(0, duration.toMillis());
  }

  @Test
  void toString_includesAllFields() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusMillis(100);
    RuntimeException error = new RuntimeException("test error");

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(error)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    String str = result.toString();
    assertTrue(str.contains("FAILED"));
    assertTrue(str.contains("error"));
    assertTrue(str.contains("startedAt"));
    assertTrue(str.contains("completedAt"));
  }

  @Test
  void toString_worksWithNullError() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .error(null)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    String str = result.toString();
    assertNotNull(str);
    assertTrue(str.contains("SUCCESS"));
  }

  @Test
  void builder_partialBuild_allowsMissingOptionalFields() {
    // Test that builder works with minimal fields
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertNull(result.getError());
  }

  @Test
  void getExecutionDuration_withExactlyOneSecond() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusSeconds(1);

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    String duration = result.getExecutionDuration();
    assertEquals("1.000 seconds", duration);
  }

  @Test
  void getExecutionDuration_withMillisecondPrecision() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusMillis(1);

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    String duration = result.getExecutionDuration();
    assertEquals("0.001 seconds", duration);
  }

  @Test
  void multipleResults_areIndependent() {
    Instant now = Instant.now();
    RuntimeException error1 = new RuntimeException("error1");

    WorkflowResult result1 =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(now)
            .completedAt(now.plusMillis(100))
            .build();

    WorkflowResult result2 =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(error1)
            .startedAt(now)
            .completedAt(now.plusMillis(200))
            .build();

    // Verify independence
    assertTrue(result1.isSuccess());
    assertTrue(result2.isFailure());
    assertNull(result1.getError());
    assertSame(error1, result2.getError());
  }

  @Test
  void workflowResultWithDifferentStatuses() {
    Instant now = Instant.now();

    // SUCCESS
    WorkflowResult success =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(now)
            .completedAt(now)
            .build();
    assertTrue(success.isSuccess());
    assertFalse(success.isFailure());

    // FAILED
    WorkflowResult failed =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new RuntimeException())
            .startedAt(now)
            .completedAt(now)
            .build();
    assertFalse(failed.isSuccess());
    assertTrue(failed.isFailure());

    // SKIPPED
    WorkflowResult skipped =
        WorkflowResult.builder()
            .status(WorkflowStatus.SKIPPED)
            .startedAt(now)
            .completedAt(now)
            .build();
    assertFalse(skipped.isSuccess());
    assertFalse(skipped.isFailure());
  }

  @Test
  void getDuration_accurateToNanosecond() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusNanos(123456789); // 123.456789 ms

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    Duration duration = result.getDuration();
    assertEquals(123456789, duration.toNanos());
  }

  @Test
  void builderPattern_allowsMethodChaining() {
    Instant now = Instant.now();

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(now)
            .completedAt(now.plusMillis(50))
            .error(null)
            .build();

    assertNotNull(result);
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void error_canBeAnyThrowableType() {
    Instant now = Instant.now();

    // RuntimeException
    WorkflowResult result1 =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new RuntimeException("runtime"))
            .startedAt(now)
            .completedAt(now)
            .build();
    assertInstanceOf(RuntimeException.class, result1.getError());

    // IOException
    WorkflowResult result2 =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new java.io.IOException("io error"))
            .startedAt(now)
            .completedAt(now)
            .build();
    assertInstanceOf(java.io.IOException.class, result2.getError());

    // Custom exception
    class CustomException extends Exception {
      public CustomException(String message) {
        super(message);
      }
    }
    WorkflowResult result3 =
        WorkflowResult.builder()
            .status(WorkflowStatus.FAILED)
            .error(new CustomException("custom"))
            .startedAt(now)
            .completedAt(now)
            .build();
    assertInstanceOf(CustomException.class, result3.getError());
  }

  @Test
  void formattedDuration_handlesVeryLongExecution() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusSeconds(3600); // 1 hour

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    String duration = result.getExecutionDuration();
    assertEquals("3600.000 seconds", duration);
  }

  @Test
  void formattedDuration_handles999Milliseconds() {
    Instant startedAt = Instant.now();
    Instant completedAt = startedAt.plusMillis(999);

    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();

    String duration = result.getExecutionDuration();
    assertEquals("0.999 seconds", duration);
  }
}
