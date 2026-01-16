package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.SagaCompensationException;
import com.workflow.saga.SagaStep;
import com.workflow.task.Task;
import com.workflow.test.WorkflowTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SagaWorkflowTest {

  @Test
  void execute_allStepsSucceed_returnsSuccess() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("SuccessSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2")
                    .action(_ -> executionOrder.add("action2"))
                    .compensation(_ -> executionOrder.add("comp2"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step3")
                    .action(_ -> executionOrder.add("action3"))
                    .compensation(_ -> executionOrder.add("comp3"))
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals(List.of("action1", "action2", "action3"), executionOrder);
  }

  @Test
  void execute_middleStepFails_compensatesPreviousStepsInReverse() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("FailureSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2")
                    .action(_ -> executionOrder.add("action2"))
                    .compensation(_ -> executionOrder.add("comp2"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step3")
                    .action(
                        _ -> {
                          executionOrder.add("action3-fail");
                          throw new RuntimeException("Step3 failed");
                        })
                    .compensation(_ -> executionOrder.add("comp3"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step4")
                    .action(_ -> executionOrder.add("action4"))
                    .compensation(_ -> executionOrder.add("comp4"))
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    // Actions 1, 2 succeed; action 3 fails; compensate 2, then 1 (reverse order)
    assertEquals(List.of("action1", "action2", "action3-fail", "comp2", "comp1"), executionOrder);
    assertNotNull(result.getError());
    assertTrue(result.getError().getMessage().contains("Step3 failed"));
  }

  @Test
  void execute_firstStepFails_noCompensationsNeeded() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("FirstFailSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(
                        _ -> {
                          executionOrder.add("action1-fail");
                          throw new RuntimeException("First step failed");
                        })
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2")
                    .action(_ -> executionOrder.add("action2"))
                    .compensation(_ -> executionOrder.add("comp2"))
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    // Only the failed action runs, no compensations needed
    assertEquals(List.of("action1-fail"), executionOrder);
  }

  @Test
  void execute_compensationAlsoFails_continuesCompensatingAndReturnsCompensationException() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("CompensationFailSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2")
                    .action(_ -> executionOrder.add("action2"))
                    .compensation(
                        _ -> {
                          executionOrder.add("comp2-fail");
                          throw new RuntimeException("Compensation 2 failed");
                        })
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step3")
                    .action(
                        _ -> {
                          executionOrder.add("action3-fail");
                          throw new RuntimeException("Step3 failed");
                        })
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    // Even though comp2 fails, comp1 should still run
    assertEquals(
        List.of("action1", "action2", "action3-fail", "comp2-fail", "comp1"), executionOrder);

    assertInstanceOf(SagaCompensationException.class, result.getError());
    SagaCompensationException sce = (SagaCompensationException) result.getError();
    assertTrue(sce.hasCompensationFailures());
    assertEquals(1, sce.getCompensationFailureCount());
    assertNotNull(sce.getCause());
    assertTrue(sce.getCause().getMessage().contains("Step3 failed"));
  }

  @Test
  void execute_emptySaga_returnsSuccess() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    SagaWorkflow saga = SagaWorkflow.builder().name("EmptySaga").build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_stepsWithoutCompensation_skipsCompensationForThose() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("PartialCompSaga")
            .step(
                SagaStep.builder()
                    .name("Step1-WithComp")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2-NoComp")
                    .action(_ -> executionOrder.add("action2"))
                    // No compensation
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step3-WithComp")
                    .action(_ -> executionOrder.add("action3"))
                    .compensation(_ -> executionOrder.add("comp3"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step4-Fail")
                    .action(
                        _ -> {
                          executionOrder.add("action4-fail");
                          throw new RuntimeException("Step4 failed");
                        })
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    // Compensations: comp3, skip step2, comp1
    assertEquals(
        List.of("action1", "action2", "action3", "action4-fail", "comp3", "comp1"), executionOrder);
  }

  @Test
  void execute_failureContextAvailableDuringCompensation() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    AtomicInteger failureCauseFound = new AtomicInteger(0);
    AtomicInteger failedStepFound = new AtomicInteger(0);

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("ContextSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> {})
                    .compensation(
                        context -> {
                          if (context.get(SagaWorkflow.SAGA_FAILURE_CAUSE) != null) {
                            failureCauseFound.incrementAndGet();
                          }
                          if ("FailingStep".equals(context.get(SagaWorkflow.SAGA_FAILED_STEP))) {
                            failedStepFound.incrementAndGet();
                          }
                        })
                    .build())
            .step(
                SagaStep.builder()
                    .name("FailingStep")
                    .action(
                        _ -> {
                          throw new RuntimeException("Intentional failure");
                        })
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertEquals(1, failureCauseFound.get(), "Compensation should have access to failure cause");
    assertEquals(1, failedStepFound.get(), "Compensation should have access to failed step name");

    // Context should be cleaned up after saga completes
    assertNull(ctx.get(SagaWorkflow.SAGA_FAILURE_CAUSE));
    assertNull(ctx.get(SagaWorkflow.SAGA_FAILED_STEP));
  }

  @Test
  void execute_usingWorkflowsAsSteps_worksCorrectly() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();

    Workflow action = WorkflowTestUtils.mockSuccessfulWorkflow("action");
    Workflow compensation = WorkflowTestUtils.mockSuccessfulWorkflow("compensation");

    SagaWorkflow saga =
        SagaWorkflow.builder().name("WorkflowSaga").step(action, compensation).build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_usingTasksAsSteps_worksCorrectly() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    Task actionTask = _ -> executionOrder.add("action");
    Task compensationTask = _ -> executionOrder.add("compensation");

    SagaWorkflow saga =
        SagaWorkflow.builder().name("TaskSaga").step(actionTask, compensationTask).build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals(List.of("action"), executionOrder);
  }

  @Test
  void getName_returnsProvidedName_orDefaultWhenNull() {
    SagaWorkflow named =
        SagaWorkflow.builder()
            .name("MySaga")
            .step(SagaStep.builder().action(_ -> {}).build())
            .build();
    assertEquals("MySaga", named.getName());

    SagaWorkflow unnamed =
        SagaWorkflow.builder().step(SagaStep.builder().action(_ -> {}).build()).build();
    assertNotNull(unnamed.getName());
    assertFalse(unnamed.getName().isEmpty());
  }

  @Test
  void getWorkflowType_returnsSaga() {
    SagaWorkflow saga = SagaWorkflow.builder().name("Test").build();
    assertTrue(saga.getWorkflowType().contains("Saga"));
  }

  @Test
  void getSubWorkflows_returnsActionWorkflows() {
    Workflow action1 = WorkflowTestUtils.mockSuccessfulWorkflow("action1");
    Workflow action2 = WorkflowTestUtils.mockSuccessfulWorkflow("action2");

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("ContainerTest")
            .step(action1, WorkflowTestUtils.mockSuccessfulWorkflow("comp1"))
            .step(action2, null)
            .build();

    List<Workflow> subWorkflows = saga.getSubWorkflows();

    assertEquals(2, subWorkflows.size());
    assertSame(action1, subWorkflows.get(0));
    assertSame(action2, subWorkflows.get(1));
  }

  @Test
  void getSteps_returnsUnmodifiableList() {
    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("StepListTest")
            .step(SagaStep.builder().name("Step1").action(_ -> {}).build())
            .build();

    List<SagaStep> steps = saga.getSteps();

    assertEquals(1, steps.size());
    assertThrows(UnsupportedOperationException.class, () -> steps.add(null));
  }

  @Test
  void execute_multipleCompensationsFail_allErrorsCollected() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("MultiCompFailSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(
                        _ -> {
                          executionOrder.add("comp1-fail");
                          throw new RuntimeException("Compensation 1 failed");
                        })
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2")
                    .action(_ -> executionOrder.add("action2"))
                    .compensation(
                        _ -> {
                          executionOrder.add("comp2-fail");
                          throw new RuntimeException("Compensation 2 failed");
                        })
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step3")
                    .action(
                        _ -> {
                          executionOrder.add("action3-fail");
                          throw new RuntimeException("Step3 failed");
                        })
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertEquals(
        List.of("action1", "action2", "action3-fail", "comp2-fail", "comp1-fail"), executionOrder);

    assertInstanceOf(SagaCompensationException.class, result.getError());
    SagaCompensationException sce = (SagaCompensationException) result.getError();
    assertEquals(2, sce.getCompensationFailureCount());
    assertEquals(2, sce.getCompensationErrors().size());
  }

  @Test
  void execute_actionReturnsNull_treatedAsFailure() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    Workflow nullReturning = WorkflowTestUtils.mockNullReturningWorkflow("null-action");

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("NullReturnSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(SagaStep.builder().name("Step2-Null").action(nullReturning).build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    assertTrue(executionOrder.contains("comp1"));
    assertTrue(result.getError().getMessage().contains("null result"));
  }

  @Test
  void execute_implementsWorkflowContainer() {
    SagaWorkflow saga = SagaWorkflow.builder().name("ContainerSaga").build();

    assertInstanceOf(WorkflowContainer.class, saga);
  }

  @Test
  void sagaStep_hasCompensation_returnsCorrectly() {
    SagaStep withComp = SagaStep.builder().action(_ -> {}).compensation(_ -> {}).build();
    assertTrue(withComp.hasCompensation());

    SagaStep withoutComp = SagaStep.builder().action(_ -> {}).build();
    assertFalse(withoutComp.hasCompensation());
  }

  @Test
  void sagaStep_getName_fallsBackToActionName() {
    Workflow namedAction = WorkflowTestUtils.mockSuccessfulWorkflow("ActionWorkflow");

    SagaStep namedStep = SagaStep.builder().name("ExplicitName").action(namedAction).build();
    assertEquals("ExplicitName", namedStep.getName());

    SagaStep unnamedStep = SagaStep.builder().action(namedAction).build();
    assertEquals("ActionWorkflow", unnamedStep.getName());
  }

  @Test
  void execute_lastStepFails_allPreviousCompensated() {
    WorkflowContext ctx = WorkflowTestUtils.createContext();
    List<String> executionOrder = new ArrayList<>();

    SagaWorkflow saga =
        SagaWorkflow.builder()
            .name("LastFailSaga")
            .step(
                SagaStep.builder()
                    .name("Step1")
                    .action(_ -> executionOrder.add("action1"))
                    .compensation(_ -> executionOrder.add("comp1"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step2")
                    .action(_ -> executionOrder.add("action2"))
                    .compensation(_ -> executionOrder.add("comp2"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step3")
                    .action(_ -> executionOrder.add("action3"))
                    .compensation(_ -> executionOrder.add("comp3"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("Step4-Last")
                    .action(
                        _ -> {
                          executionOrder.add("action4-fail");
                          throw new RuntimeException("Last step failed");
                        })
                    .compensation(_ -> executionOrder.add("comp4"))
                    .build())
            .build();

    WorkflowResult result = saga.execute(ctx);

    WorkflowTestUtils.assertFailed(result);
    // All 3 previous steps should be compensated in reverse order
    assertEquals(
        List.of("action1", "action2", "action3", "action4-fail", "comp3", "comp2", "comp1"),
        executionOrder);
  }
}
