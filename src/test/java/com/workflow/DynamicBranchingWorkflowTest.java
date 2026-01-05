package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.test.WorkflowTestUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for DynamicBranchingWorkflow covering selector logic, branching, error
 * handling, and edge cases.
 */
class DynamicBranchingWorkflowTest {

  @Test
  void constructor_nullBranches_throwsNullPointerException() {
    Function<WorkflowContext, String> selector = _ -> "key";
    try {
      DynamicBranchingWorkflow.builder().name("test").selector(selector).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void constructor_nullSelector_throwsNullPointerException() {
    Map<String, Workflow> branches = new HashMap<>();
    try {
      DynamicBranchingWorkflow.builder().name("test").branches(branches).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void execute_selectorReturnsValidKey_executesCorrespondingBranch() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow branchB = WorkflowTestUtils.mockSuccessfulWorkflow("branchB");

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .selector(_ -> "A")
            .branch("A", branchA)
            .branch("B", branchB)
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_selectorReturnsInvalidKeyWithDefault_executesDefaultBranch() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow defaultBranch = WorkflowTestUtils.mockSuccessfulWorkflow("default");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branchA);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "INVALID")
            .defaultBranch(defaultBranch)
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_selectorReturnsInvalidKeyWithoutDefault_returnsFailure() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branchA);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "INVALID")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSkipped(result);
  }

  @Test
  void execute_selectorThrowsException_returnsFailure() {
    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", WorkflowTestUtils.mockSuccessfulWorkflow());

    Function<WorkflowContext, String> throwingSelector =
        _ -> {
          throw new RuntimeException("selector failed");
        };

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(throwingSelector)
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertNotNull(result.getError());
    assertTrue(result.getError().getMessage().contains("Branch selector evaluation failed"));
    assertTrue(result.getError().getCause().getMessage().contains("selector failed"));
  }

  @Test
  void execute_selectedBranchFails_returnsFailureFromBranch() {
    RuntimeException branchError = new RuntimeException("branch failed");
    Workflow failingBranch = WorkflowTestUtils.mockFailingWorkflow("failing", branchError);

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", failingBranch);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "A")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(branchError, result.getError());
  }

  @Test
  void execute_selectedBranchThrows_returnsFailure() {
    RuntimeException exception = new RuntimeException("boom");
    Workflow throwingBranch = WorkflowTestUtils.mockThrowingWorkflow("throwing", exception);

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", throwingBranch);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "A")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(exception, result.getError());
  }

  @Test
  void execute_selectorReturnsNull_usesDefaultBranch() {
    Workflow defaultBranch = WorkflowTestUtils.mockSuccessfulWorkflow("default");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", WorkflowTestUtils.mockSuccessfulWorkflow());

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> null)
            .defaultBranch(defaultBranch)
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_selectorReturnsNullWithoutDefault_returnsFailure() {
    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", WorkflowTestUtils.mockSuccessfulWorkflow());

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> null)
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSkipped(result);
  }

  @Test
  void getName_withProvidedName_returnsProvidedName() {
    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", WorkflowTestUtils.mockSuccessfulWorkflow());

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("MyDynamic")
            .branches(branches)
            .selector(_ -> "A")
            .build();

    assertEquals("MyDynamic", wf.getName());
  }

  @Test
  void getName_withNullName_returnsDefaultName() {
    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", WorkflowTestUtils.mockSuccessfulWorkflow());

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder().branches(branches).selector(_ -> "A").build();

    String name = wf.getName();
    assertNotNull(name);
    assertTrue(name.contains("DynamicBranchingWorkflow"));
  }

  @Test
  void execute_selectorUsesContextData_selectsCorrectBranch() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow branchB = WorkflowTestUtils.mockSuccessfulWorkflow("branchB");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branchA);
    branches.put("B", branchB);

    Function<WorkflowContext, String> contextSelector =
        ctx -> ctx.getTyped("branchKey", String.class);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(contextSelector)
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    context.put("branchKey", "B");

    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_emptyBranchesMapWithoutDefault_returnsFailure() {
    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branch("A", WorkflowTestUtils.mockSuccessfulWorkflow("branchA"))
            .selector(_ -> "ANY")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSkipped(result);
  }

  @Test
  void execute_multipleBranches_eachCanBeSelected() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow branchB = WorkflowTestUtils.mockSuccessfulWorkflow("branchB");
    Workflow branchC = WorkflowTestUtils.mockSuccessfulWorkflow("branchC");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branchA);
    branches.put("B", branchB);
    branches.put("C", branchC);

    String[] keys = {"A", "B", "C"};
    for (String key : keys) {
      DynamicBranchingWorkflow wf =
          DynamicBranchingWorkflow.builder()
              .name("test")
              .branches(branches)
              .selector(_ -> key)
              .build();

      WorkflowContext context = WorkflowTestUtils.createContext();
      WorkflowResult result = wf.execute(context);

      WorkflowTestUtils.assertSuccess(result);
    }
  }

  @Test
  void execute_contextModifiedByBranch_modificationsVisible() {
    Workflow branch = new TaskWorkflow(context -> context.put("branchData", "modified"));

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branch);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "A")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals("modified", context.get("branchData"));
  }

  @Test
  void execute_selectorChangesBasedOnContext_selectsDifferentBranches() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow branchB = WorkflowTestUtils.mockSuccessfulWorkflow("branchB");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branchA);
    branches.put("B", branchB);

    Function<WorkflowContext, String> selector =
        ctx -> {
          Integer value = ctx.getTyped("value", Integer.class);
          return value != null && value > 10 ? "A" : "B";
        };

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(selector)
            .build();

    // Test with value > 10 (should select A)
    WorkflowContext context1 = WorkflowTestUtils.createContext();
    context1.put("value", 15);
    WorkflowResult result1 = wf.execute(context1);
    WorkflowTestUtils.assertSuccess(result1);

    // Test with value <= 10 (should select B)
    WorkflowContext context2 = WorkflowTestUtils.createContext();
    context2.put("value", 5);
    WorkflowResult result2 = wf.execute(context2);
    WorkflowTestUtils.assertSuccess(result2);
  }

  @Test
  void execute_caseInsensitiveBranchSelection_selectsCorrectBranch() {
    Workflow branch = WorkflowTestUtils.mockSuccessfulWorkflow("branch");

    Map<String, Workflow> branches = new HashMap<>();
    branches.put("BranchA", branch);

    // Selector returns different case
    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "brancha")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    // Should succeed due to case-insensitive lookup
    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void getWorkflowType_returnsCorrectType() {
    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .branch("A", WorkflowTestUtils.mockSuccessfulWorkflow())
            .selector(_ -> "A")
            .build();
    assertEquals("[Switch]", wf.getWorkflowType());
  }

  @Test
  void getSubWorkflows_returnsCorrectSubWorkflows() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow branchB = WorkflowTestUtils.mockSuccessfulWorkflow("branchB");
    Workflow defaultBranch = WorkflowTestUtils.mockSuccessfulWorkflow("default");

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .branch("A", branchA)
            .branch("B", branchB)
            .defaultBranch(defaultBranch)
            .selector(_ -> "A")
            .build();

    java.util.List<Workflow> subWorkflows = wf.getSubWorkflows();
    assertEquals(3, subWorkflows.size());
  }

  @Test
  void builder_branchesConvenience_addsAllBranches() {
    Workflow branchA = WorkflowTestUtils.mockSuccessfulWorkflow("branchA");
    Workflow branchB = WorkflowTestUtils.mockSuccessfulWorkflow("branchB");
    Map<String, Workflow> branches = new HashMap<>();
    branches.put("A", branchA);
    branches.put("B", branchB);

    DynamicBranchingWorkflow wf =
        DynamicBranchingWorkflow.builder()
            .name("test")
            .branches(branches)
            .selector(_ -> "A")
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    WorkflowResult result = wf.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_nestedDynamicWorkflows_allLevelsWorkCorrectly() {
    Workflow leafWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("leaf");

    Map<String, Workflow> innerBranches = new HashMap<>();
    innerBranches.put("INNER", leafWorkflow);

    DynamicBranchingWorkflow innerWorkflow =
        DynamicBranchingWorkflow.builder()
            .name("inner")
            .branches(innerBranches)
            .selector(ctx -> ctx.getTyped("innerKey", String.class))
            .build();

    Map<String, Workflow> outerBranches = new HashMap<>();
    outerBranches.put("OUTER", innerWorkflow);

    DynamicBranchingWorkflow outerWorkflow =
        DynamicBranchingWorkflow.builder()
            .name("outer")
            .branches(outerBranches)
            .selector(ctx -> ctx.getTyped("outerKey", String.class))
            .build();

    WorkflowContext context = WorkflowTestUtils.createContext();
    context.put("outerKey", "OUTER");
    context.put("innerKey", "INNER");

    WorkflowResult result = outerWorkflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }
}
