package com.workflow.examples.javascript;

import com.workflow.JavascriptWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.script.InlineScriptProvider;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Advanced example demonstrating machine learning model evaluation with JavaScript workflows.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Model prediction processing
 *   <li>Confusion matrix calculation
 *   <li>Performance metrics (accuracy, precision, recall, F1-score)
 *   <li>ROC curve data preparation
 *   <li>Model comparison and selection
 * </ul>
 *
 * <p>Use case: An ML pipeline that evaluates model predictions, calculates metrics, and selects the
 * best performing model.
 */
@UtilityClass
@Slf4j
public class MachineLearningEvaluationExample {

  public static final String ACTUAL = "actual";
  public static final String PREDICTED = "predicted";
  public static final String PROBABILITY = "probability";
  public static final String ACCURACY = "accuracy";
  public static final String PRECISION = "precision";
  public static final String RECALL = "recall";
  public static final String IMPORTANCE = "importance";
  public static final String CATEGORY = "category";
  public static final String DEMOGRAPHIC = "demographic";
  public static final String FINANCIAL = "financial";
  public static final String EMPLOYMENT = "employment";

  public static void main(String[] args) {
    log.info("=== Machine Learning Model Evaluation Examples ===\n");

    binaryClassificationEvaluation();
    multiModelComparison();
    featureImportanceAnalysis();

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: Binary classification model evaluation.
   *
   * <p>Evaluates a binary classifier with comprehensive metrics.
   */
  private static void binaryClassificationEvaluation() {
    log.info("Example 1: Binary Classification Evaluation");

    var confusionMatrix =
        JavascriptWorkflow.builder()
            .name("CalculateConfusionMatrix")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var predictions = context.get('predictions');

                    // Calculate confusion matrix
                    var tp = 0, fp = 0, tn = 0, fn = 0;

                    predictions.forEach(pred => {
                        var actual = pred.actual;
                        var predicted = pred.predicted;

                        if (actual === 1 && predicted === 1) tp++;
                        else if (actual === 0 && predicted === 1) fp++;
                        else if (actual === 0 && predicted === 0) tn++;
                        else if (actual === 1 && predicted === 0) fn++;
                    });

                    context.put('truePositives', tp);
                    context.put('falsePositives', fp);
                    context.put('trueNegatives', tn);
                    context.put('falseNegatives', fn);
                    """))
            .build();

    var calculateMetrics =
        JavascriptWorkflow.builder()
            .name("CalculateMetrics")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var tp = context.get('truePositives');
                    var fp = context.get('falsePositives');
                    var tn = context.get('trueNegatives');
                    var fn = context.get('falseNegatives');

                    var total = tp + fp + tn + fn;

                    // Calculate metrics
                    var accuracy = (tp + tn) / total;
                    var precision = tp / (tp + fp);
                    var recall = tp / (tp + fn);
                    var f1Score = 2 * (precision * recall) / (precision + recall);
                    var specificity = tn / (tn + fp);

                    // Matthews Correlation Coefficient
                    var mcc = ((tp * tn) - (fp * fn)) /
                        Math.sqrt((tp + fp) * (tp + fn) * (tn + fp) * (tn + fn));

                    context.put('accuracy', accuracy);
                    context.put('precision', precision);
                    context.put('recall', recall);
                    context.put('f1Score', f1Score);
                    context.put('specificity', specificity);
                    context.put('mcc', mcc);
                    """))
            .build();

    var rocAnalysis =
        JavascriptWorkflow.builder()
            .name("PrepareROCData")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var predictions = context.get('predictions');

                    // Sort by predicted probability (descending)
                    var sorted = predictions
                        .map(p => ({...p}))
                        .sort((a, b) => b.probability - a.probability);

                    // Calculate ROC points
                    var rocPoints = [];
                    var tp = 0, fp = 0;
                    var totalPositives = predictions.filter(p => p.actual === 1).length;
                    var totalNegatives = predictions.length - totalPositives;

                    sorted.forEach((pred, idx) => {
                        if (pred.actual === 1) tp++;
                        else fp++;

                        var tpr = tp / totalPositives;  // True Positive Rate (Recall)
                        var fpr = fp / totalNegatives;  // False Positive Rate

                        rocPoints.push({
                            threshold: pred.probability,
                            tpr: tpr,
                            fpr: fpr
                        });
                    });

                    // Calculate AUC using trapezoidal rule
                    var auc = 0;
                    for (var i = 1; i < rocPoints.length; i++) {
                        var width = rocPoints[i].fpr - rocPoints[i-1].fpr;
                        var height = (rocPoints[i].tpr + rocPoints[i-1].tpr) / 2;
                        auc += width * height;
                    }

