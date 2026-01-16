package com.workflow.examples.saga;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.saga.SagaStep;
import com.workflow.task.Task;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example demonstrating SagaWorkflow for cloud infrastructure provisioning.
 *
 * <p><b>Business Scenario:</b> A cloud platform provisioning infrastructure resources for a new
 * application deployment. The provisioning involves:
 *
 * <ul>
 *   <li>Virtual machine instance creation
 *   <li>Storage volume attachment
 *   <li>Database setup
 *   <li>Load balancer configuration
 *   <li>Security group creation
 *   <li>DNS record registration
 *   <li>SSL certificate provisioning
 * </ul>
 *
 * <p><b>Challenge:</b> Cloud resource provisioning involves multiple API calls to different
 * services. If any provisioning step fails, all previously created resources must be cleaned up to
 * avoid orphaned resources and unnecessary costs.
 *
 * <p><b>Solution:</b> SagaWorkflow ensures automatic cleanup (de-provisioning) of all resources if
 * any step fails, preventing resource leaks and controlling cloud costs.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * CloudProvisioningSagaExample example = new CloudProvisioningSagaExample();
 *
 * ProvisioningRequest request = ProvisioningRequest.builder()
 *     .projectId("PROJ-123")
 *     .environment("production")
 *     .instanceType("t3.large")
 *     .storageSize(100) // GB
 *     .databaseType("PostgreSQL")
 *     .domain("myapp.example.com")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("provisioningRequest", request);
 *
 * WorkflowResult result = example.getWorkflow().execute(context);
 *
 * if (result.isSuccess()) {
 *     String deploymentId = context.getTyped("deploymentId", String.class);
 *     System.out.println("Infrastructure provisioned: " + deploymentId);
 * } else {
 *     System.err.println("Provisioning failed and rolled back: " + result.getError().getMessage());
 * }
 * }</pre>
 */
@Slf4j
public class CloudProvisioningSagaExample {

  // Context keys
  private static final String PROVISIONING_REQUEST = "provisioningRequest";
  private static final String VM_INSTANCE_ID = "vmInstanceId";
  private static final String STORAGE_VOLUME_ID = "storageVolumeId";
  private static final String DATABASE_INSTANCE_ID = "databaseInstanceId";
  private static final String LOAD_BALANCER_ID = "loadBalancerId";
  private static final String SECURITY_GROUP_ID = "securityGroupId";
  private static final String DNS_RECORD_ID = "dnsRecordId";
  private static final String SSL_CERTIFICATE_ID = "sslCertificateId";
  private static final String DEPLOYMENT_ID = "deploymentId";
  public static final String PRODUCTION = "production";
  public static final String POSTGRE_SQL = "PostgreSQL";

  /**
   * Creates the cloud infrastructure provisioning saga workflow.
   *
   * @return configured SagaWorkflow
   */
  public Workflow getWorkflow() {
    return SagaWorkflow.builder()
        .name("CloudProvisioningSaga")
        // Step 1: Validate Request
        .step(
            SagaStep.builder()
                .name("ValidateRequest")
                .action(new ValidateProvisioningRequestTask())
                .build()) // Read-only validation
        // Step 2: Create VM Instance
        .step(
            SagaStep.builder()
                .name("CreateVMInstance")
                .action(new CreateVMInstanceTask())
                .compensation(new TerminateVMInstanceTask())
                .build())
        // Step 3: Attach Storage Volume
        .step(
            SagaStep.builder()
                .name("AttachStorageVolume")
                .action(new AttachStorageVolumeTask())
                .compensation(new DetachStorageVolumeTask())
                .build())
        // Step 4: Provision Database
        .step(
            SagaStep.builder()
                .name("ProvisionDatabase")
                .action(new ProvisionDatabaseTask())
                .compensation(new DestroyDatabaseTask())
                .build())
        // Step 5: Create Security Group
        .step(
            SagaStep.builder()
                .name("CreateSecurityGroup")
                .action(new CreateSecurityGroupTask())
                .compensation(new DeleteSecurityGroupTask())
                .build())
        // Step 6: Configure Load Balancer
        .step(
            SagaStep.builder()
                .name("ConfigureLoadBalancer")
                .action(new ConfigureLoadBalancerTask())
                .compensation(new DeleteLoadBalancerTask())
                .build())
        // Step 7: Provision SSL Certificate
        .step(
            SagaStep.builder()
                .name("ProvisionSSLCertificate")
                .action(new ProvisionSSLCertificateTask())
                .compensation(new RevokeSSLCertificateTask())
                .build())
        // Step 8: Register DNS Record
        .step(
            SagaStep.builder()
                .name("RegisterDNSRecord")
                .action(new RegisterDNSRecordTask())
                .compensation(new DeleteDNSRecordTask())
                .build())
        // Step 9: Generate Deployment Summary
        .step(
            SagaStep.builder()
                .name("GenerateDeploymentSummary")
                .action(new GenerateDeploymentSummaryTask())
                .build())
        .build();
  }

