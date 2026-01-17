package com.workflow.examples.javascript;

import com.workflow.ConditionalWorkflow;
import com.workflow.DynamicBranchingWorkflow;
import com.workflow.JavascriptWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.script.InlineScriptProvider;
import com.workflow.script.ScriptProvider;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Advanced examples showing JavascriptWorkflow integration with other workflow types.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>JavaScript for conditional logic evaluation
 *   <li>JavaScript for dynamic routing decisions
 *   <li>Complex workflow composition
 *   <li>Multi-tenant workflow patterns
 *   <li>A/B testing logic
 * </ul>
 */
@UtilityClass
@Slf4j
public class AdvancedIntegrationExample {

  public static final String ELIGIBLE_FOR_FAST_TRACK = "eligibleForFastTrack";
  public static final String REASON = "reason";
  public static final String ROUTE_TAKEN = "routeTaken";
  public static final String EXPRESS = "express";
  public static final String LEGAL = "legal";
  public static final String APPROVAL = "approval";
  public static final String FINANCE = "finance";
  public static final String STANDARD = "standard";
  public static final String AMOUNT = "amount";
  public static final String FEATURE_ENABLED = "featureEnabled";
  public static final String PROCESSED_BY = "processedBy";

  static void main() {
    log.info("=== Advanced Javascript Workflow Integration Examples ===\n");

    conditionalWithJavascript();
    dynamicBranchingWithJavascript();
    multiTenantWorkflow();
    abTestingWorkflow();

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: Use JavaScript to evaluate complex conditions.
   *
   * <p>JavaScript handles the condition evaluation, then ConditionalWorkflow routes accordingly.
   */
  private static void conditionalWithJavascript() {
    log.info("Example 1: Conditional Workflow with JavaScript");

    // JavaScript evaluates complex business condition
    ScriptProvider conditionScript =
        new InlineScriptProvider(
            """
            var order = ctx.get('order');

            // Complex eligibility logic
            var isPremium = order.customerTier === 'PREMIUM';
            var isHighValue = order.total > 1000;
            var hasMultipleItems = order.itemCount > 5;
            var isFrequentBuyer = order.customerOrderCount > 10;

            var eligibleForFastTrack =
                (isPremium && isHighValue) ||
                (hasMultipleItems && isFrequentBuyer) ||
                (isHighValue && isFrequentBuyer);

            ctx.put('eligibleForFastTrack', eligibleForFastTrack);
            ctx.put('reason', eligibleForFastTrack ?
                'Customer meets fast-track criteria' :
                'Standard processing required');
            """);

    // Build workflow
    Workflow orderRouting =
        SequentialWorkflow.builder()
            .name("OrderRouting")
            .workflow(
                JavascriptWorkflow.builder()
                    .name("EvaluateEligibility")
                    .scriptProvider(conditionScript)
                    .build())
            .workflow(
                ConditionalWorkflow.builder()
                    .condition(ctx -> ctx.getTyped(ELIGIBLE_FOR_FAST_TRACK, Boolean.class))
                    .whenTrue(createFastTrackWorkflow())
                    .whenFalse(createStandardWorkflow())
                    .build())
            .build();

    // Test with eligible order
    WorkflowContext context1 = new WorkflowContext();
    context1.put(
        "order",
        java.util.Map.of(
            "customerTier", "PREMIUM", "total", 1500.0, "itemCount", 3, "customerOrderCount", 5));

    log.info("Test 1: Premium high-value order");
    orderRouting.execute(context1);
    log.info("  Eligible: {}", context1.get(ELIGIBLE_FOR_FAST_TRACK));
    logReason(context1.get(REASON));
    log.info("  Route: {}\n", context1.get(ROUTE_TAKEN));

    // Test with non-eligible order
    WorkflowContext context2 = new WorkflowContext();
    context2.put(
        "order",
        java.util.Map.of(
            "customerTier", "STANDARD", "total", 200.0, "itemCount", 2, "customerOrderCount", 3));

    log.info("Test 2: Standard order");
    orderRouting.execute(context2);
    log.info("  Eligible: {}", context2.get(ELIGIBLE_FOR_FAST_TRACK));
    logReason(context2.get(REASON));
    log.info("  Route: {}\n", context2.get(ROUTE_TAKEN));
  }

  private static void logReason(Object reason) {
    log.info("  Reason: {}", reason);
  }

  /**
   * Example 2: Use JavaScript for multi-way routing decisions.
   *
   * <p>JavaScript determines which processing pipeline to use based on complex rules.
   */
  private static void dynamicBranchingWithJavascript() {
    log.info("Example 2: Dynamic Branching with JavaScript");

    // JavaScript determines routing
    ScriptProvider routingScript =
        new InlineScriptProvider(
            """
            var request = ctx.get('request');
            var route;

            // Routing logic
            if (request.priority === 'URGENT' && request.amount < 10000) {
                route = 'express';
            } else if (request.documentType === 'CONTRACT') {
                route = 'legal';
            } else if (request.requiresApproval && request.amount > 5000) {
                route = 'approval';
            } else if (request.documentType === 'INVOICE') {
                route = 'finance';
            } else {
                route = 'standard';
            }

            ctx.put('selectedRoute', route);
            ctx.put('routingReason', getRoutingReason(request, route));

            function getRoutingReason(req, route) {
                switch(route) {
                    case 'express': return 'Urgent request under threshold';
                    case 'legal': return 'Contract requires legal review';
                    case 'approval': return 'High-value approval needed';
                    case 'finance': return 'Invoice processing';
                    default: return 'Standard processing';
                }
            }
            """);

    // Build workflow with dynamic branching
    Workflow documentProcessing =
        SequentialWorkflow.builder()
            .name("DocumentProcessing")
            .workflow(
                JavascriptWorkflow.builder()
                    .name("DetermineRoute")
                    .scriptProvider(routingScript)
                    .build())
            .workflow(
                DynamicBranchingWorkflow.builder()
                    .selector(ctx -> ctx.getTyped("selectedRoute", String.class))
                    .branch(EXPRESS, createExpressWorkflow())
                    .branch(LEGAL, createLegalWorkflow())
                    .branch(APPROVAL, createApprovalWorkflow())
                    .branch(FINANCE, createFinanceWorkflow())
                    .branch(STANDARD, createStandardProcessingWorkflow())
                    .build())
            .build();

    // Test different request types
    testRouting(
        documentProcessing, "urgent request", java.util.Map.of("priority", "URGENT", AMOUNT, 5000));
    testRouting(
        documentProcessing, "contract", java.util.Map.of("documentType", "CONTRACT", AMOUNT, 1000));
    testRouting(
        documentProcessing,
        "high-value approval",
        java.util.Map.of("requiresApproval", true, AMOUNT, 7500));
    testRouting(
        documentProcessing, "invoice", java.util.Map.of("documentType", "INVOICE", AMOUNT, 2000));
    testRouting(
        documentProcessing,
        "standard request",
        java.util.Map.of("priority", "NORMAL", AMOUNT, 500));

    log.info("");
  }

  /**
   * Example 3: Multi-tenant workflow with tenant-specific rules.
   *
   * <p>Demonstrates loading different business rules based on tenant context.
   */
  private static void multiTenantWorkflow() {
    log.info("Example 3: Multi-Tenant Workflow");

    // Create tenant-specific workflows
    Workflow processTenant1 =
        createTenantWorkflow(
            "ACME",
            """
        var data = ctx.get('data');
        // ACME Corp specific rules
        var processed = {
            tenant: 'ACME',
            validationLevel: 'STRICT',
            discountRate: 0.15,
            approved: data.value < 50000
        };
        ctx.put('result', processed);
        """);

    Workflow processTenant2 =
        createTenantWorkflow(
            "GLOBEX",
            """
        var data = ctx.get('data');
        // Globex Corp specific rules
        var processed = {
            tenant: 'GLOBEX',
            validationLevel: 'STANDARD',
            discountRate: 0.10,
            approved: data.value < 100000
        };
        ctx.put('result', processed);
        """);

    // Process for different tenants
    WorkflowContext acmeContext = new WorkflowContext();
    acmeContext.put("data", java.util.Map.of("value", 45000));
    processTenant1.execute(acmeContext);
    log.info("ACME Result: {}", acmeContext.get("result"));

    WorkflowContext globexContext = new WorkflowContext();
    globexContext.put("data", java.util.Map.of("value", 75000));
    processTenant2.execute(globexContext);
    log.info("GLOBEX Result: {}\n", globexContext.get("result"));
  }

  /**
   * Example 4: A/B testing with deterministic variant assignment.
   *
   * <p>Uses JavaScript for consistent user bucketing in experiments.
   */
  private static void abTestingWorkflow() {
    log.info("Example 4: A/B Testing Workflow");

    ScriptProvider abTestScript =
        new InlineScriptProvider(
            """
            var userId = ctx.get('userId');
            var experimentId = ctx.get('experimentId');

            // Deterministic hash-based assignment
            function hashCode(str) {
                var hash = 0;
                for (var i = 0; i < str.length; i++) {
                    var char = str.charCodeAt(i);
                    hash = ((hash << 5) - hash) + char;
                    hash = hash & hash;
                }
                return Math.abs(hash);
            }

            var hash = hashCode(userId + experimentId);
            var bucket = hash % 100;

            var variant;
            if (bucket < 50) {
                variant = 'A';  // Control
            } else {
                variant = 'B';  // Treatment
            }

            ctx.put('variant', variant);
            ctx.put('bucket', bucket);
            ctx.put('assignment', {
                userId: userId,
                experimentId: experimentId,
                variant: variant,
                timestamp: new Date().toISOString()
            });
            """);

    Workflow abTestWorkflow =
        SequentialWorkflow.builder()
            .name("ABTestWorkflow")
            .workflow(
                JavascriptWorkflow.builder()
                    .name("AssignVariant")
                    .scriptProvider(abTestScript)
                    .build())
            .workflow(
                ConditionalWorkflow.builder()
                    .condition(ctx -> "A".equals(ctx.get("variant")))
                    .whenTrue(createVariantAWorkflow())
                    .whenFalse(createVariantBWorkflow())
                    .build())
            .build();

    // Test consistent assignment
    String[] userIds = {"user-001", "user-002", "user-003", "user-001"}; // Note: user-001 twice

    for (String userId : userIds) {
      WorkflowContext context = new WorkflowContext();
      context.put("userId", userId);
      context.put("experimentId", "pricing-test-v1");

      abTestWorkflow.execute(context);

      log.info(
          "User {}: Variant {}, Bucket {}, Feature: {}",
          userId,
          context.get("variant"),
          context.get("bucket"),
          context.get(FEATURE_ENABLED));
    }

    log.info("");
  }

  // Helper methods for creating stub workflows

  private static Workflow createFastTrackWorkflow() {
    return new SimpleWorkflow(
        "fast-track",
        ctx -> {
          log.info("  → Fast-track processing");
          ctx.put(ROUTE_TAKEN, "fast-track");
        });
  }

  private static Workflow createStandardWorkflow() {
    return new SimpleWorkflow(
        STANDARD,
        ctx -> {
          log.info("  → Standard processing");
          ctx.put(ROUTE_TAKEN, STANDARD);
        });
  }

  private static Workflow createExpressWorkflow() {
    return new SimpleWorkflow(
        EXPRESS,
        ctx -> {
          log.info("  → Express lane (2-hour SLA)");
          ctx.put(PROCESSED_BY, EXPRESS);
        });
  }

  private static Workflow createLegalWorkflow() {
    return new SimpleWorkflow(
        LEGAL,
        ctx -> {
          log.info("  → Legal review (2-day SLA)");
          ctx.put(PROCESSED_BY, LEGAL);
        });
  }

  private static Workflow createApprovalWorkflow() {
    return new SimpleWorkflow(
        APPROVAL,
        ctx -> {
          log.info("  → Approval workflow (1-day SLA)");
          ctx.put(PROCESSED_BY, APPROVAL);
        });
  }

  private static Workflow createFinanceWorkflow() {
    return new SimpleWorkflow(
        FINANCE,
        ctx -> {
          log.info("  → Finance processing (next business day)");
          ctx.put(PROCESSED_BY, FINANCE);
        });
  }

  private static Workflow createStandardProcessingWorkflow() {
    return new SimpleWorkflow(
        "standard-processing",
        ctx -> {
          log.info("  → Standard processing (5-day SLA)");
          ctx.put(PROCESSED_BY, STANDARD);
        });
  }

  private static Workflow createTenantWorkflow(String tenantId, String script) {
    return JavascriptWorkflow.builder()
        .name("TenantWorkflow-" + tenantId)
        .scriptProvider(new InlineScriptProvider(script))
        .build();
  }

  private static Workflow createVariantAWorkflow() {
    return new SimpleWorkflow(
        "variant-a",
        ctx -> {
          log.info("  → Variant A: Control pricing");
          ctx.put(FEATURE_ENABLED, false);
        });
  }

  private static Workflow createVariantBWorkflow() {
    return new SimpleWorkflow(
        "variant-b",
        ctx -> {
          log.info("  → Variant B: New pricing");
          ctx.put(FEATURE_ENABLED, true);
        });
  }

  private static void testRouting(
      Workflow workflow, String description, java.util.Map<String, Object> request) {
    WorkflowContext context = new WorkflowContext();
    context.put("request", request);

    log.info("Test: {}", description);
    workflow.execute(context);
    log.info("  Route: {}", context.get("selectedRoute"));
    logReason(context.get("routingReason"));
    log.info("  Processed by: {}\n", context.get(PROCESSED_BY));
  }

  // Simple workflow implementation for examples
  static class SimpleWorkflow implements Workflow {
    private final String name;
    private final java.util.function.Consumer<WorkflowContext> action;

    SimpleWorkflow(String name, java.util.function.Consumer<WorkflowContext> action) {
      this.name = name;
      this.action = action;
    }

    @Override
    public WorkflowResult execute(WorkflowContext context) {
      try {
        action.accept(context);
        return WorkflowResult.builder().status(com.workflow.WorkflowStatus.SUCCESS).build();
      } catch (Exception e) {
        return WorkflowResult.builder().status(com.workflow.WorkflowStatus.FAILED).error(e).build();
      }
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
