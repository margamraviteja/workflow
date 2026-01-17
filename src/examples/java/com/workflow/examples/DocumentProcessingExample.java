package com.workflow.examples;

import com.workflow.ConditionalWorkflow;
import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.TaskWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.execution.strategy.ExecutionStrategy;
import com.workflow.execution.strategy.ThreadPoolExecutionStrategy;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example: Intelligent Document Processing Workflow
 *
 * <p>< Demonstrates a production-grade document processing workflow with upload validation,
 * document type classification, type-specific processing, content analysis, quality checks,
 * indexing, and archival.
 */
@UtilityClass
@Slf4j
public class DocumentProcessingExample {

  public static final String DOCUMENT_ID = "documentId";
  public static final String FILE_NAME = "fileName";
  public static final String DOCUMENT_TYPE = "documentType";
  public static final String INDEXED = "indexed";
  public static final String ARCHIVE_LOCATION = "archiveLocation";

  static class ValidateDocumentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      String fileName = (String) context.get(FILE_NAME);
      int fileSize = (int) context.get("fileSize");

      log.info("Validating document: {} ({})", documentId, fileName);

      if (fileSize > 50 * 1024 * 1024) {
        throw new IllegalArgumentException("File size exceeds maximum allowed size (50MB)");
      }

      if (!fileName.matches("(?i).*\\.(pdf|png|jpg|jpeg|docx|txt)$")) {
        throw new IllegalArgumentException("Unsupported file format");
      }

