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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example: Email Campaign Management System
 *
 * <p>Demonstrates a comprehensive email campaign workflow with audience segmentation, template
 * preparation, SMTP health checks, batch email delivery with rate limiting, delivery monitoring,
 * and campaign analytics.
 */
@UtilityClass
@Slf4j
public class EmailCampaignExample {

  public static final String CAMPAIGN_ID = "campaignId";
  public static final String RECIPIENT_COUNT = "recipientCount";

  static class SegmentAudienceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String campaignId = (String) context.get(CAMPAIGN_ID);
      log.info("Segmenting audience for campaign {}", campaignId);

      List<String> recipients = new ArrayList<>();
      for (int i = 1; i <= 1000; i++) {
        recipients.add("user" + i + "@example.com");
      }

      context.put("recipients", recipients);
      context.put(RECIPIENT_COUNT, recipients.size());
      log.info("Audience segmentation complete. Recipients: {}", recipients.size());
    }

    @Override
    public String getName() {
      return "segment-audience";
    }
  }

  static class PrepareTemplateTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String campaignId = (String) context.get(CAMPAIGN_ID);
      log.info("Preparing email template for campaign: {}", campaignId);

      String template =
          "<html><body>"
              + "<h1>Special Offer!</h1>"
              + "<p>Dear {user_name},</p>"
              + "<p>We have an exclusive offer for you.</p>"
              + "</body></html>";

      context.put("emailTemplate", template);
      context.put("templateReady", true);
      log.info("Email template prepared successfully");
    }

    @Override
    public String getName() {
      return "prepare-template";
    }
  }

  static class CheckSMTPHealthTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("Checking SMTP service health...");

      boolean smtpHealthy = Math.random() > 0.1;
      context.put("smtpHealthy", smtpHealthy);

      if (!smtpHealthy) {
        throw new IllegalStateException("SMTP service is unavailable");
      }
      log.info("SMTP service is healthy");
    }

    @Override
    public String getName() {
      return "check-smtp-health";
    }
  }

  static class ValidateCampaignTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String campaignId = (String) context.get(CAMPAIGN_ID);
      boolean templateReady = (Boolean) context.get("templateReady");
      boolean smtpHealthy = (Boolean) context.get("smtpHealthy");
      Integer recipientCount = (Integer) context.get(RECIPIENT_COUNT);

      log.info("Validating campaign: {}", campaignId);

      if (!templateReady || !smtpHealthy || recipientCount == null || recipientCount == 0) {
        throw new IllegalStateException("Campaign validation failed");
      }

      context.put("campaignValidated", true);
      log.info("Campaign validation passed");
    }

    @Override
    public String getName() {
      return "validate-campaign";
    }
  }

  static class SendEmailBatchTask implements Task {
    private final int batchNumber;
    private final int batchSize;

    SendEmailBatchTask(int batchNumber, int batchSize) {
      this.batchNumber = batchNumber;
      this.batchSize = batchSize;
    }

    @Override
    public void execute(WorkflowContext context) {
      @SuppressWarnings("unchecked")
      List<String> recipients = (List<String>) context.get("recipients");
      String campaignId = (String) context.get(CAMPAIGN_ID);

      int startIdx = (batchNumber - 1) * batchSize;
      int endIdx = Math.min(startIdx + batchSize, recipients.size());

      log.info(
          "Sending batch {} of campaign {} to {} recipients",
          batchNumber,
          campaignId,
          endIdx - startIdx);

      int sent = 0;
      int failed = 0;

      for (int i = startIdx; i < endIdx; i++) {
        boolean deliverySuccess = Math.random() > 0.05;

        if (deliverySuccess) {
          sent++;
        } else {
          failed++;
        }

        try {
          Thread.sleep(10);
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
        }
      }

      String batchKey = "batch_" + batchNumber;
      Map<String, Integer> batchResult = new HashMap<>();
      batchResult.put("sent", sent);
      batchResult.put("failed", failed);
      context.put(batchKey, batchResult);

      log.info("Batch {} complete. Sent: {}, Failed: {}", batchNumber, sent, failed);
    }

    @Override
    public String getName() {
      return "send-batch-" + batchNumber;
    }
  }

  static class UpdateCampaignStatusTask implements Task {
    private final String status;

    UpdateCampaignStatusTask(String status) {
      this.status = status;
    }

    @Override
    public void execute(WorkflowContext context) {
      String campaignId = (String) context.get(CAMPAIGN_ID);
      log.info("Updating campaign {} status to: {}", campaignId, status);

      context.put("campaignStatus", status);
      context.put("statusUpdateTime", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

      log.info("Campaign status updated successfully");
    }

    @Override
    public String getName() {
      return "update-status";
    }
  }

  static class GenerateReportTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String campaignId = (String) context.get(CAMPAIGN_ID);
      Integer recipientCount = (Integer) context.get(RECIPIENT_COUNT);

      log.info("Generating campaign report for: {}", campaignId);

      int totalSent = 0;
      int totalFailed = 0;

      for (int i = 1; i <= 10; i++) {
        String batchKey = "batch_" + i;
        @SuppressWarnings("unchecked")
        Map<String, Integer> batchResult = (Map<String, Integer>) context.get(batchKey);

        if (batchResult != null) {
          totalSent += batchResult.get("sent");
          totalFailed += batchResult.get("failed");
        }
      }

      double successRate = recipientCount > 0 ? (totalSent * 100.0) / recipientCount : 0.0;

      context.put("totalSent", totalSent);
      context.put("totalFailed", totalFailed);
      context.put("successRate", successRate);
      context.put("reportGenerated", true);

      log.info(
          "Report generated. Total sent: {}, Failed: {}, Success rate: {}%",
          totalSent, totalFailed, successRate);
    }

    @Override
    public String getName() {
      return "generate-report";
    }
  }

  static void executeCampaign() {
    log.info("\n=== Email Campaign Management Workflow Example ===\n");

    WorkflowContext context = new WorkflowContext();
    context.put(CAMPAIGN_ID, "CAMP-2025-HOLIDAY");
    context.put("targetSegment", "premium_customers");

    RetryPolicy smtpRetry =
        new RetryPolicy() {
          @Override
          public boolean shouldRetry(int attempt, Exception error) {
            return attempt < 3;
          }

          @Override
          public BackoffStrategy backoff() {
            return BackoffStrategy.exponential(500);
          }
        };

    TaskWorkflow segmentAudience =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new SegmentAudienceTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(10000))
                .build());

    TaskWorkflow prepareTemplate =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new PrepareTemplateTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    TaskWorkflow checkSMTPHealth =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new CheckSMTPHealthTask())
                .retryPolicy(smtpRetry)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    ParallelWorkflow preparation =
        ParallelWorkflow.builder()
            .name("campaign-preparation")
            .workflow(segmentAudience)
            .workflow(prepareTemplate)
            .workflow(checkSMTPHealth)
            .shareContext(true)
            .failFast(true)
            .build();

    TaskWorkflow validateCampaign =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new ValidateCampaignTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    List<com.workflow.Workflow> emailBatches = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      emailBatches.add(
          new TaskWorkflow(
              TaskDescriptor.builder()
                  .task(new SendEmailBatchTask(i, 100))
                  .retryPolicy(smtpRetry)
                  .timeoutPolicy(TimeoutPolicy.ofMillis(30000))
                  .build()));
    }

    ParallelWorkflow sendEmails =
        ParallelWorkflow.builder()
            .name("send-email-batches")
            .workflows(emailBatches)
            .shareContext(true)
            .failFast(false)
            .build();

    TaskWorkflow updateStatus =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new UpdateCampaignStatusTask("COMPLETED"))
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(3000))
                .build());

    TaskWorkflow generateReport =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .task(new GenerateReportTask())
                .retryPolicy(RetryPolicy.NONE)
                .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
                .build());

    SequentialWorkflow campaignWorkflow =
        SequentialWorkflow.builder()
            .name("email-campaign-pipeline")
            .workflow(preparation)
            .workflow(validateCampaign)
            .workflow(sendEmails)
            .workflow(updateStatus)
            .workflow(generateReport)
            .build();

    WorkflowResult result = campaignWorkflow.execute(context);

    log.info("\n=== Campaign Execution Results ===");
    log.info("Status: {}", result.getStatus());
    log.info("Duration: {}", result.getExecutionDuration());
    log.info("Campaign ID: {}", context.get(CAMPAIGN_ID));
    log.info("Total Sent: {}", context.get("totalSent"));
    log.info("Total Failed: {}", context.get("totalFailed"));
    log.info("Success Rate: {}%", context.get("successRate"));
    log.info("Campaign Status: {}", context.get("campaignStatus"));

    if (result.getError() != null) {
      log.error("Error: {}", result.getError().getMessage());
    }
  }

  public static void main(String[] args) {
    executeCampaign();
  }
}
