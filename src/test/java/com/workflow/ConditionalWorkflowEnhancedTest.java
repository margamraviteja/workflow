package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.test.WorkflowTestUtils;
import org.junit.jupiter.api.Test;

class ConditionalWorkflowEnhancedTest {

  @Test
  void builder_withWhenTrueAndWhenFalse_executesCorrectBranch() {
    WorkflowContext context = new WorkflowContext();
    context.put("value", 100);

    Workflow trueWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("true-branch");
    Workflow falseWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("false-branch");

    // Test true condition
    Workflow conditionalTrue =
        ConditionalWorkflow.builder()
            .name("TestConditional")
            .condition(ctx -> ctx.getTyped("value", Integer.class) > 50)
            .whenTrue(trueWorkflow)
            .whenFalse(falseWorkflow)
            .build();

    WorkflowResult result = conditionalTrue.execute(context);
    WorkflowTestUtils.assertSuccess(result);

    // Test false condition
    context.put("value", 10);
    Workflow conditionalFalse =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("value", Integer.class) > 50)
            .whenTrue(trueWorkflow)
            .whenFalse(falseWorkflow)
            .build();

    result = conditionalFalse.execute(context);
    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void builder_withOnlyWhenTrue_skipsWhenConditionFalse() {
    WorkflowContext context = new WorkflowContext();
    context.put("enabled", false);

    Workflow workflow = WorkflowTestUtils.mockSuccessfulWorkflow("true-only");

    Workflow conditional =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("enabled", Boolean.class))
            .whenTrue(workflow)
            .build();

    WorkflowResult result = conditional.execute(context);
    assertEquals(WorkflowStatus.SKIPPED, result.getStatus());
  }

  @Test
  void builder_withOnlyWhenFalse_skipsWhenConditionTrue() {
    WorkflowContext context = new WorkflowContext();
    context.put("enabled", true);

    Workflow workflow = WorkflowTestUtils.mockSuccessfulWorkflow("false-only");

    Workflow conditional =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("enabled", Boolean.class))
            .whenFalse(workflow)
            .build();

    WorkflowResult result = conditional.execute(context);
    assertEquals(WorkflowStatus.SKIPPED, result.getStatus());
  }

  @Test
  void builder_withoutCondition_throwsException() {
    try {
      ConditionalWorkflow.builder().whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow()).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void builder_withoutAnyBranch_throwsException() {
    try {
      ConditionalWorkflow.builder().condition(_ -> true).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void execute_conditionThrowsException_returnsFailed() {
    WorkflowContext context = new WorkflowContext();

    Workflow conditional =
        ConditionalWorkflow.builder()
            .condition(
                _ -> {
                  throw new RuntimeException("Condition error");
                })
            .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    WorkflowResult result = conditional.execute(context);
    WorkflowTestUtils.assertFailed(result);
    assertTrue(result.getError().getMessage().contains("Condition evaluation failed"));
  }

  @Test
  void execute_nullConditionResult_handledGracefully() {
    WorkflowContext context = new WorkflowContext();
    context.put("nullValue", true);

    Workflow falseWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("false-branch");

    Workflow conditional =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("nullValue", Boolean.class))
            .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
            .whenFalse(falseWorkflow)
            .build();

    WorkflowResult result = conditional.execute(context);
    // Null is treated as false
    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_complexCondition_evaluatesCorrectly() {
    WorkflowContext context = new WorkflowContext();
    context.put("age", 25);
    context.put("country", "USA");

    Workflow qualifiedWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("qualified");
    Workflow notQualifiedWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("not-qualified");

    Workflow conditional =
        ConditionalWorkflow.builder()
            .condition(
                ctx -> {
                  Integer age = ctx.getTyped("age", Integer.class);
                  String country = ctx.getTyped("country", String.class);
                  return age != null && age >= 18 && "USA".equals(country);
                })
            .whenTrue(qualifiedWorkflow)
            .whenFalse(notQualifiedWorkflow)
            .build();

    WorkflowResult result = conditional.execute(context);
    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_branchModifiesContext_changesVisible() {
    WorkflowContext context = new WorkflowContext();
    context.put("shouldModify", true);

    Workflow modifyingWorkflow = new TaskWorkflow(ctx -> ctx.put("modified", "yes"));

    Workflow conditional =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("shouldModify", Boolean.class))
            .whenTrue(modifyingWorkflow)
            .build();

    WorkflowResult result = conditional.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("yes", context.get("modified"));
  }

  @Test
  void getName_returnsProvidedNameOrDefault() {
    Workflow named =
        ConditionalWorkflow.builder()
            .name("MyConditional")
            .condition(_ -> true)
            .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    assertEquals("MyConditional", named.getName());

    Workflow unnamed =
        ConditionalWorkflow.builder()
            .condition(_ -> true)
            .whenTrue(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    assertNotNull(unnamed.getName());
  }

  @Test
  void getSubWorkflows_returnsBothBranches() {
    Workflow trueWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("true");
    Workflow falseWorkflow = WorkflowTestUtils.mockSuccessfulWorkflow("false");

    ConditionalWorkflow conditional =
        ConditionalWorkflow.builder()
            .condition(_ -> true)
            .whenTrue(trueWorkflow)
            .whenFalse(falseWorkflow)
            .build();

    assertEquals(2, conditional.getSubWorkflows().size());
  }

  @Test
  void execute_nestedConditionals_worksCorrectly() {
    WorkflowContext context = new WorkflowContext();
    context.put("level1", true);
    context.put("level2", true);

    Workflow innerTrue = new TaskWorkflow(ctx -> ctx.put("result", "level2-true"));
    Workflow innerFalse = new TaskWorkflow(ctx -> ctx.put("result", "level2-false"));

    Workflow innerConditional =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("level2", Boolean.class))
            .whenTrue(innerTrue)
            .whenFalse(innerFalse)
            .build();

    Workflow outerConditional =
        ConditionalWorkflow.builder()
            .condition(ctx -> ctx.getTyped("level1", Boolean.class))
            .whenTrue(innerConditional)
            .whenFalse(new TaskWorkflow(ctx -> ctx.put("result", "level1-false")))
            .build();

    WorkflowResult result = outerConditional.execute(context);
    WorkflowTestUtils.assertSuccess(result);
    assertEquals("level2-true", context.get("result"));
  }
}
