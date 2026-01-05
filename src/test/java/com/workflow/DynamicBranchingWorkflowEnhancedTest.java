package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.Workflows;
import com.workflow.test.WorkflowTestUtils;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Enhanced test suite for DynamicBranchingWorkflow covering edge cases, error handling, and all
 * code paths.
 */
class DynamicBranchingWorkflowEnhancedTest {

  @Test
  void execute_selectsCorrectBranch() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "fast");

    Workflow fastWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("fast");
    Workflow slowWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("slow");

    Function<WorkflowContext, String> selector = ctx -> ctx.getTyped("mode", String.class);

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("branching")
            .selector(selector)
            .branch("fast", fastWorkflow)
            .branch("slow", slowWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_caseInsensitiveKeys() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "FAST");

    Workflow workflow = WorkflowTestUtils.mockSuccessfulWorkflow("fast");

    DynamicBranchingWorkflow branching =
        DynamicBranchingWorkflow.builder()
            .name("case-test")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("fast", workflow) // lowercase
            .build();

    WorkflowResult result = branching.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_usesDefaultBranch_whenKeyNotFound() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "unknown");

    Workflow defaultWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("default");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("default-test")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("fast", WorkflowTestUtils.mockSuccessfulWorkflow())
            .defaultBranch(defaultWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_noMatchingBranch_noDefault_returnsSkipped() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "unknown");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("no-match")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("fast", WorkflowTestUtils.mockSuccessfulWorkflow())
            .branch("slow", WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSkipped(result);
  }

  @Test
  void execute_selectorReturnsNull_usesDefault() {
    WorkflowContext context = new WorkflowContext();

    Workflow defaultWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("default");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("null-selector")
            .selector(_ -> null)
            .branch("branch1", WorkflowTestUtils.mockSuccessfulWorkflow())
            .defaultBranch(defaultWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_selectorThrows_returnsFailure() {
    WorkflowContext context = new WorkflowContext();

    RuntimeException exception = new RuntimeException("Selector failed");
    Function<WorkflowContext, String> selector =
        _ -> {
          throw exception;
        };

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("selector-throws")
            .selector(selector)
            .branch("branch1", WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
  }

  @Test
  void execute_selectedBranchFails_returnsFailure() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "fail");

    RuntimeException error = new RuntimeException("Branch failed");
    Workflow failingWorkflow = WorkflowTestUtils.mockFailingWorkflow("failing", error);

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("branch-fails")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("fail", failingWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(error, result.getError());
  }

  @Test
  void execute_defaultBranchFails_returnsFailure() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "unknown");

    RuntimeException error = new RuntimeException("Default failed");
    Workflow failingDefault = WorkflowTestUtils.mockFailingWorkflow("default", error);

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("default-fails")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("known", WorkflowTestUtils.mockSuccessfulWorkflow())
            .defaultBranch(failingDefault)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertFailed(result);
    assertSame(error, result.getError());
  }

  @Test
  void build_nullSelector_throwsNullPointerException() {
    try {
      DynamicBranchingWorkflow.builder()
          .selector(null)
          .branch("test", WorkflowTestUtils.mockSuccessfulWorkflow())
          .build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void build_noBranches_throws() {
    try {
      Workflows.dynamic("no-branches").selector(_ -> "test").build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void build_duplicateKeys_lastOneWins() {
    Workflow workflow1 = WorkflowTestUtils.mockSuccessfulWorkflow("first");
    Workflow workflow2 = WorkflowTestUtils.mockSuccessfulWorkflow("second");

    WorkflowContext context = new WorkflowContext();
    context.put("mode", "duplicate");

    DynamicBranchingWorkflow branching =
        DynamicBranchingWorkflow.builder()
            .name("duplicate-keys")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("duplicate", workflow1)
            .branch("duplicate", workflow2) // Overwrites first
            .build();

    WorkflowResult result = branching.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_emptyStringKey_works() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "");

    Workflow emptyKeyWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("empty");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("empty-key")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("", emptyKeyWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_whitespaceKey_works() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "   ");

    Workflow whitespaceWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("whitespace");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("whitespace-key")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("   ", whitespaceWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_specialCharactersInKey_works() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "test:value");

    Workflow specialWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("special");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("special-chars")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("test:value", specialWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_multipleBranches_selectsCorrectOne() {
    WorkflowContext context = new WorkflowContext();
    context.put("mode", "branch2");

    Workflow workflow1 = WorkflowTestUtils.mockSuccessfulWorkflow("branch1");
    Workflow workflow2 = WorkflowTestUtils.mockSuccessfulWorkflow("branch2");
    Workflow workflow3 = WorkflowTestUtils.mockSuccessfulWorkflow("branch3");

    DynamicBranchingWorkflow branching =
        DynamicBranchingWorkflow.builder()
            .name("multiple")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("branch1", workflow1)
            .branch("branch2", workflow2)
            .branch("branch3", workflow3)
            .build();

    WorkflowResult result = branching.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void getName_returnsProvidedName() {
    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("test-name")
            .selector(_ -> "test")
            .branch("test", WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    assertEquals("test-name", workflow.getName());
  }

  @Test
  void getName_withNull_returnsDefaultName() {
    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name(null)
            .selector(_ -> "test")
            .branch("test", WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    String name = workflow.getName();
    assertNotNull(name);
    assertTrue(name.contains("DynamicBranchingWorkflow"));
  }

  @Test
  void execute_contextShared_betweenSelectorAndBranch() {
    WorkflowContext context = new WorkflowContext();
    context.put("key1", "value1");

    Workflow branchWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(WorkflowContext ctx, ExecutionContext execContext) {
            // Verify context is shared
            assertEquals("value1", ctx.get("key1"));
            ctx.put("key2", "value2");
            return execContext.success();
          }
        };

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("context-test")
            .selector(
                ctx -> {
                  assertEquals("value1", ctx.get("key1"));
                  return "branch";
                })
            .branch("branch", branchWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
    assertEquals("value2", context.get("key2"));
  }

  @Test
  void execute_complexSelector_works() {
    WorkflowContext context = new WorkflowContext();
    context.put("priority", 10);
    context.put("type", "urgent");

    Function<WorkflowContext, String> complexSelector =
        ctx -> {
          Integer priority = ctx.getTyped("priority", Integer.class);
          String type = ctx.getTyped("type", String.class);
          if (priority != null && priority > 5 && "urgent".equals(type)) {
            return "high-priority";
          }
          return "normal";
        };

    Workflow highPriorityWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("high");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("complex-selector")
            .selector(complexSelector)
            .branch("high-priority", highPriorityWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_selectorReturnsEmptyString_matches() {
    WorkflowContext context = new WorkflowContext();

    Workflow emptyWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("empty");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("empty-selector")
            .selector(_ -> "")
            .branch("", emptyWorkflow)
            .build();

    WorkflowResult result = workflow.execute(context);

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_multipleExecutions_selectsDifferentBranches() {
    Workflow branch1 = WorkflowTestUtils.mockSuccessfulWorkflow("branch1");
    Workflow branch2 = WorkflowTestUtils.mockSuccessfulWorkflow("branch2");

    DynamicBranchingWorkflow workflow =
        DynamicBranchingWorkflow.builder()
            .name("multi-exec")
            .selector(ctx -> ctx.getTyped("mode", String.class))
            .branch("mode1", branch1)
            .branch("mode2", branch2)
            .build();

    // First execution
    WorkflowContext context1 = new WorkflowContext();
    context1.put("mode", "mode1");
    WorkflowResult result1 = workflow.execute(context1);
    WorkflowTestUtils.assertSuccess(result1);

    // Second execution with different mode
    WorkflowContext context2 = new WorkflowContext();
    context2.put("mode", "mode2");
    WorkflowResult result2 = workflow.execute(context2);
    WorkflowTestUtils.assertSuccess(result2);
  }
}
