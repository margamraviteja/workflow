package com.workflow.examples.patterns;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.task.Task;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates the Fan-Out/Fan-In pattern for parallel processing with aggregation.
 *
 * <p>Fan-Out/Fan-In is a pattern where work is distributed to multiple parallel workers (fan-out),
 * and results are collected and aggregated (fan-in). This is particularly useful for:
 *
 * <ul>
 *   <li>Batch processing large datasets
 *   <li>Parallel API calls with result aggregation
 *   <li>Map-Reduce style operations
 *   <li>Multi-source data enrichment
 * </ul>
 *
 * <p><b>Pattern Structure:</b>
 *
 * <pre>
 *           ┌─ Worker 1 ─┐
 *           ├─ Worker 2 ─┤
 * Input ──► ├─ Worker 3 ─┼──► Aggregator ──► Output
 *           ├─ Worker 4 ─┤
 *           └─ Worker 5 ─┘
 * </pre>
 */
@Slf4j
public class FanOutFanInWorkflowExample {

  public static final String RESULTS = "results";

  public static void main(String[] args) {
    // Example 1: Parallel data processing
    log.info("=== Example 1: Batch Image Processing ===\n");
    runImageProcessingExample();

    // Example 2: Multi-source data aggregation
    log.info("\n=== Example 2: Multi-Source Data Aggregation ===\n");
    runDataAggregationExample();
  }

  private static void runImageProcessingExample() {
    WorkflowContext context = new WorkflowContext();
    List<String> images =
        List.of("image1.jpg", "image2.jpg", "image3.jpg", "image4.jpg", "image5.jpg");
    context.put("images", images);
    context.put(RESULTS, Collections.synchronizedList(new ArrayList<String>()));

    // Fan-Out: Process each image in parallel
    Workflow fanOut =
        ParallelWorkflow.builder()
            .name("FanOut-ProcessImages")
            .shareContext(true) // Share context to collect results
            .failFast(false) // Process all images even if some fail
            .workflow(new TaskWorkflow(new ProcessImageTask("image1.jpg")))
            .workflow(new TaskWorkflow(new ProcessImageTask("image2.jpg")))
            .workflow(new TaskWorkflow(new ProcessImageTask("image3.jpg")))
            .workflow(new TaskWorkflow(new ProcessImageTask("image4.jpg")))
            .workflow(new TaskWorkflow(new ProcessImageTask("image5.jpg")))
            .build();

    // Fan-In: Aggregate results
    Workflow fanIn =
        SequentialWorkflow.builder()
            .name("FanIn-AggregateResults")
            .task(new AggregateImageResultsTask())
            .build();

    // Complete workflow
    Workflow workflow =
        SequentialWorkflow.builder()
            .name("ImageProcessingPipeline")
            .workflow(fanOut)
            .workflow(fanIn)
            .build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == WorkflowStatus.SUCCESS) {
      log.info("\n✅ All images processed successfully");
      log.info("Processed: {} images", context.get("processedCount"));
    }
  }

  private static void runDataAggregationExample() {
    WorkflowContext context = new WorkflowContext();
    context.put("userId", "USER-123");

    // Fan-Out: Fetch data from multiple sources
    Workflow fanOut =
        ParallelWorkflow.builder()
            .name("FanOut-FetchData")
            .shareContext(true)
            .failFast(false)
            .workflow(new TaskWorkflow(new FetchUserProfileTask()))
            .workflow(new TaskWorkflow(new FetchUserOrdersTask()))
            .workflow(new TaskWorkflow(new FetchUserPreferencesTask()))
            .workflow(new TaskWorkflow(new FetchUserActivityTask()))
            .build();

    // Fan-In: Aggregate all user data
    Workflow fanIn =
        SequentialWorkflow.builder()
            .name("FanIn-AggregateUserData")
            .task(new AggregateUserDataTask())
            .build();

    // Complete workflow
    Workflow workflow =
        SequentialWorkflow.builder()
            .name("UserDataPipeline")
            .workflow(fanOut)
            .workflow(fanIn)
            .build();

    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == WorkflowStatus.SUCCESS) {
      log.info("\n✅ User data aggregated successfully");
      Map<?, ?> userData = context.getTyped("aggregatedUserData", Map.class);
      log.info("User Data: {}", userData);
    }
  }

  // ==================== Image Processing Tasks ====================

  static class ProcessImageTask implements Task {
    private final Random random = new Random();
    private final String imageName;

    public ProcessImageTask(String imageName) {
      this.imageName = imageName;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("  [Worker] Processing: {}", imageName);
      // Simulate image processing
      try {
        Thread.sleep(random.nextInt(500) + 100L);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }

      @SuppressWarnings("unchecked")
      List<String> results = (List<String>) context.get(RESULTS);
      results.add(imageName + " (processed)");
      log.info("  [Worker] Completed: {}", imageName);
    }
  }

  static class AggregateImageResultsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("\n→ Aggregating results...");
      List<String> results = context.getTyped(RESULTS, new TypeReference<>() {});
      context.put("processedCount", results.size());
      context.put("processedImages", new ArrayList<>(results));
      log.info("  Aggregated {} results", results.size());
    }
  }

  // ==================== Data Aggregation Tasks ====================

  static class FetchUserProfileTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("  [Source 1] Fetching user profile...");
      Map<String, Object> profile = new HashMap<>();
      profile.put("name", "John Doe");
      profile.put("email", "john@example.com");
      context.put("userProfile", profile);
      log.info("  [Source 1] Profile fetched");
    }
  }

  static class FetchUserOrdersTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("  [Source 2] Fetching user orders...");
      List<String> orders = List.of("ORDER-1", "ORDER-2", "ORDER-3");
      context.put("userOrders", orders);
      log.info("  [Source 2] Orders fetched: {}", orders.size());
    }
  }

  static class FetchUserPreferencesTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("  [Source 3] Fetching user preferences...");
      Map<String, Object> prefs = new HashMap<>();
      prefs.put("theme", "dark");
      prefs.put("notifications", true);
      context.put("userPreferences", prefs);
      log.info("  [Source 3] Preferences fetched");
    }
  }

  static class FetchUserActivityTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("  [Source 4] Fetching user activity...");
      Map<String, Object> activity = new HashMap<>();
      activity.put("lastLogin", "2026-01-01");
      activity.put("loginCount", 42);
      context.put("userActivity", activity);
      log.info("  [Source 4] Activity fetched");
    }
  }

  static class AggregateUserDataTask implements Task {
    @Override
    @SuppressWarnings("unchecked")
    public void execute(WorkflowContext context) {
      log.info("\n→ Aggregating user data from multiple sources...");

      Map<String, Object> aggregated = new HashMap<>();

      // Aggregate profile
      Map<String, Object> profile = (Map<String, Object>) context.get("userProfile");
      if (profile != null) {
        aggregated.putAll(profile);
      }

      // Aggregate orders
      List<String> orders = (List<String>) context.get("userOrders");
      if (orders != null) {
        aggregated.put("orders", orders);
        aggregated.put("orderCount", orders.size());
      }

      // Aggregate preferences
      Map<String, Object> prefs = (Map<String, Object>) context.get("userPreferences");
      if (prefs != null) {
        aggregated.put("preferences", prefs);
      }

      // Aggregate activity
      Map<String, Object> activity = (Map<String, Object>) context.get("userActivity");
      if (activity != null) {
        aggregated.put("activity", activity);
      }

      context.put("aggregatedUserData", aggregated);
      log.info("  Aggregated data from " + 4 + " sources");
    }
  }
}
