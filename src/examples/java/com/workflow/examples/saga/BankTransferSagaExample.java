package com.workflow.examples.saga;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.saga.SagaStep;
import com.workflow.task.Task;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example demonstrating SagaWorkflow for bank account transfer operations.
 *
 * <p><b>Business Scenario:</b> A banking system performing money transfers between accounts across
 * different banks or services. The transfer involves:
 *
 * <ul>
 *   <li>Account validation (source and destination)
 *   <li>Balance verification
 *   <li>Fraud detection check
 *   <li>Debit from source account
 *   <li>Credit to destination account
 *   <li>Transaction fee processing
 *   <li>Notification sending
 * </ul>
 *
 * <p><b>Challenge:</b> Money transfers are critical operations requiring ACID-like guarantees
 * across distributed banking systems. If any step fails after debiting the source account, the
 * funds must be returned to ensure no money is lost.
 *
 * <p><b>Solution:</b> SagaWorkflow ensures that partial transfers are automatically rolled back,
 * maintaining the integrity of account balances across the distributed system.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * BankTransferSagaExample example = new BankTransferSagaExample();
 *
 * TransferRequest request = TransferRequest.builder()
 *     .transferId("TXF-001")
 *     .sourceAccountId("ACC-12345")
 *     .destinationAccountId("ACC-67890")
 *     .amount(new BigDecimal("1500.00"))
 *     .currency("USD")
 *     .purpose("Invoice Payment")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("transferRequest", request);
 *
 * WorkflowResult result = example.getWorkflow().execute(context);
 *
 * if (result.isSuccess()) {
 *     String confirmationCode = context.getTyped("confirmationCode", String.class);
 *     System.out.println("Transfer successful: " + confirmationCode);
 * } else {
 *     System.err.println("Transfer failed and rolled back: " + result.getError().getMessage());
 * }
 * }</pre>
 */
@Slf4j
public class BankTransferSagaExample {

  // Context keys
  private static final String TRANSFER_REQUEST = "transferRequest";
  private static final String SOURCE_ACCOUNT_LOCKED = "sourceAccountLocked";
  private static final String DEST_ACCOUNT_LOCKED = "destinationAccountLocked";
  private static final String DEBIT_REFERENCE = "debitReference";
  private static final String CREDIT_REFERENCE = "creditReference";
  private static final String FEE_TRANSACTION_ID = "feeTransactionId";
  private static final String CONFIRMATION_CODE = "confirmationCode";
  private static final String FRAUD_CHECK_ID = "fraudCheckId";
  public static final String SOURCE_ACCOUNT_BALANCE = "sourceAccountBalance";

  /**
   * Creates the bank transfer saga workflow.
   *
   * @return configured SagaWorkflow
   */
  public Workflow getWorkflow() {
    return SagaWorkflow.builder()
        .name("BankTransferSaga")
        // Step 1: Validate Accounts
        .step(
            SagaStep.builder()
                .name("ValidateAccounts")
                .action(new ValidateAccountsTask())
                .build()) // Read-only validation
        // Step 2: Check Balance
        .step(
            SagaStep.builder()
                .name("CheckBalance")
                .action(new CheckBalanceTask())
                .build()) // Read-only check
        // Step 3: Fraud Detection
        .step(
            SagaStep.builder()
                .name("FraudDetection")
                .action(new FraudDetectionTask())
                .build()) // Read-only check
        // Step 4: Lock Accounts
        .step(
            SagaStep.builder()
                .name("LockAccounts")
                .action(new LockAccountsTask())
                .compensation(new UnlockAccountsTask())
                .build())
        // Step 5: Debit Source Account
        .step(
            SagaStep.builder()
                .name("DebitSourceAccount")
                .action(new DebitSourceAccountTask())
                .compensation(new ReverseDebitTask())
                .build())
        // Step 6: Credit Destination Account
        .step(
            SagaStep.builder()
                .name("CreditDestinationAccount")
                .action(new CreditDestinationAccountTask())
                .compensation(new ReverseCreditTask())
                .build())
        // Step 7: Process Transfer Fee
        .step(
            SagaStep.builder()
                .name("ProcessTransferFee")
                .action(new ProcessTransferFeeTask())
                .compensation(new RefundTransferFeeTask())
                .build())
        // Step 8: Generate Confirmation
        .step(
            SagaStep.builder()
                .name("GenerateConfirmation")
                .action(new GenerateConfirmationTask())
                .build())
        // Step 9: Send Notifications
        .step(
            SagaStep.builder()
                .name("SendNotifications")
                .action(new SendNotificationsTask())
                .build()) // Best effort
        .build();
  }

