package com.workflow.examples.jdbc;

import com.workflow.context.WorkflowContext;
import com.workflow.task.JdbcStreamingQueryTask;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Comprehensive examples demonstrating JdbcStreamingQueryTask usage with an in-memory H2 database.
 *
 * <p>JdbcStreamingQueryTask processes large result sets efficiently by streaming rows one at a time
 * rather than loading all results into memory. This is ideal for:
 *
 * <ul>
 *   <li>Processing millions of rows without OutOfMemoryError
 *   <li>ETL operations and data transformations
 *   <li>Generating reports from large datasets
 *   <li>Real-time data processing and aggregations
 * </ul>
 *
 * <p>Examples from simple to complex:
 *
 * <ul>
 *   <li>Example 1: Simple streaming with callback
 *   <li>Example 2: Streaming with aggregation
 *   <li>Example 3: Streaming with filtering and transformation
 *   <li>Example 4: Large dataset streaming (memory efficiency demo)
 *   <li>Example 5: Dynamic streaming from context
 *   <li>Example 6: Early termination with conditional processing
 * </ul>
 */
@Slf4j
public class JdbcStreamingQueryTaskExample {

  public static final String COMPLETED = "COMPLETED";
  public static final String PENDING = "PENDING";
  public static final String CANCELLED = "CANCELLED";
  public static final String ID = "ID";
  public static final String AMOUNT = "AMOUNT";
  public static final String CATEGORY = "CATEGORY";
  private final DataSource dataSource;

  public JdbcStreamingQueryTaskExample() throws SQLException {
    this.dataSource = createDataSource();
    initializeDatabase();
  }

  /** Creates an H2 in-memory database data source. */
  private DataSource createDataSource() {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:stream_db;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  /** Initializes the database with a large dataset for streaming examples. */
  private void initializeDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      // Create transactions table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS transactions ("
              + "id INT PRIMARY KEY, "
              + "customer_id INT, "
              + "amount DECIMAL(10,2), "
              + "category VARCHAR(50), "
              + "transaction_date DATE, "
              + "status VARCHAR(20)"
              + ")");

      // Create large_dataset table for performance testing
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS large_dataset ("
              + "id INT PRIMARY KEY, "
              + "\"value\" VARCHAR(100), "
              + "score INT"
              + ")");

      // Insert sample transactions
      String[] categories = {"GROCERIES", "ELECTRONICS", "DINING", "TRAVEL", "ENTERTAINMENT"};
      String[] statuses = {COMPLETED, PENDING, CANCELLED};

      for (int i = 1; i <= 1000; i++) {
        String category = categories[i % categories.length];
        String status = statuses[i % statuses.length];
        double amount = 10.0 + (i % 500);
        int customerId = (i % 100) + 1;
        int dayOfMonth = (i % 28) + 1;

        stmt.addBatch(
            String.format(
                "INSERT INTO transactions VALUES (%d, %d, %.2f, '%s', '2024-06-%02d', '%s')",
                i, customerId, amount, category, dayOfMonth, status));
      }
      stmt.executeBatch();

      // Insert large dataset
      int batchSize = 1000;
      for (int i = 1; i <= 10000; i++) {
        stmt.addBatch(
            String.format("INSERT INTO large_dataset VALUES (%d, 'VALUE_%d', %d)", i, i, i % 100));

        // Execute batch every 1000 records to manage memory efficiently
        if (i % batchSize == 0) {
          stmt.executeBatch();
        }
      }
      stmt.executeBatch();

