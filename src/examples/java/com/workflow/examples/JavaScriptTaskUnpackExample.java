package com.workflow.examples;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.task.JavaScriptTask;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;

/**
 * Comprehensive examples demonstrating the enhanced JavaScriptTask with unpackResult feature.
 *
 * <p>This class showcases various use cases where JavaScript can return multiple values that are
 * automatically unpacked into the workflow context, including:
 *
 * <ul>
 *   <li>Financial calculations returning multiple metrics
 *   <li>Data analysis returning statistics
 *   <li>String processing with multiple transformations
 *   <li>Complex business rules evaluation
 *   <li>API response transformation
 * </ul>
 */
@Slf4j
public class JavaScriptTaskUnpackExample {

  public static final String PRICE = "price";
  public static final String QUANTITY = "quantity";
  public static final String DISCOUNT = "discount";
  public static final String ORDERS = "orders";
  public static final String AMOUNT = "amount";
  public static final String STATUS = "status";
  public static final String PAID = "paid";
  public static final String ITEMS = "items";
  public static final String USER_INPUT = "userInput";
  public static final String CUSTOMER_AGE = "customerAge";
  public static final String ACCOUNT_BALANCE = "accountBalance";
  public static final String CREDIT_SCORE = "creditScore";
  public static final String YEARS_WITH_BANK = "yearsWithBank";
  public static final String API_RESPONSE = "apiResponse";
  public static final String RAW_DATA = "rawData";
  public static final String NUMBERS = "numbers";
  public static final String AVERAGE = "average";
  public static final String COUNT = "count";