                    context.put('rocPoints', rocPoints);
                    context.put('auc', auc);
                    """))
            .build();

    Workflow evaluationPipeline =
        SequentialWorkflow.builder()
            .name("ModelEvaluation")
            .workflow(confusionMatrix)
            .workflow(calculateMetrics)
            .workflow(rocAnalysis)
            .build();

    // Simulate model predictions
    List<Map<String, Object>> predictions =
        List.of(
            Map.of(ACTUAL, 1, PREDICTED, 1, PROBABILITY, 0.95),
            Map.of(ACTUAL, 1, PREDICTED, 1, PROBABILITY, 0.88),
            Map.of(ACTUAL, 1, PREDICTED, 0, PROBABILITY, 0.45),
            Map.of(ACTUAL, 0, PREDICTED, 0, PROBABILITY, 0.12),
            Map.of(ACTUAL, 0, PREDICTED, 0, PROBABILITY, 0.08),
            Map.of(ACTUAL, 0, PREDICTED, 1, PROBABILITY, 0.72),
            Map.of(ACTUAL, 1, PREDICTED, 1, PROBABILITY, 0.91),
            Map.of(ACTUAL, 0, PREDICTED, 0, PROBABILITY, 0.15));

    WorkflowContext context = new WorkflowContext();
    context.put("predictions", predictions);

    evaluationPipeline.execute(context);

    log.info("=== Confusion Matrix ===");
    log.info("True Positives: {}", context.get("truePositives"));
    log.info("False Positives: {}", context.get("falsePositives"));
    log.info("True Negatives: {}", context.get("trueNegatives"));
    log.info("False Negatives: {}", context.get("falseNegatives"));

    log.info("\n=== Performance Metrics ===");
    log.info("Accuracy: {}", context.get(ACCURACY));
    log.info("Precision: {}", context.get(PRECISION));
    log.info("Recall: {}", context.get(RECALL));
    log.info("F1-Score: {}", context.get("f1Score"));
    log.info("Specificity: {}", context.get("specificity"));
    log.info("MCC: {}", context.get("mcc"));
    log.info("AUC: {}\n", context.get("auc"));
  }

  /**
   * Example 2: Compare multiple models and select the best one.
   *
   * <p>Evaluates multiple models and ranks them based on multiple criteria.
   */
  private static void multiModelComparison() {
    log.info("Example 2: Multi-Model Comparison");

    var compareModels =
        JavascriptWorkflow.builder()
            .name("CompareModels")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var models = context.get('models');

                    // Rank models by multiple criteria
                    var ranked = models.map(model => {
                        // Composite score (weighted average)
                        var score =
                            model.accuracy * 0.3 +
                            model.precision * 0.25 +
                            model.recall * 0.25 +
                            model.auc * 0.2;

                        return {
                            ...model,
                            compositeScore: score,
                            rank: 0
                        };
                    });

                    // Sort by composite score (descending)
                    ranked.sort((a, b) => b.compositeScore - a.compositeScore);

                    // Assign ranks
                    ranked.forEach((model, idx) => {
                        model.rank = idx + 1;
                    });

                    // Find best model
                    var bestModel = ranked[0];

                    // Identify models that are close to best
                    var threshold = bestModel.compositeScore * 0.95;
                    var topModels = ranked.filter(m => m.compositeScore >= threshold);

                    context.put('rankedModels', ranked);
                    context.put('bestModel', bestModel);
                    context.put('topModels', topModels);

                    // Performance analysis
                    var avgAccuracy = models.reduce((sum, m) => sum + m.accuracy, 0) / models.length;
                    var maxAccuracy = Math.max(...models.map(m => m.accuracy));
                    var minAccuracy = Math.min(...models.map(m => m.accuracy));

                    context.put('avgAccuracy', avgAccuracy);
                    context.put('maxAccuracy', maxAccuracy);
                    context.put('minAccuracy', minAccuracy);
                    context.put('accuracyRange', maxAccuracy - minAccuracy);
                    """))
            .build();

    // Simulate model results
    List<Map<String, Object>> models =
        List.of(
            Map.of(
                "name", "RandomForest", ACCURACY, 0.92, PRECISION, 0.90, RECALL, 0.88, "auc", 0.95),
            Map.of(
                "name",
                "LogisticRegression",
                ACCURACY,
                0.88,
                PRECISION,
                0.85,
                RECALL,
                0.90,
                "auc",
                0.92),
            Map.of(
                "name",
                "GradientBoosting",
                ACCURACY,
                0.94,
                PRECISION,
                0.93,
                RECALL,
                0.91,
                "auc",
                0.96),
            Map.of(
                "name",
                "NeuralNetwork",
                ACCURACY,
                0.91,
                PRECISION,
                0.89,
                RECALL,
                0.92,
                "auc",
                0.94),
            Map.of("name", "SVM", ACCURACY, 0.87, PRECISION, 0.86, RECALL, 0.85, "auc", 0.90));

    WorkflowContext context = new WorkflowContext();
    context.put("models", models);

    compareModels.execute(context);

    log.info("Best Model: {}", context.get("bestModel"));
    log.info("\nTop Models:");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> topModels = (List<Map<String, Object>>) context.get("topModels");
    topModels.forEach(model -> log.info("  {}", model));

    log.info("\n=== Performance Analysis ===");
    log.info("Average Accuracy: {}", context.get("avgAccuracy"));
    log.info("Max Accuracy: {}", context.get("maxAccuracy"));
    log.info("Min Accuracy: {}", context.get("minAccuracy"));
    log.info("Accuracy Range: {}\n", context.get("accuracyRange"));
  }

  /**
   * Example 3: Feature importance analysis.
   *
   * <p>Analyzes feature importance scores and identifies key features.
   */
  private static void featureImportanceAnalysis() {
    log.info("Example 3: Feature Importance Analysis");

    var analyzeFeatures =
        JavascriptWorkflow.builder()
            .name("AnalyzeFeatureImportance")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var features = context.get('features');

                    // Sort by importance (descending)
                    var sorted = features
                        .map(f => ({...f}))
                        .sort((a, b) => b.importance - a.importance);

                    // Calculate cumulative importance
                    var totalImportance = sorted.reduce((sum, f) => sum + f.importance, 0);
                    var cumulative = 0;

                    var enriched = sorted.map((feature, idx) => {
                        cumulative += feature.importance;
                        return {
                            ...feature,
                            rank: idx + 1,
                            percentage: (feature.importance / totalImportance) * 100,
                            cumulativePercentage: (cumulative / totalImportance) * 100
                        };
                    });

                    // Identify top features (80% cumulative importance)
                    var topFeatures = enriched.filter(f => f.cumulativePercentage <= 80);

                    // Group features by category
                    var byCategory = enriched.reduce((acc, f) => {
                        if (!acc[f.category]) {
                            acc[f.category] = {
                                features: [],
                                totalImportance: 0
                            };
                        }
                        acc[f.category].features.push(f);
                        acc[f.category].totalImportance += f.importance;
                        return acc;
                    }, {});

                    context.put('sortedFeatures', enriched);
                    context.put('topFeatures', topFeatures);
                    context.put('featuresByCategory', byCategory);
                    context.put('totalFeatures', features.length);
                    context.put('topFeatureCount', topFeatures.length);
                    """))
            .build();

    // Simulate feature importance scores
    List<Map<String, Object>> features =
        List.of(
            Map.of("name", "age", IMPORTANCE, 0.25, CATEGORY, DEMOGRAPHIC),
            Map.of("name", "income", IMPORTANCE, 0.18, CATEGORY, DEMOGRAPHIC),
            Map.of("name", "credit_score", IMPORTANCE, 0.22, CATEGORY, FINANCIAL),
            Map.of("name", "loan_amount", IMPORTANCE, 0.15, CATEGORY, FINANCIAL),
            Map.of("name", "employment_years", IMPORTANCE, 0.08, CATEGORY, EMPLOYMENT),
            Map.of("name", "debt_ratio", IMPORTANCE, 0.07, CATEGORY, FINANCIAL),
            Map.of("name", "education", IMPORTANCE, 0.03, CATEGORY, DEMOGRAPHIC),
            Map.of("name", "marital_status", IMPORTANCE, 0.02, CATEGORY, DEMOGRAPHIC));

    WorkflowContext context = new WorkflowContext();
    context.put("features", features);

    analyzeFeatures.execute(context);

    log.info("Total Features: {}", context.get("totalFeatures"));
    log.info("Top Features (80% importance): {}", context.get("topFeatureCount"));

    log.info("\n=== Top Features ===");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> topFeatures = (List<Map<String, Object>>) context.get("topFeatures");
    topFeatures.forEach(
        feature ->
            log.info(
                "  Rank {}: {} ({}%, cumulative: {}%)",
                feature.get("rank"),
                feature.get("name"),
                feature.get("percentage"),
                feature.get("cumulativePercentage")));

    log.info("\n=== Features by Category ===");

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> byCategory =
        (Map<String, Map<String, Object>>) context.get("featuresByCategory");
    byCategory.forEach(
        (category, data) -> {
          log.info("Category: {}", category);
          log.info("  Total Importance: {}", data.get("totalImportance"));

          @SuppressWarnings("unchecked")
          List<Map<String, Object>> categoryFeatures =
              (List<Map<String, Object>>) data.get("features");
          log.info("  Feature Count: {}", categoryFeatures.size());
        });

    log.info("");
  }
}
