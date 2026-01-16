package com.workflow.examples.javascript;

import com.workflow.ConditionalWorkflow;
import com.workflow.DynamicBranchingWorkflow;
import com.workflow.JavascriptWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.script.InlineScriptProvider;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Advanced example demonstrating webhook processing with JavaScript workflows.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Webhook signature verification
 *   <li>Event type routing
 *   <li>Payload transformation
 *   <li>Conditional processing based on webhook metadata
 *   <li>Response generation
 *   <li>Error handling and retry logic
 * </ul>
 *
 * <p>Use case: A webhook processor that receives events from various sources (GitHub, Stripe,
 * Shopify), verifies signatures, routes to appropriate handlers, and generates responses.
 */
@UtilityClass
@Slf4j
public class WebhookProcessingExample {

  public static final String PAYLOAD = "payload";

  public static void main(String[] args) {
    log.info("=== Webhook Processing Examples ===\n");

    githubWebhookProcessing();
    stripeWebhookProcessing();
    genericWebhookRouter();

    log.info("\n=== Examples completed successfully ===");
  }

  /**
   * Example 1: GitHub webhook processing.
   *
   * <p>Processes GitHub webhooks with signature verification and event-specific handling.
   */
  private static void githubWebhookProcessing() {
    log.info("Example 1: GitHub Webhook Processing");

    var signatureVerification =
        JavascriptWorkflow.builder()
            .name("VerifySignature")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var payload = ctx.get('payload');
                    var signature = ctx.get('signature');
                    var secret = ctx.get('webhookSecret');

                    // Simulate HMAC-SHA256 verification
                    // In production, use crypto library
                    var expectedSignature = 'sha256=' + signature.substring(7);

                    var isValid = signature === expectedSignature;
                    ctx.put('signatureValid', isValid);

                    if (!isValid) {
                        ctx.put('error', 'Invalid webhook signature');
                        ctx.put('statusCode', 401);
                    }
                    """))
            .build();

    var eventRouting =
        JavascriptWorkflow.builder()
            .name("DetermineEventType")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var payload = JSON.parse(ctx.get('payload'));
                    var event = ctx.get('githubEvent');

                    ctx.put('eventType', event);
                    ctx.put('parsedPayload', payload);

                    // Extract common metadata
                    var metadata = {
                        repository: payload.repository?.full_name,
                        sender: payload.sender?.login,
                        timestamp: payload.created_at || new Date().toISOString()
                    };

                    ctx.put('metadata', metadata);
                    """))
            .build();

    var pushEventHandler =
        createSimpleWorkflow(
            "HandlePushEvent",
            """
            var payload = ctx.get('parsedPayload');

            var commits = payload.commits || [];
            var branch = payload.ref?.replace('refs/heads/', '');

            var summary = {
                branch: branch,
                commitCount: commits.length,
                pusher: payload.pusher?.name,
                added: commits.reduce((sum, c) => sum + (c.added?.length || 0), 0),
                modified: commits.reduce((sum, c) => sum + (c.modified?.length || 0), 0),
                removed: commits.reduce((sum, c) => sum + (c.removed?.length || 0), 0)
            };

            ctx.put('eventSummary', summary);
            ctx.put('statusCode', 200);
            ctx.put('response', { status: 'processed', type: 'push' });
            """);

    var prEventHandler =
        createSimpleWorkflow(
            "HandlePullRequestEvent",
            """
            var payload = ctx.get('parsedPayload');
            var pr = payload.pull_request;

            var summary = {
                action: payload.action,
                prNumber: pr.number,
                title: pr.title,
                author: pr.user.login,
                state: pr.state,
                commits: pr.commits,
                additions: pr.additions,
                deletions: pr.deletions
            };

            ctx.put('eventSummary', summary);
            ctx.put('statusCode', 200);
            ctx.put('response', { status: 'processed', type: 'pull_request' });
            """);

    Workflow githubWebhook =
        SequentialWorkflow.builder()
            .name("GitHubWebhookProcessor")
            .workflow(signatureVerification)
            .workflow(
                ConditionalWorkflow.builder()
                    .condition(ctx -> Boolean.TRUE.equals(ctx.get("signatureValid")))
                    .whenTrue(
                        SequentialWorkflow.builder()
                            .workflow(eventRouting)
                            .workflow(
                                DynamicBranchingWorkflow.builder()
                                    .selector(ctx -> ctx.getTyped("eventType", String.class))
                                    .branch("push", pushEventHandler)
                                    .branch("pull_request", prEventHandler)
                                    .defaultBranch(
                                        createSimpleWorkflow(
                                            "DefaultHandler",
                                            "ctx.put('statusCode', 200); "
                                                + "ctx.put('response', { status:"
                                                + " 'ignored' });"))
                                    .build())
                            .build())
                    .whenFalse(
                        createSimpleWorkflow(
                            "Unauthorized",
                            "ctx.put('statusCode', 401); "
                                + "ctx.put('response', { error: 'Invalid signature' });"))
                    .build())
            .build();

    // Simulate GitHub push webhook
    WorkflowContext context = new WorkflowContext();
    context.put("githubEvent", "push");
    context.put("signature", "sha256=abc123");
    context.put("webhookSecret", "my-secret");
    context.put(
        PAYLOAD,
        """
        {
            "ref": "refs/heads/main",
            "commits": [
                {"added": ["file1.txt"], "modified": ["file2.txt"], "removed": []}
            ],
            "pusher": {"name": "john_doe"},
            "repository": {"full_name": "company/repo"},
            "sender": {"login": "john_doe"}
        }
        """);

    githubWebhook.execute(context);

    log.info("Status Code: {}", context.get("statusCode"));
    log.info("Event Summary: {}", context.get("eventSummary"));
    log.info("Response: {}\n", context.get("response"));
  }

  /**
   * Example 2: Stripe webhook processing with event validation.
   *
   * <p>Processes Stripe payment webhooks with idempotency checking and event handling.
   */
  private static void stripeWebhookProcessing() {
    log.info("Example 2: Stripe Webhook Processing");

    var validation =
        JavascriptWorkflow.builder()
            .name("ValidateStripeEvent")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var payload = JSON.parse(ctx.get('payload'));
                    var processedEvents = ctx.get('processedEvents') || [];

                    // Idempotency check
                    var isDuplicate = processedEvents.includes(payload.id);
                    if (isDuplicate) {
                        ctx.put('isDuplicate', true);
                        ctx.put('statusCode', 200);
                        ctx.put('response', { status: 'duplicate', id: payload.id });
                        return;
                    }

                    ctx.put('isDuplicate', false);
                    ctx.put('eventId', payload.id);
                    ctx.put('eventType', payload.type);
                    ctx.put('eventData', payload.data);
                    """))
            .build();

    var paymentSucceededHandler =
        createSimpleWorkflow(
            "HandlePaymentSucceeded",
            """
            var eventData = ctx.get('eventData');
            var payment = eventData.object;

            var result = {
                customerId: payment.customer,
                amount: payment.amount / 100,
                currency: payment.currency,
                status: payment.status,
                receiptEmail: payment.receipt_email
            };

            ctx.put('paymentResult', result);
            ctx.put('statusCode', 200);
            ctx.put('response', { status: 'processed', type: 'payment.succeeded' });

            // Mark as processed
            var processed = ctx.get('processedEvents') || [];
            processed.push(ctx.get('eventId'));
            ctx.put('processedEvents', processed);
            """);

    var paymentFailedHandler =
        createSimpleWorkflow(
            "HandlePaymentFailed",
            """
            var eventData = ctx.get('eventData');
            var payment = eventData.object;

            var result = {
                customerId: payment.customer,
                amount: payment.amount / 100,
                failureCode: payment.failure_code,
                failureMessage: payment.failure_message
            };

            ctx.put('paymentResult', result);
            ctx.put('statusCode', 200);
            ctx.put('response', { status: 'processed', type: 'payment.failed' });

            // Mark as processed
            var processed = ctx.get('processedEvents') || [];
            processed.push(ctx.get('eventId'));
            ctx.put('processedEvents', processed);
            """);

    Workflow stripeWebhook =
        SequentialWorkflow.builder()
            .name("StripeWebhookProcessor")
            .workflow(validation)
            .workflow(
                ConditionalWorkflow.builder()
                    .condition(ctx -> !Boolean.TRUE.equals(ctx.get("isDuplicate")))
                    .whenTrue(
                        DynamicBranchingWorkflow.builder()
                            .selector(ctx -> ctx.getTyped("eventType", String.class))
                            .branch("payment_intent.succeeded", paymentSucceededHandler)
                            .branch("payment_intent.payment_failed", paymentFailedHandler)
                            .defaultBranch(
                                createSimpleWorkflow(
                                    "DefaultHandler",
                                    "ctx.put('statusCode', 200); "
                                        + "ctx.put('response', { status: 'ignored' });"))
                            .build())
                    .build())
            .build();

    // Simulate Stripe payment success webhook
    WorkflowContext context = new WorkflowContext();
    context.put(
        PAYLOAD,
        """
        {
            "id": "evt_123",
            "type": "payment_intent.succeeded",
            "data": {
                "object": {
                    "customer": "cus_456",
                    "amount": 9999,
                    "currency": "usd",
                    "status": "succeeded",
                    "receipt_email": "customer@example.com"
                }
            }
        }
        """);

    stripeWebhook.execute(context);

    log.info("Status Code: {}", context.get("statusCode"));
    log.info("Payment Result: {}", context.get("paymentResult"));
    log.info("Response: {}\n", context.get("response"));
  }

  /**
   * Example 3: Generic webhook router with custom transformation.
   *
   * <p>Demonstrates a generic webhook processor that can handle multiple webhook sources with
   * custom transformations.
   */
  private static void genericWebhookRouter() {
    log.info("Example 3: Generic Webhook Router");

    var router =
        JavascriptWorkflow.builder()
            .name("RouteWebhook")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var source = ctx.get('source');
                    var payload = JSON.parse(ctx.get('payload'));

                    // Extract normalized event data
                    var normalizedEvent = {
                        source: source,
                        timestamp: new Date().toISOString(),
                        eventType: null,
                        data: null
                    };

                    switch(source) {
                        case 'github':
                            normalizedEvent.eventType = ctx.get('githubEvent');
                            normalizedEvent.data = {
                                repository: payload.repository?.full_name,
                                action: payload.action,
                                sender: payload.sender?.login
                            };
                            break;
                        case 'stripe':
                            normalizedEvent.eventType = payload.type;
                            normalizedEvent.data = {
                                eventId: payload.id,
                                objectType: payload.data?.object?.object,
                                amount: payload.data?.object?.amount
                            };
                            break;
                        case 'shopify':
                            normalizedEvent.eventType = ctx.get('shopifyTopic');
                            normalizedEvent.data = {
                                orderId: payload.id,
                                customer: payload.customer,
                                total: payload.total_price
                            };
                            break;
                    }

                    ctx.put('normalizedEvent', normalizedEvent);
                    ctx.put('routeKey', source + '.' + normalizedEvent.eventType);
                    """))
            .build();

    var processor =
        JavascriptWorkflow.builder()
            .name("ProcessEvent")
            .scriptProvider(
                new InlineScriptProvider(
                    """
                    var event = ctx.get('normalizedEvent');

                    // Simulate processing
                    var processed = {
                        id: Math.random().toString(36).substring(7),
                        source: event.source,
                        type: event.eventType,
                        processedAt: new Date().toISOString(),
                        data: event.data,
                        status: 'processed'
                    };

                    ctx.put('processedEvent', processed);
                    ctx.put('statusCode', 200);
                    """))
            .build();

    Workflow genericRouter =
        SequentialWorkflow.builder()
            .name("GenericWebhookRouter")
            .workflow(router)
            .workflow(processor)
            .build();

    // Test with different sources
    String[][] payloads = {
      {
        "github",
        "push",
        """
                    {"repository": {"full_name": "user/repo"}, "sender": {"login": "user"}}
                    """
      },
      {
        "stripe",
        null,
        """
                    {"id": "evt_123", "type": "payment_intent.succeeded", "data": {"object":
                     {"object": "payment_intent", "amount": 9999}}}
                    """
      },
      {
        "shopify",
        "orders/create",
        """
                    {"id": 123, "customer": {"id": 456}, "total_price": "99.99"}
                    """
      }
    };

    for (String[] test : payloads) {
      WorkflowContext context = new WorkflowContext();
      context.put("source", test[0]);
      if (test[1] != null) {
        if (test[0].equals("github")) context.put("githubEvent", test[1]);
        if (test[0].equals("shopify")) context.put("shopifyTopic", test[1]);
      }
      context.put(PAYLOAD, test[2]);

      genericRouter.execute(context);

      log.info("Source: {}", test[0]);
      log.info("Normalized Event: {}", context.get("normalizedEvent"));
      log.info("Processed Event: {}", context.get("processedEvent"));
      log.info("");
    }
  }

  private static Workflow createSimpleWorkflow(String name, String script) {
    return JavascriptWorkflow.builder()
        .name(name)
        .scriptProvider(new InlineScriptProvider(script))
        .build();
  }
}