  /**
   * Example 1: Financial Calculations
   *
   * <p>Calculate multiple financial metrics from order data in a single JavaScript execution.
   */
  public static void example1FinancialCalculations() {
    log.info("=== Example 1: Financial Calculations ===");

    WorkflowContext context = new WorkflowContext();
    context.put(PRICE, 100.0);
    context.put(QUANTITY, 10);
    context.put(DISCOUNT, 0.15); // 15% discount

    JavaScriptTask calculateFinancials =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  subtotal: price * quantity, "
                    + "  discountAmount: price * quantity * discount, "
                    + "  subtotalAfterDiscount: price * quantity * (1 - discount), "
                    + "  tax: price * quantity * (1 - discount) * 0.08, "
                    + "  total: price * quantity * (1 - discount) * 1.08, "
                    + "  savings: price * quantity * discount "
                    + "})")
            .inputBindings(Map.of(PRICE, PRICE, QUANTITY, QUANTITY, DISCOUNT, DISCOUNT))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    Workflow workflow =
        SequentialWorkflow.builder().name("FinancialCalculation").task(calculateFinancials).build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == com.workflow.WorkflowStatus.SUCCESS) {
      log.info("Order Summary:");
      log.info("  Subtotal: ${}", context.get("subtotal"));
      log.info("  Discount Amount: ${}", context.get("discountAmount"));
      log.info("  Subtotal After Discount: ${}", context.get("subtotalAfterDiscount"));
      log.info("  Tax (8%): ${}", context.get("tax"));
      log.info("  Total: ${}", context.get("total"));
      log.info("  You Saved: ${}\n", context.get("savings"));
    }
  }

  /**
   * Example 2: Data Analysis
   *
   * <p>Analyze a list of orders and return multiple statistical insights.
   */
  public static void example2DataAnalysis() {
    log.info("=== Example 2: Data Analysis ===");

    WorkflowContext context = new WorkflowContext();
    context.put(
        ORDERS,
        List.of(
            Map.of("id", 1, AMOUNT, 100.0, STATUS, PAID, ITEMS, 3),
            Map.of("id", 2, AMOUNT, 250.0, STATUS, PAID, ITEMS, 5),
            Map.of("id", 3, AMOUNT, 75.0, STATUS, "pending", ITEMS, 2),
            Map.of("id", 4, AMOUNT, 180.0, STATUS, PAID, ITEMS, 4),
            Map.of("id", 5, AMOUNT, 50.0, STATUS, "cancelled", ITEMS, 1)));

    JavaScriptTask analyzeOrders =
        JavaScriptTask.builder()
            .scriptText(
                "const paid = orders.filter(o => o.status === 'paid');"
                    + "const pending = orders.filter(o => o.status === 'pending');"
                    + "const cancelled = orders.filter(o => o.status === 'cancelled');"
                    + "const allAmounts = orders.map(o => o.amount);"
                    + "const totalRevenue = paid.reduce((sum, o) => sum + o.amount, 0);"
                    + "({ "
                    + "  totalOrders: orders.length, "
                    + "  paidOrders: paid.length, "
                    + "  pendingOrders: pending.length, "
                    + "  cancelledOrders: cancelled.length, "
                    + "  totalRevenue: totalRevenue, "
                    + "  averageOrderValue: totalRevenue / paid.length, "
                    + "  largestOrder: Math.max(...allAmounts), "
                    + "  smallestOrder: Math.min(...allAmounts), "
                    + "  totalItems: orders.reduce((sum, o) => sum + o.items, 0), "
                    + "  conversionRate: (paid.length / orders.length * 100).toFixed(2) + '%' "
                    + "})")
            .inputBindings(Map.of(ORDERS, ORDERS))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    Workflow workflow =
        SequentialWorkflow.builder().name("OrderAnalysis").task(analyzeOrders).build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == com.workflow.WorkflowStatus.SUCCESS) {
      log.info("Order Analytics:");
      log.info("  Total Orders: {}", context.get("totalOrders"));
      log.info("  Paid Orders: {}", context.get("paidOrders"));
      log.info("  Pending Orders: {}", context.get("pendingOrders"));
      log.info("  Cancelled Orders: {}", context.get("cancelledOrders"));
      log.info("  Total Revenue: ${}", context.get("totalRevenue"));
      log.info("  Average Order Value: ${}", context.get("averageOrderValue"));
      log.info("  Largest Order: ${}", context.get("largestOrder"));
      log.info("  Smallest Order: ${}", context.get("smallestOrder"));
      log.info("  Total Items Sold: {}", context.get("totalItems"));
      log.info("  Conversion Rate: {}\n", context.get("conversionRate"));
    }
  }

  /**
   * Example 3: String Processing
   *
   * <p>Process user input and return multiple transformed versions.
   */
  public static void example3StringProcessing() {
    log.info("=== Example 3: String Processing ===");

    WorkflowContext context = new WorkflowContext();
    context.put(USER_INPUT, "  hello WORLD from JavaScript  ");

    JavaScriptTask processString =
        JavaScriptTask.builder()
            .scriptText(
                "const trimmed = userInput.trim();"
                    + "({ "
                    + "  original: userInput, "
                    + "  trimmed: trimmed, "
                    + "  uppercase: trimmed.toUpperCase(), "
                    + "  lowercase: trimmed.toLowerCase(), "
                    + "  titleCase: trimmed.toLowerCase().replace(/\\b\\w/g, c => c.toUpperCase()), "
                    + "  reversed: trimmed.split('').reverse().join(''), "
                    + "  wordCount: trimmed.split(/\\s+/).length, "
                    + "  charCount: trimmed.length, "
                    + "  slug: trimmed.toLowerCase().replace(/\\s+/g, '-') "
                    + "})")
            .inputBindings(Map.of(USER_INPUT, USER_INPUT))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    Workflow workflow =
        SequentialWorkflow.builder().name("StringProcessing").task(processString).build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == com.workflow.WorkflowStatus.SUCCESS) {
      log.info("String Transformations:");
      log.info("  Original: '{}'", context.get("original"));
      log.info("  Trimmed: '{}'", context.get("trimmed"));
      log.info("  Uppercase: '{}'", context.get("uppercase"));
      log.info("  Lowercase: '{}'", context.get("lowercase"));
      log.info("  Title Case: '{}'", context.get("titleCase"));
      log.info("  Reversed: '{}'", context.get("reversed"));
      log.info("  Word Count: {}", context.get("wordCount"));
      log.info("  Character Count: {}", context.get("charCount"));
      log.info("  Slug: '{}'\n", context.get("slug"));
    }
  }

  /**
   * Example 4: Business Rules Evaluation
   *
   * <p>Evaluate complex business rules and return multiple decision factors.
   */
  public static void example4BusinessRulesEvaluation() {
    log.info("=== Example 4: Business Rules Evaluation ===");

    WorkflowContext context = new WorkflowContext();
    context.put(CUSTOMER_AGE, 28);
    context.put(ACCOUNT_BALANCE, 5000.0);
    context.put(CREDIT_SCORE, 720);
    context.put(YEARS_WITH_BANK, 3);

    JavaScriptTask evaluateRules =
        JavaScriptTask.builder()
            .scriptText(
                "const isAdult = customerAge >= 18;"
                    + "const hasGoodCredit = creditScore >= 700;"
                    + "const hasBalance = accountBalance >= 1000;"
                    + "const isLoyalCustomer = yearsWithBank >= 2;"
                    + "const qualifiesForPremium = hasGoodCredit && hasBalance && isLoyalCustomer;"
                    + "const qualifiesForLoan = isAdult && creditScore >= 650 && accountBalance >= 500;"
                    + "const riskLevel = creditScore >= 750 ? 'low' : creditScore >= 650 ? 'medium' : 'high';"
                    + "const maxLoanAmount = qualifiesForLoan ? accountBalance * 10 : 0;"
                    + "({ "
                    + "  isEligibleCustomer: isAdult, "
                    + "  hasGoodCreditScore: hasGoodCredit, "
                    + "  hasSufficientBalance: hasBalance, "
                    + "  isLoyalCustomer: isLoyalCustomer, "
                    + "  qualifiesForPremiumAccount: qualifiesForPremium, "
                    + "  qualifiesForLoan: qualifiesForLoan, "
                    + "  riskLevel: riskLevel, "
                    + "  maxLoanAmount: maxLoanAmount, "
                    + "  recommendedProducts: qualifiesForPremium ? ['Premium Card', 'Investment Account'] : ['Basic Card'], "
                    + "  discountPercentage: isLoyalCustomer ? 10 : 5 "
                    + "})")
            .inputBindings(
                Map.of(
                    CUSTOMER_AGE, CUSTOMER_AGE,
                    ACCOUNT_BALANCE, ACCOUNT_BALANCE,
                    CREDIT_SCORE, CREDIT_SCORE,
                    YEARS_WITH_BANK, YEARS_WITH_BANK))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    Workflow workflow =
        SequentialWorkflow.builder().name("BusinessRulesEvaluation").task(evaluateRules).build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == com.workflow.WorkflowStatus.SUCCESS) {
      log.info("Business Rules Evaluation:");
      log.info("  Is Eligible Customer: {}", context.get("isEligibleCustomer"));
      log.info("  Has Good Credit Score: {}", context.get("hasGoodCreditScore"));
      log.info("  Has Sufficient Balance: {}", context.get("hasSufficientBalance"));
      log.info("  Is Loyal Customer: {}", context.get("isLoyalCustomer"));
      log.info("  Qualifies for Premium Account: {}", context.get("qualifiesForPremiumAccount"));
      log.info("  Qualifies for Loan: {}", context.get("qualifiesForLoan"));
      log.info("  Risk Level: {}", context.get("riskLevel"));
      log.info("  Max Loan Amount: ${}", context.get("maxLoanAmount"));
      log.info("  Recommended Products: {}", context.get("recommendedProducts"));
      log.info("  Discount Percentage: {}%\n", context.get("discountPercentage"));
    }
  }

  /**
   * Example 5: API Response Transformation
   *
   * <p>Transform a complex API response into multiple flattened fields.
   */
  public static void example5ApiResponseTransformation() {
    log.info("=== Example 5: API Response Transformation ===");

    WorkflowContext context = new WorkflowContext();
    context.put(
        API_RESPONSE,
        Map.of(
            "user",
            Map.of("id", 123, "name", "John Doe", "email", "john@example.com"),
            "subscription",
            Map.of("plan", "premium", "startDate", "2024-01-01", "active", true),
            "usage",
            Map.of("apiCalls", 1500, "storage", 2.5, "bandwidth", 45.8)));

    JavaScriptTask transformResponse =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  userId: apiResponse.user.id, "
                    + "  userName: apiResponse.user.name, "
                    + "  userEmail: apiResponse.user.email, "
                    + "  subscriptionPlan: apiResponse.subscription.plan, "
                    + "  subscriptionActive: apiResponse.subscription.active, "
                    + "  subscriptionStart: apiResponse.subscription.startDate, "
                    + "  apiCallsUsed: apiResponse.usage.apiCalls, "
                    + "  storageUsedGB: apiResponse.usage.storage, "
                    + "  bandwidthUsedGB: apiResponse.usage.bandwidth, "
                    + "  isPremium: apiResponse.subscription.plan === 'premium', "
                    + "  isHighUsage: apiResponse.usage.apiCalls > 1000 "
                    + "})")
            .inputBindings(Map.of(API_RESPONSE, API_RESPONSE))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    Workflow workflow =
        SequentialWorkflow.builder().name("ApiTransformation").task(transformResponse).build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == com.workflow.WorkflowStatus.SUCCESS) {
      log.info("Transformed API Response:");
      log.info("  User ID: {}", context.get("userId"));
      log.info("  User Name: {}", context.get("userName"));
      log.info("  User Email: {}", context.get("userEmail"));
      log.info("  Subscription Plan: {}", context.get("subscriptionPlan"));
      log.info("  Subscription Active: {}", context.get("subscriptionActive"));
      log.info("  Subscription Start: {}", context.get("subscriptionStart"));
      log.info("  API Calls Used: {}", context.get("apiCallsUsed"));
      log.info("  Storage Used: {} GB", context.get("storageUsedGB"));
      log.info("  Bandwidth Used: {} GB", context.get("bandwidthUsedGB"));
      log.info("  Is Premium: {}", context.get("isPremium"));
      log.info("  Is High Usage: {}\n", context.get("isHighUsage"));
    }
  }

  /**
   * Example 6: Multi-Step Data Pipeline
   *
   * <p>Chain multiple JavaScript tasks, each unpacking results for the next step.
   */
  public static void example6MultiStepPipeline() {
    log.info("=== Example 6: Multi-Step Data Pipeline ===");

    WorkflowContext context = new WorkflowContext();
    context.put(RAW_DATA, "100,200,150,175,225,190");

    // Step 1: Parse and calculate basic stats
    JavaScriptTask parseAndCalculate =
        JavaScriptTask.builder()
            .scriptText(
                "const numbers = rawData.split(',').map(Number);"
                    + "const sum = numbers.reduce((a, b) => a + b, 0);"
                    + "({ "
                    + "  numbers: numbers, "
                    + "  sum: sum, "
                    + "  count: numbers.length, "
                    + "  average: sum / numbers.length "
                    + "})")
            .inputBindings(Map.of(RAW_DATA, RAW_DATA))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    // Step 2: Calculate advanced statistics using previous results
    JavaScriptTask calculateAdvancedStats =
        JavaScriptTask.builder()
            .scriptText(
                "const sorted = [...numbers].sort((a, b) => a - b);"
                    + "const median = sorted.length % 2 === 0 "
                    + "  ? (sorted[sorted.length/2 - 1] + sorted[sorted.length/2]) / 2 "
                    + "  : sorted[Math.floor(sorted.length/2)];"
                    + "const variance = numbers.reduce((acc, val) => acc + Math.pow(val - average, 2), 0) / count;"
                    + "({ "
                    + "  min: Math.min(...numbers), "
                    + "  max: Math.max(...numbers), "
                    + "  median: median, "
                    + "  variance: variance, "
                    + "  stdDev: Math.sqrt(variance), "
                    + "  range: Math.max(...numbers) - Math.min(...numbers) "
                    + "})")
            .inputBindings(Map.of(NUMBERS, NUMBERS, AVERAGE, AVERAGE, COUNT, COUNT))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    Workflow workflow =
        SequentialWorkflow.builder()
            .name("DataPipeline")
            .task(parseAndCalculate)
            .task(calculateAdvancedStats)
            .build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == com.workflow.WorkflowStatus.SUCCESS) {
      log.info("Statistical Analysis:");
      log.info("  Numbers: {}", context.get(NUMBERS));
      log.info("  Count: {}", context.get(COUNT));
      log.info("  Sum: {}", context.get("sum"));
      log.info("  Average: {}", context.get(AVERAGE));
      log.info("  Min: {}", context.get("min"));
      log.info("  Max: {}", context.get("max"));
      log.info("  Median: {}", context.get("median"));
      log.info("  Variance: {}", context.get("variance"));
      log.info("  Standard Deviation: {}", context.get("stdDev"));
      log.info("  Range: {}\n", context.get("range"));
    }
  }

  /**
   * Example 7: Comparison with Single Output Mode
   *
   * <p>Shows the difference between unpackResult=true and traditional single output.
   */
  public static void example7ComparisonWithSingleOutput() {
    log.info("=== Example 7: Comparison - Single Output vs Unpack ===");

    WorkflowContext context = new WorkflowContext();
    context.put("x", 10);
    context.put("y", 20);

    // Traditional approach: single output key
    log.info("Traditional Approach (Single Output Key):");
    JavaScriptTask singleOutput =
        JavaScriptTask.builder()
            .scriptText("({ sum: x + y, product: x * y, difference: y - x })")
            .inputBindings(Map.of("x", "x", "y", "y"))
            .outputKey("calculations")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    singleOutput.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> calculations = (Map<String, Object>) context.get("calculations");
    log.info("  Result at 'calculations': {}", calculations);
    log.info("  Access sum: calculations.get('sum') = {}", calculations.get("sum"));
    log.info("  Access product: calculations.get('product') = {}\n", calculations.get("product"));

    // New approach: unpack results
    WorkflowContext context2 = new WorkflowContext();
    context2.put("x", 10);
    context2.put("y", 20);

    log.info("New Approach (Unpack Results):");
    JavaScriptTask unpackOutput =
        JavaScriptTask.builder()
            .scriptText("({ sum: x + y, product: x * y, difference: y - x })")
            .inputBindings(Map.of("x", "x", "y", "y"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    unpackOutput.execute(context2);

    log.info("  Direct access - sum: {}", context2.get("sum"));
    log.info("  Direct access - product: {}", context2.get("product"));
    log.info("  Direct access - difference: {}", context2.get("difference"));
    log.info("  Much cleaner! All values are top-level context keys.\n");
  }

  public static void main(String[] args) {
    try {
      example1FinancialCalculations();
      example2DataAnalysis();
      example3StringProcessing();
      example4BusinessRulesEvaluation();
      example5ApiResponseTransformation();
      example6MultiStepPipeline();
      example7ComparisonWithSingleOutput();

      log.info("=== Summary ===");
      log.info(
          "unpackResult=true: JavaScript returns an object, all properties become context entries");
      log.info(
          "unpackResult=false (default): JavaScript result stored at single outputKey location");
      log.info("Use unpackResult when you need multiple computed values from one script!");
    } catch (Exception e) {
      log.error("Error in examples", e);
    }
  }
}
