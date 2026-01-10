package com.workflow.examples.javascript;

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
 * Examples demonstrating data transformation and ETL-style operations with JavascriptWorkflow.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>JSON data transformation
 *   <li>Data filtering and aggregation
 *   <li>Data enrichment
 *   <li>Multi-step ETL pipelines
 *   <li>Complex data flattening
 * </ul>
 *
 * <p>Use case: Processing API responses, transforming data between systems, ETL operations.
 */
@UtilityClass
@Slf4j
public class DataTransformationExample {

  public static final String STATUS = "status";
  public static final String AMOUNT = "amount";
  public static final String PRICE = "price";
  public static final String INVENTORY = "inventory";

  public static void main(String[] args) {
    log.info("=== Data Transformation Examples ===\n");

    simpleTransformation();
    filteringAndAggregation();
    dataEnrichment();
    complexFlattening();
    etlPipeline();

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: Simple API response transformation.
   *
   * <p>Transform external API format to internal domain model.
   */
  private static void simpleTransformation() {
    log.info("Example 1: Simple Data Transformation");

    ScriptProvider transformScript =
        new InlineScriptProvider(
            """
            var apiResponse = JSON.parse(context.get('apiResponse'));

            // Transform from external API format to internal model
            var transformed = apiResponse.results.map(user => ({
                id: user.userId,
                fullName: user.firstName + ' ' + user.lastName,
                email: user.contactInfo.primaryEmail,
                active: user.status === 'ACTIVE',
                registeredDate: user.metadata.createdAt
            }));

            context.put('users', transformed);
            context.put('userCount', transformed.length);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("TransformAPIResponse")
            .scriptProvider(transformScript)
            .build();

    String apiResponse =
        """
        {
            "results": [
                {
                    "userId": "U001",
                    "firstName": "John",
                    "lastName": "Doe",
                    "contactInfo": {"primaryEmail": "john@example.com"},
                    "status": "ACTIVE",
                    "metadata": {"createdAt": "2024-01-15"}
                },
                {
                    "userId": "U002",
                    "firstName": "Jane",
                    "lastName": "Smith",
                    "contactInfo": {"primaryEmail": "jane@example.com"},
                    "status": "INACTIVE",
                    "metadata": {"createdAt": "2024-02-20"}
                }
            ]
        }
        """;

    WorkflowContext context = new WorkflowContext();
    context.put("apiResponse", apiResponse);

    workflow.execute(context);

    log.info("Transformed {} users", context.get("userCount"));
    log.info("Users: {}\n", context.get("users"));
  }

  /**
   * Example 2: Filtering and aggregation operations.
   *
   * <p>Filter data and compute statistics in one pass.
   */
  private static void filteringAndAggregation() {
    log.info("Example 2: Filtering and Aggregation");

    ScriptProvider filterScript =
        new InlineScriptProvider(
            """
            var orders = context.get('orders');

            // Filter and categorize
            var paid = orders.filter(o => o.status === 'PAID');
            var pending = orders.filter(o => o.status === 'PENDING');
            var cancelled = orders.filter(o => o.status === 'CANCELLED');

            // Calculate statistics
            var stats = {
                totalOrders: orders.length,
                paidOrders: paid.length,
                pendingOrders: pending.length,
                cancelledOrders: cancelled.length,

                totalRevenue: paid.reduce((sum, o) => sum + o.amount, 0),
                pendingRevenue: pending.reduce((sum, o) => sum + o.amount, 0),

                averageOrderValue: paid.length > 0
                    ? paid.reduce((sum, o) => sum + o.amount, 0) / paid.length
                    : 0,

                highValueOrders: paid.filter(o => o.amount > 1000).length
            };

            context.put('paidOrders', paid);
            context.put('pendingOrders', pending);
            context.put('statistics', stats);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("FilterAndAggregate")
            .scriptProvider(filterScript)
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(
        "orders",
        java.util.List.of(
            java.util.Map.of("id", "O1", STATUS, "PAID", AMOUNT, 150.0),
            java.util.Map.of("id", "O2", STATUS, "PAID", AMOUNT, 2500.0),
            java.util.Map.of("id", "O3", STATUS, "PENDING", AMOUNT, 300.0),
            java.util.Map.of("id", "O4", STATUS, "PAID", AMOUNT, 85.0),
            java.util.Map.of("id", "O5", STATUS, "CANCELLED", AMOUNT, 200.0),
            java.util.Map.of("id", "O6", STATUS, "PAID", AMOUNT, 1200.0)));

    workflow.execute(context);

    log.info("Statistics: {}\n", context.get("statistics"));
  }

  /**
   * Example 3: Data enrichment.
   *
   * <p>Enrich incoming data with additional computed fields.
   */
  private static void dataEnrichment() {
    log.info("Example 3: Data Enrichment");

    ScriptProvider enrichScript =
        new InlineScriptProvider(
            """
            var products = context.get('products');

            // Enrich each product with computed fields
            var enriched = products.map(product => {
                var basePrice = product.price;
                var markup = 1.3; // 30% markup
                var retailPrice = basePrice * markup;
                var tax = retailPrice * 0.08;
                var finalPrice = retailPrice + tax;

                return {
                    ...product,
                    retailPrice: Math.round(retailPrice * 100) / 100,
                    tax: Math.round(tax * 100) / 100,
                    finalPrice: Math.round(finalPrice * 100) / 100,
                    profitMargin: Math.round((retailPrice - basePrice) * 100) / 100,
                    category: categorizeProduct(product),
                    inStock: product.inventory > 0,
                    stockLevel: getStockLevel(product.inventory)
                };
            });

            function categorizeProduct(p) {
                if (p.price < 50) return 'Budget';
                if (p.price < 200) return 'Standard';
                return 'Premium';
            }

            function getStockLevel(inventory) {
                if (inventory === 0) return 'OUT_OF_STOCK';
                if (inventory < 10) return 'LOW';
                if (inventory < 50) return 'MEDIUM';
                return 'HIGH';
            }

            context.put('enrichedProducts', enriched);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("EnrichProducts").scriptProvider(enrichScript).build();

    WorkflowContext context = new WorkflowContext();
    context.put(
        "products",
        java.util.List.of(
            java.util.Map.of("id", "P1", "name", "Widget", PRICE, 25.99, INVENTORY, 150),
            java.util.Map.of("id", "P2", "name", "Gadget", PRICE, 89.99, INVENTORY, 5),
            java.util.Map.of("id", "P3", "name", "Device", PRICE, 299.99, INVENTORY, 0)));

    workflow.execute(context);

    log.info("Enriched Products:");
    var enriched = (java.util.List<?>) context.get("enrichedProducts");
    enriched.forEach(p -> log.info("  {}", p));
    log.info("");
  }

  /**
   * Example 4: Complex data flattening.
   *
   * <p>Flatten nested data structures for reporting or database insertion.
   */
  private static void complexFlattening() {
    log.info("Example 4: Complex Data Flattening");

    ScriptProvider flattenScript =
        new InlineScriptProvider(
            """
            var response = JSON.parse(context.get('nestedData'));
            var flattened = [];

            // Flatten nested structure: user -> orders -> items
            response.users.forEach(user => {
                user.orders.forEach(order => {
                    order.items.forEach(item => {
                        flattened.push({
                            userId: user.id,
                            userName: user.name,
                            userEmail: user.email,
                            orderId: order.id,
                            orderDate: order.date,
                            orderStatus: order.status,
                            itemId: item.id,
                            itemName: item.name,
                            itemPrice: item.price,
                            itemQuantity: item.quantity,
                            lineTotal: item.price * item.quantity
                        });
                    });
                });
            });

            context.put('flattenedData', flattened);
            context.put('recordCount', flattened.length);
            """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("FlattenData").scriptProvider(flattenScript).build();

    String nestedData =
        """
        {
            "users": [
                {
                    "id": "U1",
                    "name": "Alice",
                    "email": "alice@example.com",
                    "orders": [
                        {
                            "id": "O1",
                            "date": "2024-01-15",
                            "status": "COMPLETED",
                            "items": [
                                {"id": "I1", "name": "Book", "price": 29.99, "quantity": 2},
                                {"id": "I2", "name": "Pen", "price": 4.99, "quantity": 5}
                            ]
                        }
                    ]
                },
                {
                    "id": "U2",
                    "name": "Bob",
                    "email": "bob@example.com",
                    "orders": [
                        {
                            "id": "O2",
                            "date": "2024-01-20",
                            "status": "PENDING",
                            "items": [
                                {"id": "I3", "name": "Laptop", "price": 999.99, "quantity": 1}
                            ]
                        }
                    ]
                }
            ]
        }
        """;

    WorkflowContext context = new WorkflowContext();
    context.put("nestedData", nestedData);

    workflow.execute(context);

    log.info("Flattened {} records", context.get("recordCount"));
    var flattened = (java.util.List<?>) context.get("flattenedData");
    flattened.forEach(recordData -> log.info("  {}", recordData));
    log.info("");
  }

  /**
   * Example 5: Multi-step ETL pipeline.
   *
   * <p>Complete Extract-Transform-Load pipeline using multiple JavaScript steps.
   */
  private static void etlPipeline() {
    log.info("Example 5: ETL Pipeline");

    // Step 1: Extract and parse
    ScriptProvider extractScript =
        new InlineScriptProvider(
            """
            var rawData = context.get('rawData');
            var parsed = JSON.parse(rawData);
            context.put('extractedData', parsed.data);
            context.put('extractedCount', parsed.data.length);
            """);

    // Step 2: Transform and clean
    ScriptProvider transformScript =
        new InlineScriptProvider(
            """
            var data = context.get('extractedData');

            // Clean and transform
            var transformed = data
                .filter(record => record.valid !== false)  // Remove invalid records
                .map(record => ({
                    id: record.id,
                    name: record.name.trim().toUpperCase(),
                    email: record.email.toLowerCase(),
                    amount: parseFloat(record.amount.replace(/[^0-9.]/g, '')),
                    category: record.category || 'UNCATEGORIZED',
                    processedAt: new Date().toISOString()
                }));

            context.put('transformedData', transformed);
            context.put('transformedCount', transformed.length);
            context.put('filteredCount', data.length - transformed.length);
            """);

    // Step 3: Aggregate and summarize
    ScriptProvider loadScript =
        new InlineScriptProvider(
            """
            var data = context.get('transformedData');

            // Group by category and calculate totals
            var summary = data.reduce((acc, record) => {
                if (!acc[record.category]) {
                    acc[record.category] = {
                        category: record.category,
                        count: 0,
                        total: 0,
                        records: []
                    };
                }
                acc[record.category].count++;
                acc[record.category].total += record.amount;
                acc[record.category].records.push(record.id);
                return acc;
            }, {});

            context.put('summary', Object.values(summary));
            context.put('totalAmount', data.reduce((sum, r) => sum + r.amount, 0));
            """);

    // Build ETL pipeline
    Workflow etlPipeline =
        SequentialWorkflow.builder()
            .name("ETL_Pipeline")
            .workflow(
                JavascriptWorkflow.builder().name("Extract").scriptProvider(extractScript).build())
            .workflow(
                JavascriptWorkflow.builder()
                    .name("Transform")
                    .scriptProvider(transformScript)
                    .build())
            .workflow(JavascriptWorkflow.builder().name("Load").scriptProvider(loadScript).build())
            .build();

    // Sample raw data
    String rawData =
        """
        {
            "data": [
                {"id": "R1", "name": "  alice  ", "email": "ALICE@EXAMPLE.COM",
                 "amount": "$100.50", "category": "A", "valid": true},
                {"id": "R2", "name": "bob", "email": "BOB@EXAMPLE.COM",
                 "amount": "$250.75", "category": "B", "valid": true},
                {"id": "R3", "name": "charlie", "email": "charlie@example.com",
                 "amount": "$75.25", "category": "A", "valid": true},
                {"id": "R4", "name": "invalid", "email": "invalid@example.com",
                 "amount": "$0", "valid": false}
            ]
        }
        """;

    WorkflowContext context = new WorkflowContext();
    context.put("rawData", rawData);

    WorkflowResult result = etlPipeline.execute(context);

    log.info("ETL Pipeline Status: {}", result.getStatus());
    log.info("Extracted: {} records", context.get("extractedCount"));
    log.info("Transformed: {} records", context.get("transformedCount"));
    log.info("Filtered out: {} records", context.get("filteredCount"));
    log.info("Total Amount: ${}", context.get("totalAmount"));
    log.info("Summary by Category:");
    var summary = (java.util.List<?>) context.get("summary");
    summary.forEach(cat -> log.info("  {}", cat));
    log.info("");
  }
}
