package com.workflow.examples.jdbc;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.task.JdbcBatchUpdateTask;
import com.workflow.task.JdbcQueryTask;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Comprehensive examples demonstrating JdbcBatchUpdateTask usage with an in-memory H2 database.
 *
 * <p>This class shows examples from simple to complex:
 *
 * <ul>
 *   <li>Example 1: Simple batch INSERT
 *   <li>Example 2: Batch UPDATE
 *   <li>Example 3: Large batch INSERT (bulk loading)
 *   <li>Example 4: Mixed batch operations
 *   <li>Example 5: Dynamic batch from context
 *   <li>Example 6: Batch with error handling
 * </ul>
 */
@Slf4j
public class JdbcBatchUpdateTaskExample {

  public static final String ENGINEERING = "Engineering";
  public static final String MARKETING = "Marketing";
  public static final String SALES = "Sales";
  public static final String HR = "HR";
  public static final String INSERT_EMPLOYEE_QUERY =
      "INSERT INTO employees (id, name, department, salary, hire_date) VALUES (?, ?, ?, ?, ?)";
  public static final String EMPLOYEE = "EMPLOYEE";
  public static final String AUDIT_RESULTS = "auditResults";
  public static final String RESULT = "result";
  private final DataSource dataSource;

  public JdbcBatchUpdateTaskExample() throws SQLException {
    this.dataSource = createDataSource();
    initializeDatabase();
  }

  /** Creates an H2 in-memory database data source. */
  private DataSource createDataSource() {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:batch_db;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  /** Initializes the database schema. */
  private void initializeDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      // Create employees table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS employees ("
              + "id INT PRIMARY KEY, "
              + "name VARCHAR(100) NOT NULL, "
              + "department VARCHAR(50), "
              + "salary DECIMAL(10,2), "
              + "hire_date DATE"
              + ")");