      context.put("validated", true);
      log.info("Document validation passed");
    }

    @Override
    public String getName() {
      return "validate-document";
    }
  }

  static class ClassifyDocumentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String fileName = (String) context.get(FILE_NAME);
      log.info("Classifying document: {}", fileName);

      String documentType;
      if (fileName.toLowerCase().endsWith(".pdf")) {
        documentType = "PDF";
      } else if (fileName.toLowerCase().matches("(?i).*(png|jpg|jpeg)$")) {
        documentType = "IMAGE";
      } else if (fileName.toLowerCase().endsWith(".docx")) {
        documentType = "DOCX";
      } else {
        documentType = "TEXT";
      }

      context.put(DOCUMENT_TYPE, documentType);
      log.info("Document classified as: {}", documentType);
    }

    @Override
    public String getName() {
      return "classify-document";
    }
  }

  static class ExtractPdfContentTask implements Task {
    private final Random random = new Random();

    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Extracting content from PDF: {}", documentId);

      String content = "Extracted PDF content with " + (random.nextInt() * 5000) + " characters";
      int pageCount = (random.nextInt() * 50) + 1;

      context.put("pdfContent", content);
      context.put("pageCount", pageCount);
      log.info("PDF extraction complete. Pages: {}", pageCount);
    }

    @Override
    public String getName() {
      return "extract-pdf";
    }
  }

  static class RunOcrTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Running OCR on document: {}", documentId);

      try {
        Thread.sleep(200);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }

      String ocrText = "Optical character recognition extracted text";
      double confidence = 0.85 + (Math.random() * 0.15);

      context.put("ocrText", ocrText);
      context.put("ocrConfidence", confidence);
      log.info("OCR complete. Confidence: {}%", (int) (confidence * 100));
    }

    @Override
    public String getName() {
      return "run-ocr";
    }
  }

  static class ProcessImageTask implements Task {
    private final Random random = new Random();

    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Processing image: {}", documentId);

      int rotationAngle = random.nextInt() * 360;
      String enhancement = "brightness=+15%, contrast=+10%";

      context.put("imageRotation", rotationAngle);
      context.put("imageEnhancement", enhancement);
      log.info("Image processing complete");
    }

    @Override
    public String getName() {
      return "process-image";
    }
  }

  static class ExtractMetadataTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      String fileName = (String) context.get(FILE_NAME);

      log.info("Extracting metadata from document: {}", documentId);

      Map<String, Object> metadata = new HashMap<>();
      metadata.put(DOCUMENT_ID, documentId);
      metadata.put(FILE_NAME, fileName);
      metadata.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
      metadata.put(DOCUMENT_TYPE, context.get(DOCUMENT_TYPE));

      context.put("metadata", metadata);
      log.info("Metadata extraction complete");
    }

    @Override
    public String getName() {
      return "extract-metadata";
    }
  }

  static class AnalyzeContentTask implements Task {
    private final Random random = new Random();

    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Analyzing content of document: {}", documentId);

      String detectedLanguage = "en";
      int wordCount = (random.nextInt() * 10000) + 1000;
      int entityCount = (random.nextInt() * 100) + 10;

      Map<String, Object> analysis = new HashMap<>();
      analysis.put("language", detectedLanguage);
      analysis.put("wordCount", wordCount);
      analysis.put("entityCount", entityCount);

      context.put("contentAnalysis", analysis);
      log.info("Content analysis complete");
    }

    @Override
    public String getName() {
      return "analyze-content";
    }
  }

  static class PerformQualityCheckTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Performing quality checks on document: {}", documentId);

      Double ocrConfidence = (Double) context.get("ocrConfidence");

      boolean qualityPassed = ocrConfidence == null || ocrConfidence >= 0.7;

      context.put("qualityPassed", qualityPassed);
      log.info("Quality check: {}", qualityPassed ? "PASSED" : "FAILED");
    }

    @Override
    public String getName() {
      return "quality-check";
    }
  }

  static class IndexDocumentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Indexing document: {}", documentId);

      @SuppressWarnings("unchecked")
      Map<String, Object> metadata = (Map<String, Object>) context.get("metadata");
      if (metadata != null) {
        context.put(INDEXED, true);
        log.info("Document indexed successfully");
      }
    }

    @Override
    public String getName() {
      return "index-document";
    }
  }

  static class ArchiveDocumentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Archiving document: {}", documentId);

      String archiveLocation = "s3://archive/documents/" + documentId;
      context.put(ARCHIVE_LOCATION, archiveLocation);
      log.info("Document archived at: {}", archiveLocation);
    }

    @Override
    public String getName() {
      return "archive-document";
    }
  }

  static class GenerateProcessingReportTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String documentId = (String) context.get(DOCUMENT_ID);
      log.info("Generating processing report for document: {}", documentId);

      Map<String, Object> report = new HashMap<>();
      report.put(DOCUMENT_ID, documentId);
      report.put("status", "COMPLETED");
      report.put(DOCUMENT_TYPE, context.get(DOCUMENT_TYPE));
      report.put("qualityCheckPassed", context.get("qualityPassed"));
      report.put(INDEXED, context.get(INDEXED));
      report.put(ARCHIVE_LOCATION, context.get(ARCHIVE_LOCATION));
      report.put("completedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

      context.put("processingReport", report);
      log.info("Processing report generated");
    }

    @Override
    public String getName() {
      return "generate-report";
    }
  }

  static void processDocument() throws Exception {
    log.info("\n=== Document Processing Workflow Example ===\n");

    WorkflowContext context = new WorkflowContext();
    context.put(DOCUMENT_ID, "DOC-2025-78901");
    context.put(FILE_NAME, "quarterly-report.pdf");
    context.put("fileSize", 5 * 1024 * 1024);
    context.put("processingStarted", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

    RetryPolicy processingRetry =
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

    TaskWorkflow validateDocument =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ValidateDocumentTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    TaskWorkflow classifyDocument =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ClassifyDocumentTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    TaskWorkflow extractPdf =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ExtractPdfContentTask())
                .retryPolicy(processingRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(15000))
                .build());

    TaskWorkflow runOcr =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new RunOcrTask())
                .retryPolicy(processingRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    ExecutionStrategy executionStrategy = new ThreadPoolExecutionStrategy();
    ParallelWorkflow pdfProcessing =
        ParallelWorkflow.builder()
            .name("pdf-processing")
            .workflow(extractPdf)
            .workflow(runOcr)
            .shareContext(true)
            .failFast(false)
            .executionStrategy(executionStrategy)
            .build();

    TaskWorkflow processImage =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ProcessImageTask())
                .retryPolicy(processingRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    ConditionalWorkflow pdfConditional =
        ConditionalWorkflow.builder()
            .name("pdf-conditional")
            .condition(ctx -> "PDF".equals(ctx.get(DOCUMENT_TYPE)))
            .whenTrue(pdfProcessing)
            .build();

    ConditionalWorkflow imageConditional =
        ConditionalWorkflow.builder()
            .name("image-conditional")
            .condition(ctx -> "IMAGE".equals(ctx.get(DOCUMENT_TYPE)))
            .whenTrue(processImage)
            .build();

    TaskWorkflow extractMetadata =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ExtractMetadataTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow analyzeContent =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new AnalyzeContentTask())
                .retryPolicy(processingRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    TaskWorkflow performQualityCheck =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new PerformQualityCheckTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow indexDocument =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new IndexDocumentTask())
                .retryPolicy(processingRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow archiveDocument =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ArchiveDocumentTask())
                .retryPolicy(processingRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    TaskWorkflow generateReport =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new GenerateProcessingReportTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    SequentialWorkflow documentProcessing =
        SequentialWorkflow.builder()
            .name("document-processing-pipeline")
            .workflow(validateDocument)
            .workflow(classifyDocument)
            .workflow(pdfConditional)
            .workflow(imageConditional)
            .workflow(extractMetadata)
            .workflow(analyzeContent)
            .workflow(performQualityCheck)
            .workflow(indexDocument)
            .workflow(archiveDocument)
            .workflow(generateReport)
            .build();

    log.info("\n{}\n", documentProcessing.toTreeString());

    WorkflowResult result = documentProcessing.execute(context);
    executionStrategy.close();

    log.info("\n=== Document Processing Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration());

    @SuppressWarnings("unchecked")
    Map<String, Object> report = (Map<String, Object>) context.get("processingReport");
    if (report != null) {
      log.info("Processing Report:");
      report.forEach((k, v) -> log.info("  {}: {}", k, v));
    }

    if (result.getError() != null) {
      log.error("Error: {}", result.getError().getMessage());
    }
  }

  static void main() throws Exception {
    processDocument();
  }
}
