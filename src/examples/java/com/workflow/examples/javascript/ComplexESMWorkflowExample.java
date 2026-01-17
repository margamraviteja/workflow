package com.workflow.examples.javascript;

import com.workflow.JavascriptWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.script.FileScriptProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Complex real-world examples demonstrating ESM module support in production scenarios.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Multistep data processing pipelines with shared modules
 *   <li>Business rule engines with modular logic
 *   <li>Complex validation systems
 *   <li>Data transformation chains
 *   <li>Microservices orchestration patterns
 * </ul>
 *
 * <h3>Architecture Pattern:</h3>
 *
 * <p>These examples demonstrate a layered architecture:
 *
 * <pre>
 * ┌─────────────────────────┐
 * │   Main Workflow         │  ← Entry point script
 * └───────────┬─────────────┘
 *             │
 *       ┌─────┴─────┐
 *       │           │
 * ┌─────▼──────┐ ┌──▼────────┐
 * │ Business   │ │ Services  │  ← Domain logic modules
 * │ Rules      │ │ Layer     │
 * └─────┬──────┘ └──┬────────┘
 *       │           │
 *    ┌──▼───────────▼───┐
 *    │  Utils Library   │       ← Shared utilities
 *    └──────────────────┘
 * </pre>
 *
 * <p>Run the examples with {@code main()} method.
 */
@UtilityClass
@Slf4j
public class ComplexESMWorkflowExample {

  public static final String QUANTITY = "quantity";
  public static final String PRODUCT_ID = "productId";
  public static final String PRICE = "price";
  public static final String AMOUNT = "amount";
  public static final String TIMESTAMP = "timestamp";

  static void main() throws IOException {
    Path workDir = Files.createTempDirectory("complex-esm-");

    try {
      log.info("=== Complex ESM Workflow Examples ===\n");
      log.info("Working directory: {}\n", workDir);

      orderProcessingPipeline(workDir);
      fraudDetectionSystem(workDir);
      dataEnrichmentPipeline(workDir);
      multiTenantBusinessRules(workDir);

      log.info("\n=== All examples completed successfully ===");
    } finally {
      deleteDirectory(workDir);
    }
  }