      // Create sales table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS sales ("
              + "id INT PRIMARY KEY AUTO_INCREMENT, "
              + "product VARCHAR(100), "
              + "quantity INT, "
              + "amount DECIMAL(10,2), "
              + "sale_date DATE"
              + ")");

      // Create audit_log table
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS audit_log ("
              + "id INT PRIMARY KEY AUTO_INCREMENT, "
              + "action VARCHAR(50), "
              + "entity_type VARCHAR(50), "
              + "entity_id INT, "
              + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
              + ")");

      log.info("Database initialized");
    } catch (SQLException e) {
      log.error("Failed to initialize database", e);
      throw e;
    }
  }

  /**
   * Example 1: Simple batch INSERT. Demonstrates inserting multiple rows efficiently with batch
   * processing.
   */
  public void example1SimpleBatchInsert() {
    log.info("\n=== Example 1: Simple Batch INSERT ===");

    // Prepare batch parameters - List of parameter lists
    List<List<Object>> batchParams = new ArrayList<>();
    batchParams.add(Arrays.asList(1, "Alice Johnson", ENGINEERING, 95000.00, "2023-01-15"));
    batchParams.add(Arrays.asList(2, "Bob Smith", MARKETING, 75000.00, "2023-02-20"));
    batchParams.add(Arrays.asList(3, "Charlie Brown", ENGINEERING, 105000.00, "2023-03-10"));
    batchParams.add(Arrays.asList(4, "Diana Prince", SALES, 85000.00, "2023-04-05"));
    batchParams.add(Arrays.asList(5, "Eve Wilson", HR, 70000.00, "2023-05-12"));

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql(INSERT_EMPLOYEE_QUERY)
            .batchParams(batchParams)
            .writingBatchResultsTo("batchResults")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    int[] results = (int[]) context.get("batchResults");
    log.info("Batch insert results: {} statements executed", results.length);

    int totalInserted = 0;
    for (int i = 0; i < results.length; i++) {
      log.info("  Statement {}: {} row(s) affected", i + 1, results[i]);
      totalInserted += results[i];
    }
    log.info("Total rows inserted: {}", totalInserted);

    // Verify
    verifyEmployeeCount();
  }

  /** Example 2: Batch UPDATE. Demonstrates updating multiple rows with different parameters. */
  public void example2BatchUpdate() {
    log.info("\n=== Example 2: Batch UPDATE ===");

    // Give raises to specific employees
    List<List<Object>> batchParams = new ArrayList<>();
    batchParams.add(Arrays.asList(105000.00, 1)); // Alice: 95k -> 105k
    batchParams.add(Arrays.asList(82000.00, 2)); // Bob: 75k -> 82k
    batchParams.add(Arrays.asList(78000.00, 5)); // Eve: 70k -> 78k

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE employees SET salary = ? WHERE id = ?")
            .batchParams(batchParams)
            .writingBatchResultsTo("updateResults")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    int[] results = (int[]) context.get("updateResults");
    log.info("Batch update completed: {} statements", results.length);

    for (int i = 0; i < results.length; i++) {
      log.info("  Update {}: {} row(s) affected", i + 1, results[i]);
    }

    // Verify
    verifyEmployeeSalaries();
  }

  /**
   * Example 3: Large batch INSERT (bulk loading). Demonstrates efficient bulk data loading with
   * large batches.
   */
  public void example3BulkDataLoad() {
    log.info("\n=== Example 3: Bulk Data Load ===");

    // Generate 1000 sales records
    List<List<Object>> batchParams = new ArrayList<>();
    String[] products = {"Laptop", "Mouse", "Keyboard", "Monitor", "Headphones"};

    for (int i = 0; i < 1000; i++) {
      String product = products[i % products.length];
      int quantity = (i % 10) + 1;
      double amount = quantity * (99.99 + (i % 50));
      String date = "2024-06-" + String.format("%02d", (i % 28) + 1);

      batchParams.add(Arrays.asList(product, quantity, amount, date));
    }

    long startTime = System.currentTimeMillis();

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO sales (product, quantity, amount, sale_date) VALUES (?, ?, ?, ?)")
            .batchParams(batchParams)
            .writingBatchResultsTo("bulkResults")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    long endTime = System.currentTimeMillis();

    int[] results = (int[]) context.get("bulkResults");
    int totalInserted = Arrays.stream(results).sum();

    log.info("Bulk insert completed:");
    log.info("  Total records: {}", totalInserted);
    log.info("  Time taken: {} ms", endTime - startTime);
    log.info("  Average: {} ms per record", (endTime - startTime) / (double) totalInserted);

    // Verify
    verifySalesCount();
  }

  /**
   * Example 4: Mixed batch operations with audit logging. Demonstrates coordinating batch updates
   * with audit trail.
   */
  public void example4MixedBatchOperations() {
    log.info("\n=== Example 4: Mixed Batch Operations ===");

    // Batch 1: Update employee departments
    List<List<Object>> departmentUpdates = new ArrayList<>();
    departmentUpdates.add(Arrays.asList(ENGINEERING, 2)); // Bob moves to Engineering
    departmentUpdates.add(Arrays.asList(SALES, 5)); // Eve moves to Sales

    JdbcBatchUpdateTask updateTask =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("UPDATE employees SET department = ? WHERE id = ?")
            .batchParams(departmentUpdates)
            .writingBatchResultsTo("deptUpdates")
            .build();

    // Batch 2: Create audit log entries
    List<List<Object>> auditEntries = new ArrayList<>();
    auditEntries.add(Arrays.asList("DEPARTMENT_CHANGE", EMPLOYEE, 2));
    auditEntries.add(Arrays.asList("DEPARTMENT_CHANGE", EMPLOYEE, 5));

    JdbcBatchUpdateTask auditTask =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO audit_log (action, entity_type, entity_id) VALUES (?, ?, ?)")
            .batchParams(auditEntries)
            .writingBatchResultsTo(AUDIT_RESULTS)
            .build();

    // Execute both batches
    WorkflowContext context = new WorkflowContext();
    updateTask.execute(context);
    auditTask.execute(context);

    int[] deptResults = (int[]) context.get("deptUpdates");
    int[] auditResults = (int[]) context.get(AUDIT_RESULTS);

    log.info("Department updates: {} changes", Arrays.stream(deptResults).sum());
    log.info("Audit entries created: {}", Arrays.stream(auditResults).sum());

    // Verify
    verifyEmployeeDepartments();
    verifyAuditLog();
  }

  /**
   * Example 5: Dynamic batch from context. Demonstrates reading batch parameters from workflow
   * context at runtime.
   */
  public void example5DynamicBatch() {
    log.info("\n=== Example 5: Dynamic Batch from Context ===");

    JdbcBatchUpdateTask task =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("batchSql")
            .readingBatchParamsFrom("batchParams")
            .writingBatchResultsTo("results")
            .build();

    WorkflowContext context = new WorkflowContext();

    // Scenario: Dynamically insert new employees based on workflow data
    List<List<Object>> newEmployees = new ArrayList<>();
    newEmployees.add(Arrays.asList(6, "Frank Miller", "IT", 88000.00, "2024-06-15"));
    newEmployees.add(Arrays.asList(7, "Grace Lee", "Finance", 92000.00, "2024-06-16"));
    newEmployees.add(Arrays.asList(8, "Henry Ford", "Operations", 95000.00, "2024-06-17"));

    context.put("batchSql", INSERT_EMPLOYEE_QUERY);
    context.put("batchParams", newEmployees);

    task.execute(context);

    int[] results = (int[]) context.get("results");
    log.info("Dynamic batch: inserted {} employees", Arrays.stream(results).sum());

    // Verify
    verifyEmployeeCount();
  }

  /**
   * Example 6: Batch with conditional logic. Demonstrates using batch operations within a workflow
   * with conditional processing.
   */
  public void example6ConditionalBatchProcessing() {
    log.info("\n=== Example 6: Conditional Batch Processing ===");

    // Step 1: Query employees by department
    JdbcQueryTask queryTask =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, salary FROM employees WHERE department = ?")
            .params(List.of(ENGINEERING))
            .writingResultsTo("engineers")
            .build();

    WorkflowContext context = new WorkflowContext();
    queryTask.execute(context);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> engineers = (List<Map<String, Object>>) context.get("engineers");
    log.info("Found {} engineers", engineers.size());

    // Step 2: Prepare batch to give 10% raise to all engineers
    List<List<Object>> salaryUpdates = new ArrayList<>();
    for (Map<String, Object> emp : engineers) {
      Integer id = (Integer) emp.get("ID");
      double currentSalary = ((Number) emp.get("SALARY")).doubleValue();
      Double newSalary = currentSalary * 1.10; // 10% raise

      salaryUpdates.add(Arrays.asList(newSalary, id));
      log.info(
          "  Preparing raise for {}: ${} -> ${}",
          emp.get("NAME"),
          currentSalary,
          String.format("%.2f", newSalary));
    }

    // Step 3: Execute batch update
    if (!salaryUpdates.isEmpty()) {
      JdbcBatchUpdateTask batchTask =
          JdbcBatchUpdateTask.builder()
              .dataSource(dataSource)
              .sql("UPDATE employees SET salary = ? WHERE id = ?")
              .batchParams(salaryUpdates)
              .writingBatchResultsTo("raiseResults")
              .build();

      batchTask.execute(context);

      int[] results = (int[]) context.get("raiseResults");
      log.info("Applied raises to {} engineers", Arrays.stream(results).sum());
    }

    // Verify
    verifyEmployeeSalaries();
  }

  /**
   * Bonus: Complete workflow with batch processing. Demonstrates end-to-end workflow using batch
   * operations.
   */
  public void exampleBonusCompleteWorkflow() {
    log.info("\n=== Bonus: Complete Workflow with Batch Processing ===");

    // Prepare data: New hires for onboarding
    List<List<Object>> newHires = new ArrayList<>();
    newHires.add(Arrays.asList(9, "Iris Chen", ENGINEERING, 98000.00, "2024-07-01"));
    newHires.add(Arrays.asList(10, "Jack Ryan", "Security", 102000.00, "2024-07-01"));

    // Task 1: Batch insert new employees
    JdbcBatchUpdateTask insertEmployees =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql(INSERT_EMPLOYEE_QUERY)
            .batchParams(newHires)
            .writingBatchResultsTo("insertResults")
            .build();

    // Task 2: Create audit log entries for new hires
    List<List<Object>> auditEntries = new ArrayList<>();
    for (List<Object> hire : newHires) {
      auditEntries.add(Arrays.asList("NEW_HIRE", EMPLOYEE, hire.getFirst()));
    }

    JdbcBatchUpdateTask logAudit =
        JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .sql("INSERT INTO audit_log (action, entity_type, entity_id) VALUES (?, ?, ?)")
            .batchParams(auditEntries)
            .writingBatchResultsTo(AUDIT_RESULTS)
            .build();

    // Task 3: Query all employees to generate summary
    JdbcQueryTask querySummary =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql(
                "SELECT department, COUNT(*) as emp_count, AVG(salary) as avg_salary FROM employees GROUP BY department")
            .writingResultsTo("summary")
            .build();

    // Create and execute workflow
    Workflow workflow =
        SequentialWorkflow.builder()
            .name("EmployeeOnboarding")
            .task(insertEmployees)
            .task(logAudit)
            .task(querySummary)
            .build();

    WorkflowContext context = new WorkflowContext();
    var result = workflow.execute(context);

    log.info("Workflow execution status: {}", result.getStatus());

    int[] insertResults = (int[]) context.get("insertResults");
    int[] auditResults = (int[]) context.get(AUDIT_RESULTS);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> summary = (List<Map<String, Object>>) context.get("summary");

    log.info("Employees inserted: {}", Arrays.stream(insertResults).sum());
    log.info("Audit logs created: {}", Arrays.stream(auditResults).sum());
    log.info("\nDepartment Summary:");
    summary.forEach(
        dept ->
            log.info(
                "  {}: {} employees, avg salary ${}",
                dept.get("DEPARTMENT"),
                dept.get("EMP_COUNT"),
                String.format("%.2f", ((Number) dept.get("AVG_SALARY")).doubleValue())));
  }

  // Helper methods for verification
  private void verifyEmployeeCount() {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT COUNT(*) as count FROM employees")
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Total employees in database: {}", result.getFirst().get("COUNT"));
  }

  private void verifyEmployeeSalaries() {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, salary FROM employees ORDER BY id")
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Employee salaries:");
    result.forEach(
        emp -> log.info("  ID {}: {} - ${}", emp.get("ID"), emp.get("NAME"), emp.get("SALARY")));
  }

  private void verifyEmployeeDepartments() {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT name, department FROM employees ORDER BY name")
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Employee departments:");
    result.forEach(emp -> log.info("  {}: {}", emp.get("NAME"), emp.get("DEPARTMENT")));
  }

  private void verifySalesCount() {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT COUNT(*) as count FROM sales")
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Total sales records: {}", result.getFirst().get("COUNT"));
  }

  private void verifyAuditLog() {
    JdbcQueryTask query =
        JdbcQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT action, entity_type, entity_id FROM audit_log ORDER BY timestamp DESC")
            .writingResultsTo(RESULT)
            .build();

    WorkflowContext ctx = new WorkflowContext();
    query.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) ctx.get(RESULT);
    log.info("Audit log entries:");
    result.forEach(
        entry ->
            log.info(
                "  {}: {} {}",
                entry.get("ACTION"),
                entry.get("ENTITY_TYPE"),
                entry.get("ENTITY_ID")));
  }

  /** Main method to run all examples. */
  static void main() throws SQLException {
    JdbcBatchUpdateTaskExample example = new JdbcBatchUpdateTaskExample();

    example.example1SimpleBatchInsert();
    example.example2BatchUpdate();
    example.example3BulkDataLoad();
    example.example4MixedBatchOperations();
    example.example5DynamicBatch();
    example.example6ConditionalBatchProcessing();
    example.exampleBonusCompleteWorkflow();

    log.info("\n=== All examples completed successfully! ===");
  }
}
