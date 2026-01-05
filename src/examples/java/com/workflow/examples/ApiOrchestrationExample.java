package com.workflow.examples;

import com.workflow.FallbackWorkflow;
import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.TaskWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example: API Request Orchestration and Aggregation
 *
 * <p>Demonstrates sophisticated API orchestration with multiple independent API calls, circuit
 * breaker handling, fallback mechanisms, response aggregation, conditional API chaining, and rate
 * limiting with retry strategies.
 */
@Slf4j
@UtilityClass
public class ApiOrchestrationExample {

  public static final String USER_ID = "userId";
  public static final String PREMIUM = "premium";
  public static final String USER_DATA = "userData";
  public static final String PRODUCT_ID = "productId";
  public static final String RECOMMENDATION_DATA = "recommendationData";

  static class CallUserServiceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String userId = (String) context.get(USER_ID);
      log.info("Calling User Service API for user: {}", userId);

      boolean success = Math.random() > 0.1;

      if (!success) {
        throw new IllegalStateException("User Service API call failed");
      }

      Map<String, Object> userData = new HashMap<>();
      userData.put("id", userId);
      userData.put("name", "John Doe");
      userData.put("email", "john@example.com");
      userData.put("tier", PREMIUM);

      context.put(USER_DATA, userData);
      log.info("User Service API call successful");
    }

    @Override
    public String getName() {
      return "call-user-service";
    }
  }

  static class CallProductServiceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String productId = (String) context.get(PRODUCT_ID);
      log.info("Calling Product Service API for product: {}", productId);

      boolean success = Math.random() > 0.15;

      if (!success) {
        throw new IllegalStateException("Product Service API call failed");
      }

      Map<String, Object> productData = new HashMap<>();
      productData.put("id", productId);
      productData.put("name", "Premium Widget");
      productData.put("price", 99.99);
      productData.put("availability", "in-stock");

      context.put("productData", productData);
      log.info("Product Service API call successful");
    }

    @Override
    public String getName() {
      return "call-product-service";
    }
  }

  static class CallInventoryServiceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String productId = (String) context.get(PRODUCT_ID);
      log.info("Calling Inventory Service API for product: {}", productId);

      boolean success = Math.random() > 0.08;

      if (!success) {
        throw new IllegalStateException("Inventory Service API call failed");
      }

      Map<String, Object> inventoryData = new HashMap<>();
      inventoryData.put(PRODUCT_ID, productId);
      inventoryData.put("quantity", 50);
      inventoryData.put("warehouse", "US-East-1");

      context.put("inventoryData", inventoryData);
      log.info("Inventory Service API call successful");
    }

    @Override
    public String getName() {
      return "call-inventory-service";
    }
  }

  static class CallRecommendationServiceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String userId = (String) context.get(USER_ID);
      log.info("Calling Recommendation Service API for user: {}", userId);

      boolean success = Math.random() > 0.2;

      if (!success) {
        throw new IllegalStateException("Recommendation Service API call failed");
      }

      Map<String, Object> recommendations = new HashMap<>();
      recommendations.put(USER_ID, userId);
      recommendations.put("recommended1", "PROD-456");
      recommendations.put("recommended2", "PROD-789");
      recommendations.put("confidence", 0.92);

      context.put(RECOMMENDATION_DATA, recommendations);
      log.info("Recommendation Service API call successful");
    }

    @Override
    public String getName() {
      return "call-recommendation-service";
    }
  }

  static class CallPricingServiceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String productId = (String) context.get(PRODUCT_ID);
      @SuppressWarnings("unchecked")
      Map<String, Object> userData = (Map<String, Object>) context.get(USER_DATA);
      String tier = userData != null ? (String) userData.get("tier") : "standard";

      log.info("Calling Pricing Service API for product: {} with tier: {}", productId, tier);

      boolean success = Math.random() > 0.05;

      if (!success) {
        throw new IllegalStateException("Pricing Service API call failed");
      }

      double basePrice = 99.99;
      Map<String, Object> pricingData = new HashMap<>();
      pricingData.put(PRODUCT_ID, productId);
      pricingData.put("basePrice", basePrice);
      pricingData.put("discount", PREMIUM.equals(tier) ? 0.15 : 0.0);
      pricingData.put("finalPrice", basePrice * (1 - (PREMIUM.equals(tier) ? 0.15 : 0.0)));

      context.put("pricingData", pricingData);
      log.info("Pricing Service API call successful");
    }

    @Override
    public String getName() {
      return "call-pricing-service";
    }
  }

  static class AggregateResultsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("Aggregating API responses...");

      @SuppressWarnings("unchecked")
      Map<String, Object> userData = (Map<String, Object>) context.get(USER_DATA);
      @SuppressWarnings("unchecked")
      Map<String, Object> productData = (Map<String, Object>) context.get("productData");
      @SuppressWarnings("unchecked")
      Map<String, Object> inventoryData = (Map<String, Object>) context.get("inventoryData");
      @SuppressWarnings("unchecked")
      Map<String, Object> pricingData = (Map<String, Object>) context.get("pricingData");
      @SuppressWarnings("unchecked")
      Map<String, Object> recommendationData =
          (Map<String, Object>) context.get(RECOMMENDATION_DATA);

      Map<String, Object> aggregatedResponse = new HashMap<>();

      if (userData != null) aggregatedResponse.put("user", userData);
      if (productData != null) aggregatedResponse.put("product", productData);
      if (inventoryData != null) aggregatedResponse.put("inventory", inventoryData);
      if (pricingData != null) aggregatedResponse.put("pricing", pricingData);
      if (recommendationData != null) aggregatedResponse.put("recommendations", recommendationData);

      context.put("aggregatedResponse", aggregatedResponse);
      log.info("API responses aggregated successfully");
    }

    @Override
    public String getName() {
      return "aggregate-results";
    }
  }

  static void orchestrateApis() {
    log.info("\n=== API Orchestration Workflow Example ===\n");

    WorkflowContext context = new WorkflowContext();
    context.put(USER_ID, "USR-12345");
    context.put(PRODUCT_ID, "PROD-789");

    RetryPolicy apiRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 3;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.exponential(100);
          }
        };

    TaskWorkflow userServiceCall =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CallUserServiceTask())
                .retryPolicy(apiRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow productServiceCall =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CallProductServiceTask())
                .retryPolicy(apiRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow inventoryServiceCall =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CallInventoryServiceTask())
                .retryPolicy(apiRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow pricingServiceCall =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CallPricingServiceTask())
                .retryPolicy(apiRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    ParallelWorkflow parallelProductApis =
        ParallelWorkflow.builder()
            .name("parallel-product-apis")
            .workflow(productServiceCall)
            .workflow(inventoryServiceCall)
            .workflow(pricingServiceCall)
            .shareContext(true)
            .failFast(false)
            .build();

    TaskWorkflow recommendationServiceCall =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CallRecommendationServiceTask())
                .retryPolicy(apiRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow emptyRecommendationsFallback =
        new TaskWorkflow(
            ctx -> {
              log.warn("Using empty recommendations as fallback");
              Map<String, Object> emptyRecommendations = new HashMap<>();
              emptyRecommendations.put(USER_ID, ctx.get(USER_ID));
              emptyRecommendations.put("confidence", 0.0);
              ctx.put(RECOMMENDATION_DATA, emptyRecommendations);
            });

    FallbackWorkflow recommendationWithFallback =
        FallbackWorkflow.builder()
            .name("recommendation-with-fallback")
            .primary(recommendationServiceCall)
            .fallback(emptyRecommendationsFallback)
            .build();

    TaskWorkflow aggregateResults =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new AggregateResultsTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    SequentialWorkflow apiOrchestration =
        SequentialWorkflow.builder()
            .name("api-orchestration-pipeline")
            .workflow(userServiceCall)
            .workflow(parallelProductApis)
            .workflow(recommendationWithFallback)
            .workflow(aggregateResults)
            .build();

    WorkflowResult result = apiOrchestration.execute(context);

    log.info("\n=== API Orchestration Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration());

    @SuppressWarnings("unchecked")
    Map<String, Object> aggregatedResponse =
        (Map<String, Object>) context.get("aggregatedResponse");
    if (aggregatedResponse != null) {
      log.info("Aggregated Response:");
      aggregatedResponse.forEach(
          (key, value) -> {
            if (value instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> map = (Map<String, Object>) value;
              log.info("  {}:", key);
              map.forEach((k, v) -> log.info("    {}: {}", k, v));
            } else {
              log.info("  {}: {}", key, value);
            }
          });
    }

    if (result.getError() != null) {
      log.error("Error: {}", result.getError().getMessage());
    }
  }

  public static void main(String[] args) {
    orchestrateApis();
  }
}