  /**
   * Example 1: E-commerce Order Processing Pipeline.
   *
   * <p>A complete order processing workflow with modular validation, pricing, and tax calculations.
   */
  private static void orderProcessingPipeline(Path baseDir) throws IOException {
    log.info("Example 1: E-commerce Order Processing Pipeline");

    // Create lib directory for shared utilities
    Path libDir = Files.createDirectories(baseDir.resolve("lib"));

    // Validation utilities
    Files.writeString(
        libDir.resolve("validation.mjs"),
        """
        export function validateOrder(order) {
            const errors = [];

            if (!order.customerId) errors.push('Customer ID required');
            if (!order.items || order.items.length === 0) errors.push('Order must have items');

            for (const item of order.items || []) {
                if (!item.productId) errors.push(`Item missing product ID`);
                if (!item.quantity || item.quantity < 1) errors.push(`Invalid quantity for ${item.productId}`);
                if (!item.price || item.price < 0) errors.push(`Invalid price for ${item.productId}`);
            }

            return { isValid: errors.length === 0, errors };
        }

        export function validateAddress(address) {
            const errors = [];

            if (!address.street) errors.push('Street required');
            if (!address.city) errors.push('City required');
            if (!address.state) errors.push('State required');
            if (!address.zipCode) errors.push('ZIP code required');

            return { isValid: errors.length === 0, errors };
        }
        """);

    // Pricing logic
    Files.writeString(
        libDir.resolve("pricing.mjs"),
        """
        export function calculateItemTotal(item) {
            return item.price * item.quantity;
        }

        export function calculateSubtotal(items) {
            return items.reduce((sum, item) => sum + calculateItemTotal(item), 0);
        }

        export function applyDiscount(subtotal, discountPercent) {
            return subtotal * (1 - discountPercent / 100);
        }

        export function calculateTax(amount, taxRate) {
            return amount * (taxRate / 100);
        }
        """);

    // Customer tier pricing
    Files.writeString(
        libDir.resolve("customerPricing.mjs"),
        """
        import { calculateSubtotal, applyDiscount, calculateTax } from './pricing.mjs';

        export function getPricingForTier(tier) {
            const tiers = {
                'BASIC': { discount: 0, taxRate: 8.5 },
                'SILVER': { discount: 5, taxRate: 8.5 },
                'GOLD': { discount: 10, taxRate: 8.5 },
                'PLATINUM': { discount: 15, taxRate: 7.5 }
            };
            return tiers[tier] || tiers['BASIC'];
        }

        export function calculateFinalPrice(items, customerTier) {
            const subtotal = calculateSubtotal(items);
            const pricing = getPricingForTier(customerTier);

            const discountedAmount = applyDiscount(subtotal, pricing.discount);
            const tax = calculateTax(discountedAmount, pricing.taxRate);
            const total = discountedAmount + tax;

            return {
                subtotal: Math.round(subtotal * 100) / 100,
                discount: Math.round((subtotal - discountedAmount) * 100) / 100,
                discountPercent: pricing.discount,
                tax: Math.round(tax * 100) / 100,
                taxRate: pricing.taxRate,
                total: Math.round(total * 100) / 100
            };
        }
        """);

    // Step 1: Validate Order
    Path validateScript = baseDir.resolve("validateOrder.mjs");
    Files.writeString(
        validateScript,
        """
        import { validateOrder, validateAddress } from './lib/validation.mjs';

        const order = ctx.get('order');

        const orderValidation = validateOrder(order);
        const addressValidation = validateAddress(order.shippingAddress || {});

        const allErrors = [...orderValidation.errors, ...addressValidation.errors];

        ctx.put('validationResult', {
            isValid: allErrors.length === 0,
            errors: allErrors
        });
        """);

    // Step 2: Calculate Pricing
    Path pricingScript = baseDir.resolve("calculatePricing.mjs");
    Files.writeString(
        pricingScript,
        """
        import { calculateFinalPrice } from './lib/customerPricing.mjs';

        const order = ctx.get('order');
        const customerTier = ctx.get('customerTier') || 'BASIC';

        const pricing = calculateFinalPrice(order.items, customerTier);

        ctx.put('pricing', pricing);
        ctx.put('orderTotal', pricing.total);
        """);

    // Step 3: Enrich Order
    Path enrichScript = baseDir.resolve("enrichOrder.mjs");
    Files.writeString(
        enrichScript,
        """
        const order = ctx.get('order');
        const pricing = ctx.get('pricing');

        const enrichedOrder = {
            ...order,
            orderId: 'ORD-' + Date.now(),
            orderDate: new Date().toISOString(),
            status: 'PENDING',
            pricing: pricing,
            total: pricing.total
        };

        ctx.put('enrichedOrder', enrichedOrder);
        """);

    // Build the complete pipeline
    Workflow orderPipeline =
        SequentialWorkflow.builder()
            .name("OrderProcessingPipeline")
            .workflow(
                JavascriptWorkflow.builder()
                    .name("ValidateOrder")
                    .scriptProvider(new FileScriptProvider(validateScript))
                    .build())
            .workflow(
                JavascriptWorkflow.builder()
                    .name("CalculatePricing")
                    .scriptProvider(new FileScriptProvider(pricingScript))
                    .build())
            .workflow(
                JavascriptWorkflow.builder()
                    .name("EnrichOrder")
                    .scriptProvider(new FileScriptProvider(enrichScript))
                    .build())
            .build();

    // Execute pipeline
    WorkflowContext context = new WorkflowContext();
    context.put(
        "order",
        java.util.Map.of(
            "customerId",
            "CUST-123",
            "items",
            java.util.List.of(
                java.util.Map.of(PRODUCT_ID, "PROD-1", QUANTITY, 2, PRICE, 29.99),
                java.util.Map.of(PRODUCT_ID, "PROD-2", QUANTITY, 1, PRICE, 49.99)),
            "shippingAddress",
            java.util.Map.of(
                "street", "123 Main St", "city", "Seattle", "state", "WA", "zipCode", "98101")));
    context.put("customerTier", "GOLD");

    WorkflowResult result = orderPipeline.execute(context);

    log.info("Pipeline Status: {}", result.getStatus());
    log.info("Validation: {}", context.get("validationResult"));
    log.info("Pricing: {}", context.get("pricing"));
    log.info("Enriched Order: {}\n", context.get("enrichedOrder"));
  }

