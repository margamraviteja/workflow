package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.*;
import com.workflow.ratelimit.FixedWindowRateLimiter;
import com.workflow.saga.SagaStep;
import com.workflow.script.ScriptProvider;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TreeRendererTest {

  @Test
  @DisplayName("Test 1: Simple Atomic Task")
  void testSimpleTask() {
    Workflow task = new MockTask("SingleTask");
    String output = task.toTreeString();

    assertEquals(
        """
            └── SingleTask (Task)
            """,
        output);
  }

  @Test
  @DisplayName("Test 2: Standard Sequential Container")
  void testSimpleSequence() {
    Workflow seq =
        SequentialWorkflow.builder()
            .name("LinearFlow")
            .workflow(new MockTask("Step1"))
            .workflow(new MockTask("Step2"))
            .build();

    assertEquals(
        """
            └── LinearFlow [Sequence]
                ├── Step1 (Task)
                └── Step2 (Task)
            """,
        seq.toTreeString());
  }

  @Test
  @DisplayName("Test 3: Custom Conditional (Binary Branching)")
  void testConditionalWithSubTasks() {
    Workflow truePath =
        SequentialWorkflow.builder()
            .name("TruePath")
            .workflow(new MockTask("T1"))
            .workflow(new MockTask("T2"))
            .build();

    Workflow conditional =
        ConditionalWorkflow.builder()
            .name("Check")
            .condition(_ -> true)
            .whenTrue(truePath)
            .whenFalse(new MockTask("F1"))
            .build();

    assertEquals(
        """
            └── Check [Conditional]
                ├── When True -> TruePath [Sequence]
                │   ├── T1 (Task)
                │   └── T2 (Task)
                └── When False -> F1 (Task)
            """,
        conditional.toTreeString());
  }

  @Test
  @DisplayName("Test 4: Dynamic Branching (Switch-Case)")
  void testDynamicSwitch() {
    Map<String, Workflow> branches = new LinkedHashMap<>();
    branches.put("A", new MockTask("TaskA"));

    DynamicBranchingWorkflow router =
        DynamicBranchingWorkflow.builder()
            .name("Router")
            .branches(branches)
            .selector(_ -> "A")
            .defaultBranch(new MockTask("Def"))
            .build();

    assertEquals(
        """
            └── Router [Switch]
                ├── CASE "A" -> TaskA (Task)
                └── DEFAULT -> Def (Task)
            """,
        router.toTreeString());
  }

  @Test
  @DisplayName("Test 5: Deeply Nested Complex Workflow")
  void testDeeplyNestedComplexFlow() {
    Workflow complex =
        SequentialWorkflow.builder()
            .name("Root")
            .workflow(
                FallbackWorkflow.builder()
                    .name("TrySafe")
                    .primary(
                        ParallelWorkflow.builder()
                            .name("Process")
                            .workflow(new MockTask("P1"))
                            .workflow(new MockTask("P2"))
                            .build())
                    .fallback(new MockTask("Recovery"))
                    .build())
            .workflow(
                RateLimitedWorkflow.builder()
                    .name("FinalLimit")
                    .workflow(new MockTask("Notify"))
                    .rateLimitStrategy(new FixedWindowRateLimiter(100, Duration.ofMinutes(1)))
                    .build())
            .build();

    assertEquals(
        """
            └── Root [Sequence]
                ├── TrySafe [Fallback]
                │   ├── TRY (Primary) -> Process [Parallel]
                │   │   ├── P1 (Task)
                │   │   └── P2 (Task)
                │   └── ON FAILURE -> Recovery (Task)
                └── FinalLimit [Rate-Limited]
                    └── Notify (Task)
            """,
        complex.toTreeString());
  }

  @Test
  @DisplayName("Verify visualization of multi-branch router with default fallback")
  void testMultiBranchVisualization() {
    // Use LinkedHashMap to ensure the iteration order matches our expected text block
    Map<String, Workflow> branches = new LinkedHashMap<>();

    // Branch A: Simple Task
    branches.put("CREDIT", new MockTask("CreditService"));

    // Branch B: Nested Sequence
    branches.put(
        "DEBIT",
        SequentialWorkflow.builder()
            .name("DebitProcess")
            .workflow(new MockTask("Validate"))
            .workflow(new MockTask("Deduct"))
            .build());

    DynamicBranchingWorkflow router =
        DynamicBranchingWorkflow.builder()
            .name("PaymentRouter")
            .branches(branches)
            .selector(ctx -> ctx.getTyped("type", String.class))
            .defaultBranch(new MockTask("ManualAudit"))
            .build();

    String actualOutput = router.toTreeString();

    // The text block represents exactly how the TreeRenderer handles
    // intermediate vertical bars (│) vs final branches (└──).
    String expectedOutput =
        """
            └── PaymentRouter [Switch]
                ├── CASE "CREDIT" -> CreditService (Task)
                ├── CASE "DEBIT" -> DebitProcess [Sequence]
                │   ├── Validate (Task)
                │   └── Deduct (Task)
                └── DEFAULT -> ManualAudit (Task)
            """;

    assertEquals(expectedOutput, actualOutput);
  }

  @Test
  @DisplayName("Verify visualization of multi-branch router without default fallback")
  void testMultiBranchNoDefaultVisualization() {
    Map<String, Workflow> branches = new LinkedHashMap<>();
    branches.put("SMS", new MockTask("SmsNotify"));
    branches.put("EMAIL", new MockTask("EmailNotify"));

    DynamicBranchingWorkflow router =
        DynamicBranchingWorkflow.builder()
            .name("Notifier")
            .branches(branches)
            .selector(_ -> "SMS")
            .build();

    String actualOutput = router.toTreeString();

    String expectedOutput =
        """
            └── Notifier [Switch]
                ├── CASE "SMS" -> SmsNotify (Task)
                └── CASE "EMAIL" -> EmailNotify (Task)
            """;

    assertEquals(expectedOutput, actualOutput);
  }

  @Test
  @DisplayName("Level 3: Sequence -> Conditional -> Sequence")
  void testLevel3Nesting() {
    Workflow approvalFlow =
        SequentialWorkflow.builder()
            .name("ApprovalFlow")
            .workflow(new MockTask("ManagerReview"))
            .workflow(new MockTask("LogDecision"))
            .build();

    Workflow mainWorkflow =
        SequentialWorkflow.builder()
            .name("OrderProcess")
            .workflow(new MockTask("Validate"))
            .workflow(
                ConditionalWorkflow.builder()
                    .name("HighValueCheck")
                    .condition(_ -> true)
                    .whenTrue(approvalFlow)
                    .whenFalse(new MockTask("AutoApprove"))
                    .build())
            .build();

    assertEquals(
        """
                └── OrderProcess [Sequence]
                    ├── Validate (Task)
                    └── HighValueCheck [Conditional]
                        ├── When True -> ApprovalFlow [Sequence]
                        │   ├── ManagerReview (Task)
                        │   └── LogDecision (Task)
                        └── When False -> AutoApprove (Task)
                """,
        mainWorkflow.toTreeString());
  }

  @Test
  @DisplayName("Level 4: Sequence -> Fallback -> Parallel -> Sequence")
  void testLevel4Nesting() {
    Workflow dataSync =
        ParallelWorkflow.builder()
            .name("DataSync")
            .workflow(new MockTask("SyncCRM"))
            .workflow(
                SequentialWorkflow.builder()
                    .name("ERPUpdate")
                    .workflow(new MockTask("Connect"))
                    .workflow(new MockTask("Push"))
                    .build())
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("MainPipeline")
            .workflow(
                FallbackWorkflow.builder()
                    .name("ResilientSync")
                    .primary(dataSync)
                    .fallback(new MockTask("LocalQueueFallback"))
                    .build())
            .build();

    assertEquals(
        """
                └── MainPipeline [Sequence]
                    └── ResilientSync [Fallback]
                        ├── TRY (Primary) -> DataSync [Parallel]
                        │   ├── SyncCRM (Task)
                        │   └── ERPUpdate [Sequence]
                        │       ├── Connect (Task)
                        │       └── Push (Task)
                        └── ON FAILURE -> LocalQueueFallback (Task)
                """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Level 5: Sequence -> RateLimit -> Switch -> Sequence -> Parallel")
  void testLevel5Nesting() {
    // Level 5
    Workflow deepParallel =
        ParallelWorkflow.builder()
            .name("Compute")
            .workflow(new MockTask("EngineA"))
            .workflow(new MockTask("EngineB"))
            .build();

    // Level 4
    Workflow subSequence =
        SequentialWorkflow.builder().name("SubStep").workflow(deepParallel).build();

    // Level 3
    Map<String, Workflow> routes = new LinkedHashMap<>();
    routes.put("v1", subSequence);
    Workflow router =
        DynamicBranchingWorkflow.builder()
            .name("VersionRouter")
            .branches(routes)
            .selector(_ -> "v1")
            .defaultBranch(new MockTask("v2-Default"))
            .build();

    // Level 2
    Workflow limited =
        RateLimitedWorkflow.builder()
            .name("Gateway")
            .workflow(router)
            .rateLimitStrategy(new FixedWindowRateLimiter(5, Duration.ofSeconds(1)))
            .build();

    // Level 1
    Workflow root = SequentialWorkflow.builder().name("EnterpriseRoot").workflow(limited).build();

    assertEquals(
        """
                └── EnterpriseRoot [Sequence]
                    └── Gateway [Rate-Limited]
                        └── VersionRouter [Switch]
                            ├── CASE "v1" -> SubStep [Sequence]
                            │   └── Compute [Parallel]
                            │       ├── EngineA (Task)
                            │       └── EngineB (Task)
                            └── DEFAULT -> v2-Default (Task)
                """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Edge Case: Empty Sequence Container")
  void testEmptySequence() {
    Workflow emptySeq = SequentialWorkflow.builder().name("EmptyFlow").build();

    assertEquals(
        """
                └── EmptyFlow [Sequence]
                """,
        emptySeq.toTreeString().stripTrailing() + "\n");
    // Note: Check if your renderer adds a newline for empty children
  }

  @Test
  @DisplayName("Edge Case: Switch without Default Branch")
  void testSwitchNoDefault() {
    Map<String, Workflow> branches = new LinkedHashMap<>();
    branches.put("KEY", new MockTask("OnlyTask"));

    DynamicBranchingWorkflow router =
        DynamicBranchingWorkflow.builder()
            .name("PartialRouter")
            .branches(branches)
            .selector(_ -> "KEY")
            .build();

    String output = router.toTreeString();

    // Verify "DEFAULT" line is absent
    assertFalse(output.contains("DEFAULT"));
    assertEquals(
        """
                └── PartialRouter [Switch]
                    └── CASE "KEY" -> OnlyTask (Task)
                """,
        output);
  }

  @Test
  @DisplayName("Combination: Fallback wrapping a Parallel block")
  void testParallelInsideFallback() {
    Workflow parallelBatch =
        ParallelWorkflow.builder()
            .name("CloudUpload")
            .workflow(new MockTask("S3-Upload"))
            .workflow(new MockTask("GCS-Upload"))
            .build();

    Workflow fallback =
        FallbackWorkflow.builder()
            .name("ResilientUpload")
            .primary(parallelBatch)
            .fallback(new MockTask("DiskBackup"))
            .build();

    assertEquals(
        """
                └── ResilientUpload [Fallback]
                    ├── TRY (Primary) -> CloudUpload [Parallel]
                    │   ├── S3-Upload (Task)
                    │   └── GCS-Upload (Task)
                    └── ON FAILURE -> DiskBackup (Task)
                """,
        fallback.toTreeString());
  }

  @Test
  @DisplayName("Combination: Switch branch containing a Rate Limiter")
  void testRateLimitInsideSwitch() {
    Workflow throttledSms =
        RateLimitedWorkflow.builder()
            .name("SmsThrottler")
            .workflow(new MockTask("TwilioSend"))
            .rateLimitStrategy(new FixedWindowRateLimiter(5, Duration.ofSeconds(10)))
            .build();

    Map<String, Workflow> channels = new LinkedHashMap<>();
    channels.put("SMS", throttledSms);
    channels.put("PUSH", new MockTask("FirebaseSend"));

    DynamicBranchingWorkflow router =
        DynamicBranchingWorkflow.builder()
            .name("NotificationRouter")
            .branches(channels)
            .selector(_ -> "SMS")
            .defaultBranch(new MockTask("LogOnly"))
            .build();

    assertEquals(
        """
                └── NotificationRouter [Switch]
                    ├── CASE "SMS" -> SmsThrottler [Rate-Limited]
                    │   └── TwilioSend (Task)
                    ├── CASE "PUSH" -> FirebaseSend (Task)
                    └── DEFAULT -> LogOnly (Task)
                """,
        router.toTreeString());
  }

  @Test
  @DisplayName("Combination: Multi-level Mixed Nesting")
  void testMixedDeepNesting() {
    Workflow innerSequence =
        SequentialWorkflow.builder()
            .name("AuthChain")
            .workflow(new MockTask("TokenGen"))
            .workflow(new MockTask("VaultStore"))
            .build();

    Workflow conditional =
        ConditionalWorkflow.builder()
            .name("UserExists?")
            .condition(_ -> true)
            .whenTrue(innerSequence)
            .whenFalse(new MockTask("CreateUser"))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("AppInit")
            .workflow(new MockTask("ConfigLoad"))
            .workflow(conditional)
            .workflow(new MockTask("Launch"))
            .build();

    assertEquals(
        """
                └── AppInit [Sequence]
                    ├── ConfigLoad (Task)
                    ├── UserExists? [Conditional]
                    │   ├── When True -> AuthChain [Sequence]
                    │   │   ├── TokenGen (Task)
                    │   │   └── VaultStore (Task)
                    │   └── When False -> CreateUser (Task)
                    └── Launch (Task)
                """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Combination: Asymmetric Branch Depth in Parallel")
  void testAsymmetricParallel() {
    // Deep branch
    Workflow deepBranch =
        SequentialWorkflow.builder()
            .name("DeepBranch")
            .workflow(new MockTask("Level1"))
            .workflow(
                SequentialWorkflow.builder()
                    .name("Level2")
                    .workflow(new MockTask("Level3"))
                    .build())
            .build();

    // Shallow branch
    Workflow shallowBranch = new MockTask("QuickTask");

    Workflow root =
        ParallelWorkflow.builder()
            .name("ParallelRoot")
            .workflow(deepBranch)
            .workflow(shallowBranch)
            .build();

    assertEquals(
        """
            └── ParallelRoot [Parallel]
                ├── DeepBranch [Sequence]
                │   ├── Level1 (Task)
                │   └── Level2 [Sequence]
                │       └── Level3 (Task)
                └── QuickTask (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Combination: Sequence -> Fallback -> Sequence -> Task")
  void testRetryFallbackSequence() {
    Workflow recoveryProcess =
        SequentialWorkflow.builder()
            .name("RecoveryProcess")
            .workflow(new MockTask("ClearCache"))
            .workflow(new MockTask("RestartService"))
            .build();

    Workflow mainTask = new MockTask("PrimaryAPI");

    Workflow root =
        SequentialWorkflow.builder()
            .name("AppRoot")
            .workflow(
                FallbackWorkflow.builder()
                    .name("ApiWithRecovery")
                    .primary(mainTask)
                    .fallback(recoveryProcess)
                    .build())
            .workflow(new MockTask("Finalize"))
            .build();

    assertEquals(
        """
            └── AppRoot [Sequence]
                ├── ApiWithRecovery [Fallback]
                │   ├── TRY (Primary) -> PrimaryAPI (Task)
                │   └── ON FAILURE -> RecoveryProcess [Sequence]
                │       ├── ClearCache (Task)
                │       └── RestartService (Task)
                └── Finalize (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Combination: Nested Parallel blocks")
  void testNestedParallel() {
    Workflow subParallel =
        ParallelWorkflow.builder()
            .name("InnerGrid")
            .workflow(new MockTask("NodeA"))
            .workflow(new MockTask("NodeB"))
            .build();

    Workflow root =
        ParallelWorkflow.builder()
            .name("OuterGrid")
            .workflow(new MockTask("PreCheck"))
            .workflow(subParallel)
            .workflow(new MockTask("PostCheck"))
            .build();

    assertEquals(
        """
            └── OuterGrid [Parallel]
                ├── PreCheck (Task)
                ├── InnerGrid [Parallel]
                │   ├── NodeA (Task)
                │   └── NodeB (Task)
                └── PostCheck (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Level 5: Double Fallback with Nested Sequences")
  void testDeepFallbackChain() {
    Workflow leaf =
        SequentialWorkflow.builder().name("DeepLeaf").workflow(new MockTask("FinalTask")).build();

    Workflow innerFallback =
        FallbackWorkflow.builder()
            .name("InnerTry")
            .primary(leaf)
            .fallback(new MockTask("InnerRecovery"))
            .build();

    Workflow outerFallback =
        FallbackWorkflow.builder()
            .name("OuterTry")
            .primary(innerFallback)
            .fallback(new MockTask("OuterRecovery"))
            .build();

    Workflow root =
        SequentialWorkflow.builder().name("EnterpriseRoot").workflow(outerFallback).build();

    assertEquals(
        """
            └── EnterpriseRoot [Sequence]
                └── OuterTry [Fallback]
                    ├── TRY (Primary) -> InnerTry [Fallback]
                    │   ├── TRY (Primary) -> DeepLeaf [Sequence]
                    │   │   └── FinalTask (Task)
                    │   └── ON FAILURE -> InnerRecovery (Task)
                    └── ON FAILURE -> OuterRecovery (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Level 5: Sequence -> Fallback -> Parallel -> Sequence -> Task")
  void testLevel5Complex() {
    Workflow leafSequence =
        SequentialWorkflow.builder()
            .name("DatabaseUpdate")
            .workflow(new MockTask("WriteRecord"))
            .workflow(new MockTask("Commit"))
            .build();

    Workflow parallelBlock =
        ParallelWorkflow.builder()
            .name("Distribute")
            .workflow(new MockTask("NotifyQueue"))
            .workflow(leafSequence)
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("GlobalProcessor")
            .workflow(
                FallbackWorkflow.builder()
                    .name("SafeDistribute")
                    .primary(parallelBlock)
                    .fallback(new MockTask("LocalLogFallback"))
                    .build())
            .build();

    assertEquals(
        """
            └── GlobalProcessor [Sequence]
                └── SafeDistribute [Fallback]
                    ├── TRY (Primary) -> Distribute [Parallel]
                    │   ├── NotifyQueue (Task)
                    │   └── DatabaseUpdate [Sequence]
                    │       ├── WriteRecord (Task)
                    │       └── Commit (Task)
                    └── ON FAILURE -> LocalLogFallback (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Level 5: Sequence -> RateLimit -> Switch -> Parallel -> Task")
  void testLevel5SwitchParallel() {
    Workflow workerNodes =
        ParallelWorkflow.builder()
            .name("ComputeCluster")
            .workflow(new MockTask("Node1"))
            .workflow(new MockTask("Node2"))
            .build();

    Map<String, Workflow> cases = new LinkedHashMap<>();
    cases.put("HEAVY", workerNodes);

    Workflow router =
        DynamicBranchingWorkflow.builder()
            .name("LoadBalancer")
            .branches(cases)
            .selector(_ -> "HEAVY")
            .defaultBranch(new MockTask("LightWorker"))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("AppRoot")
            .workflow(
                RateLimitedWorkflow.builder()
                    .name("Throttle")
                    .workflow(router)
                    .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofSeconds(1)))
                    .build())
            .build();

    assertEquals(
        """
            └── AppRoot [Sequence]
                └── Throttle [Rate-Limited]
                    └── LoadBalancer [Switch]
                        ├── CASE "HEAVY" -> ComputeCluster [Parallel]
                        │   ├── Node1 (Task)
                        │   └── Node2 (Task)
                        └── DEFAULT -> LightWorker (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Level 5: RateLimit -> Fallback -> Parallel -> [Sequence, Task]")
  void testComplexResilientApi() {
    // Level 4 & 5 (The Primary path: A Parallel block containing a Sequence)
    Workflow primaryPath =
        ParallelWorkflow.builder()
            .name("SyncEngines")
            .workflow(
                SequentialWorkflow.builder()
                    .name("InventoryUpdate")
                    .workflow(new MockTask("CheckStock"))
                    .workflow(new MockTask("ReserveItems"))
                    .build())
            .workflow(new MockTask("PricingService"))
            .build();

    // Level 3 (The Fallback path: A Simple Task)
    Workflow recovery = new MockTask("CircuitBreakerLog");

    // Level 2 (The Fallback Container)
    Workflow resilienceWrapper =
        FallbackWorkflow.builder()
            .name("ResilienceLayer")
            .primary(primaryPath)
            .fallback(recovery)
            .build();

    // Level 1 (The Root: Rate Limited)
    Workflow root =
        RateLimitedWorkflow.builder()
            .name("PublicGateway")
            .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofMinutes(1)))
            .workflow(resilienceWrapper)
            .build();

    assertEquals(
        """
            └── PublicGateway [Rate-Limited]
                └── ResilienceLayer [Fallback]
                    ├── TRY (Primary) -> SyncEngines [Parallel]
                    │   ├── InventoryUpdate [Sequence]
                    │   │   ├── CheckStock (Task)
                    │   │   └── ReserveItems (Task)
                    │   └── PricingService (Task)
                    └── ON FAILURE -> CircuitBreakerLog (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Level 6: Sequence -> Conditional -> Switch -> Parallel")
  void testDeepMultiChannelFlow() {
    // Level 5 & 6
    Workflow smsBranch =
        ParallelWorkflow.builder()
            .name("SmsCluster")
            .workflow(new MockTask("ProviderA"))
            .workflow(new MockTask("ProviderB"))
            .build();

    // Level 4 (Switch)
    Map<String, Workflow> channels = new LinkedHashMap<>();
    channels.put("SMS", smsBranch);
    Workflow router =
        DynamicBranchingWorkflow.builder()
            .name("ChannelRouter")
            .branches(channels)
            .selector(_ -> "SMS")
            .defaultBranch(new MockTask("DefaultEmail"))
            .build();

    // Level 3 (Sequence)
    Workflow notificationFlow =
        SequentialWorkflow.builder()
            .name("NotifyFlow")
            .workflow(new MockTask("LookupUser"))
            .workflow(router)
            .build();

    // Level 2 (Conditional)
    Workflow root =
        ConditionalWorkflow.builder()
            .name("AlertEnabled?")
            .condition(_ -> true)
            .whenTrue(notificationFlow)
            .whenFalse(new MockTask("SilentDrop"))
            .build();

    assertEquals(
        """
            └── AlertEnabled? [Conditional]
                ├── When True -> NotifyFlow [Sequence]
                │   ├── LookupUser (Task)
                │   └── ChannelRouter [Switch]
                │       ├── CASE "SMS" -> SmsCluster [Parallel]
                │       │   ├── ProviderA (Task)
                │       │   └── ProviderB (Task)
                │       └── DEFAULT -> DefaultEmail (Task)
                └── When False -> SilentDrop (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Complex Asymmetric: Sequence with a Deep Middle Child")
  void testAsymmetricDeepMiddleChild() {
    Workflow deepMiddle =
        FallbackWorkflow.builder()
            .name("MiddleProcess")
            .primary(
                SequentialWorkflow.builder()
                    .name("PrimarySteps")
                    .workflow(new MockTask("Step1"))
                    .workflow(new MockTask("Step2"))
                    .build())
            .fallback(new MockTask("Recovery"))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("MainPipeline")
            .workflow(new MockTask("Start"))
            .workflow(deepMiddle)
            .workflow(new MockTask("End"))
            .build();

    assertEquals(
        """
            └── MainPipeline [Sequence]
                ├── Start (Task)
                ├── MiddleProcess [Fallback]
                │   ├── TRY (Primary) -> PrimarySteps [Sequence]
                │   │   ├── Step1 (Task)
                │   │   └── Step2 (Task)
                │   └── ON FAILURE -> Recovery (Task)
                └── End (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("E-Commerce Checkout with Payment Fallback")
  void testCheckoutPipeline() {
    Workflow paymentLogic =
        FallbackWorkflow.builder()
            .name("ChargeCreditCard")
            .primary(new MockTask("StripeProvider"))
            .fallback(new MockTask("PayPalBackup"))
            .build();

    Workflow loyaltyBranch =
        ConditionalWorkflow.builder()
            .name("LoyaltyMemberCheck")
            .condition(_ -> true)
            .whenTrue(new MockTask("AddBonusPoints"))
            .whenFalse(new MockTask("OfferMembership"))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("CheckoutPipeline")
            .workflow(new MockTask("ValidateCart"))
            .workflow(paymentLogic)
            .workflow(loyaltyBranch)
            .workflow(new MockTask("EmailReceipt"))
            .build();

    assertEquals(
        """
            └── CheckoutPipeline [Sequence]
                ├── ValidateCart (Task)
                ├── ChargeCreditCard [Fallback]
                │   ├── TRY (Primary) -> StripeProvider (Task)
                │   └── ON FAILURE -> PayPalBackup (Task)
                ├── LoyaltyMemberCheck [Conditional]
                │   ├── When True -> AddBonusPoints (Task)
                │   └── When False -> OfferMembership (Task)
                └── EmailReceipt (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("IoT Onboarding with Device Routing")
  void testIotOnboarding() {
    // Setup for Camera: Parallel Firmware and Auth check
    Workflow cameraSetup =
        ParallelWorkflow.builder()
            .name("CameraSetup")
            .workflow(new MockTask("UpdateFirmware"))
            .workflow(new MockTask("EnableEncryption"))
            .build();

    // Dynamic Routing based on Device Type
    Map<String, Workflow> deviceRoutes = new LinkedHashMap<>();
    deviceRoutes.put("CAMERA", cameraSetup);
    deviceRoutes.put("SENSOR", new MockTask("SimplePairing"));

    Workflow router =
        DynamicBranchingWorkflow.builder()
            .name("DeviceRouter")
            .branches(deviceRoutes)
            .selector(_ -> "CAMERA")
            .defaultBranch(new MockTask("GenericPairing"))
            .build();

    Workflow root =
        RateLimitedWorkflow.builder()
            .name("OnboardingGateway")
            .workflow(router)
            .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofMinutes(1)))
            .build();

    assertEquals(
        """
            └── OnboardingGateway [Rate-Limited]
                └── DeviceRouter [Switch]
                    ├── CASE "CAMERA" -> CameraSetup [Parallel]
                    │   ├── UpdateFirmware (Task)
                    │   └── EnableEncryption (Task)
                    ├── CASE "SENSOR" -> SimplePairing (Task)
                    └── DEFAULT -> GenericPairing (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Infrastructure Provisioning (High Complexity)")
  void testCloudProvisioning() {
    // Level 5 & 6: Microservices
    Workflow services =
        ParallelWorkflow.builder()
            .name("Microservices")
            .workflow(new MockTask("AuthService"))
            .workflow(new MockTask("OrderService"))
            .build();

    // Level 4: App Layer
    Workflow appLayer =
        SequentialWorkflow.builder()
            .name("AppLayer")
            .workflow(new MockTask("ProvisionEC2"))
            .workflow(services)
            .build();

    // Level 2 & 3: Database with Fallback
    Workflow dbLayer =
        FallbackWorkflow.builder()
            .name("DatabaseLayer")
            .primary(new MockTask("RDS-Primary"))
            .fallback(new MockTask("RDS-ReadReplica"))
            .build();

    // Level 1: Main Pipeline
    Workflow root =
        SequentialWorkflow.builder()
            .name("CloudDeploy")
            .workflow(new MockTask("VPC-Setup"))
            .workflow(dbLayer)
            .workflow(appLayer)
            .build();

    assertEquals(
        """
            └── CloudDeploy [Sequence]
                ├── VPC-Setup (Task)
                ├── DatabaseLayer [Fallback]
                │   ├── TRY (Primary) -> RDS-Primary (Task)
                │   └── ON FAILURE -> RDS-ReadReplica (Task)
                └── AppLayer [Sequence]
                    ├── ProvisionEC2 (Task)
                    └── Microservices [Parallel]
                        ├── AuthService (Task)
                        └── OrderService (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Multi-Region Migration with Parallel Fallbacks")
  void testMultiRegionMigration() {
    Workflow usEast =
        FallbackWorkflow.builder()
            .name("US-East-Sync")
            .primary(new MockTask("Primary-DB"))
            .fallback(new MockTask("Secondary-DB"))
            .build();

    Workflow euWest =
        FallbackWorkflow.builder()
            .name("EU-West-Sync")
            .primary(new MockTask("Primary-DB-EU"))
            .fallback(new MockTask("Secondary-DB-EU"))
            .build();

    Workflow migration =
        ParallelWorkflow.builder()
            .name("GlobalMigration")
            .workflow(usEast)
            .workflow(euWest)
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("MigrationOrchestrator")
            .workflow(new MockTask("PreCheck"))
            .workflow(migration)
            .workflow(new MockTask("FinalCleanup"))
            .build();

    assertEquals(
        """
            └── MigrationOrchestrator [Sequence]
                ├── PreCheck (Task)
                ├── GlobalMigration [Parallel]
                │   ├── US-East-Sync [Fallback]
                │   │   ├── TRY (Primary) -> Primary-DB (Task)
                │   │   └── ON FAILURE -> Secondary-DB (Task)
                │   └── EU-West-Sync [Fallback]
                │       ├── TRY (Primary) -> Primary-DB-EU (Task)
                │       └── ON FAILURE -> Secondary-DB-EU (Task)
                └── FinalCleanup (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Fraud Detection with Nested Conditional Switch")
  void testFraudDetectionPipeline() {
    // Level 5 & 6: Scoring Engines
    Map<String, Workflow> engines = new LinkedHashMap<>();
    engines.put("AI", new MockTask("TensorFlowScoring"));
    engines.put("RULE", new MockTask("LegacyRules"));

    Workflow scoringRouter =
        DynamicBranchingWorkflow.builder()
            .name("ScoringEngine")
            .branches(engines)
            .selector(_ -> "AI")
            .defaultBranch(new MockTask("DefaultScore"))
            .build();

    // Level 4: Validation Sequence
    Workflow validation =
        SequentialWorkflow.builder()
            .name("DeepValidation")
            .workflow(new MockTask("IdentityCheck"))
            .workflow(scoringRouter)
            .build();

    // Level 2 & 3: Conditional high-value check
    Workflow root =
        RateLimitedWorkflow.builder()
            .name("TransactionGateway")
            .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofMinutes(1)))
            .workflow(
                ConditionalWorkflow.builder()
                    .name("HighValue?")
                    .condition(_ -> true)
                    .whenTrue(validation)
                    .whenFalse(new MockTask("FastTrack"))
                    .build())
            .build();

    assertEquals(
        """
            └── TransactionGateway [Rate-Limited]
                └── HighValue? [Conditional]
                    ├── When True -> DeepValidation [Sequence]
                    │   ├── IdentityCheck (Task)
                    │   └── ScoringEngine [Switch]
                    │       ├── CASE "AI" -> TensorFlowScoring (Task)
                    │       ├── CASE "RULE" -> LegacyRules (Task)
                    │       └── DEFAULT -> DefaultScore (Task)
                    └── When False -> FastTrack (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("CI/CD Pipeline with Parallel Testing and Rollback")
  void testDeploymentPipeline() {
    Workflow parallelTests =
        ParallelWorkflow.builder()
            .name("TestSuite")
            .workflow(new MockTask("UnitTests"))
            .workflow(new MockTask("Linting"))
            .build();

    Workflow deployment =
        FallbackWorkflow.builder()
            .name("DeployToProd")
            .primary(new MockTask("K8s-Apply"))
            .fallback(new MockTask("K8s-Rollback"))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("MainPipeline")
            .workflow(new MockTask("BuildArtifact"))
            .workflow(parallelTests)
            .workflow(deployment)
            .build();

    assertEquals(
        """
            └── MainPipeline [Sequence]
                ├── BuildArtifact (Task)
                ├── TestSuite [Parallel]
                │   ├── UnitTests (Task)
                │   └── Linting (Task)
                └── DeployToProd [Fallback]
                    ├── TRY (Primary) -> K8s-Apply (Task)
                    └── ON FAILURE -> K8s-Rollback (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Real World: Order Fulfillment with Multi-Warehouse Parallel Check")
  void testOrderFulfillmentPipeline() {
    // Level 5 & 6: Warehouse Checks
    Workflow warehouseChecks =
        ParallelWorkflow.builder()
            .name("WarehouseAvailability")
            .workflow(new MockTask("EastCoastWh"))
            .workflow(new MockTask("WestCoastWh"))
            .build();

    // Level 4: Inventory Logic with Fallback
    Workflow inventoryLogic =
        FallbackWorkflow.builder()
            .name("InventoryReservation")
            .primary(warehouseChecks)
            .fallback(new MockTask("BackorderQueue"))
            .build();

    // Level 2 & 3: Main Processing Sequence
    Workflow mainProcessing =
        SequentialWorkflow.builder()
            .name("ProcessOrder")
            .workflow(new MockTask("ValidatePayment"))
            .workflow(inventoryLogic)
            .build();

    // Level 1: Root Gateway
    Workflow root =
        RateLimitedWorkflow.builder()
            .name("OrderGateway")
            .workflow(mainProcessing)
            .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofMinutes(1)))
            .build();

    assertEquals(
        """
            └── OrderGateway [Rate-Limited]
                └── ProcessOrder [Sequence]
                    ├── ValidatePayment (Task)
                    └── InventoryReservation [Fallback]
                        ├── TRY (Primary) -> WarehouseAvailability [Parallel]
                        │   ├── EastCoastWh (Task)
                        │   └── WestCoastWh (Task)
                        └── ON FAILURE -> BackorderQueue (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Real World: MFA Orchestrator with Provider Fallback")
  void testMfaOrchestrator() {
    // Level 4 & 5: SMS Logic
    Workflow smsFlow =
        FallbackWorkflow.builder()
            .name("SmsProvider")
            .primary(new MockTask("Twilio"))
            .fallback(new MockTask("MessageBird"))
            .build();

    // Level 3: Router
    Map<String, Workflow> mfaMethods = new LinkedHashMap<>();
    mfaMethods.put("SMS", smsFlow);
    mfaMethods.put("APP", new MockTask("AuthenticatorApp"));

    Workflow mfaRouter =
        DynamicBranchingWorkflow.builder()
            .name("MfaMethodSelector")
            .branches(mfaMethods)
            .selector(_ -> "SMS")
            .defaultBranch(new MockTask("EmailBackup"))
            .build();

    // Level 1 & 2: Main Auth Sequence
    Workflow root =
        SequentialWorkflow.builder()
            .name("SecurityGateway")
            .workflow(new MockTask("CheckCredentials"))
            .workflow(mfaRouter)
            .build();

    assertEquals(
        """
            └── SecurityGateway [Sequence]
                ├── CheckCredentials (Task)
                └── MfaMethodSelector [Switch]
                    ├── CASE "SMS" -> SmsProvider [Fallback]
                    │   ├── TRY (Primary) -> Twilio (Task)
                    │   └── ON FAILURE -> MessageBird (Task)
                    ├── CASE "APP" -> AuthenticatorApp (Task)
                    └── DEFAULT -> EmailBackup (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Real World: Global CDN Invalidation with Region-Specific Sequences")
  void testCdnInvalidation() {

    // Level 4 & 5: Parallel Regions
    Workflow globalPurge =
        ParallelWorkflow.builder()
            .name("GlobalInvalidation")
            .workflow(regionSeq("US"))
            .workflow(regionSeq("EU"))
            .workflow(regionSeq("APAC"))
            .build();

    // Level 1, 2 & 3: Top Level Logic
    Workflow root =
        SequentialWorkflow.builder()
            .name("CdnController")
            .workflow(
                ConditionalWorkflow.builder()
                    .name("IsGlobal?")
                    .condition(_ -> true)
                    .whenTrue(globalPurge)
                    .whenFalse(new MockTask("LocalPurgeOnly"))
                    .build())
            .build();

    assertEquals(
        """
            └── CdnController [Sequence]
                └── IsGlobal? [Conditional]
                    ├── When True -> GlobalInvalidation [Parallel]
                    │   ├── US-Purge [Sequence]
                    │   │   ├── Connect (Task)
                    │   │   └── ClearCache (Task)
                    │   ├── EU-Purge [Sequence]
                    │   │   ├── Connect (Task)
                    │   │   └── ClearCache (Task)
                    │   └── APAC-Purge [Sequence]
                    │       ├── Connect (Task)
                    │       └── ClearCache (Task)
                    └── When False -> LocalPurgeOnly (Task)
            """,
        root.toTreeString());
  }

  @Test
  @DisplayName("ForEach 1: Simple Sequential Loop")
  void testSimpleForEach() {
    Workflow processItem = new MockTask("ProcessItem");

    Workflow forEach =
        ForEachWorkflow.builder()
            .name("SyncImages")
            .itemsKey("imageList")
            .itemVariable("currentImage")
            .workflow(processItem)
            .build();

    assertEquals(
        """
                └── SyncImages [ForEach]
                    └── FOR EACH (currentImage IN imageList) -> ProcessItem (Task)
                """,
        forEach.toTreeString());
  }

  @Test
  @DisplayName("ForEach 2: Loop wrapping a Sequence")
  void testForEachWithSequence() {
    Workflow itemPipeline =
        SequentialWorkflow.builder()
            .name("ItemPipeline")
            .workflow(new MockTask("Validate"))
            .workflow(new MockTask("Archive"))
            .build();

    Workflow forEach =
        ForEachWorkflow.builder()
            .name("ArchiveBatch")
            .itemsKey("files")
            .itemVariable("file")
            .workflow(itemPipeline)
            .build();

    assertEquals(
        """
                └── ArchiveBatch [ForEach]
                    └── FOR EACH (file IN files) -> ItemPipeline [Sequence]
                        ├── Validate (Task)
                        └── Archive (Task)
                """,
        forEach.toTreeString());
  }

  @Test
  @DisplayName("ForEach 3: Nested Loops (O(n^2) Visualization)")
  void testNestedForEach() {
    // Level 3
    Workflow processCell = new MockTask("ProcessCell");

    // Level 2
    Workflow rowLoop =
        ForEachWorkflow.builder()
            .name("RowProcessor")
            .itemsKey("currentRow")
            .itemVariable("cell")
            .workflow(processCell)
            .build();

    // Level 1
    Workflow gridLoop =
        ForEachWorkflow.builder()
            .name("GridProcessor")
            .itemsKey("matrix")
            .itemVariable("currentRow")
            .workflow(rowLoop)
            .build();

    assertEquals(
        """
                └── GridProcessor [ForEach]
                    └── FOR EACH (currentRow IN matrix) -> RowProcessor [ForEach]
                        └── FOR EACH (cell IN currentRow) -> ProcessCell (Task)
                """,
        gridLoop.toTreeString());
  }

  @Test
  @DisplayName("ForEach 4: Complex Loop with Branching and Fallbacks")
  void testComplexForEach() {
    // A complex logic inside each iteration
    Workflow itemLogic =
        FallbackWorkflow.builder()
            .name("ResilientUpdate")
            .primary(
                DynamicBranchingWorkflow.builder()
                    .name("TypeRouter")
                    .selector(_ -> "TYPE_A")
                    .branch("TYPE_A", new MockTask("FastPath"))
                    .defaultBranch(new MockTask("SlowPath"))
                    .build())
            .fallback(new MockTask("LogFailure"))
            .build();

    Workflow forEach =
        ForEachWorkflow.builder()
            .name("BatchProcessor")
            .itemsKey("payloads")
            .itemVariable("payload")
            .workflow(itemLogic)
            .build();

    assertEquals(
        """
                └── BatchProcessor [ForEach]
                    └── FOR EACH (payload IN payloads) -> ResilientUpdate [Fallback]
                        ├── TRY (Primary) -> TypeRouter [Switch]
                        │   ├── CASE "TYPE_A" -> FastPath (Task)
                        │   └── DEFAULT -> SlowPath (Task)
                        └── ON FAILURE -> LogFailure (Task)
                """,
        forEach.toTreeString());
  }

  @Test
  @DisplayName("ForEach 5: Sequence containing a Loop followed by a Task")
  void testSequenceWithLoop() {
    Workflow loop =
        ForEachWorkflow.builder()
            .name("CleanTempFiles")
            .itemsKey("oldFiles")
            .itemVariable("f")
            .workflow(new MockTask("DeleteFile"))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("CleanupJob")
            .workflow(new MockTask("IdentifyFiles"))
            .workflow(loop)
            .workflow(new MockTask("SendReport"))
            .build();

    assertEquals(
        """
                └── CleanupJob [Sequence]
                    ├── IdentifyFiles (Task)
                    ├── CleanTempFiles [ForEach]
                    │   └── FOR EACH (f IN oldFiles) -> DeleteFile (Task)
                    └── SendReport (Task)
                """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Repeat 1: Simple Fixed Iteration")
  void testSimpleRepeat() {
    Workflow task = new MockTask("PingServer");
    Workflow repeat =
        RepeatWorkflow.builder()
            .name("HealthCheckLoop")
            .times(5)
            .indexVariable("retryCount")
            .workflow(task)
            .build();

    assertEquals(
        """
                └── HealthCheckLoop [Repeat]
                    └── REPEAT 5 TIMES (index: retryCount) -> PingServer (Task)
                """,
        repeat.toTreeString());
  }

  @Test
  @DisplayName("Repeat 2: Repeat wrapping a Sequence")
  void testRepeatWithSequence() {
    Workflow seq =
        SequentialWorkflow.builder()
            .name("StepChain")
            .workflow(new MockTask("Auth"))
            .workflow(new MockTask("Fetch"))
            .build();

    Workflow repeat =
        RepeatWorkflow.builder()
            .name("AuthRetry")
            .times(3)
            .workflow(seq) // Uses default index variable "iteration"
            .build();

    assertEquals(
        """
                └── AuthRetry [Repeat]
                    └── REPEAT 3 TIMES (index: iteration) -> StepChain [Sequence]
                        ├── Auth (Task)
                        └── Fetch (Task)
                """,
        repeat.toTreeString());
  }

  @Test
  @DisplayName("Repeat 3: Deeply Nested (Loop inside a Loop)")
  void testNestedRepeat() {
    Workflow leaf = new MockTask("ComputeNode");

    Workflow innerRepeat =
        RepeatWorkflow.builder()
            .name("InnerLoop")
            .times(2)
            .indexVariable("j")
            .workflow(leaf)
            .build();

    Workflow outerRepeat =
        RepeatWorkflow.builder()
            .name("OuterLoop")
            .times(2)
            .indexVariable("i")
            .workflow(innerRepeat)
            .build();

    assertEquals(
        """
                └── OuterLoop [Repeat]
                    └── REPEAT 2 TIMES (index: i) -> InnerLoop [Repeat]
                        └── REPEAT 2 TIMES (index: j) -> ComputeNode (Task)
                """,
        outerRepeat.toTreeString());
  }

  @Test
  @DisplayName("Conditional 1: Standard If-Else")
  void testStandardConditional() {
    Workflow conditional =
        ConditionalWorkflow.builder()
            .name("IsAdmin?")
            .condition(_ -> true)
            .whenTrue(new MockTask("GrantAccess"))
            .whenFalse(new MockTask("DenyAccess"))
            .build();

    assertEquals(
        """
                └── IsAdmin? [Conditional]
                    ├── When True -> GrantAccess (Task)
                    └── When False -> DenyAccess (Task)
                """,
        conditional.toTreeString());
  }

  @Test
  @DisplayName("Conditional 2: If-Only (No False Branch provided)")
  void testConditionalNoFalse() {
    // If no whenFalse is provided, it should default to a NoOp
    Workflow conditional =
        ConditionalWorkflow.builder()
            .name("SendWelcomeEmail?")
            .condition(_ -> false)
            .whenTrue(new MockTask("SendEmail"))
            .build();

    assertEquals(
        """
                └── SendWelcomeEmail? [Conditional]
                    └── When True -> SendEmail (Task)
                """,
        conditional.toTreeString());
  }

  @Test
  @DisplayName("Conditional 3: Nested Conditions")
  void testNestedConditional() {
    Workflow nested =
        ConditionalWorkflow.builder()
            .name("TierCheck")
            .condition(_ -> true)
            .whenTrue(new MockTask("PremiumLogic"))
            .whenFalse(new MockTask("BasicLogic"))
            .build();

    Workflow root =
        ConditionalWorkflow.builder()
            .name("UserCheck")
            .condition(_ -> true)
            .whenTrue(nested)
            .whenFalse(new MockTask("GuestLogic"))
            .build();

    assertEquals(
        """
                └── UserCheck [Conditional]
                    ├── When True -> TierCheck [Conditional]
                    │   ├── When True -> PremiumLogic (Task)
                    │   └── When False -> BasicLogic (Task)
                    └── When False -> GuestLogic (Task)
                """,
        root.toTreeString());
  }

  @Test
  @DisplayName("Saga 1: Simple Distributed Transaction")
  void testSimpleSaga() {
    Workflow saga =
        SagaWorkflow.builder()
            .name("OrderSaga")
            .step(
                SagaStep.builder()
                    .name("ReserveStock")
                    .action(new MockTask("StockAction"))
                    .compensation(new MockTask("StockCleanup"))
                    .build())
            .step(
                SagaStep.builder()
                    .name("ChargeCard")
                    .action(new MockTask("PaymentAction"))
                    .compensation(new MockTask("RefundAction"))
                    .build())
            .build();

    assertEquals(
        """
                └── OrderSaga [Saga]
                    ├── STEP 1: ReserveStock
                    │   ├── ACTION -> StockAction (Task)
                    │   └── REVERT -> StockCleanup (Task)
                    └── STEP 2: ChargeCard
                        ├── ACTION -> PaymentAction (Task)
                        └── REVERT -> RefundAction (Task)
                """,
        saga.toTreeString());
  }

  @Test
  @DisplayName("Saga 2: Saga Step with No Compensation")
  void testSagaNoCompensation() {
    Workflow saga =
        SagaWorkflow.builder()
            .name("EmailSaga")
            .step(
                SagaStep.builder()
                    .name("SendEmail")
                    .action(new MockTask("SmtpTask"))
                    // No compensation possible for a send email
                    .build())
            .build();

    assertEquals(
        """
                └── EmailSaga [Saga]
                    └── STEP 1: SendEmail
                        └── ACTION -> SmtpTask (Task)
                """,
        saga.toTreeString());
  }

  @Test
  @DisplayName("Saga 3: Complex Nested Saga Actions")
  void testComplexSagaActions() {
    Workflow complexAction =
        SequentialWorkflow.builder()
            .name("MultiStepAction")
            .workflow(new MockTask("Sub1"))
            .workflow(new MockTask("Sub2"))
            .build();

    Workflow saga =
        SagaWorkflow.builder()
            .name("NestedSaga")
            .step(
                SagaStep.builder()
                    .name("ComplexStep")
                    .action(complexAction)
                    .compensation(new MockTask("SimpleUndo"))
                    .build())
            .build();

    assertEquals(
        """
                └── NestedSaga [Saga]
                    └── STEP 1: ComplexStep
                        ├── ACTION -> MultiStepAction [Sequence]
                        │   ├── Sub1 (Task)
                        │   └── Sub2 (Task)
                        └── REVERT -> SimpleUndo (Task)
                """,
        saga.toTreeString());
  }

  @Test
  @DisplayName("JS 1: Script from File Provider")
  void testJsFileVisualization() {
    // Mocking a provider that points to a specific file
    ScriptProvider fileProvider =
        () ->
            new ScriptProvider.ScriptSource(
                "console.log('hi');",
                java.net.URI.create("file:///deploy/scripts/calculate-tax.js"));

    Workflow jsWorkflow =
        JavascriptWorkflow.builder().name("TaxCalculator").scriptProvider(fileProvider).build();

    assertEquals(
        """
                └── TaxCalculator [JavaScript]
                    └── SRC -> calculate-tax.js (eval)
                """,
        jsWorkflow.toTreeString());
  }

  @Test
  @DisplayName("JS 2: Embedded in a Sequence")
  void testJsInSequence() {
    Workflow js =
        JavascriptWorkflow.builder()
            .name("ApplyDiscount")
            .scriptProvider(() -> new ScriptProvider.ScriptSource("ctx.put('d', 10);", null))
            .build();

    Workflow root =
        SequentialWorkflow.builder()
            .name("OrderPipeline")
            .workflow(new MockTask("LoadOrder"))
            .workflow(js)
            .build();

    assertEquals(
        """
                └── OrderPipeline [Sequence]
                    ├── LoadOrder (Task)
                    └── ApplyDiscount [JavaScript]
                        └── SRC -> inline (eval)
                """,
        root.toTreeString());
  }

  @Test
  @DisplayName("JS 3: Inline Script Visualization")
  void testInlineJsVisualization() {
    // When URI is null, it should display "inline"
    Workflow js =
        JavascriptWorkflow.builder()
            .name("QuickMath")
            .scriptProvider(() -> new ScriptProvider.ScriptSource("ctx.put('result', 1+1);", null))
            .build();

    assertEquals(
        """
                └── QuickMath [JavaScript]
                    └── SRC -> inline (eval)
                """,
        js.toTreeString());
  }

  @Test
  @DisplayName("JS 4: JS inside a ForEach Loop")
  void testJsInsideLoop() {
    Workflow js =
        JavascriptWorkflow.builder()
            .name("ProcessItem")
            .scriptProvider(
                () ->
                    new ScriptProvider.ScriptSource(
                        "console.log(ctx.get('item'));",
                        java.net.URI.create("file:///logic/transform.js")))
            .build();

    Workflow forEach =
        ForEachWorkflow.builder()
            .name("BatchRun")
            .itemsKey("data")
            .itemVariable("item")
            .workflow(js)
            .build();

    assertEquals(
        """
                └── BatchRun [ForEach]
                    └── FOR EACH (item IN data) -> ProcessItem [JavaScript]
                        └── SRC -> transform.js (eval)
                """,
        forEach.toTreeString());
  }

  @Test
  @DisplayName("JS 5: Conditional Branching with JS")
  void testJsConditional() {
    Workflow trueJs =
        JavascriptWorkflow.builder()
            .name("HandleAdmin")
            .scriptProvider(
                () -> new ScriptProvider.ScriptSource("", java.net.URI.create("file:///admin.js")))
            .build();

    Workflow conditional =
        ConditionalWorkflow.builder()
            .name("RoleCheck")
            .condition(_ -> true)
            .whenTrue(trueJs)
            .whenFalse(new MockTask("DefaultAccess"))
            .build();

    assertEquals(
        """
                └── RoleCheck [Conditional]
                    ├── When True -> HandleAdmin [JavaScript]
                    │   └── SRC -> admin.js (eval)
                    └── When False -> DefaultAccess (Task)
                """,
        conditional.toTreeString());
  }

  @Test
  @DisplayName("JS 6: Error Handling Visualization")
  void testJsErrorSource() {
    // A provider that throws an exception during source loading
    ScriptProvider errorProvider =
        () -> {
          throw new RuntimeException("Disk Read Error");
        };

    Workflow js =
        JavascriptWorkflow.builder().name("UnstableScript").scriptProvider(errorProvider).build();

    assertEquals(
        """
                └── UnstableScript [JavaScript]
                    └── SRC -> [Error] (eval)
                """,
        js.toTreeString());
  }

  // --- Helper Mock for Testing ---

  Workflow regionSeq(String region) {
    return SequentialWorkflow.builder()
        .name(region + "-Purge")
        .workflow(new MockTask("Connect"))
        .workflow(new MockTask("ClearCache"))
        .build();
  }

  private static class MockTask extends TaskWorkflow {
    private final String name;

    public MockTask(String name) {
      super(_ -> {});
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
