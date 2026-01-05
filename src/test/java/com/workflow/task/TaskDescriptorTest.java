package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class TaskDescriptorTest {

  @Test
  void shouldBuildWithAllFields() {
    Task task = _ -> CompletableFuture.completedFuture("result");
    RetryPolicy retryPolicy = RetryPolicy.limitedRetries(3);
    TimeoutPolicy timeoutPolicy = TimeoutPolicy.ofSeconds(5);

    TaskDescriptor descriptor =
        TaskDescriptor.builder()
            .name("testTask")
            .task(task)
            .retryPolicy(retryPolicy)
            .timeoutPolicy(timeoutPolicy)
            .build();

    assertEquals("testTask", descriptor.getName());
    assertEquals(task, descriptor.getTask());
    assertEquals(retryPolicy, descriptor.getRetryPolicy());
    assertEquals(timeoutPolicy, descriptor.getTimeoutPolicy());
  }

  @Test
  void shouldUseDefaultPolicies() {
    Task task = _ -> CompletableFuture.completedFuture("result");

    TaskDescriptor descriptor = TaskDescriptor.builder().task(task).build();

    assertNotNull(descriptor.getTask());
    assertEquals(RetryPolicy.NONE, descriptor.getRetryPolicy());
    assertEquals(TimeoutPolicy.NONE, descriptor.getTimeoutPolicy());
    assertNull(descriptor.getName());
  }

  @Test
  void shouldBuildWithNameOnly() {
    Task task = _ -> CompletableFuture.completedFuture("result");

    TaskDescriptor descriptor = TaskDescriptor.builder().name("myTask").task(task).build();

    assertEquals("myTask", descriptor.getName());
    assertEquals(task, descriptor.getTask());
    assertEquals(RetryPolicy.NONE, descriptor.getRetryPolicy());
    assertEquals(TimeoutPolicy.NONE, descriptor.getTimeoutPolicy());
  }

  @Test
  void shouldBuildWithRetryPolicyOnly() {
    Task task = _ -> CompletableFuture.completedFuture("result");
    RetryPolicy retryPolicy = RetryPolicy.limitedRetries(5);

    TaskDescriptor descriptor =
        TaskDescriptor.builder().task(task).retryPolicy(retryPolicy).build();

    assertEquals(task, descriptor.getTask());
    assertEquals(retryPolicy, descriptor.getRetryPolicy());
    assertEquals(TimeoutPolicy.NONE, descriptor.getTimeoutPolicy());
  }

  @Test
  void shouldBuildWithTimeoutPolicyOnly() {
    Task task = _ -> CompletableFuture.completedFuture("result");
    TimeoutPolicy timeoutPolicy = TimeoutPolicy.ofSeconds(10);

    TaskDescriptor descriptor =
        TaskDescriptor.builder().task(task).timeoutPolicy(timeoutPolicy).build();

    assertEquals(task, descriptor.getTask());
    assertEquals(RetryPolicy.NONE, descriptor.getRetryPolicy());
    assertEquals(timeoutPolicy, descriptor.getTimeoutPolicy());
  }

  @Test
  void shouldSupportValueEquality() {
    Task task = _ -> CompletableFuture.completedFuture("result");
    RetryPolicy retryPolicy = RetryPolicy.limitedRetries(3);
    TimeoutPolicy timeoutPolicy = TimeoutPolicy.ofSeconds(5);

    TaskDescriptor descriptor1 =
        TaskDescriptor.builder()
            .name("task")
            .task(task)
            .retryPolicy(retryPolicy)
            .timeoutPolicy(timeoutPolicy)
            .build();

    TaskDescriptor descriptor2 =
        TaskDescriptor.builder()
            .name("task")
            .task(task)
            .retryPolicy(retryPolicy)
            .timeoutPolicy(timeoutPolicy)
            .build();

    assertEquals(descriptor1, descriptor2);
    assertEquals(descriptor1.hashCode(), descriptor2.hashCode());
  }

  @Test
  void shouldNotEqualWithDifferentNames() {
    Task task = _ -> CompletableFuture.completedFuture("result");

    TaskDescriptor descriptor1 = TaskDescriptor.builder().name("task1").task(task).build();
    TaskDescriptor descriptor2 = TaskDescriptor.builder().name("task2").task(task).build();

    assertNotEquals(descriptor1, descriptor2);
  }

  @Test
  void shouldNotEqualWithDifferentTasks() {
    Task task1 = _ -> CompletableFuture.completedFuture("result1");
    Task task2 = _ -> CompletableFuture.completedFuture("result2");

    TaskDescriptor descriptor1 = TaskDescriptor.builder().task(task1).build();
    TaskDescriptor descriptor2 = TaskDescriptor.builder().task(task2).build();

    assertNotEquals(descriptor1, descriptor2);
  }

  @Test
  void shouldNotEqualWithDifferentRetryPolicies() {
    Task task = _ -> CompletableFuture.completedFuture("result");
    RetryPolicy retry1 = RetryPolicy.limitedRetries(3);
    RetryPolicy retry2 = RetryPolicy.limitedRetries(5);

    TaskDescriptor descriptor1 = TaskDescriptor.builder().task(task).retryPolicy(retry1).build();
    TaskDescriptor descriptor2 = TaskDescriptor.builder().task(task).retryPolicy(retry2).build();

    assertNotEquals(descriptor1, descriptor2);
  }

  @Test
  void shouldNotEqualWithDifferentTimeoutPolicies() {
    Task task = _ -> CompletableFuture.completedFuture("result");
    TimeoutPolicy timeout1 = TimeoutPolicy.ofSeconds(5);
    TimeoutPolicy timeout2 = TimeoutPolicy.ofSeconds(10);

    TaskDescriptor descriptor1 =
        TaskDescriptor.builder().task(task).timeoutPolicy(timeout1).build();
    TaskDescriptor descriptor2 =
        TaskDescriptor.builder().task(task).timeoutPolicy(timeout2).build();

    assertNotEquals(descriptor1, descriptor2);
  }

  @Test
  void shouldHaveWorkingToString() {
    Task task = _ -> CompletableFuture.completedFuture("result");

    TaskDescriptor descriptor = TaskDescriptor.builder().name("myTask").task(task).build();

    String toString = descriptor.toString();
    assertTrue(toString.contains("myTask"));
    assertTrue(toString.contains("TaskDescriptor"));
  }

  @Test
  void shouldHandleNullName() {
    Task task = _ -> CompletableFuture.completedFuture("result");

    TaskDescriptor descriptor = TaskDescriptor.builder().task(task).name(null).build();

    assertNull(descriptor.getName());
    assertNotNull(descriptor.getTask());
  }
}