  /**
   * Example 2: Fraud Detection System.
   *
   * <p>Multi-layered fraud detection with rule-based scoring and risk assessment.
   */
  private static void fraudDetectionSystem(Path baseDir) throws IOException {
    log.info("Example 2: Fraud Detection System");

    Path fraudDir = Files.createDirectories(baseDir.resolve("fraud"));

    // Velocity checks module
    Files.writeString(
        fraudDir.resolve("velocityChecks.mjs"),
        """
        export function checkTransactionVelocity(transaction, history) {
            const recentCount = history.filter(t =>
                new Date(t.timestamp).getTime() > Date.now() - 3600000
            ).length;

            return {
                recentTransactions: recentCount,
                isHighVelocity: recentCount > 5,
                riskScore: recentCount > 5 ? 20 : 0
            };
        }

        export function checkAmountVelocity(transaction, history) {
            const recentTotal = history
                .filter(t => new Date(t.timestamp).getTime() > Date.now() - 86400000)
                .reduce((sum, t) => sum + t.amount, 0);

            return {
                dailyTotal: recentTotal,
                isHighAmount: recentTotal > 10000,
                riskScore: recentTotal > 10000 ? 15 : 0
            };
        }
        """);

    // Geographic checks
    Files.writeString(
        fraudDir.resolve("geoChecks.mjs"),
        """
        export function checkLocationAnomaly(transaction, profile) {
            const distance = calculateDistance(
                transaction.location,
                profile.usualLocation
            );

            return {
                distance: distance,
                isAnomaly: distance > 500,
                riskScore: distance > 500 ? 25 : 0
            };
        }

        function calculateDistance(loc1, loc2) {
            // Simplified distance calculation
            return Math.abs(loc1.lat - loc2.lat) + Math.abs(loc1.lon - loc2.lon);
        }

        export function checkHighRiskCountry(transaction) {
            const highRiskCountries = ['XX', 'YY', 'ZZ'];
            const isHighRisk = highRiskCountries.includes(transaction.country);

            return {
                isHighRiskCountry: isHighRisk,
                riskScore: isHighRisk ? 30 : 0
            };
        }
        """);

    // Pattern analysis
    Files.writeString(
        fraudDir.resolve("patternAnalysis.mjs"),
        """
        export function checkUnusualAmount(transaction, profile) {
            const avgAmount = profile.averageTransaction;
            const ratio = transaction.amount / avgAmount;

            return {
                amountRatio: ratio,
                isUnusual: ratio > 5,
                riskScore: ratio > 5 ? 20 : (ratio > 3 ? 10 : 0)
            };
        }

        export function checkUnusualTime(transaction) {
            const hour = new Date(transaction.timestamp).getHours();
            const isUnusualTime = hour < 6 || hour > 23;

            return {
                hour: hour,
                isUnusualTime: isUnusualTime,
                riskScore: isUnusualTime ? 10 : 0
            };
        }
        """);

    // Main fraud detection
    Path fraudMainScript = baseDir.resolve("fraudDetection.mjs");
    Files.writeString(
        fraudMainScript,
        """
        import { checkTransactionVelocity, checkAmountVelocity } from './fraud/velocityChecks.mjs';
        import { checkLocationAnomaly, checkHighRiskCountry } from './fraud/geoChecks.mjs';
        import { checkUnusualAmount, checkUnusualTime } from './fraud/patternAnalysis.mjs';

        const transaction = ctx.get('transaction');
        const customerProfile = ctx.get('customerProfile');
        const transactionHistory = ctx.get('transactionHistory') || [];

        // Run all fraud checks
        const velocityCheck = checkTransactionVelocity(transaction, transactionHistory);
        const amountVelocityCheck = checkAmountVelocity(transaction, transactionHistory);
        const locationCheck = checkLocationAnomaly(transaction, customerProfile);
        const countryCheck = checkHighRiskCountry(transaction);
        const amountCheck = checkUnusualAmount(transaction, customerProfile);
        const timeCheck = checkUnusualTime(transaction);

        // Aggregate risk score
        const totalRiskScore =
            velocityCheck.riskScore +
            amountVelocityCheck.riskScore +
            locationCheck.riskScore +
            countryCheck.riskScore +
            amountCheck.riskScore +
            timeCheck.riskScore;

        // Determine action
        let action;
        let reason = [];

        if (totalRiskScore > 50) {
            action = 'BLOCK';
            reason.push('High risk score: ' + totalRiskScore);
        } else if (totalRiskScore > 30) {
            action = 'MANUAL_REVIEW';
            reason.push('Medium risk score: ' + totalRiskScore);
        } else {
            action = 'APPROVE';
        }

        // Collect triggered rules
        if (velocityCheck.isHighVelocity) reason.push('High transaction velocity');
        if (locationCheck.isAnomaly) reason.push('Geographic anomaly');
        if (countryCheck.isHighRiskCountry) reason.push('High-risk country');
        if (amountCheck.isUnusual) reason.push('Unusual transaction amount');

        ctx.put('fraudAnalysis', {
            riskScore: totalRiskScore,
            action: action,
            reasons: reason,
            checks: {
                velocity: velocityCheck,
                amountVelocity: amountVelocityCheck,
                location: locationCheck,
                country: countryCheck,
                amount: amountCheck,
                time: timeCheck
            }
        });
        """);

    // Execute fraud detection
    JavascriptWorkflow fraudWorkflow =
        JavascriptWorkflow.builder()
            .name("FraudDetection")
            .scriptProvider(new FileScriptProvider(fraudMainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(
        "transaction",
        java.util.Map.of(
            "id",
            "TXN-789",
            AMOUNT,
            5000.0,
            TIMESTAMP,
            "2024-01-15T23:30:00Z",
            "location",
            java.util.Map.of("lat", 47.6, "lon", -122.3),
            "country",
            "US"));
    context.put(
        "customerProfile",
        java.util.Map.of(
            "averageTransaction",
            250.0,
            "usualLocation",
            java.util.Map.of("lat", 40.7, "lon", -74.0)));
    context.put(
        "transactionHistory",
        java.util.List.of(
            java.util.Map.of(TIMESTAMP, "2024-01-15T23:00:00Z", AMOUNT, 100.0),
            java.util.Map.of(TIMESTAMP, "2024-01-15T23:10:00Z", AMOUNT, 150.0),
            java.util.Map.of(TIMESTAMP, "2024-01-15T23:15:00Z", AMOUNT, 200.0)));

    fraudWorkflow.execute(context);

    log.info("Fraud Analysis: {}\n", context.get("fraudAnalysis"));
  }

  /**
   * Example 3: Data Enrichment Pipeline.
   *
   * <p>Multistep data transformation with validation, normalization, and enrichment.
   */
  private static void dataEnrichmentPipeline(Path baseDir) throws IOException {
    log.info("Example 3: Data Enrichment Pipeline");

    Path etlDir = Files.createDirectories(baseDir.resolve("etl"));

    // Data cleaners
    Files.writeString(
        etlDir.resolve("cleaners.mjs"),
        """
        export function cleanEmail(email) {
            return email ? email.toLowerCase().trim() : null;
        }

        export function cleanPhone(phone) {
            if (!phone) return null;
            return phone.replace(/\\D/g, '');
        }

        export function cleanName(name) {
            if (!name) return null;
            return name.trim().replace(/\\s+/g, ' ');
        }
        """);

    // Data validators
    Files.writeString(
        etlDir.resolve("validators.mjs"),
        """
        export function validateEmail(email) {
            return email && /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email);
        }

        export function validatePhone(phone) {
            return phone && /^\\d{10}$/.test(phone);
        }

        export function validateAge(age) {
            return age && age >= 0 && age <= 150;
        }
        """);

    // Data enrichers
    Files.writeString(
        etlDir.resolve("enrichers.mjs"),
        """
        export function enrichWithMetadata(record) {
            return {
                ...record,
                processedAt: new Date().toISOString(),
                version: '1.0'
            };
        }

        export function calculateDerivedFields(record) {
            const age = record.age;
            let ageGroup;

            if (age < 18) ageGroup = 'MINOR';
            else if (age < 30) ageGroup = 'YOUNG_ADULT';
            else if (age < 50) ageGroup = 'ADULT';
            else if (age < 65) ageGroup = 'MIDDLE_AGED';
            else ageGroup = 'SENIOR';

            return {
                ...record,
                ageGroup: ageGroup
            };
        }
        """);

    // Pipeline steps
    Path step1 = baseDir.resolve("cleanData.mjs");
    Files.writeString(
        step1,
        """
        import { cleanEmail, cleanPhone, cleanName } from './etl/cleaners.mjs';

        const records = ctx.get('rawData');

        const cleaned = records.map(record => ({
            ...record,
            email: cleanEmail(record.email),
            phone: cleanPhone(record.phone),
            name: cleanName(record.name)
        }));

        ctx.put('cleanedData', cleaned);
        """);

    Path step2 = baseDir.resolve("validateData.mjs");
    Files.writeString(
        step2,
        """
        import { validateEmail, validatePhone, validateAge } from './etl/validators.mjs';

        const records = ctx.get('cleanedData');

        const validated = records.map(record => {
            const errors = [];

            if (!validateEmail(record.email)) errors.push('Invalid email');
            if (!validatePhone(record.phone)) errors.push('Invalid phone');
            if (!validateAge(record.age)) errors.push('Invalid age');

            return {
                ...record,
                isValid: errors.length === 0,
                validationErrors: errors
            };
        });

        const validRecords = validated.filter(r => r.isValid);
        const invalidRecords = validated.filter(r => !r.isValid);

        ctx.put('validatedData', validRecords);
        ctx.put('invalidRecords', invalidRecords);
        """);

    Path step3 = baseDir.resolve("enrichData.mjs");
    Files.writeString(
        step3,
        """
        import { enrichWithMetadata, calculateDerivedFields } from './etl/enrichers.mjs';

        const records = ctx.get('validatedData');

        const enriched = records
            .map(enrichWithMetadata)
            .map(calculateDerivedFields);

        ctx.put('enrichedData', enriched);
        """);

    // Build pipeline
    Workflow enrichmentPipeline =
        SequentialWorkflow.builder()
            .name("DataEnrichmentPipeline")
            .workflow(
                JavascriptWorkflow.builder()
                    .name("CleanData")
                    .scriptProvider(new FileScriptProvider(step1))
                    .build())
            .workflow(
                JavascriptWorkflow.builder()
                    .name("ValidateData")
                    .scriptProvider(new FileScriptProvider(step2))
                    .build())
            .workflow(
                JavascriptWorkflow.builder()
                    .name("EnrichData")
                    .scriptProvider(new FileScriptProvider(step3))
                    .build())
            .build();

    // Execute
    WorkflowContext context = new WorkflowContext();
    context.put(
        "rawData",
        java.util.List.of(
            java.util.Map.of(
                "name",
                "  john doe  ",
                "email",
                "JOHN@EXAMPLE.COM",
                "phone",
                "(555) 123-4567",
                "age",
                25),
            java.util.Map.of(
                "name",
                "jane smith",
                "email",
                "invalid-email",
                "phone",
                "555-234-5678",
                "age",
                35)));

    enrichmentPipeline.execute(context);

    log.info("Valid Records: {}", context.get("validatedData"));
    log.info("Invalid Records: {}", context.get("invalidRecords"));
    log.info("Enriched Data: {}\n", context.get("enrichedData"));
  }

  /**
   * Example 4: Multi-Tenant Business Rules.
   *
   * <p>Tenant-specific business rules loaded from separate module files.
   */
  private static void multiTenantBusinessRules(Path baseDir) throws IOException {
    log.info("Example 4: Multi-Tenant Business Rules");

    // Create tenant directories
    Path tenantsDir = Files.createDirectories(baseDir.resolve("tenants"));
    Path acmeDir = Files.createDirectories(tenantsDir.resolve("acme"));
    Path globexDir = Files.createDirectories(tenantsDir.resolve("globex"));

    // ACME Corp rules
    Files.writeString(
        acmeDir.resolve("pricingRules.mjs"),
        """
        export function calculatePrice(basePrice, quantity) {
            let discount = 0;

            // Volume discounts
            if (quantity >= 100) discount = 20;
            else if (quantity >= 50) discount = 15;
            else if (quantity >= 10) discount = 10;

            const discountedPrice = basePrice * (1 - discount / 100);
            return {
                basePrice,
                discount,
                unitPrice: discountedPrice,
                total: discountedPrice * quantity
            };
        }

        export const config = {
            taxRate: 8.5,
            shippingThreshold: 500,
            shippingFee: 25
        };
        """);

    // Globex Corp rules
    Files.writeString(
        globexDir.resolve("pricingRules.mjs"),
        """
        export function calculatePrice(basePrice, quantity) {
            let discount = 0;

            // Different discount structure
            if (quantity >= 200) discount = 25;
            else if (quantity >= 100) discount = 18;
            else if (quantity >= 20) discount = 12;

            const discountedPrice = basePrice * (1 - discount / 100);
            return {
                basePrice,
                discount,
                unitPrice: discountedPrice,
                total: discountedPrice * quantity
            };
        }

        export const config = {
            taxRate: 7.0,
            shippingThreshold: 1000,
            shippingFee: 15
        };
        """);

    // Write the ACME workflow script to baseDir
    Path acmeScript = baseDir.resolve("acmeWorkflow.mjs");
    Files.writeString(
        acmeScript,
        """
    import { calculatePrice, config } from './tenants/acme/pricingRules.mjs';

    const basePrice = ctx.get('basePrice');
    const quantity = ctx.get('quantity');

    const pricing = calculatePrice(basePrice, quantity);
    const tax = pricing.total * (config.taxRate / 100);
    const shipping = pricing.total >= config.shippingThreshold ? 0 : config.shippingFee;

    ctx.put('result', {
        tenant: 'ACME',
        ...pricing,
        tax,
        shipping,
        grandTotal: pricing.total + tax + shipping
    });
    """);
    JavascriptWorkflow acmeWorkflow =
        JavascriptWorkflow.builder()
            .name("ACME-Pricing")
            .scriptProvider(new FileScriptProvider(acmeScript))
            .build();

    // Write the Globex workflow script to baseDir
    Path globexScript = baseDir.resolve("globexWorkflow.mjs");
    Files.writeString(
        globexScript,
        """
    import { calculatePrice, config } from './tenants/globex/pricingRules.mjs';

    const basePrice = ctx.get('basePrice');
    const quantity = ctx.get('quantity');

    const pricing = calculatePrice(basePrice, quantity);
    const tax = pricing.total * (config.taxRate / 100);
    const shipping = pricing.total >= config.shippingThreshold ? 0 : config.shippingFee;

    ctx.put('result', {
        tenant: 'GLOBEX',
        ...pricing,
        tax,
        shipping,
        grandTotal: pricing.total + tax + shipping
    });
    """);

    JavascriptWorkflow globexWorkflow =
        JavascriptWorkflow.builder()
            .name("Globex-Pricing")
            .scriptProvider(new FileScriptProvider(globexScript))
            .build();

    // Test ACME
    WorkflowContext acmeContext = new WorkflowContext();
    acmeContext.put("basePrice", 100.0);
    acmeContext.put(QUANTITY, 50);
    acmeWorkflow.execute(acmeContext);
    log.info("ACME Result: {}", acmeContext.get("result"));

    // Test Globex
    WorkflowContext globexContext = new WorkflowContext();
    globexContext.put("basePrice", 100.0);
    globexContext.put(QUANTITY, 50);
    globexWorkflow.execute(globexContext);
    log.info("GLOBEX Result: {}\n", globexContext.get("result"));
  }

  private static void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (Stream<Path> stream = Files.walk(directory)) {
        stream
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException _) {
                    // Ignore
                  }
                });
      }
    }
  }
}
