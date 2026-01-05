package com.workflow.listener;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.context.WorkflowContext;
import com.workflow.helper.WorkflowResults;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowListenerTest {

  private WorkflowListeners listeners;
  private WorkflowContext context;
  private TestListener testListener;
  private TestWorkflowListener listener1;
  private TestWorkflowListener listener2;

  @BeforeEach
  void setUp() {
    listeners = new WorkflowListeners();
    context = new WorkflowContext();
    testListener = new TestListener();
    listener1 = new TestWorkflowListener();
    listener2 = new TestWorkflowListener();
  }

  static class TestListener implements WorkflowListener {
    final List<String> events = new ArrayList<>();

    @Override
    public void onStart(String name, WorkflowContext ctx) {
      events.add("START:" + name);
    }

    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
      events.add("SUCCESS:" + name);
    }

    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable error) {
      events.add("FAILURE:" + name + ":" + error.getMessage());
    }
  }

  private static class TestWorkflowListener implements WorkflowListener {
    boolean startCalled = false;
    boolean successCalled = false;
    boolean failureCalled = false;
    String receivedName;
    WorkflowResult receivedResult;
    Throwable receivedError;

    @Override
    public void onStart(String workflowName, WorkflowContext context) {
      this.startCalled = true;
      this.receivedName = workflowName;
    }

    @Override
    public void onSuccess(String workflowName, WorkflowContext context, WorkflowResult result) {
      this.successCalled = true;
      this.receivedResult = result;
    }

    @Override
    public void onFailure(String workflowName, WorkflowContext context, Throwable error) {
      this.failureCalled = true;
      this.receivedError = error;
    }
  }

  @Test
  void register_addsListenerSuccessfully() {
    listeners.register(testListener);

    listeners.notifyStart("TestWorkflow", context);

    assertEquals(1, testListener.events.size());
    assertEquals("START:TestWorkflow", testListener.events.getFirst());
  }

  @Test
  void register_multipleListeners_allReceiveNotifications() {
    TestListener listener4 = new TestListener();
    TestListener listener5 = new TestListener();

    listeners.register(listener4);
    listeners.register(listener5);

    listeners.notifyStart("TestWorkflow", context);

    assertEquals(1, listener4.events.size());
    assertEquals(1, listener5.events.size());
  }

  @Test
  void unregister_removesListener() {
    listeners.register(testListener);
    listeners.unregister(testListener);

    listeners.notifyStart("TestWorkflow", context);

    assertTrue(testListener.events.isEmpty());
  }

  @Test
  void unregister_nonExistentListener_doesNotThrow() {
    TestListener listener = new TestListener();

    assertDoesNotThrow(() -> listeners.unregister(listener));
  }

  @Test
  void notifyStart_notifiesAllListeners() {
    TestListener listener4 = new TestListener();
    TestListener listener5 = new TestListener();

    listeners.register(listener4);
    listeners.register(listener5);

    listeners.notifyStart("Workflow1", context);

    assertEquals("START:Workflow1", listener4.events.getFirst());
    assertEquals("START:Workflow1", listener5.events.getFirst());
  }

  @Test
  void notifySuccess_notifiesAllListeners() {
    WorkflowResult result =
        WorkflowResult.builder()
            .status(WorkflowStatus.SUCCESS)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

    listeners.register(testListener);
    listeners.notifySuccess("Workflow1", context, result);

    assertEquals("SUCCESS:Workflow1", testListener.events.getFirst());
  }

  @Test
  void notifyFailure_notifiesAllListeners() {
    RuntimeException error = new RuntimeException("Test error");

    listeners.register(testListener);
    listeners.notifyFailure("Workflow1", context, error);

    assertEquals("FAILURE:Workflow1:Test error", testListener.events.getFirst());
  }

  @Test
  void listeners_receiveEventsInRegistrationOrder() {
    TestListener listener4 = new TestListener();
    TestListener listener5 = new TestListener();
    TestListener listener6 = new TestListener();

    listeners.register(listener4);
    listeners.register(listener5);
    listeners.register(listener6);

    listeners.notifyStart("Test", context);

    assertFalse(listener4.events.isEmpty());
    assertFalse(listener5.events.isEmpty());
    assertFalse(listener6.events.isEmpty());
  }

  @Test
  void listener_exceptionInOneListener_doesNotAffectOthers() {
    TestListener goodListener = new TestListener();
    WorkflowListener badListener =
        new WorkflowListener() {
          @Override
          public void onStart(String name, WorkflowContext ctx) {
            throw new RuntimeException("Listener error");
          }

          @Override
          public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
            // no-op
          }

          @Override
          public void onFailure(String name, WorkflowContext ctx, Throwable error) {
            // no-op
          }
        };

    listeners.register(badListener);
    listeners.register(goodListener);

    assertDoesNotThrow(() -> listeners.notifyStart("Test", context));
    assertEquals(1, goodListener.events.size());
  }

  @Test
  void clear_removesAllListeners() {
    listeners.register(testListener);
    listeners.register(new TestListener());

    listeners.clear();
    listeners.notifyStart("Test", context);

    assertTrue(testListener.events.isEmpty());
  }

  @Test
  void workflowListener_defaultMethods_doNotThrow() {
    WorkflowListener emptyListener =
        new WorkflowListener() {
          @Override
          public void onStart(String name, WorkflowContext ctx) {
            // no-op
          }

          @Override
          public void onSuccess(String name, WorkflowContext ctx, WorkflowResult result) {
            // no-op
          }

          @Override
          public void onFailure(String name, WorkflowContext ctx, Throwable error) {
            // no-op
          }
        };

    assertDoesNotThrow(() -> emptyListener.onStart("Test", context));
    assertDoesNotThrow(
        () -> emptyListener.onSuccess("Test", context, WorkflowResult.builder().build()));
    assertDoesNotThrow(() -> emptyListener.onFailure("Test", context, new RuntimeException()));
  }

  @Test
  void testNotifyStart() {
    listeners.register(listener1);
    listeners.register(listener2);

    listeners.notifyStart("test-wf", context);

    assertTrue(listener1.startCalled);
    assertTrue(listener2.startCalled);
    assertEquals("test-wf", listener1.receivedName);
  }

  @Test
  void testUnregister() {
    listeners.register(listener1);
    boolean removed = listeners.unregister(listener1);

    listeners.notifyStart("test-wf", context);

    assertTrue(removed);
    assertFalse(listener1.startCalled, "Listener should not have been called after unregistering");
  }

  @Test
  void testRegisterNull() {
    assertThrows(IllegalArgumentException.class, () -> listeners.register(null));
  }

  @Test
  void testClear() {
    listeners.register(listener1);
    listeners.clear();

    listeners.notifyStart("test-wf", context);
    assertFalse(listener1.startCalled);
  }

  @Test
  void testFailureIsolation() {
    // This listener throws an exception
    WorkflowListener crashingListener =
        new WorkflowListener() {
          @Override
          public void onStart(String name, WorkflowContext ctx) {
            throw new RuntimeException("I crashed!");
          }

          @Override
          public void onSuccess(String n, WorkflowContext c, WorkflowResult r) {
            // no-op
          }

          @Override
          public void onFailure(String n, WorkflowContext c, Throwable e) {
            // no-op
          }
        };

    listeners.register(crashingListener);
    listeners.register(listener1);

    // Act & Assert
    assertDoesNotThrow(
        () -> listeners.notifyStart("test-wf", context),
        "Registry should catch listener exceptions internally");

    assertTrue(listener1.startCalled, "Second listener should still be notified");
  }

  @Test
  void testNotifySuccess() {
    WorkflowResult result = WorkflowResults.success(Instant.now(), Instant.now());
    listeners.register(listener1);

    listeners.notifySuccess("test-wf", context, result);

    assertTrue(listener1.successCalled);
    assertEquals(result, listener1.receivedResult);
  }

  @Test
  void testNotifyFailure() {
    Throwable error = new RuntimeException("Boom");
    listeners.register(listener1);

    listeners.notifyFailure("test-wf", context, error);

    assertTrue(listener1.failureCalled);
    assertEquals(error, listener1.receivedError);
  }

  @Test
  void testNotifySuccessCatchBlock() {
    // Arrange
    AtomicBoolean secondListenerCalled = new AtomicBoolean(false);

    WorkflowListener crashingListener =
        new BaseTestListener() {
          @Override
          public void onSuccess(String name, WorkflowContext ctx, WorkflowResult res) {
            throw new RuntimeException("Success callback failed!");
          }
        };

    WorkflowListener healthyListener =
        new BaseTestListener() {
          @Override
          public void onSuccess(String name, WorkflowContext ctx, WorkflowResult res) {
            secondListenerCalled.set(true);
          }
        };

    listeners.register(crashingListener);
    listeners.register(healthyListener);

    // Act & Assert
    assertDoesNotThrow(
        () ->
            listeners.notifySuccess(
                "wf", context, WorkflowResults.success(Instant.now(), Instant.now())),
        "Registry should catch exception from onSuccess internally");

    assertTrue(
        secondListenerCalled.get(),
        "The second listener should be notified even if the first one threw an exception");
  }

  @Test
  void testNotifyFailureCatchBlock() {
    // Arrange
    AtomicBoolean secondListenerCalled = new AtomicBoolean(false);
    Throwable workflowError = new RuntimeException("Original Workflow Error");

    WorkflowListener crashingListener =
        new BaseTestListener() {
          @Override
          public void onFailure(String name, WorkflowContext ctx, Throwable err) {
            throw new RuntimeException("Failure callback itself failed!");
          }
        };

    WorkflowListener healthyListener =
        new BaseTestListener() {
          @Override
          public void onFailure(String name, WorkflowContext ctx, Throwable err) {
            secondListenerCalled.set(true);
          }
        };

    listeners.register(crashingListener);
    listeners.register(healthyListener);

    // Act & Assert
    assertDoesNotThrow(
        () -> listeners.notifyFailure("wf", context, workflowError),
        "Registry should catch exception from onFailure internally");

    assertTrue(
        secondListenerCalled.get(),
        "The second listener should be notified even if the first one threw an exception");
  }

  /** Helper base class to avoid implementing all methods in every anonymous class */
  private abstract static class BaseTestListener implements WorkflowListener {
    @Override
    public void onStart(String name, WorkflowContext ctx) {}

    @Override
    public void onSuccess(String name, WorkflowContext ctx, WorkflowResult res) {}

    @Override
    public void onFailure(String name, WorkflowContext ctx, Throwable err) {}
  }
}