      log.info("Database initialized with sample data");
      log.info("  - 1,000 transactions");
      log.info("  - 10,000 large dataset records");
    } catch (SQLException e) {
      log.error("Failed to initialize database", e);
      throw e;
    }
  }

  /** Example 1: Simple streaming with callback. Demonstrates basic row-by-row processing. */
  public void example1SimpleStreaming() {
    log.info("\n=== Example 1: Simple Streaming ===");

    // Define callback to process each row
    AtomicInteger counter = new AtomicInteger(0);
    Consumer<Map<String, Object>> rowCallback =
        row -> {
          if (counter.incrementAndGet() <= 5) { // Print first 5 rows
            log.info(
                "Transaction: ID={}, Amount=${}, Category={}",
                row.get(ID),
                row.get(AMOUNT),
                row.get(CATEGORY));
          }
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, amount, category FROM transactions WHERE status = ?")
            .params(List.of(COMPLETED))
            .rowCallback(rowCallback)
            .writingRowCountTo("rowCount")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    Long rowCount = (Long) context.get("rowCount");
    log.info("Total rows processed: {}", rowCount);
  }

  /**
   * Example 2: Streaming with aggregation. Demonstrates calculating aggregates while streaming
   * (custom aggregation).
   */
  public void example2StreamingAggregation() {
    log.info("\n=== Example 2: Streaming Aggregation ===");

    // Custom aggregation: calculate totals by category
    class CategoryStats {
      double totalAmount = 0;
      int count = 0;
    }

    Map<String, CategoryStats> categoryTotals = new java.util.HashMap<>();

    Consumer<Map<String, Object>> aggregationCallback =
        row -> {
          String category = (String) row.get(CATEGORY);
          double amount = ((Number) row.get(AMOUNT)).doubleValue();

          categoryTotals.computeIfAbsent(category, _ -> new CategoryStats());
          CategoryStats stats = categoryTotals.get(category);
          stats.totalAmount += amount;
          stats.count++;
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT category, amount FROM transactions WHERE status = ?")
            .params(List.of(COMPLETED))
            .rowCallback(aggregationCallback)
            .writingRowCountTo("totalRows")
            .fetchSize(100) // Optimize fetch size
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    log.info("Category spending summary:");
    categoryTotals.forEach(
        (category, stats) ->
            log.info(
                "  {}: {} transactions, ${} total, ${} average",
                category,
                stats.count,
                String.format("%.2f", stats.totalAmount),
                String.format("%.2f", stats.totalAmount / stats.count)));

    Long totalRows = (Long) context.get("totalRows");
    log.info("Total transactions processed: {}", totalRows);
  }

  /**
   * Example 3: Streaming with filtering and transformation. Demonstrates processing, filtering, and
   * collecting specific rows.
   */
  public void example3FilterAndTransform() {
    log.info("\n=== Example 3: Filter and Transform ===");

    // Collect high-value transactions (amount > 300)
    List<String> highValueTransactionIds = new ArrayList<>();
    AtomicReference<Double> totalHighValue = new AtomicReference<>(0.0);

    Consumer<Map<String, Object>> filterCallback =
        row -> {
          double amount = ((Number) row.get(AMOUNT)).doubleValue();

          if (amount > 300.0) {
            Integer id = (Integer) row.get(ID);
            String category = (String) row.get(CATEGORY);

            highValueTransactionIds.add("TXN-" + id + "-" + category);
            totalHighValue.updateAndGet(current -> current + amount);

            log.info(
                "High value transaction found: ID={}, Amount=${}, Category={}",
                id,
                String.format("%.2f", amount),
                category);
          }
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, amount, category FROM transactions ORDER BY amount DESC")
            .rowCallback(filterCallback)
            .writingRowCountTo("scannedRows")
            .fetchSize(50)
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    log.info("\nSummary:");
    log.info("  Total rows scanned: {}", context.get("scannedRows"));
    log.info("  High value transactions found: {}", highValueTransactionIds.size());
    log.info("  Total high value amount: ${}", String.format("%.2f", totalHighValue.get()));
  }

  /**
   * Example 4: Large dataset streaming (memory efficiency demo). Demonstrates processing large
   * datasets without loading into memory.
   */
  public void example4LargeDatasetStreaming() {
    log.info("\n=== Example 4: Large Dataset Streaming (Memory Efficiency) ===");

    AtomicInteger processedCount = new AtomicInteger(0);
    AtomicInteger highScoreCount = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    Consumer<Map<String, Object>> processCallback =
        row -> {
          int count = processedCount.incrementAndGet();
          int score = (Integer) row.get("SCORE");

          // Some processing logic
          if (score > 50) {
            highScoreCount.incrementAndGet();
          }

          // Log progress every 2000 rows
          if (count % 2000 == 0) {
            log.info("Processed {} rows so far...", count);
          }
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, \"value\", score FROM large_dataset")
            .rowCallback(processCallback)
            .writingRowCountTo("totalProcessed")
            .fetchSize(1000) // Larger fetch size for better performance
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    long endTime = System.currentTimeMillis();
    Long totalProcessed = (Long) context.get("totalProcessed");

    log.info("\nStreaming Performance:");
    log.info("  Total rows processed: {}", totalProcessed);
    log.info("  High score rows: {}", highScoreCount.get());
    log.info("  Time taken: {} ms", endTime - startTime);
    log.info("  Throughput: {} rows/sec", (totalProcessed * 1000.0) / (endTime - startTime));
  }

  /**
   * Example 5: Dynamic streaming from context. Demonstrates reading SQL, parameters, and callback
   * from workflow context.
   */
  public void example5DynamicStreaming() {
    log.info("\n=== Example 5: Dynamic Streaming from Context ===");

    // Prepare callback in context
    List<String> collectedData = new ArrayList<>();
    Consumer<Map<String, Object>> dynamicCallback =
        row -> {
          String category = (String) row.get(CATEGORY);
          double amount = ((Number) row.get(AMOUNT)).doubleValue();
          collectedData.add(category + ":" + String.format("%.2f", amount));
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("querySql")
            .readingParamsFrom("queryParams")
            .readingRowCallbackFrom("callback")
            .writingRowCountTo("count")
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("querySql", "SELECT category, amount FROM transactions WHERE customer_id = ?");
    context.put("queryParams", List.of(5));
    context.put("callback", dynamicCallback);

    task.execute(context);

    log.info("Transactions for customer 5:");
    collectedData.forEach(data -> log.info("  {}", data));
    log.info("Total: {} transactions", context.get("count"));
  }

  private static class EarlyTerminationException extends RuntimeException {
    public EarlyTerminationException(String message) {
      super(message);
    }
  }

  /**
   * Example 6: Early termination with conditional processing. Demonstrates stopping stream
   * processing when a condition is met.
   */
  public void example6EarlyTermination() {
    log.info("\n=== Example 6: Early Termination ===");

    AtomicInteger rowsProcessed = new AtomicInteger(0);
    AtomicReference<String> firstHighValueTxn = new AtomicReference<>(null);

    Consumer<Map<String, Object>> earlyExitCallback =
        row -> {
          rowsProcessed.incrementAndGet();
          double amount = ((Number) row.get(AMOUNT)).doubleValue();

          // Stop processing when we find first transaction over $450
          if (amount > 450.0 && firstHighValueTxn.get() == null) {
            Integer id = (Integer) row.get(ID);
            String category = (String) row.get(CATEGORY);
            firstHighValueTxn.set("ID=" + id + ", Amount=$" + amount + ", Category=" + category);

            log.info("Found target transaction: {}", firstHighValueTxn.get());

            // Throw exception to terminate streaming early
            throw new EarlyTerminationException("Target found - early termination");
          }
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, amount, category FROM transactions ORDER BY amount DESC")
            .rowCallback(earlyExitCallback)
            .writingRowCountTo("rowsBeforeTermination")
            .build();

    WorkflowContext context = new WorkflowContext();

    try {
      task.execute(context);
    } catch (Exception _) {
      log.info("Processing terminated early as expected");
    }

    log.info("Rows processed before termination: {}", rowsProcessed.get());
    log.info("First high-value transaction: {}", firstHighValueTxn.get());
  }

  /**
   * Example 7: Streaming with complex transformations (ETL scenario). Demonstrates real-world
   * ETL-like processing with data transformation.
   */
  public void example7ETLScenario() {
    log.info("\n=== Example 7: ETL Scenario ===");

    // Simulate ETL: Extract, Transform, Load
    class TransformedRecord {
      final String transactionKey;
      final String customerSegment;
      final double normalizedAmount;

      TransformedRecord(String key, String segment, double amount) {
        this.transactionKey = key;
        this.customerSegment = segment;
        this.normalizedAmount = amount;
      }
    }

    List<TransformedRecord> transformedRecords = new ArrayList<>();

    Consumer<Map<String, Object>> etlCallback =
        row -> {
          // Extract
          Integer id = (Integer) row.get(ID);
          Integer customerId = (Integer) row.get("CUSTOMER_ID");
          double amount = ((Number) row.get(AMOUNT)).doubleValue();
          String status = (String) row.get("STATUS");

          // Transform
          if (COMPLETED.equals(status)) {
            String txnKey = String.format("TXN-%05d", id);
            String segment = customerId % 10 == 0 ? "PREMIUM" : "STANDARD";
            double normalized = Math.log(amount + 1) * 100; // Example normalization

            // Load (into list, but could be database, file, etc.)
            transformedRecords.add(new TransformedRecord(txnKey, segment, normalized));
          }
        };

    JdbcStreamingQueryTask task =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, customer_id, amount, status FROM transactions")
            .rowCallback(etlCallback)
            .writingRowCountTo("extractedRows")
            .fetchSize(200)
            .build();

    WorkflowContext context = new WorkflowContext();
    long startTime = System.currentTimeMillis();
    task.execute(context);
    long endTime = System.currentTimeMillis();

    log.info("ETL Processing Complete:");
    log.info("  Rows extracted: {}", context.get("extractedRows"));
    log.info("  Rows transformed & loaded: {}", transformedRecords.size());
    log.info("  Processing time: {} ms", endTime - startTime);

    // Show sample of transformed records
    log.info("\nSample transformed records:");
    transformedRecords.stream()
        .limit(5)
        .forEach(
            transformedRecord ->
                log.info(
                    "  Key: {}, Segment: {}, Normalized: {}",
                    transformedRecord.transactionKey,
                    transformedRecord.customerSegment,
                    String.format("%.2f", transformedRecord.normalizedAmount)));
  }

  /**
   * Bonus: Compare streaming vs. non-streaming memory usage. Demonstrates why streaming is better
   * for large datasets.
   */
  public void exampleBonusMemoryComparison() {
    log.info("\n=== Bonus: Memory Efficiency Comparison ===");

    // Streaming approach - constant memory
    log.info("Processing 10,000 rows with streaming...");
    AtomicInteger streamCount = new AtomicInteger(0);

    long beforeStream = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    Consumer<Map<String, Object>> countCallback = _ -> streamCount.incrementAndGet();

    JdbcStreamingQueryTask streamTask =
        JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM large_dataset")
            .rowCallback(countCallback)
            .fetchSize(500)
            .build();

    streamTask.execute(new WorkflowContext());

    long afterStream = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    log.info("Streaming completed:");
    log.info("  Rows processed: {}", streamCount.get());
    log.info("  Memory delta: ~{} KB", (afterStream - beforeStream) / 1024);
    log.info("  Memory per row: ~{} bytes", (afterStream - beforeStream) / streamCount.get());

    log.info(
        "\nKey benefit: Streaming maintains constant memory usage regardless of result set size!");
    log.info("Perfect for processing millions of rows without OutOfMemoryError.");
  }

  /** Main method to run all examples. */
  public static void main(String[] args) throws SQLException {
    JdbcStreamingQueryTaskExample example = new JdbcStreamingQueryTaskExample();

    example.example1SimpleStreaming();
    example.example2StreamingAggregation();
    example.example3FilterAndTransform();
    example.example4LargeDatasetStreaming();
    example.example5DynamicStreaming();
    example.example6EarlyTermination();
    example.example7ETLScenario();
    example.exampleBonusMemoryComparison();

    log.info("\n=== All examples completed successfully! ===");
  }
}
