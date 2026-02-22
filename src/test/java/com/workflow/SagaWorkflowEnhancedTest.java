package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.saga.SagaStep;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for SagaWorkflow Increases coverage for SagaWorkflow class */
@DisplayName("SagaWorkflow - Enhanced Tests")
class SagaWorkflowEnhancedTest {

  private WorkflowContext context;
  private List<String> executionLog;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
    executionLog = new ArrayList<>();
  }

  @Nested
  @DisplayName("Basic Saga Tests")
  class BasicSagaTests {

    @Test
    @DisplayName("Saga with single successful step")
    void testSagaSingleSuccessfulStep() {
      SagaStep step =
          SagaStep.builder()
              .name("SingleStep")
              .action(_ -> executionLog.add("action"))
              .compensation(_ -> executionLog.add("compensation"))
              .build();

      SagaWorkflow saga = SagaWorkflow.builder().name("SingleStepSaga").step(step).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(List.of("action"), executionLog);
    }

    @Test
    @DisplayName("Saga with multiple successful steps")
    void testSagaMultipleSuccessfulSteps() {
      SagaStep step1 =
          SagaStep.builder()
              .name("Step1")
              .action(
                  ctx -> {
                    executionLog.add("action1");
                    ctx.put("step1_done", true);
                  })
              .compensation(_ -> executionLog.add("compensation1"))
              .build();

      SagaStep step2 =
          SagaStep.builder()
              .name("Step2")
              .action(
                  ctx -> {
                    executionLog.add("action2");
                    ctx.put("step2_done", true);
                  })
              .compensation(_ -> executionLog.add("compensation2"))
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder().name("MultiStepSaga").step(step1).step(step2).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(List.of("action1", "action2"), executionLog);
      assertTrue(context.getTyped("step1_done", Boolean.class));
      assertTrue(context.getTyped("step2_done", Boolean.class));
    }

    @Test
    @DisplayName("Saga with step without compensation")
    void testSagaStepWithoutCompensation() {
      SagaStep step =
          SagaStep.builder()
              .name("NoCompensation")
              .action(_ -> executionLog.add("action"))
              // No compensation provided
              .build();

      SagaWorkflow saga = SagaWorkflow.builder().name("NoCompensationSaga").step(step).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(List.of("action"), executionLog);
    }
  }

  @Nested
  @DisplayName("Compensation Tests")
  class CompensationTests {

    @Test
    @DisplayName("Saga compensates in reverse order on failure")
    void testSagaCompensationReverseOrder() {
      SagaStep step1 =
          SagaStep.builder()
              .name("Step1")
              .action(_ -> executionLog.add("action1"))
              .compensation(_ -> executionLog.add("compensation1"))
              .build();

      SagaStep step2 =
          SagaStep.builder()
              .name("Step2")
              .action(_ -> executionLog.add("action2"))
              .compensation(_ -> executionLog.add("compensation2"))
              .build();

      SagaStep step3 =
          SagaStep.builder()
              .name("Step3")
              .action(
                  _ -> {
                    executionLog.add("action3");
                    throw new RuntimeException("Step 3 failed");
                  })
              .compensation(_ -> executionLog.add("compensation3"))
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder()
              .name("CompensationOrderSaga")
              .step(step1)
              .step(step2)
              .step(step3)
              .build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      // Actions execute 1, 2, 3, then compensations execute in reverse: 2, 1
      assertEquals(
          List.of("action1", "action2", "action3", "compensation2", "compensation1"), executionLog);
    }

    @Test
    @DisplayName("Saga compensation skips steps without compensation")
    void testSagaCompensationSkipsStepsWithoutCompensation() {
      SagaStep step1 =
          SagaStep.builder()
              .name("Step1")
              .action(_ -> executionLog.add("action1"))
              .compensation(_ -> executionLog.add("compensation1"))
              .build();

      SagaStep step2 =
          SagaStep.builder()
              .name("Step2ReadOnly")
              .action(_ -> executionLog.add("action2"))
              // No compensation
              .build();

      SagaStep step3 =
          SagaStep.builder()
              .name("Step3")
              .action(
                  _ -> {
                    executionLog.add("action3");
                    throw new RuntimeException("Failed");
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder()
              .name("SkipNoCompensationSaga")
              .step(step1)
              .step(step2)
              .step(step3)
              .build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      assertEquals(List.of("action1", "action2", "action3", "compensation1"), executionLog);
    }

    @Test
    @DisplayName("Saga handles compensation failure")
    void testSagaCompensationFailure() {
      SagaStep step1 =
          SagaStep.builder()
              .name("Step1")
              .action(_ -> executionLog.add("action1"))
              .compensation(
                  _ -> {
                    executionLog.add("compensation1");
                    throw new RuntimeException("Compensation failed");
                  })
              .build();

      SagaStep step2 =
          SagaStep.builder()
              .name("Step2")
              .action(
                  _ -> {
                    executionLog.add("action2");
                    throw new RuntimeException("Action failed");
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder().name("CompensationFailureSaga").step(step1).step(step2).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      assertTrue(executionLog.contains("compensation1"));
    }
  }

  @Nested
  @DisplayName("Context and Data Flow Tests")
  class ContextDataFlowTests {

    @Test
    @DisplayName("Saga steps share context")
    void testSagaStepsShareContext() {
      SagaStep step1 =
          SagaStep.builder()
              .name("Step1")
              .action(ctx -> ctx.put("shared_data", "value_from_step1"))
              .build();

      SagaStep step2 =
          SagaStep.builder()
              .name("Step2")
              .action(
                  ctx -> {
                    String data = ctx.getTyped("shared_data", String.class);
                    assertEquals("value_from_step1", data);
                    ctx.put("shared_data", "modified_by_step2");
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder().name("SharedContextSaga").step(step1).step(step2).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals("modified_by_step2", context.get("shared_data"));
    }

    @Test
    @DisplayName("Compensation has access to failure context")
    void testCompensationAccessToFailureContext() {
      context.put("initial_value", "initial");

      SagaStep step1 =
          SagaStep.builder()
              .name("Step1")
              .action(ctx -> ctx.put("step1_value", "step1_data"))
              .compensation(
                  ctx -> {
                    // Should have access to context data
                    assertNotNull(ctx.get("initial_value"));
                    assertNotNull(ctx.get("step1_value"));
                    ctx.put("compensation_executed", true);
                  })
              .build();

      SagaStep step2 =
          SagaStep.builder()
              .name("Step2")
              .action(
                  _ -> {
                    throw new RuntimeException("Step2 failed");
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder().name("CompensationContextSaga").step(step1).step(step2).build();

      saga.execute(context);

      assertTrue(context.getTyped("compensation_executed", Boolean.class));
    }
  }

  @Nested
  @DisplayName("Real-World Scenarios")
  class RealWorldScenariosTests {

    @Test
    @DisplayName("Order processing saga")
    void testOrderProcessingSaga() {
      SagaStep reserveInventory =
          SagaStep.builder()
              .name("ReserveInventory")
              .action(
                  ctx -> {
                    executionLog.add("inventory_reserved");
                    ctx.put("reservation_id", "RES123");
                  })
              .compensation(
                  ctx -> {
                    String resId = ctx.getTyped("reservation_id", String.class);
                    executionLog.add("inventory_released_" + resId);
                  })
              .build();

      SagaStep processPayment =
          SagaStep.builder()
              .name("ProcessPayment")
              .action(
                  ctx -> {
                    executionLog.add("payment_processed");
                    ctx.put("transaction_id", "TXN456");
                  })
              .compensation(
                  ctx -> {
                    String txnId = ctx.getTyped("transaction_id", String.class);
                    executionLog.add("payment_refunded_" + txnId);
                  })
              .build();

      SagaStep sendConfirmation =
          SagaStep.builder()
              .name("SendConfirmation")
              .action(
                  _ -> {
                    // This step fails
                    throw new RuntimeException("Email service down");
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder()
              .name("OrderSaga")
              .step(reserveInventory)
              .step(processPayment)
              .step(sendConfirmation)
              .build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      assertTrue(executionLog.contains("inventory_reserved"));
      assertTrue(executionLog.contains("payment_processed"));
      assertTrue(executionLog.contains("payment_refunded_TXN456"));
      assertTrue(executionLog.contains("inventory_released_RES123"));
    }

    @Test
    @DisplayName("Bank transfer saga")
    void testBankTransferSaga() {
      context.put("amount", 100.0);

      SagaStep debitAccount =
          SagaStep.builder()
              .name("DebitSource")
              .action(
                  ctx -> {
                    Double amount = ctx.getTyped("amount", Double.class);
                    executionLog.add("debited_" + amount);
                    ctx.put("source_balance", 900.0);
                  })
              .compensation(
                  ctx -> {
                    Double amount = ctx.getTyped("amount", Double.class);
                    executionLog.add("credit_back_" + amount);
                  })
              .build();

      SagaStep creditAccount =
          SagaStep.builder()
              .name("CreditDestination")
              .action(
                  ctx -> {
                    Double amount = ctx.getTyped("amount", Double.class);
                    executionLog.add("credited_" + amount);
                  })
              .compensation(
                  ctx -> {
                    Double amount = ctx.getTyped("amount", Double.class);
                    executionLog.add("debit_back_" + amount);
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder()
              .name("BankTransferSaga")
              .step(debitAccount)
              .step(creditAccount)
              .build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertTrue(executionLog.contains("debited_100.0"));
      assertTrue(executionLog.contains("credited_100.0"));
      assertFalse(executionLog.contains("credit_back"));
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Empty saga succeeds")
    void testEmptySaga() {
      SagaWorkflow saga = SagaWorkflow.builder().name("EmptySaga").build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    }

    @Test
    @DisplayName("Saga with only compensation steps")
    void testSagaOnlyCompensationSteps() {
      // Steps with no actions should still work
      SagaStep step =
          SagaStep.builder()
              .name("OnlyCompensation")
              .action(_ -> WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build())
              .compensation(_ -> executionLog.add("compensation"))
              .build();

      SagaWorkflow saga = SagaWorkflow.builder().name("OnlyCompensationSaga").step(step).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertTrue(executionLog.isEmpty()); // Compensation not called on success
    }

    @Test
    @DisplayName("Saga with all read-only steps")
    void testSagaAllReadOnlySteps() {
      SagaStep read1 =
          SagaStep.builder().name("Read1").action(_ -> executionLog.add("read1")).build();

      SagaStep read2 =
          SagaStep.builder()
              .name("Read2")
              .action(
                  _ -> {
                    executionLog.add("read2");
                    throw new RuntimeException("Read failed");
                  })
              .build();

      SagaWorkflow saga =
          SagaWorkflow.builder().name("ReadOnlySaga").step(read1).step(read2).build();

      WorkflowResult result = saga.execute(context);

      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      assertEquals(List.of("read1", "read2"), executionLog);
    }
  }
}