  static void main() {
    BankTransferSagaExample example = new BankTransferSagaExample();

    // Scenario 1: Successful transfer
    log.info("=== Scenario 1: Successful Transfer ===");
    runSuccessfulTransfer(example);

    // Scenario 2: Insufficient balance
    log.info("\n=== Scenario 2: Insufficient Balance ===");
    runInsufficientBalanceTransfer(example);

    // Scenario 3: Fraud detected
    log.info("\n=== Scenario 3: Fraud Detected ===");
    runFraudDetectedTransfer(example);

    // Scenario 4: Credit failure - debit reversed
    log.info("\n=== Scenario 4: Credit Failure with Automatic Reversal ===");
    runCreditFailureTransfer(example);
  }

  private static void runSuccessfulTransfer(BankTransferSagaExample example) {
    TransferRequest request =
        TransferRequest.builder()
            .transferId("TXF-" + UUID.randomUUID().toString().substring(0, 8))
            .sourceAccountId("ACC-12345")
            .sourceAccountName("John Doe")
            .sourceBank("Bank of America")
            .destinationAccountId("ACC-67890")
            .destinationAccountName("Jane Smith")
            .destinationBank("Chase Bank")
            .amount(new BigDecimal("1500.00"))
            .currency("USD")
            .purpose("Invoice Payment #INV-2024-001")
            .initiatedAt(LocalDateTime.now())
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(TRANSFER_REQUEST, request);
    context.put(SOURCE_ACCOUNT_BALANCE, new BigDecimal("5000.00"));
    context.put("destinationAccountActive", true);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isSuccess()) {
      String confirmationCode = context.getTyped(CONFIRMATION_CODE, String.class);
      log.info("✅ Transfer successful!");
      log.info("   Confirmation Code: {}", confirmationCode);
      log.info("   Amount: ${} {}", request.getAmount(), request.getCurrency());
      log.info("   From: {} ({})", request.getSourceAccountName(), request.getSourceAccountId());
      log.info(
          "   To: {} ({})", request.getDestinationAccountName(), request.getDestinationAccountId());
    }
  }

  private static void runInsufficientBalanceTransfer(BankTransferSagaExample example) {
    TransferRequest request =
        TransferRequest.builder()
            .transferId("TXF-" + UUID.randomUUID().toString().substring(0, 8))
            .sourceAccountId("ACC-11111")
            .sourceAccountName("Bob Johnson")
            .sourceBank("Wells Fargo")
            .destinationAccountId("ACC-22222")
            .destinationAccountName("Alice Williams")
            .destinationBank("Citibank")
            .amount(new BigDecimal("5000.00"))
            .currency("USD")
            .purpose("Large Purchase")
            .initiatedAt(LocalDateTime.now())
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(TRANSFER_REQUEST, request);
    context.put(SOURCE_ACCOUNT_BALANCE, new BigDecimal("500.00")); // Insufficient balance

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      log.info("❌ Transfer failed: {}", result.getError().getMessage());
      log.info("   No funds were debited from the account");
    }
  }

  private static void runFraudDetectedTransfer(BankTransferSagaExample example) {
    TransferRequest request =
        TransferRequest.builder()
            .transferId("TXF-" + UUID.randomUUID().toString().substring(0, 8))
            .sourceAccountId("ACC-33333")
            .sourceAccountName("Suspicious User")
            .sourceBank("Bank of America")
            .destinationAccountId("ACC-44444")
            .destinationAccountName("Unknown Recipient")
            .destinationBank("Foreign Bank")
            .amount(new BigDecimal("50000.00")) // Large amount
            .currency("USD")
            .purpose("Urgent Transfer")
            .initiatedAt(LocalDateTime.now())
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(TRANSFER_REQUEST, request);
    context.put(SOURCE_ACCOUNT_BALANCE, new BigDecimal("60000.00"));
    context.put("simulateFraudDetection", true); // Trigger fraud detection

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      log.info("❌ Transfer blocked: {}", result.getError().getMessage());
      log.info("   Account security team has been notified");
    }
  }

  private static void runCreditFailureTransfer(BankTransferSagaExample example) {
    TransferRequest request =
        TransferRequest.builder()
            .transferId("TXF-" + UUID.randomUUID().toString().substring(0, 8))
            .sourceAccountId("ACC-55555")
            .sourceAccountName("Michael Brown")
            .sourceBank("PNC Bank")
            .destinationAccountId("ACC-66666")
            .destinationAccountName("Closed Account")
            .destinationBank("TD Bank")
            .amount(new BigDecimal("2500.00"))
            .currency("USD")
            .purpose("Payment")
            .initiatedAt(LocalDateTime.now())
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(TRANSFER_REQUEST, request);
    context.put(SOURCE_ACCOUNT_BALANCE, new BigDecimal("10000.00"));
    context.put("simulateCreditFailure", true); // Trigger credit failure

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      log.info("❌ Transfer failed: {}", result.getError().getMessage());
      log.info(
          "   Compensation executed: Funds automatically returned to source account ({})",
          request.getSourceAccountId());
    }
  }

  // ==================== Domain Models ====================

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class TransferRequest {
    private String transferId;
    private String sourceAccountId;
    private String sourceAccountName;
    private String sourceBank;
    private String destinationAccountId;
    private String destinationAccountName;
    private String destinationBank;
    private BigDecimal amount;
    private String currency;
    private String purpose;
    private LocalDateTime initiatedAt;
  }

  // ==================== Action Tasks ====================

  /** Validates source and destination accounts. */
  static class ValidateAccountsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info("→ Validating accounts for transfer: {}", request.getTransferId());

      // Validate source account
      if (request.getSourceAccountId() == null || request.getSourceAccountId().trim().isEmpty()) {
        throw new TaskExecutionException("Invalid source account ID");
      }

      // Validate destination account
      if (request.getDestinationAccountId() == null
          || request.getDestinationAccountId().trim().isEmpty()) {
        throw new TaskExecutionException("Invalid destination account ID");
      }

      // Cannot transfer to same account
      if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
        throw new TaskExecutionException("Cannot transfer to the same account");
      }

      // Validate amount
      if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new TaskExecutionException("Transfer amount must be positive");
      }

      if (request.getAmount().compareTo(new BigDecimal("100000.00")) > 0) {
        log.warn("⚠ Large transfer amount detected: ${}", request.getAmount());
      }

      log.info("✓ Account validation passed");
    }
  }

  /** Checks if source account has sufficient balance. */
  static class CheckBalanceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      BigDecimal balance = context.getTyped(SOURCE_ACCOUNT_BALANCE, BigDecimal.class);

      log.info("→ Checking balance for account: {}", request.getSourceAccountId());
      log.info("   Available Balance: ${}", balance);
      log.info("   Transfer Amount: ${}", request.getAmount());

      BigDecimal requiredAmount =
          request.getAmount().add(new BigDecimal("5.00")); // Include transfer fee

      if (balance.compareTo(requiredAmount) < 0) {
        throw new TaskExecutionException(
            String.format(
                "Insufficient funds: Available $%s, Required $%s", balance, requiredAmount));
      }

      log.info("✓ Sufficient balance available");
    }
  }

  /** Performs fraud detection checks. */
  static class FraudDetectionTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info("→ Running fraud detection for transfer: {}", request.getTransferId());

      // Simulate fraud detection
      if (Boolean.TRUE.equals(context.get("simulateFraudDetection"))) {
        throw new TaskExecutionException(
            "Suspicious activity detected: Large transfer to unverified account. "
                + "Transfer blocked for security review.");
      }

      // Check for unusual patterns
      if (request.getAmount().compareTo(new BigDecimal("10000.00")) > 0) {
        log.warn("⚠ High-value transfer flagged for enhanced monitoring");
      }

      String checkId = "FRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(FRAUD_CHECK_ID, checkId);

      log.info("✓ Fraud detection passed (Check ID: {})", checkId);
    }
  }

  /** Locks both accounts for the transfer operation. */
  static class LockAccountsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info("→ Locking accounts for transfer");

      // Lock source account
      log.info("   Locking source account: {}", request.getSourceAccountId());
      context.put(SOURCE_ACCOUNT_LOCKED, true);

      // Lock destination account
      log.info("   Locking destination account: {}", request.getDestinationAccountId());
      context.put(DEST_ACCOUNT_LOCKED, true);

      log.info("✓ Accounts locked successfully");
    }
  }

  /** Debits amount from source account. */
  static class DebitSourceAccountTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info(
          "→ Debiting ${} from account: {}", request.getAmount(), request.getSourceAccountId());

      // Simulate debit operation
      String debitRef = "DBT-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
      context.put(DEBIT_REFERENCE, debitRef);

      BigDecimal balance = context.getTyped(SOURCE_ACCOUNT_BALANCE, BigDecimal.class);
      BigDecimal newBalance = balance.subtract(request.getAmount());
      context.put(SOURCE_ACCOUNT_BALANCE, newBalance);

      log.info("✓ Debit successful: {} (New Balance: ${})", debitRef, newBalance);
    }
  }

  /** Credits amount to destination account. */
  static class CreditDestinationAccountTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info(
          "→ Crediting ${} to account: {}", request.getAmount(), request.getDestinationAccountId());

      // Simulate credit failure
      if (Boolean.TRUE.equals(context.get("simulateCreditFailure"))) {
        throw new TaskExecutionException(
            "Destination account is closed or inactive: " + request.getDestinationAccountId());
      }

      // Simulate credit operation
      String creditRef = "CRD-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
      context.put(CREDIT_REFERENCE, creditRef);

      log.info("✓ Credit successful: {}", creditRef);
    }
  }

  /** Processes transfer fee. */
  static class ProcessTransferFeeTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info("→ Processing transfer fee for transfer: {}", request.getTransferId());

      BigDecimal fee = new BigDecimal("5.00"); // Fixed fee
      log.info("→ Processing transfer fee: ${}", fee);

      String feeTransactionId = "FEE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(FEE_TRANSACTION_ID, feeTransactionId);

      BigDecimal balance = context.getTyped(SOURCE_ACCOUNT_BALANCE, BigDecimal.class);
      BigDecimal newBalance = balance.subtract(fee);
      context.put(SOURCE_ACCOUNT_BALANCE, newBalance);

      log.info("✓ Transfer fee processed: {} (Fee: ${})", feeTransactionId, fee);
    }
  }

  /** Generates transfer confirmation. */
  static class GenerateConfirmationTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      log.info("→ Generating transfer confirmation for transfer: {}", request.getTransferId());

      String confirmationCode =
          "CONF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
      context.put(CONFIRMATION_CODE, confirmationCode);

      log.info("✓ Confirmation generated: {}", confirmationCode);
    }
  }

  /** Sends transfer notifications. */
  static class SendNotificationsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
      String confirmationCode = context.getTyped(CONFIRMATION_CODE, String.class);

      log.info("→ Sending transfer notifications");

      // Notify sender
      log.info(
          "   Email sent to {} (Source): Transfer of ${} completed",
          request.getSourceAccountName(),
          request.getAmount());

      // Notify recipient
      log.info(
          "   Email sent to {} (Destination): You received ${} from {}",
          request.getDestinationAccountName(),
          request.getAmount(),
          request.getSourceAccountName());

      log.info("✓ Notifications sent (Confirmation: {})", confirmationCode);
    }
  }

  // ==================== Compensation Tasks ====================

  /** Unlocks accounts after failure. */
  static class UnlockAccountsTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);

      if (Boolean.TRUE.equals(context.get(SOURCE_ACCOUNT_LOCKED))) {
        log.warn("↩ Unlocking source account: {}", request.getSourceAccountId());
        context.remove(SOURCE_ACCOUNT_LOCKED);
      }

      if (Boolean.TRUE.equals(context.get(DEST_ACCOUNT_LOCKED))) {
        log.warn("↩ Unlocking destination account: {}", request.getDestinationAccountId());
        context.remove(DEST_ACCOUNT_LOCKED);
      }

      log.info("✓ Accounts unlocked");
    }
  }

  /** Reverses debit from source account. */
  static class ReverseDebitTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String debitRef = context.getTyped(DEBIT_REFERENCE, String.class);

      if (debitRef != null) {
        TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
        log.warn("↩ Reversing debit: {} (Amount: ${})", debitRef, request.getAmount());

        // Restore balance
        BigDecimal balance = context.getTyped(SOURCE_ACCOUNT_BALANCE, BigDecimal.class);
        BigDecimal restoredBalance = balance.add(request.getAmount());
        context.put(SOURCE_ACCOUNT_BALANCE, restoredBalance);

        String reversalRef = "REV-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        context.put("debitReversalRef", reversalRef);
        context.remove(DEBIT_REFERENCE);

        log.info("✓ Debit reversed: {} (Balance restored: ${})", reversalRef, restoredBalance);
      }
    }
  }

  /** Reverses credit to destination account. */
  static class ReverseCreditTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String creditRef = context.getTyped(CREDIT_REFERENCE, String.class);

      if (creditRef != null) {
        TransferRequest request = context.getTyped(TRANSFER_REQUEST, TransferRequest.class);
        log.warn("↩ Reversing credit: {} (Amount: ${})", creditRef, request.getAmount());

        String reversalRef = "REV-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        context.put("creditReversalRef", reversalRef);
        context.remove(CREDIT_REFERENCE);

        log.info("✓ Credit reversed: {}", reversalRef);
      }
    }
  }

  /** Refunds transfer fee. */
  static class RefundTransferFeeTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String feeTransactionId = context.getTyped(FEE_TRANSACTION_ID, String.class);

      if (feeTransactionId != null) {
        BigDecimal fee = new BigDecimal("5.00");
        log.warn("↩ Refunding transfer fee: {} (Amount: ${})", feeTransactionId, fee);

        // Restore fee
        BigDecimal balance = context.getTyped(SOURCE_ACCOUNT_BALANCE, BigDecimal.class);
        BigDecimal restoredBalance = balance.add(fee);
        context.put(SOURCE_ACCOUNT_BALANCE, restoredBalance);

        String refundRef = "FREF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        context.put("feeRefundRef", refundRef);
        context.remove(FEE_TRANSACTION_ID);

        log.info("✓ Transfer fee refunded: {}", refundRef);
      }
    }
  }
}
