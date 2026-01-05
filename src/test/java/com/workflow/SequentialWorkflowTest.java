package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.Workflows;
import com.workflow.task.NoOpTask;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import com.workflow.test.WorkflowTestUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

class SequentialWorkflowTest {

  @Test
  void execute_allWorkflowsSucceed_returnsSuccess() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    SequentialWorkflow wf =
        Workflows.sequential("seq")
            .workflow(w1)
            .workflow(w2)
            .task(TaskDescriptor.builder().task(new NoOpTask()).build())
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_firstWorkflowFails_shortCircuitsAndReturnsFailure() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    RuntimeException cause = new RuntimeException("first failed");
    Workflow failing = WorkflowTestUtils.mockFailingWorkflow("failing", cause);
    Workflow neverRun = WorkflowTestUtils.mockSuccessfulWorkflow("never-run");

    SequentialWorkflow wf =
        SequentialWorkflow.builder().name("seq-fail").workflow(failing).workflow(neverRun).build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertSame(cause, result.getError());
  }

  @Test
  void execute_laterWorkflowFails_returnsFailureFromThatWorkflow() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow ok = WorkflowTestUtils.mockSuccessfulWorkflow("ok");
    RuntimeException cause = new IllegalStateException("later failed");
    Workflow failing = WorkflowTestUtils.mockFailingWorkflow("failing", cause);

    SequentialWorkflow wf =
        SequentialWorkflow.builder().name("seq-later-fail").workflow(ok).workflow(failing).build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertSame(cause, result.getError());
  }

  @Test
  void execute_workflowReturnsNull_returnsFailureWithDescriptiveError() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow returnsNull = WorkflowTestUtils.mockNullReturningWorkflow("null-workflow");

    SequentialWorkflow wf =
        SequentialWorkflow.builder().name("seq-null").workflow(returnsNull).build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertTrue(result.getError().getMessage().contains("returned null result"));
    assertTrue(result.getError().getMessage().contains("null-workflow"));
  }

  @Test
  void getName_returnsProvidedName_orDefaultWhenNull() {
    Workflow w = WorkflowTestUtils.mockSuccessfulWorkflow();

    SequentialWorkflow named = SequentialWorkflow.builder().name("mySeq").workflow(w).build();
    assertEquals("mySeq", named.getName());

    SequentialWorkflow unnamed = SequentialWorkflow.builder().name(null).workflow(w).build();
    assertNotNull(unnamed.getName());
    assertFalse(unnamed.getName().isEmpty());
  }

  @Test
  void builder_taskConvenience_executesTaskWorkflow() {
    // Create a Task that mutates a flag when executed.
    final boolean[] ran = {false};
    Task t = _ -> ran[0] = true;

    SequentialWorkflow wf = SequentialWorkflow.builder().name("task-builder").task(t).build();

    WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

    WorkflowTestUtils.assertSuccess(result);
    assertTrue(ran[0], "Task should have been executed by TaskWorkflow created by builder.task()");
  }

  @Test
  void execute_emptyWorkflows_returnsSuccess() {
    SequentialWorkflow wf = SequentialWorkflow.builder().name("empty").build();
    WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_workflowThrowsException_wrapsInFailureResult() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    RuntimeException exception = new RuntimeException("boom");
    Workflow throwing = WorkflowTestUtils.mockThrowingWorkflow("throwing", exception);

    SequentialWorkflow wf =
        SequentialWorkflow.builder().name("seq-throwing").workflow(throwing).build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    // The exception should be wrapped by AbstractWorkflow.execute()
    assertNotNull(result.getError());
  }

  @Test
  void execute_multipleWorkflows_executesInOrder() {
    WorkflowContext ctx = new WorkflowContext();

    // Tasks that append to a list in order
    Task task1 = context -> context.put("order", context.get("order", "") + "1");
    Task task2 = context -> context.put("order", context.get("order", "") + "2");
    Task task3 = context -> context.put("order", context.get("order", "") + "3");

    SequentialWorkflow wf =
        SequentialWorkflow.builder().name("ordered").task(task1).task(task2).task(task3).build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals("123", ctx.get("order"));
  }

  @Test
  void builder_workflowsConvenience_executesAllWorkflows() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    SequentialWorkflow wf =
        Workflows.sequential("seq").workflows(java.util.Arrays.asList(w1, w2, null)).build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void builder_workflowsConvenience_withNullList() {
    SequentialWorkflow wf = Workflows.sequential("seq").workflows(null).build();

    WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void getWorkflowType_returnsCorrectType() {
    SequentialWorkflow wf = SequentialWorkflow.builder().build();
    assertEquals("[Sequence]", wf.getWorkflowType());
  }

  @Test
  void getSubWorkflows_returnsUnmodifiableList() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    SequentialWorkflow wf = SequentialWorkflow.builder().workflow(w1).build();
    List<Workflow> subWorkflows = wf.getSubWorkflows();
    assertEquals(1, subWorkflows.size());
    assertThrows(UnsupportedOperationException.class, () -> subWorkflows.add(w1));
  }
}
