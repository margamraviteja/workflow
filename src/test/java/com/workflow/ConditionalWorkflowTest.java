package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.Workflows;
import com.workflow.test.WorkflowTestUtils;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ConditionalWorkflowTest {

  @Test
  void build_whenConditionIsNull_throwsNullPointerException() {
    try {
      Workflows.conditional("wf")
          .condition(null)
          .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
          .build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void build_whenBothBranchesNull_throwsIllegalArgumentException() {
    Predicate<WorkflowContext> cond = _ -> true;
    try {
      ConditionalWorkflow.builder()
          .name("wf")
          .condition(cond)
          .whenTrue(null)
          .whenFalse(null)
          .build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void execute_whenConditionTrue_executes_whenTrue_andReturnsItsResult() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow whenTrue = WorkflowTestUtils.mockSuccessfulWorkflow("true-branch");
    Predicate<WorkflowContext> cond = _ -> true;

    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .name("true-wf")
            .condition(cond)
            .whenTrue(whenTrue)
            .whenFalse(null)
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_whenConditionFalse_executes_whenFalse_andReturnsItsResult() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow whenFalse = WorkflowTestUtils.mockSuccessfulWorkflow("false-branch");
    Predicate<WorkflowContext> cond = _ -> false;

    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .name("false-wf")
            .condition(cond)
            .whenTrue(null)
            .whenFalse(whenFalse)
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_selectedBranchNull_returnsSkippedResult() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    // condition true but whenTrue is null -> should return SKIPPED
    Predicate<WorkflowContext> cond = _ -> true;
    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .name("skip-wf")
            .condition(cond)
            .whenTrue(null)
            .whenFalse(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertSkipped(result);
  }

  @Test
  void getName_returnsProvidedName_orDefaultWhenNull() {
    Predicate<WorkflowContext> cond = _ -> true;
    Workflow whenTrue = WorkflowTestUtils.mockSuccessfulWorkflow();

    ConditionalWorkflow named =
        ConditionalWorkflow.builder().name("myName").condition(cond).whenTrue(whenTrue).build();
    assertEquals("myName", named.getName());

    ConditionalWorkflow unnamed =
        ConditionalWorkflow.builder().name(null).condition(cond).whenTrue(whenTrue).build();
    assertNotNull(unnamed.getName());
    assertFalse(unnamed.getName().isEmpty());
  }

  @Test
  void execute_whenPredicateThrows_returnsFailureWithWrappedException() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Predicate<WorkflowContext> throwing =
        _ -> {
          throw new RuntimeException("predicate failed");
        };

    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .name("err")
            .condition(throwing)
            .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertTrue(result.getError().getMessage().contains("Condition evaluation failed"));
    assertTrue(result.getError().getCause().getMessage().contains("predicate failed"));
  }

  @Test
  void execute_whenTrueFails_returnsFailureResult() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    RuntimeException error = new RuntimeException("branch failed");
    Workflow failingBranch = WorkflowTestUtils.mockFailingWorkflow(error);
    Predicate<WorkflowContext> cond = _ -> true;

    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .name("fail-wf")
            .condition(cond)
            .whenTrue(failingBranch)
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertSame(error, result.getError());
  }

  @Test
  void execute_whenFalseFails_returnsFailureResult() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    RuntimeException error = new RuntimeException("branch failed");
    Workflow failingBranch = WorkflowTestUtils.mockFailingWorkflow(error);
    Predicate<WorkflowContext> cond = _ -> false;

    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .name("fail-wf")
            .condition(cond)
            .whenFalse(failingBranch)
            .build();

    WorkflowResult result = wf.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertSame(error, result.getError());
  }

  @Test
  void getWorkflowType_returnsCorrectType() {
    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .condition(_ -> true)
            .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();
    assertEquals("[Conditional]", wf.getWorkflowType());
  }

  @Test
  void getSubWorkflows_returnsCorrectSubWorkflows() {
    Workflow whenTrue = WorkflowTestUtils.mockSuccessfulWorkflow("true-branch");
    Workflow whenFalse = WorkflowTestUtils.mockSuccessfulWorkflow("false-branch");
    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .condition(_ -> true)
            .whenTrue(whenTrue)
            .whenFalse(whenFalse)
            .build();

    java.util.List<Workflow> subWorkflows = wf.getSubWorkflows();
    assertEquals(2, subWorkflows.size());
  }

  @Test
  void getSubWorkflows_withNullBranch_returnsCorrectSubWorkflows() {
    Workflow whenTrue = WorkflowTestUtils.mockSuccessfulWorkflow("true-branch");
    ConditionalWorkflow wf =
        ConditionalWorkflow.builder()
            .condition(_ -> true)
            .whenTrue(whenTrue)
            .whenFalse(null)
            .build();

    java.util.List<Workflow> subWorkflows = wf.getSubWorkflows();
    assertEquals(1, subWorkflows.size());
  }
}