  static void main() {
    CloudProvisioningSagaExample example = new CloudProvisioningSagaExample();

    // Scenario 1: Successful provisioning
    log.info("=== Scenario 1: Successful Infrastructure Provisioning ===");
    runSuccessfulProvisioning(example);

    // Scenario 2: Database provisioning failure
    log.info("\n=== Scenario 2: Database Provisioning Failure ===");
    runDatabaseProvisioningFailure(example);

    // Scenario 3: Load balancer configuration failure
    log.info("\n=== Scenario 3: Load Balancer Failure with Full Rollback ===");
    runLoadBalancerFailure(example);

    // Scenario 4: DNS registration failure
    log.info("\n=== Scenario 4: DNS Registration Failure ===");
    runDNSRegistrationFailure(example);
  }

  private static void runSuccessfulProvisioning(CloudProvisioningSagaExample example) {
    ProvisioningRequest request =
        ProvisioningRequest.builder()
            .projectId("PROJ-001")
            .projectName("E-Commerce Platform")
            .environment(PRODUCTION)
            .region("us-east-1")
            .instanceType("t3.xlarge")
            .storageSize(200) // GB
            .databaseType(POSTGRE_SQL)
            .databaseSize(50) // GB
            .domain("shop.example.com")
            .requestedBy("devops@example.com")
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(PROVISIONING_REQUEST, request);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isSuccess()) {
      String deploymentId = context.getTyped(DEPLOYMENT_ID, String.class);
      String vmInstanceId = context.getTyped(VM_INSTANCE_ID, String.class);
      String databaseId = context.getTyped(DATABASE_INSTANCE_ID, String.class);
      String domain = request.getDomain();

      log.info("✅ Infrastructure provisioned successfully!");
      log.info("   Deployment ID: {}", deploymentId);
      log.info("   VM Instance: {}", vmInstanceId);
      log.info("   Database: {}", databaseId);
      log.info("   Domain: https://{}", domain);
      log.info("   Environment: {}", request.getEnvironment());
    }
  }

  private static void runDatabaseProvisioningFailure(CloudProvisioningSagaExample example) {
    ProvisioningRequest request =
        ProvisioningRequest.builder()
            .projectId("PROJ-002")
            .projectName("Analytics Dashboard")
            .environment("staging")
            .region("eu-west-1")
            .instanceType("t3.medium")
            .storageSize(100)
            .databaseType("MySQL")
            .databaseSize(20)
            .domain("analytics.example.com")
            .requestedBy("admin@example.com")
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(PROVISIONING_REQUEST, request);
    context.put("simulateDatabaseFailure", true);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      logError(result);
      log.info("   All resources have been cleaned up automatically");
      log.info("   - VM instance terminated");
      log.info("   - Storage volume detached and deleted");
    }
  }

  private static void logError(WorkflowResult result) {
    log.info("❌ Provisioning failed: {}", result.getError().getMessage());
  }

  private static void runLoadBalancerFailure(CloudProvisioningSagaExample example) {
    ProvisioningRequest request =
        ProvisioningRequest.builder()
            .projectId("PROJ-003")
            .projectName("API Gateway")
            .environment(PRODUCTION)
            .region("ap-south-1")
            .instanceType("t3.large")
            .storageSize(150)
            .databaseType(POSTGRE_SQL)
            .databaseSize(30)
            .domain("api.example.com")
            .requestedBy("platform@example.com")
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(PROVISIONING_REQUEST, request);
    context.put("simulateLoadBalancerFailure", true);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      logError(result);
      log.info("   Compensation executed in reverse order:");
      log.info("   - Security group deleted");
      log.info("   - Database destroyed");
      log.info("   - Storage detached");
      log.info("   - VM terminated");
    }
  }

  private static void runDNSRegistrationFailure(CloudProvisioningSagaExample example) {
    ProvisioningRequest request =
        ProvisioningRequest.builder()
            .projectId("PROJ-004")
            .projectName("Mobile Backend")
            .environment(PRODUCTION)
            .region("us-west-2")
            .instanceType("t3.2xlarge")
            .storageSize(500)
            .databaseType(POSTGRE_SQL)
            .databaseSize(100)
            .domain("invalid..domain.com") // Invalid domain
            .requestedBy("mobile@example.com")
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(PROVISIONING_REQUEST, request);
    context.put("simulateDNSFailure", true);

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isFailure()) {
      logError(result);
      log.info("   Complete infrastructure teardown in progress:");
      log.info("   - SSL certificate revoked");
      log.info("   - Load balancer deleted");
      log.info("   - Security group deleted");
      log.info("   - Database destroyed");
      log.info("   - Storage detached");
      log.info("   - VM terminated");
    }
  }

  // ==================== Domain Models ====================

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class ProvisioningRequest {
    private String projectId;
    private String projectName;
    private String environment;
    private String region;
    private String instanceType;
    private int storageSize; // GB
    private String databaseType;
    private int databaseSize; // GB
    private String domain;
    private String requestedBy;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class DeploymentSummary {
    private String deploymentId;
    private String projectId;
    private String vmInstanceId;
    private String databaseInstanceId;
    private String loadBalancerId;
    private String domain;
    private String sslCertificateId;
    private LocalDateTime provisionedAt;
    private Map<String, String> resourceIds;
  }

  // ==================== Action Tasks ====================

  /** Validates provisioning request. */
  static class ValidateProvisioningRequestTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      log.info("→ Validating provisioning request for project: {}", request.getProjectId());

      // Validate required fields
      if (request.getProjectId() == null || request.getProjectId().trim().isEmpty()) {
        throw new TaskExecutionException("Project ID is required");
      }

      if (request.getInstanceType() == null) {
        throw new TaskExecutionException("Instance type is required");
      }

      if (request.getStorageSize() <= 0) {
        throw new TaskExecutionException("Storage size must be positive");
      }

      if (request.getDatabaseSize() <= 0) {
        throw new TaskExecutionException("Database size must be positive");
      }

      // Validate domain
      if (request.getDomain() == null || !request.getDomain().matches("^[a-zA-Z0-9.-]+$")) {
        throw new TaskExecutionException("Invalid domain name: " + request.getDomain());
      }

      log.info("✓ Validation passed: {} ({})", request.getProjectName(), request.getEnvironment());
    }
  }

  /** Creates a VM instance. */
  static class CreateVMInstanceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      log.info("→ Creating VM instance: {} in {}", request.getInstanceType(), request.getRegion());

      // Simulate cloud API call
      String instanceId = "i-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(VM_INSTANCE_ID, instanceId);

      log.info("✓ VM instance created: {} (Type: {})", instanceId, request.getInstanceType());
      log.info("   Status: Running");
      log.info("   Region: {}", request.getRegion());
    }
  }

  /** Attaches storage volume to VM. */
  static class AttachStorageVolumeTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      String vmInstanceId = context.getTyped(VM_INSTANCE_ID, String.class);

      log.info("→ Creating and attaching storage volume: {} GB", request.getStorageSize());

      // Simulate storage API call
      String volumeId = "vol-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(STORAGE_VOLUME_ID, volumeId);

      log.info("✓ Storage volume created: {} ({} GB)", volumeId, request.getStorageSize());
      log.info("   Attached to: {}", vmInstanceId);
    }
  }

  /** Provisions database instance. */
  static class ProvisionDatabaseTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      log.info(
          "→ Provisioning {} database: {} GB",
          request.getDatabaseType(),
          request.getDatabaseSize());

      // Simulate database provisioning failure
      if (Boolean.TRUE.equals(context.get("simulateDatabaseFailure"))) {
        throw new TaskExecutionException(
            "Database provisioning failed: Insufficient resources in region "
                + request.getRegion());
      }

      // Simulate database API call
      String dbInstanceId = "db-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
      context.put(DATABASE_INSTANCE_ID, dbInstanceId);

      String connectionString =
          String.format("postgresql://user:password@%s.db.example.com:5432/appdb", dbInstanceId);
      context.put("databaseConnectionString", connectionString);

      log.info("✓ Database provisioned: {} ({})", dbInstanceId, request.getDatabaseType());
      log.info("   Size: {} GB", request.getDatabaseSize());
      log.info("   Connection: {}", connectionString);
    }
  }

  /** Creates security group with firewall rules. */
  static class CreateSecurityGroupTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      log.info("→ Creating security group for project: {}", request.getProjectId());

      // Simulate security group creation
      String securityGroupId = "sg-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(SECURITY_GROUP_ID, securityGroupId);

      log.info("✓ Security group created: {}", securityGroupId);
      log.info("   Inbound rules: HTTP (80), HTTPS (443), SSH (22)");
      log.info("   Outbound rules: All traffic allowed");
    }
  }

  /** Configures load balancer. */
  static class ConfigureLoadBalancerTask implements Task {

    private final Random random = new Random();

    private String generateRandomIP() {
      return String.format(
          "%d.%d.%d.%d",
          random.nextInt(223) + 1, random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    @Override
    public void execute(WorkflowContext context) {
      String vmInstanceId = context.getTyped(VM_INSTANCE_ID, String.class);
      log.info("→ Configuring load balancer");

      // Simulate load balancer failure
      if (Boolean.TRUE.equals(context.get("simulateLoadBalancerFailure"))) {
        throw new TaskExecutionException(
            "Load balancer configuration failed: Target group health check timeout");
      }

      // Simulate load balancer API call
      String loadBalancerId = "lb-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(LOAD_BALANCER_ID, loadBalancerId);

      String publicIp = generateRandomIP();
      context.put("loadBalancerIP", publicIp);

      log.info("✓ Load balancer configured: {}", loadBalancerId);
      log.info("   Public IP: {}", publicIp);
      log.info("   Target: {}", vmInstanceId);
      log.info("   Health check: Enabled");
    }
  }

  /** Provisions SSL certificate. */
  static class ProvisionSSLCertificateTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      log.info("→ Provisioning SSL certificate for: {}", request.getDomain());

      // Simulate SSL certificate provisioning
      String certificateId = "cert-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
      context.put(SSL_CERTIFICATE_ID, certificateId);

      log.info("✓ SSL certificate provisioned: {}", certificateId);
      log.info("   Domain: {}", request.getDomain());
      log.info("   Expiry: 90 days");
      log.info("   Auto-renewal: Enabled");
    }
  }

  /** Registers DNS record. */
  static class RegisterDNSRecordTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      String loadBalancerIP = context.getTyped("loadBalancerIP", String.class);

      log.info("→ Registering DNS record: {} → {}", request.getDomain(), loadBalancerIP);

      // Simulate DNS registration failure
      if (Boolean.TRUE.equals(context.get("simulateDNSFailure"))) {
        throw new TaskExecutionException(
            "DNS registration failed: Invalid domain format " + request.getDomain());
      }

      // Simulate DNS API call
      String dnsRecordId = "dns-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(DNS_RECORD_ID, dnsRecordId);

      log.info("✓ DNS record registered: {}", dnsRecordId);
      log.info("   Type: A");
      log.info("   Name: {}", request.getDomain());
      log.info("   Value: {}", loadBalancerIP);
      log.info("   TTL: 300 seconds");
    }
  }

  /** Generates deployment summary. */
  static class GenerateDeploymentSummaryTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      ProvisioningRequest request =
          context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
      log.info("→ Generating deployment summary");

      Map<String, String> resourceIds = new HashMap<>();
      resourceIds.put("vmInstance", context.getTyped(VM_INSTANCE_ID, String.class));
      resourceIds.put("storageVolume", context.getTyped(STORAGE_VOLUME_ID, String.class));
      resourceIds.put("database", context.getTyped(DATABASE_INSTANCE_ID, String.class));
      resourceIds.put("securityGroup", context.getTyped(SECURITY_GROUP_ID, String.class));
      resourceIds.put("loadBalancer", context.getTyped(LOAD_BALANCER_ID, String.class));
      resourceIds.put("sslCertificate", context.getTyped(SSL_CERTIFICATE_ID, String.class));
      resourceIds.put("dnsRecord", context.getTyped(DNS_RECORD_ID, String.class));

      DeploymentSummary summary =
          DeploymentSummary.builder()
              .deploymentId("DEPLOY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
              .projectId(request.getProjectId())
              .vmInstanceId(context.getTyped(VM_INSTANCE_ID, String.class))
              .databaseInstanceId(context.getTyped(DATABASE_INSTANCE_ID, String.class))
              .loadBalancerId(context.getTyped(LOAD_BALANCER_ID, String.class))
              .domain(request.getDomain())
              .sslCertificateId(context.getTyped(SSL_CERTIFICATE_ID, String.class))
              .provisionedAt(LocalDateTime.now())
              .resourceIds(resourceIds)
              .build();

      context.put(DEPLOYMENT_ID, summary.getDeploymentId());
      context.put("deploymentSummary", summary);

      log.info("✓ Deployment summary generated: {}", summary.getDeploymentId());
    }
  }

  // ==================== Compensation Tasks ====================

  /** Terminates VM instance. */
  static class TerminateVMInstanceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String instanceId = context.getTyped(VM_INSTANCE_ID, String.class);

      if (instanceId != null) {
        log.warn("↩ Terminating VM instance: {}", instanceId);
        context.remove(VM_INSTANCE_ID);
        log.info("✓ VM instance terminated: {}", instanceId);
      }
    }
  }

  /** Detaches and deletes storage volume. */
  static class DetachStorageVolumeTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String volumeId = context.getTyped(STORAGE_VOLUME_ID, String.class);

      if (volumeId != null) {
        log.warn("↩ Detaching and deleting storage volume: {}", volumeId);
        context.remove(STORAGE_VOLUME_ID);
        log.info("✓ Storage volume deleted: {}", volumeId);
      }
    }
  }

  /** Destroys database instance. */
  static class DestroyDatabaseTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String dbInstanceId = context.getTyped(DATABASE_INSTANCE_ID, String.class);

      if (dbInstanceId != null) {
        log.warn("↩ Destroying database instance: {}", dbInstanceId);
        log.warn("   Final snapshot: Skipped (rollback scenario)");
        context.remove(DATABASE_INSTANCE_ID);
        log.info("✓ Database instance destroyed: {}", dbInstanceId);
      }
    }
  }

  /** Deletes security group. */
  static class DeleteSecurityGroupTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String securityGroupId = context.getTyped(SECURITY_GROUP_ID, String.class);

      if (securityGroupId != null) {
        log.warn("↩ Deleting security group: {}", securityGroupId);
        context.remove(SECURITY_GROUP_ID);
        log.info("✓ Security group deleted: {}", securityGroupId);
      }
    }
  }

  /** Deletes load balancer. */
  static class DeleteLoadBalancerTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String loadBalancerId = context.getTyped(LOAD_BALANCER_ID, String.class);

      if (loadBalancerId != null) {
        log.warn("↩ Deleting load balancer: {}", loadBalancerId);
        context.remove(LOAD_BALANCER_ID);
        log.info("✓ Load balancer deleted: {}", loadBalancerId);
      }
    }
  }

  /** Revokes SSL certificate. */
  static class RevokeSSLCertificateTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String certificateId = context.getTyped(SSL_CERTIFICATE_ID, String.class);

      if (certificateId != null) {
        log.warn("↩ Revoking SSL certificate: {}", certificateId);
        context.remove(SSL_CERTIFICATE_ID);
        log.info("✓ SSL certificate revoked: {}", certificateId);
      }
    }
  }

  /** Deletes DNS record. */
  static class DeleteDNSRecordTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String dnsRecordId = context.getTyped(DNS_RECORD_ID, String.class);

      if (dnsRecordId != null) {
        ProvisioningRequest request =
            context.getTyped(PROVISIONING_REQUEST, ProvisioningRequest.class);
        log.warn("↩ Deleting DNS record: {} ({})", dnsRecordId, request.getDomain());
        context.remove(DNS_RECORD_ID);
        log.info("✓ DNS record deleted: {}", dnsRecordId);
      }
    }
  }
}
