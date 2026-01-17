package com.workflow.examples;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.TaskWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example: Log Processing and Analytics Pipeline
 *
 * <p>Demonstrates a production-grade log processing workflow with log collection from multiple
 * sources, parallel parsing and normalization, anomaly detection, statistics aggregation, report
 * generation, and archival.
 */
@UtilityClass
@Slf4j
public class LogProcessingExample {

  public static final String ERROR = "ERROR";
  public static final String SOURCE = "source";
  public static final String LEVEL = "level";
  public static final String NORMALIZED = "normalized_";
  public static final String ERROR_RATE = "errorRate";
  public static final String TOTAL_LOGS = "totalLogs";

  static class CollectLogsTask implements Task {
    private final String source;
    private final Random random = new Random();

    CollectLogsTask(String source) {
      this.source = source;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Collecting logs from source: {}", source);

      int logCount = 5000 + (random.nextInt() * 5000);
      List<String> logs = new ArrayList<>();

      for (int i = 0; i < logCount; i++) {
        String level;
        double randomVal = random.nextDouble();

        if (randomVal > 0.8) {
          level = ERROR;
        } else if (randomVal > 0.3) {
          level = "WARN";
        } else {
          level = "INFO";
        }

        String logEntry =
            String.format(
                "[%s] %s - Message %d from %s",
                level, LocalDateTime.now().format(DateTimeFormatter.ISO_TIME), i, source);
        logs.add(logEntry);
      }

      String sourceKey = "logs_" + source.replace("-", "_");
      context.put(sourceKey, logs);
      log.info("Collected {} logs from {}", logCount, source);
    }

    @Override
    public String getName() {
      return "collect-" + source;
    }
  }

  static class ParseAndNormalizeLogsTask implements Task {
    private final String source;

    ParseAndNormalizeLogsTask(String source) {
      this.source = source;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Parsing and normalizing logs from: {}", source);

      String sourceKey = "logs_" + source.replace("-", "_");
      @SuppressWarnings("unchecked")
      List<String> rawLogs = (List<String>) context.get(sourceKey);

      if (rawLogs == null) {
        throw new IllegalStateException("Logs not found for source: " + source);
      }

      List<Map<String, Object>> normalizedLogs = new ArrayList<>();
      for (String logEntry : rawLogs) {
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("timestamp", LocalDateTime.now().toString());
        parsed.put(SOURCE, source);
        parsed.put(LEVEL, extractLevel(logEntry));
        parsed.put("message", logEntry);
        normalizedLogs.add(parsed);
      }

      String normalizedKey = NORMALIZED + source.replace("-", "_");
      context.put(normalizedKey, normalizedLogs);
      log.info("Normalized {} logs from {}", normalizedLogs.size(), source);
    }

    private String extractLevel(String log) {
      if (log.contains("[ERROR]")) return ERROR;
      if (log.contains("[WARN]")) return "WARN";
      return "INFO";
    }

    @Override
    public String getName() {
      return "parse-" + source;
    }
  }

  static class DetectAnomaliesTask implements Task {
    private final String source;

    DetectAnomaliesTask(String source) {
      this.source = source;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Detecting anomalies in logs from: {}", source);

      String normalizedKey = NORMALIZED + source.replace("-", "_");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> logs = (List<Map<String, Object>>) context.get(normalizedKey);

      if (logs == null) {
        throw new IllegalStateException("Normalized logs not found for source: " + source);
      }

      List<Map<String, Object>> anomalies = new ArrayList<>();
      long errorCount = logs.stream().filter(l -> ERROR.equals(l.get(LEVEL))).count();
      double errorRate = (double) errorCount / logs.size();

      if (errorRate > 0.1) {
        Map<String, Object> anomaly = new HashMap<>();
        anomaly.put("type", "HIGH_ERROR_RATE");
        anomaly.put(SOURCE, source);
        anomaly.put(ERROR_RATE, errorRate);
        anomalies.add(anomaly);
      }

      String anomalyKey = "anomalies_" + source.replace("-", "_");
      context.put(anomalyKey, anomalies);
      log.info("Detected {} anomalies in logs from {}", anomalies.size(), source);
    }

    @Override
    public String getName() {
      return "detect-anomalies-" + source;
    }
  }

  static class CalculateStatisticsTask implements Task {
    private final String source;

    CalculateStatisticsTask(String source) {
      this.source = source;
    }

    @Override
    public void execute(WorkflowContext context) {
      log.info("Calculating statistics for logs from: {}", source);

      String normalizedKey = NORMALIZED + source.replace("-", "_");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> logs = (List<Map<String, Object>>) context.get(normalizedKey);

      if (logs == null) {
        throw new IllegalStateException("Logs not found for source: " + source);
      }

      long errorCount = logs.stream().filter(l -> ERROR.equals(l.get(LEVEL))).count();
      long warnCount = logs.stream().filter(l -> "WARN".equals(l.get(LEVEL))).count();

      Map<String, Object> stats = new HashMap<>();
      stats.put(SOURCE, source);
      stats.put(TOTAL_LOGS, logs.size());
      stats.put("errorCount", errorCount);
      stats.put("warnCount", warnCount);
      stats.put(ERROR_RATE, (double) errorCount / logs.size());

      String statsKey = "stats_" + source.replace("-", "_");
      context.put(statsKey, stats);
      log.info("Statistics calculated for {}", source);
    }

