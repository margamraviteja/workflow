package com.workflow.examples.javascript;

import com.workflow.JavascriptWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.script.InlineScriptProvider;
import com.workflow.script.ScriptProvider;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Advanced example demonstrating real-time data processing with JavaScript workflows.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Stream processing with windowing
 *   <li>Anomaly detection algorithms
 *   <li>Statistical analysis (moving average, standard deviation)
 *   <li>Alert generation based on thresholds
 *   <li>Time-series data handling
 * </ul>
 *
 * <p>Use case: A monitoring system that processes metrics in real-time, detects anomalies, and
 * generates alerts.
 */
@UtilityClass
@Slf4j
public class RealTimeDataProcessingExample {

  public static final String TIMESTAMP = "timestamp";
  public static final String VALUE = "value";
  public static final String REGION = "region";
  public static final String US_EAST = "us-east";
  public static final String US_WEST = "us-west";
  public static final String SERVICE = "service";
  public static final String EU_WEST = "eu-west";
  public static final String API = "api";
  public static final String WEB = "web";
  public static final String LATENCY = "latency";
  public static final String STATUS = "status";

  public static void main(String[] args) {
    log.info("=== Real-Time Data Processing Examples ===\n");

    anomalyDetection();
    streamProcessing();
    metricsAggregation();

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: Anomaly detection using statistical methods.
   *
   * <p>Implements Z-score algorithm to detect outliers in metric streams.
   */
  private static void anomalyDetection() {
    log.info("Example 1: Anomaly Detection");

    ScriptProvider anomalyScript =
        new InlineScriptProvider(
            """
            var metrics = ctx.get('metrics');

            // Calculate mean
            var sum = metrics.reduce((acc, m) => acc + m.value, 0);
            var mean = sum / metrics.length;

            // Calculate standard deviation
            var squaredDiffs = metrics.map(m => Math.pow(m.value - mean, 2));
            var variance = squaredDiffs.reduce((acc, val) => acc + val, 0) / metrics.length;
            var stdDev = Math.sqrt(variance);

            // Detect anomalies using Z-score (threshold: 2.5)
            var threshold = 2.5;
            var anomalies = metrics.filter(m => {
                var zScore = Math.abs((m.value - mean) / stdDev);
                return zScore > threshold;
            });

            ctx.put('mean', mean);
            ctx.put('stdDev', stdDev);
            ctx.put('anomalies', anomalies);
            ctx.put('anomalyCount', anomalies.length);
            ctx.put('anomalyRate', anomalies.length / metrics.length);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("AnomalyDetection").scriptProvider(anomalyScript).build();

    // Simulate metric stream with anomalies
    List<Map<String, Object>> metrics =
        List.of(
            Map.of(TIMESTAMP, "2024-01-01T10:00:00Z", VALUE, 100),
            Map.of(TIMESTAMP, "2024-01-01T10:01:00Z", VALUE, 102),
            Map.of(TIMESTAMP, "2024-01-01T10:02:00Z", VALUE, 98),
            Map.of(TIMESTAMP, "2024-01-01T10:03:00Z", VALUE, 500), // Anomaly
            Map.of(TIMESTAMP, "2024-01-01T10:04:00Z", VALUE, 101),
            Map.of(TIMESTAMP, "2024-01-01T10:05:00Z", VALUE, 99),
            Map.of(TIMESTAMP, "2024-01-01T10:06:00Z", VALUE, 5), // Anomaly
            Map.of(TIMESTAMP, "2024-01-01T10:07:00Z", VALUE, 103));

    WorkflowContext context = new WorkflowContext();
    context.put("metrics", metrics);

    workflow.execute(context);

    log.info("Mean: {}", context.get("mean"));
    log.info("Standard Deviation: {}", context.get("stdDev"));
    log.info("Anomalies Found: {}", context.get("anomalyCount"));
    log.info("Anomaly Rate: {}", context.get("anomalyRate"));
    log.info("Anomalies: {}\n", context.get("anomalies"));
  }

  /**
   * Example 2: Stream processing with sliding window aggregation.
   *
   * <p>Processes time-series data using sliding windows to calculate moving averages and trends.
   */
  private static void streamProcessing() {
    log.info("Example 2: Stream Processing with Sliding Windows");

    ScriptProvider streamScript =
        new InlineScriptProvider(
            """
            var events = ctx.get('events');
            var windowSize = ctx.get('windowSize') || 5;

            // Calculate moving averages with sliding window
            var movingAverages = [];
            for (var i = 0; i <= events.length - windowSize; i++) {
                var window = events.slice(i, i + windowSize);
                var avg = window.reduce((sum, e) => sum + e.value, 0) / windowSize;
                var timestamp = window[window.length - 1].timestamp;

                movingAverages.push({
                    timestamp: timestamp,
                    average: avg,
                    windowStart: window[0].timestamp,
                    windowEnd: timestamp
                });
            }

            // Detect trend (increasing/decreasing/stable)
            var trend = 'STABLE';
            if (movingAverages.length >= 2) {
                var recent = movingAverages.slice(-3);
                var increasing = recent.every((val, i, arr) =>
                    i === 0 || val.average > arr[i-1].average
                );
                var decreasing = recent.every((val, i, arr) =>
                    i === 0 || val.average < arr[i-1].average
                );

                if (increasing) trend = 'INCREASING';
                else if (decreasing) trend = 'DECREASING';
            }

            ctx.put('movingAverages', movingAverages);
            ctx.put('trend', trend);
            ctx.put('latestAverage', movingAverages[movingAverages.length - 1].average);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("StreamProcessing").scriptProvider(streamScript).build();

    // Simulate event stream
    List<Map<String, Object>> events =
        List.of(
            Map.of(TIMESTAMP, "10:00", VALUE, 100),
            Map.of(TIMESTAMP, "10:01", VALUE, 110),
            Map.of(TIMESTAMP, "10:02", VALUE, 120),
            Map.of(TIMESTAMP, "10:03", VALUE, 130),
            Map.of(TIMESTAMP, "10:04", VALUE, 140),
            Map.of(TIMESTAMP, "10:05", VALUE, 150),
            Map.of(TIMESTAMP, "10:06", VALUE, 160),
            Map.of(TIMESTAMP, "10:07", VALUE, 170));

    WorkflowContext context = new WorkflowContext();
    context.put("events", events);
    context.put("windowSize", 5);

    workflow.execute(context);

    log.info("Trend: {}", context.get("trend"));
    log.info("Latest Average: {}", context.get("latestAverage"));
    log.info("Moving Averages: {}\n", context.get("movingAverages"));
  }

  /**
   * Example 3: Complex metrics aggregation with multiple dimensions.
   *
   * <p>Aggregates metrics by multiple dimensions (region, service, status) and generates summary
   * statistics.
   */
  private static void metricsAggregation() {
    log.info("Example 3: Multi-Dimensional Metrics Aggregation");

    ScriptProvider aggregationScript =
        new InlineScriptProvider(
            """
            var metrics = ctx.get('metrics');

            // Group by region
            var byRegion = metrics.reduce((acc, m) => {
                if (!acc[m.region]) {
                    acc[m.region] = {
                        count: 0,
                        totalLatency: 0,
                        errors: 0,
                        requests: []
                    };
                }
                acc[m.region].count++;
                acc[m.region].totalLatency += m.latency;
                if (m.status >= 400) acc[m.region].errors++;
                acc[m.region].requests.push(m);
                return acc;
            }, {});

            // Calculate region statistics
            var regionStats = Object.keys(byRegion).map(region => {
                var data = byRegion[region];
                return {
                    region: region,
                    requestCount: data.count,
                    avgLatency: data.totalLatency / data.count,
                    errorRate: data.errors / data.count,
                    p95Latency: calculatePercentile(data.requests.map(r => r.latency), 95),
                    p99Latency: calculatePercentile(data.requests.map(r => r.latency), 99)
                };
            });

            // Group by service
            var byService = metrics.reduce((acc, m) => {
                if (!acc[m.service]) {
                    acc[m.service] = { count: 0, errors: 0 };
                }
                acc[m.service].count++;
                if (m.status >= 400) acc[m.service].errors++;
                return acc;
            }, {});

            // Calculate overall statistics
            var totalRequests = metrics.length;
            var totalErrors = metrics.filter(m => m.status >= 400).length;
            var avgLatency = metrics.reduce((sum, m) => sum + m.latency, 0) / totalRequests;

            function calculatePercentile(values, percentile) {
                var sorted = values.sort((a, b) => a - b);
                var index = Math.ceil(sorted.length * percentile / 100) - 1;
                return sorted[index];
            }

            ctx.put('regionStats', regionStats);
            ctx.put('serviceStats', byService);
            ctx.put('overallErrorRate', totalErrors / totalRequests);
            ctx.put('overallAvgLatency', avgLatency);
            ctx.put('totalRequests', totalRequests);
            """);

    ScriptProvider alertScript =
        new InlineScriptProvider(
            """
            var regionStats = ctx.get('regionStats');
            var errorThreshold = 0.05; // 5% error rate
            var latencyThreshold = 200; // 200ms

            var alerts = regionStats
                .filter(stat =>
                    stat.errorRate > errorThreshold ||
                    stat.avgLatency > latencyThreshold
                )
                .map(stat => ({
                    region: stat.region,
                    severity: stat.errorRate > errorThreshold * 2 ? 'CRITICAL' : 'WARNING',
                    issues: [
                        stat.errorRate > errorThreshold ?
                            `High error rate: ${(stat.errorRate * 100).toFixed(2)}%` : null,
                        stat.avgLatency > latencyThreshold ?
                            `High latency: ${stat.avgLatency.toFixed(2)}ms` : null
                    ].filter(x => x != null)
                }));

            ctx.put('alerts', alerts);
            ctx.put('alertCount', alerts.length);
            """);

    Workflow metricsWorkflow =
        SequentialWorkflow.builder()
            .name("MetricsAggregation")
            .workflow(
                JavascriptWorkflow.builder()
                    .name("AggregateMetrics")
                    .scriptProvider(aggregationScript)
                    .build())
            .workflow(
                JavascriptWorkflow.builder()
                    .name("GenerateAlerts")
                    .scriptProvider(alertScript)
                    .build())
            .build();

    // Simulate metrics data
    List<Map<String, Object>> metrics =
        List.of(
            Map.of(REGION, US_EAST, SERVICE, API, LATENCY, 150, STATUS, 200),
            Map.of(REGION, US_EAST, SERVICE, API, LATENCY, 180, STATUS, 200),
            Map.of(REGION, US_EAST, SERVICE, WEB, LATENCY, 250, STATUS, 500),
            Map.of(REGION, US_WEST, SERVICE, API, LATENCY, 120, STATUS, 200),
            Map.of(REGION, US_WEST, SERVICE, API, LATENCY, 300, STATUS, 500),
            Map.of(REGION, EU_WEST, SERVICE, WEB, LATENCY, 90, STATUS, 200),
            Map.of(REGION, EU_WEST, SERVICE, API, LATENCY, 100, STATUS, 200));

    WorkflowContext context = new WorkflowContext();
    context.put("metrics", metrics);

    WorkflowResult result = metricsWorkflow.execute(context);

    log.info("Workflow Status: {}", result.getStatus());
    log.info("Total Requests: {}", context.get("totalRequests"));
    log.info("Overall Error Rate: {}", context.get("overallErrorRate"));
    log.info("Overall Avg Latency: {}", context.get("overallAvgLatency"));
    log.info("\nRegion Statistics:");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> regionStats = (List<Map<String, Object>>) context.get("regionStats");
    regionStats.forEach(stat -> log.info("  {}", stat));

    log.info("\nAlerts Generated: {}", context.get("alertCount"));
    log.info("Alerts: {}\n", context.get("alerts"));
  }
}
