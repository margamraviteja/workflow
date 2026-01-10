package com.workflow.examples.javascript;

import com.workflow.JavascriptWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.script.FileScriptProvider;
import com.workflow.task.Task;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Advanced example demonstrating dynamic business rules using JavascriptWorkflow.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>File-based script loading
 *   <li>Hot-reloading of business rules
 *   <li>Complex risk assessment logic
 *   <li>Integration with sequential workflows
 *   <li>Real-world order processing scenario
 * </ul>
 *
 * <p>Use case: An e-commerce platform that needs to assess order risk before processing payments.
 * Business rules can be updated without redeploying the application.
 */
@UtilityClass
@Slf4j
public class DynamicBusinessRulesExample {

  public static final String BASE_PRICE = "basePrice";

  public static void main(String[] args) throws IOException {
    log.info("=== Dynamic Business Rules Example ===\n");

    // Create temp directory for scripts
    Path scriptsDir = Files.createTempDirectory("workflow-scripts");
    log.info("Scripts directory: {}\n", scriptsDir);

    try {
      demonstrateRiskAssessment(scriptsDir);
      demonstrateHotReload(scriptsDir);
      demonstrateIntegratedWorkflow(scriptsDir);
    } finally {
      // Cleanup
      try (Stream<Path> pathStream = Files.walk(scriptsDir)) {
        pathStream
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.deleteIfExists(path);
                  } catch (IOException _) {
                    // Ignore
                  }
                });
      }
    }

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: Order risk assessment using JavaScript rules.
   *
   * <p>Demonstrates loading complex business logic from a file.
   */
  private static void demonstrateRiskAssessment(Path scriptsDir) throws IOException {
    log.info("Example 1: Order Risk Assessment");

    // Create risk assessment script
    String riskScript =
        """
        var order = JSON.parse(context.get('orderJson'));
        var riskScore = 0;
        var riskFactors = [];

        // High value order check
        if (order.total > 5000) {
            riskScore += 10;
            riskFactors.push('High value order: $' + order.total);
        }

        // International shipping check
        if (order.shipping.country !== 'US') {
            riskScore += 5;
            riskFactors.push('International shipping to ' + order.shipping.country);
        }

        // New customer check
        if (order.customer.yearsActive < 1) {
            riskScore += 15;
            riskFactors.push('New customer (active for ' + order.customer.yearsActive + ' years)');
        }

        // Multiple payment attempts
        if (order.paymentAttempts > 1) {
            riskScore += 20;
            riskFactors.push('Multiple payment attempts: ' + order.paymentAttempts);
        }

        // Rush shipping
        if (order.shipping.method === 'EXPRESS') {
            riskScore += 3;
            riskFactors.push('Express shipping requested');
        }

        // Different billing and shipping addresses
        if (order.billing.address !== order.shipping.address) {
            riskScore += 8;
            riskFactors.push('Billing and shipping addresses differ');
        }

        // Determine action
        var action;
        if (riskScore > 30) {
            action = 'REJECT';
        } else if (riskScore > 20) {
            action = 'MANUAL_REVIEW';
        } else {
            action = 'AUTO_APPROVE';
        }

        // Store results
        context.put('riskScore', riskScore);
        context.put('riskFactors', riskFactors);
        context.put('action', action);
        context.put('requiresReview', action === 'MANUAL_REVIEW');
        """;

    Path riskScriptPath = scriptsDir.resolve("risk_assessment.js");
    Files.writeString(riskScriptPath, riskScript);

    // Create workflow
    FileScriptProvider provider = new FileScriptProvider(riskScriptPath);
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("OrderRiskAssessment").scriptProvider(provider).build();

    // Test with different orders
    String highRiskOrder =
        """
        {
            "total": 8500,
            "customer": {"yearsActive": 0.2},
            "shipping": {"country": "RU", "method": "EXPRESS", "address": "123 Foreign St"},
            "billing": {"address": "456 Different Rd"},
            "paymentAttempts": 3
        }
        """;
    testOrder(workflow, highRiskOrder);

    String mediumRiskOrder =
        """
        {
            "total": 2500,
            "customer": {"yearsActive": 0.8},
            "shipping": {"country": "CA", "method": "STANDARD", "address": "789 Maple Ave"},
            "billing": {"address": "789 Maple Ave"},
            "paymentAttempts": 1
        }
        """;
    testOrder(workflow, mediumRiskOrder);

    String lowRiskOrder =
        """
        {
            "total": 150,
            "customer": {"yearsActive": 5.0},
            "shipping": {"country": "US", "method": "STANDARD", "address": "321 Oak St"},
            "billing": {"address": "321 Oak St"},
            "paymentAttempts": 1
        }
        """;
    testOrder(workflow, lowRiskOrder);

    log.info("");
  }

  /**
   * Example 2: Demonstrate hot-reloading of business rules.
   *
   * <p>Shows how rules can be updated without application restart.
   */
  private static void demonstrateHotReload(Path scriptsDir) throws IOException {
    log.info("Example 2: Hot Reload of Business Rules");

    Path pricingScriptPath = scriptsDir.resolve("pricing_rules.js");

    // Version 1: Simple pricing
    String pricingV1 =
        """
        var basePrice = context.get('basePrice');
        var finalPrice = basePrice * 1.1; // 10% markup
        context.put('finalPrice', finalPrice);
        context.put('version', 'v1');
        """;

    Files.writeString(pricingScriptPath, pricingV1);

    FileScriptProvider provider = new FileScriptProvider(pricingScriptPath);
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("DynamicPricing").scriptProvider(provider).build();

    // Execute with version 1
    WorkflowContext context1 = new WorkflowContext();
    context1.put(BASE_PRICE, 100.0);
    workflow.execute(context1);

    log.info("Version 1 Results:");
    log.info("  Base Price: ${}", context1.get(BASE_PRICE));
    log.info("  Final Price: ${}", context1.get("finalPrice"));
    log.info("  Version: {}", context1.get("version"));

    // Update to version 2: Complex pricing with tiers
    String pricingV2 =
        """
        var basePrice = context.get('basePrice');
        var customerTier = context.get('customerTier') || 'STANDARD';

        var markup = 1.1; // Default 10%

        // Tier-based pricing
        if (customerTier === 'PLATINUM') {
            markup = 1.05; // 5% markup for platinum
        } else if (customerTier === 'GOLD') {
            markup = 1.07; // 7% markup for gold
        }

        // Volume discount
        if (basePrice > 1000) {
            markup -= 0.02; // 2% off for bulk
        }

        var finalPrice = basePrice * markup;
        context.put('finalPrice', Math.round(finalPrice * 100) / 100);
        context.put('version', 'v2');
        context.put('markup', markup);
        """;

    Files.writeString(pricingScriptPath, pricingV2);

    // Execute with version 2 (same workflow instance, new script)
    WorkflowContext context2 = new WorkflowContext();
    context2.put(BASE_PRICE, 1200.0);
    context2.put("customerTier", "PLATINUM");
    workflow.execute(context2);

    log.info("\nVersion 2 Results (after hot reload):");
    log.info("  Base Price: ${}", context2.get(BASE_PRICE));
    log.info("  Customer Tier: {}", context2.get("customerTier"));
    log.info("  Markup: {}", context2.get("markup"));
    log.info("  Final Price: ${}", context2.get("finalPrice"));
    log.info("  Version: {}\n", context2.get("version"));
  }

  /**
   * Example 3: JavaScript workflow integrated into larger orchestration.
   *
   * <p>Shows how JavascriptWorkflow works alongside other tasks.
   */
  private static void demonstrateIntegratedWorkflow(Path scriptsDir) throws IOException {
    log.info("Example 3: Integrated Order Processing Workflow");

    // Create validation script
    String validationScript =
        """
        var order = context.get('order');
        var errors = [];

        // Validate required fields
        if (!order.customerId) errors.push('Customer ID is required');
        if (!order.items || order.items.length === 0) errors.push('Order must have items');
        if (!order.total || order.total <= 0) errors.push('Total must be positive');

        // Business rule validations
        if (order.total > 10000 && !order.approvedBy) {
            errors.push('Orders over $10,000 require approval');
        }

        var isValid = errors.length === 0;
        context.put('validationErrors', errors);
        context.put('isValid', isValid);

        if (!isValid) {
            throw new Error('Validation failed: ' + errors.join(', '));
        }
        """;

    Path validationScriptPath = scriptsDir.resolve("order_validation.js");
    Files.writeString(validationScriptPath, validationScript);

    // Create calculation script
    String calculationScript =
        """
        var order = context.get('order');

        // Calculate line totals
        var lineTotal = order.items.reduce((sum, item) =>
            sum + (item.price * item.quantity), 0);

        // Calculate tax
        var taxRate = 0.08;
        var tax = lineTotal * taxRate;

        // Calculate shipping
        var shipping = lineTotal > 100 ? 0 : 9.99;

        // Calculate final total
        var total = lineTotal + tax + shipping;

        context.put('lineTotal', Math.round(lineTotal * 100) / 100);
        context.put('tax', Math.round(tax * 100) / 100);
        context.put('shipping', Math.round(shipping * 100) / 100);
        context.put('calculatedTotal', Math.round(total * 100) / 100);
        """;

    Path calculationScriptPath = scriptsDir.resolve("order_calculation.js");
    Files.writeString(calculationScriptPath, calculationScript);

    // Build integrated workflow
    Workflow orderProcessing =
        SequentialWorkflow.builder()
            .name("IntegratedOrderProcessing")
            .task(new LogTask("Starting order processing"))
            .workflow(
                JavascriptWorkflow.builder()
                    .name("ValidateOrder")
                    .scriptProvider(new FileScriptProvider(validationScriptPath))
                    .build())
            .task(new LogTask("Order validation passed"))
            .workflow(
                JavascriptWorkflow.builder()
                    .name("CalculateTotals")
                    .scriptProvider(new FileScriptProvider(calculationScriptPath))
                    .build())
            .task(new LogTask("Order totals calculated"))
            .task(new DisplayResultsTask())
            .build();

    // Execute workflow
    WorkflowContext context = new WorkflowContext();
    context.put(
        "order",
        java.util.Map.of(
            "customerId",
            "CUST-123",
            "items",
            java.util.List.of(
                java.util.Map.of("name", "Widget", "price", 29.99, "quantity", 2),
                java.util.Map.of("name", "Gadget", "price", 49.99, "quantity", 1)),
            "total",
            109.97));

    WorkflowResult result = orderProcessing.execute(context);

    log.info("Workflow Status: {}\n", result.getStatus());
  }

  private static void testOrder(JavascriptWorkflow workflow, String orderJson) {
    WorkflowContext context = new WorkflowContext();
    context.put("orderJson", orderJson);

    WorkflowResult result = workflow.execute(context);

    log.info("Order Assessment:");
    log.info("  Risk Score: {}", context.get("riskScore"));
    log.info("  Action: {}", context.get("action"));
    log.info("  Risk Factors: {}", context.get("riskFactors"));
    log.info("  Status: {}\n", result.getStatus());
  }

  // Helper tasks

  static class LogTask implements Task {
    private final String message;

    LogTask(String message) {
      this.message = message;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Task: {}", message);
    }

    @Override
    public String getName() {
      return "LogTask";
    }
  }

  static class DisplayResultsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("=== Order Processing Results ===");
      log.info("Line Total: ${}", context.get("lineTotal"));
      log.info("Tax: ${}", context.get("tax"));
      log.info("Shipping: ${}", context.get("shipping"));
      log.info("Final Total: ${}", context.get("calculatedTotal"));
    }

    @Override
    public String getName() {
      return "DisplayResults";
    }
  }
}
