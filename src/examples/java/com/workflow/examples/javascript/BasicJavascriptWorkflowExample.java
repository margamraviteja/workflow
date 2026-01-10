package com.workflow.examples.javascript;

import com.workflow.JavascriptWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.script.InlineScriptProvider;
import com.workflow.script.ScriptProvider;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic examples demonstrating JavascriptWorkflow capabilities.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Inline script execution
 *   <li>Simple calculations and data manipulation
 *   <li>Conditional logic in JavaScript
 *   <li>Working with context data
 * </ul>
 *
 * <p>Run the examples with {@code main()} method.
 */
@UtilityClass
@Slf4j
public class BasicJavascriptWorkflowExample {

  public static void main(String[] args) {
    log.info("=== Basic Javascript Workflow Examples ===\n");

    simpleCalculation();
    conditionalLogic();
    dataManipulation();
    jsonProcessing();

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: Simple arithmetic calculation.
   *
   * <p>Demonstrates basic context access and result storage.
   */
  private static void simpleCalculation() {
    log.info("Example 1: Simple Calculation");

    // Create inline script provider
    ScriptProvider calculationScript =
        new InlineScriptProvider(
            """
            var price = context.get('price');
            var quantity = context.get('quantity');
            var total = price * quantity;
            context.put('total', total);
            context.put('calculated', true);
            """);

    // Build workflow
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("SimpleCalculation")
            .scriptProvider(calculationScript)
            .build();

    // Setup context
    WorkflowContext context = new WorkflowContext();
    context.put("price", 29.99);
    context.put("quantity", 3);

    // Execute
    WorkflowResult result = workflow.execute(context);

    // Verify results
    logStatus(result);
    log.info("Total: ${}", context.get("total"));
    log.info("Calculated: {}\n", context.get("calculated"));
  }

  private static void logStatus(WorkflowResult result) {
    log.info("Status: {}", result.getStatus());
  }

  /**
   * Example 2: Conditional logic based on business rules.
   *
   * <p>Demonstrates if-else logic and complex decision-making.
   */
  private static void conditionalLogic() {
    log.info("Example 2: Conditional Logic");

    ScriptProvider discountScript =
        new InlineScriptProvider(
            """
            var orderTotal = context.get('orderTotal');
            var customerTier = context.get('customerTier');

            var discountPercent = 0;
            var discountCode = null;

            // Apply tier-based discount
            if (customerTier === 'PLATINUM') {
                discountPercent = 20;
                discountCode = 'PLATINUM20';
            } else if (customerTier === 'GOLD') {
                discountPercent = 15;
                discountCode = 'GOLD15';
            } else if (customerTier === 'SILVER') {
                discountPercent = 10;
                discountCode = 'SILVER10';
            }

            // Additional discount for large orders
            if (orderTotal > 1000) {
                discountPercent += 5;
                discountCode = 'BULK5_' + discountCode;
            }

            var discountAmount = orderTotal * (discountPercent / 100);
            var finalTotal = orderTotal - discountAmount;

            context.put('discountPercent', discountPercent);
            context.put('discountCode', discountCode);
            context.put('discountAmount', discountAmount);
            context.put('finalTotal', finalTotal);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("DiscountCalculation")
            .scriptProvider(discountScript)
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("orderTotal", 1200.0);
    context.put("customerTier", "GOLD");

    WorkflowResult result = workflow.execute(context);

    logStatus(result);
    log.info("Order Total: ${}", context.get("orderTotal"));
    log.info("Customer Tier: {}", context.get("customerTier"));
    log.info("Discount Code: {}", context.get("discountCode"));
    log.info("Discount Percent: {}%", context.get("discountPercent"));
    log.info("Discount Amount: ${}", context.get("discountAmount"));
    log.info("Final Total: ${}\n", context.get("finalTotal"));
  }

  /**
   * Example 3: Data manipulation with arrays and objects.
   *
   * <p>Demonstrates working with complex data structures.
   */
  private static void dataManipulation() {
    log.info("Example 3: Data Manipulation");

    ScriptProvider dataScript =
        new InlineScriptProvider(
            """
            var numbers = context.get('numbers');

            // Calculate statistics
            var sum = numbers.reduce((acc, n) => acc + n, 0);
            var avg = sum / numbers.length;
            var min = Math.min(...numbers);
            var max = Math.max(...numbers);

            // Filter and transform
            var evenNumbers = numbers.filter(n => n % 2 === 0);
            var doubled = numbers.map(n => n * 2);

            context.put('sum', sum);
            context.put('average', avg);
            context.put('min', min);
            context.put('max', max);
            context.put('evenNumbers', evenNumbers);
            context.put('doubled', doubled);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("DataManipulation").scriptProvider(dataScript).build();

    WorkflowContext context = new WorkflowContext();
    context.put("numbers", java.util.List.of(5, 12, 8, 3, 19, 7, 14, 2));

    WorkflowResult result = workflow.execute(context);

    logStatus(result);
    log.info("Original Numbers: {}", context.get("numbers"));
    log.info("Sum: {}", context.get("sum"));
    log.info("Average: {}", context.get("average"));
    log.info("Min: {}, Max: {}", context.get("min"), context.get("max"));
    log.info("Even Numbers: {}", context.get("evenNumbers"));
    log.info("Doubled: {}\n", context.get("doubled"));
  }

  /**
   * Example 4: JSON parsing and manipulation.
   *
   * <p>Demonstrates working with JSON data.
   */
  private static void jsonProcessing() {
    log.info("Example 4: JSON Processing");

    ScriptProvider jsonScript =
        new InlineScriptProvider(
            """
            var jsonString = context.get('jsonData');
            var data = JSON.parse(jsonString);

            // Extract and transform
            var userCount = data.users.length;
            var activeUsers = data.users.filter(u => u.active);
            var userEmails = data.users.map(u => u.email);

            // Build summary
            var summary = {
                totalUsers: userCount,
                activeUsers: activeUsers.length,
                inactiveUsers: userCount - activeUsers.length,
                emails: userEmails
            };

            context.put('summary', summary);
            context.put('activeUserNames', activeUsers.map(u => u.name));
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("JSONProcessing").scriptProvider(jsonScript).build();

    String jsonData =
        """
        {
            "users": [
                {"id": 1, "name": "Alice", "email": "alice@example.com", "active": true},
                {"id": 2, "name": "Bob", "email": "bob@example.com", "active": false},
                {"id": 3, "name": "Charlie", "email": "charlie@example.com", "active": true}
            ]
        }
        """;

    WorkflowContext context = new WorkflowContext();
    context.put("jsonData", jsonData);

    WorkflowResult result = workflow.execute(context);

    logStatus(result);
    log.info("Summary: {}", context.get("summary"));
    log.info("Active Users: {}\n", context.get("activeUserNames"));
  }
}