    @Override
    public String getName() {
      return "stats-" + source;
    }
  }

  static class AggregateMetricsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("Aggregating metrics from all sources...");

      Map<String, Object> aggregated = new HashMap<>();
      long totalLogs = 0;
      long totalErrors = 0;

      for (String source : new String[] {"api-server", "database", "cache", "queue"}) {
        String statsKey = "stats_" + source.replace("-", "_");
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) context.get(statsKey);

        if (stats != null) {
          totalLogs += (long) stats.get(TOTAL_LOGS);
          totalErrors += (long) stats.get("errorCount");
        }
      }

      aggregated.put(TOTAL_LOGS, totalLogs);
      aggregated.put("totalErrors", totalErrors);
      aggregated.put(ERROR_RATE, totalLogs > 0 ? (double) totalErrors / totalLogs : 0.0);
      aggregated.put("processedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

      context.put("aggregatedMetrics", aggregated);
      log.info("Metrics aggregated. Total logs: {}, Errors: {}", totalLogs, totalErrors);
    }

    @Override
    public String getName() {
      return "aggregate-metrics";
    }
  }

  static class GenerateReportTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("Generating analytics report...");

      @SuppressWarnings("unchecked")
      Map<String, Object> metrics = (Map<String, Object>) context.get("aggregatedMetrics");

      if (metrics == null) {
        throw new IllegalStateException("Aggregated metrics not found");
      }

      Map<String, Object> report = new HashMap<>();
      report.put("reportType", "LOG_ANALYTICS");
      report.put("period", "daily");
      report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
      report.putAll(metrics);

      context.put("analyticsReport", report);
      log.info("Analytics report generated successfully");
    }

    @Override
    public String getName() {
      return "generate-report";
    }
  }

  static void processLogs() {
    log.info("\n=== Log Processing and Analytics Workflow Example ===\n");

    WorkflowContext context = new WorkflowContext();

    List<String> sources = new ArrayList<>();
    sources.add("api-server");
    sources.add("database");
    sources.add("cache");
    sources.add("queue");

    RetryPolicy collectionRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 2;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.constant(300);
          }
        };

    List<com.workflow.Workflow> collectWorkflows = new ArrayList<>();
    for (String source : sources) {
      collectWorkflows.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new CollectLogsTask(source))
                  .retryPolicy(collectionRetry)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(15000))
                  .build()));
    }

    ParallelWorkflow collectLogs =
        ParallelWorkflow.builder()
            .name("collect-logs")
            .workflows(collectWorkflows)
            .shareContext(true)
            .failFast(false)
            .build();

    List<com.workflow.Workflow> parseWorkflows = new ArrayList<>();
    for (String source : sources) {
      parseWorkflows.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new ParseAndNormalizeLogsTask(source))
                  .retryPolicy(RetryPolicy.NONE)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(20000))
                  .build()));
    }

    ParallelWorkflow parseLogs =
        ParallelWorkflow.builder()
            .name("parse-logs")
            .workflows(parseWorkflows)
            .shareContext(true)
            .failFast(false)
            .build();

    List<com.workflow.Workflow> anomalyWorkflows = new ArrayList<>();
    for (String source : sources) {
      anomalyWorkflows.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new DetectAnomaliesTask(source))
                  .retryPolicy(RetryPolicy.NONE)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(15000))
                  .build()));
    }

    ParallelWorkflow detectAnomalies =
        ParallelWorkflow.builder()
            .name("detect-anomalies")
            .workflows(anomalyWorkflows)
            .shareContext(true)
            .failFast(false)
            .build();

    List<com.workflow.Workflow> statsWorkflows = new ArrayList<>();
    for (String source : sources) {
      statsWorkflows.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new CalculateStatisticsTask(source))
                  .retryPolicy(RetryPolicy.NONE)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(15000))
                  .build()));
    }

    ParallelWorkflow calculateStats =
        ParallelWorkflow.builder()
            .name("calc-stats")
            .workflows(statsWorkflows)
            .shareContext(true)
            .failFast(false)
            .build();

    TaskWorkflow aggregateMetrics =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new AggregateMetricsTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow generateReport =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new GenerateReportTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    SequentialWorkflow logProcessing =
        SequentialWorkflow.builder()
            .name("log-processing-pipeline")
            .workflow(collectLogs)
            .workflow(parseLogs)
            .workflow(detectAnomalies)
            .workflow(calculateStats)
            .workflow(aggregateMetrics)
            .workflow(generateReport)
            .build();

    WorkflowResult result = logProcessing.execute(context);

    log.info("\n=== Log Processing Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration());

    @SuppressWarnings("unchecked")
    Map<String, Object> report = (Map<String, Object>) context.get("analyticsReport");
    if (report != null) {
      log.info("Analytics Report:");
      report.forEach((k, v) -> log.info("  {}: {}", k, v));
    }

    if (result.getError() != null) {
      log.error("Error: {}", result.getError().getMessage());
    }
  }

  static void main() {
    processLogs();
  }
}
