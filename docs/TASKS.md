# Workflow Engine - Task Reference

## Table of Contents
- [Overview](#overview)
- [Task Hierarchy](#task-hierarchy)
- [HTTP Tasks](#http-tasks)
- [File Tasks](#file-tasks)
- [Database Tasks](#database-tasks)
- [Processing Tasks](#processing-tasks)
- [Utility Tasks](#utility-tasks)
- [Creating Custom Tasks](#creating-custom-tasks)

## Overview

Tasks are the atomic units of work in the Workflow Engine. Each task implements a single responsibility and can be composed into complex workflows. Tasks read inputs from and write outputs to the shared `WorkflowContext`.

### Task Contract

All tasks implement the `Task` interface:

```java
public interface Task {
    void execute(WorkflowContext context) throws TaskExecutionException;
    String getName();
}
```

### Base Classes

- **`AbstractTask`**: Base class providing common task infrastructure
- **`AbstractHttpTask`**: Base class for HTTP tasks with request/response handling

## Task Hierarchy

```
Task (interface)
    │
    ├── AbstractTask (abstract)
    │   ├── DelayTask
    │   ├── NoOpTask
    │   ├── FileReadTask
    │   ├── FileWriteTask
    │   ├── ShellCommandTask
    │   ├── JdbcQueryTask
    │   ├── JdbcTypedQueryTask
    │   ├── JdbcStreamingQueryTask
    │   ├── JdbcUpdateTask
    │   ├── JdbcBatchUpdateTask
    │   ├── JdbcCallableTask
    │   ├── JdbcTransactionTask
    │   └── AbstractHttpTask (abstract)
    │       ├── GetHttpTask
    │       ├── PostHttpTask
    │       ├── PutHttpTask
    │       └── DeleteHttpTask
    │
    └── Custom implementations
```

## HTTP Tasks

### GetHttpTask

Performs HTTP GET requests with automatic JSON handling.

**Purpose**: Retrieve data from RESTful APIs

**Features**:
- Automatic `Accept: application/json` header
- Type-safe response deserialization
- Query parameter support
- Custom headers

**Builder Configuration**:
```
GetHttpTask.Builder<T>
    .url(String)                    // Target URL
    .urlFromContext(String)         // URL from context key
    .header(String, String)         // Add custom header
    .headers(Map<String, String>)   // Add multiple headers
    .responseType(Class<T>)         // Response type for deserialization
    .responseContextKey(String)     // Context key for response (default: "httpResponse")
    .build()
```

**Examples**:

```java
public void example() {
    HttpClient client = HttpClient.newHttpClient();
    GetHttpTask<String> task = new GetHttpTask.Builder<String>(client)
            .url("https://api.example.com/users")
            .build();

    task.execute(new WorkflowContext());

    String response = context.getTyped("httpResponse", String.class);

    // Typed response with auth
    GetHttpTask<User> task = new GetHttpTask.Builder<User>(client)
            .url("https://api.example.com/users/123")
            .responseType(User.class)
            .header("Authorization", "Bearer " + token)
            .responseContextKey("userData")
            .build();

    User user = context.getTyped("userData", User.class);

   // Dynamic URL from context
    context.put("apiUrl", "https://api.example.com/search");
    context.put("queryParams", Map.of("q", "java", "limit", "10"));

    GetHttpTask<String> task = new GetHttpTask.Builder<String>(client)
            .urlFromContext("apiUrl")
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - Optional `queryParams` (Map<String, String>) for dynamic query parameters
  - Optional URL from context if using `urlFromContext()`
- **Outputs**: 
  - Response stored at configured key (default: "httpResponse")

### PostHttpTask

Performs HTTP POST requests with JSON or form data.

**Purpose**: Create resources or submit data to APIs

**Features**:
- JSON body support
- Form data URL encoding
- Automatic Content-Type header
- Response deserialization

**Builder Configuration**:
```
PostHttpTask.Builder<T>
    .url(String)
    .urlFromContext(String)
    .body(String)                   // JSON body (highest precedence)
    .form(Map<String, String>)      // Form data (URL-encoded)
    .header(String, String)
    .responseType(Class<T>)
    .responseContextKey(String)
    .build()
```

**Body Priority**:
1. Explicit `body()` parameter
2. Context key "REQUEST_BODY"
3. Form data via `form()`
4. Empty body

**Examples**:

```java
public void example() {
    PostTask<ApiResponse> task = new PostTask.Builder<ApiResponse>(client)
            .url("https://api.example.com/users")
            .body("{\"name\":\"John\",\"email\":\"john@example.com\"}")
            .responseType(ApiResponse.class)
            .build();

    // Form submission
    PostTask<String> formTask = new PostTask.Builder<String>(client)
            .url("https://api.example.com/login")
            .form(Map.of(
                    "username", "john",
                    "password", "secret123"
            ))
            .build();

    // Body from context
    context.put("REQUEST_BODY", userJson);
    PostTask<User> task = new PostTask.Builder<User>(client)
            .url("https://api.example.com/users")
            .responseType(User.class)
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - Optional `REQUEST_BODY` (String) for body content
  - Optional URL from context
- **Outputs**: 
  - Response stored at configured key

### PutHttpTask

Performs HTTP PUT requests for updating resources.

**Purpose**: Update existing resources

**Features**: Same as PostHttpTask (JSON body, form data, etc.)

**Examples**:

```java
// Update user
PutTask<User> task = new PutTask.Builder<User>(client)
    .url("https://api.example.com/users/123")
    .body("{\"name\":\"John Updated\"}")
    .responseType(User.class)
    .build();

// Form update
PutTask<String> task = new PutTask.Builder<String>(client)
    .url("https://api.example.com/settings")
    .form(Map.of("theme", "dark", "language", "en"))
    .build();
```

### DeleteHttpTask

Performs HTTP DELETE requests.

**Purpose**: Delete resources

**Features**:
- Simple DELETE operations
- Optional request body
- Response handling

**Examples**:

```java
// Simple delete
DeleteHttpTask<String> task = new DeleteHttpTask.Builder<String>(client)
    .url("https://api.example.com/users/123")
    .build();

// Delete with auth
DeleteHttpTask<ApiResponse> task = new DeleteHttpTask.Builder<ApiResponse>(client)
    .url("https://api.example.com/sessions/" + sessionId)
    .header("Authorization", "Bearer " + token)
    .responseType(ApiResponse.class)
    .build();
```

## File Tasks

### FileReadTask

Reads file contents into context.

**Purpose**: Load data from files

**Features**:
- Path-based file reading
- Encoding support
- UTF-8 default encoding

**Constructor**:
```java
FileReadTask(Path filePath, String contextKey);
```

**Examples**:

```java
// Read JSON file
public void example() {
    FileReadTask task = new FileReadTask(
            Path.of("data/users.json"),
            "userData"
    );

    task.execute(context);
    String jsonData = context.getTyped("userData", String.class);

    // Read configuration
    FileReadTask configTask = new FileReadTask(
            Path.of("config/application.properties"),
            "config"
    );
}
```

**Context Usage**:
- **Inputs**: None
- **Outputs**: 
  - File contents stored at specified context key (String)

**Error Handling**:
- Throws `TaskExecutionException` on:
  - File not found
  - Permission errors
  - I/O errors

### FileWriteTask

Writes context data to files.

**Purpose**: Persist processed data

**Features**:
- Path-based file writing
- Creates parent directories if needed
- UTF-8 encoding
- Overwrites existing files

**Constructor**:
```java
FileWriteTask(Path filePath, String contextKey);
```

**Examples**:

```java
// Write processed data
public void example() {
    context.put("processedData", jsonString);
    FileWriteTask task = new FileWriteTask(
            Path.of("output/results.json"),
            "processedData"
    );

    task.execute(context);

    // Save report
    context.put("report", reportContent);
    FileWriteTask reportTask = new FileWriteTask(
            Path.of("reports/daily-report.txt"),
            "report"
    );
}
```

**Context Usage**:
- **Inputs**: 
  - Data from specified context key (toString() called)
- **Outputs**: None

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Permission errors
  - Disk full
  - I/O errors

## Database Tasks

### JdbcQueryTask

Executes SQL SELECT queries using JDBC and returns results as a list of maps.

**Purpose**: Query databases and retrieve data

**Features**:
- Parameterized query support (prevents SQL injection)
- Automatic result set mapping to List<Map<String, Object>>
- Connection pooling support via DataSource
- Handles JDBC-specific types (Clob, Array, etc.)
- Preserves column order using LinkedHashMap

**Builder Configuration**:
```
JdbcQueryTask.builder()
    .dataSource(DataSource)         // JDBC DataSource (required)
    .readingSqlFrom(String)         // Context key for SQL query (required)
    .readingParamsFrom(String)      // Context key for parameters (required)
    .writingResultsTo(String)       // Context key for results (required)
    .build()
```

**Constructor**:
```java
JdbcQueryTask(
    DataSource dataSource,
    String sqlKey,
    String paramsKey,
    String outputKey
);
```

**Examples**:

```java
// Basic query with parameters
public void basicQuery() {
    DataSource dataSource = createDataSource(); // Your connection pool
    
    WorkflowContext context = new WorkflowContext();
    context.put("sql", "SELECT id, name, email FROM users WHERE status = ? AND created_at > ?");
    context.put("params", Arrays.asList("ACTIVE", LocalDate.of(2024, 1, 1)));
    
    JdbcQueryTask task = JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingParamsFrom("params")
            .writingResultsTo("queryResults")
            .build();
    
    task.execute(context);
    
    List<Map<String, Object>> rows = (List<Map<String, Object>>) context.get("queryResults");
    for (Map<String, Object> row : rows) {
        Integer id = (Integer) row.get("id");
        String name = (String) row.get("name");
        String email = (String) row.get("email");
        // Process row...
    }
}

// Query without parameters
public void simpleQuery() {
    WorkflowContext context = new WorkflowContext();
    context.put("sql", "SELECT * FROM products ORDER BY price DESC LIMIT 10");
    context.put("params", Collections.emptyList());
    
    JdbcQueryTask task = new JdbcQueryTask(
            dataSource,
            "sql",
            "params",
            "topProducts"
    );
    
    task.execute(context);
    List<Map<String, Object>> products = context.get("topProducts");
}

// Complex query with joins
public void complexQuery() {
    context.put("userQuery", 
        "SELECT u.id, u.name, o.order_id, o.total " +
        "FROM users u " +
        "LEFT JOIN orders o ON u.id = o.user_id " +
        "WHERE u.country = ? AND o.status = ?");
    context.put("userParams", Arrays.asList("USA", "COMPLETED"));
    
    JdbcQueryTask task = JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("userQuery")
            .readingParamsFrom("userParams")
            .writingResultsTo("userOrders")
            .build();
}

// In a workflow
public Workflow buildUserReportWorkflow(DataSource dataSource) {
    JdbcQueryTask queryTask = JdbcQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("reportSql")
            .readingParamsFrom("reportParams")
            .writingResultsTo("reportData")
            .build();
    
    return SequentialWorkflow.builder()
            .name("UserReport")
            .task(context -> {
                // Prepare query
                context.put("reportSql", "SELECT * FROM user_stats WHERE date >= ?");
                context.put("reportParams", List.of(LocalDate.now().minusDays(30)));
            })
            .task(queryTask)
            .task(context -> {
                // Process results
                List<Map<String, Object>> data = context.get("reportData");
                generateReport(data);
            })
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - SQL query string (from configured sqlKey)
  - Optional List<Object> parameters (from configured paramsKey, empty list if not present)
- **Outputs**: 
  - List<Map<String, Object>> results (at configured outputKey)
  - Each Map represents one row with column names as keys

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Database connection failures
  - SQL syntax errors
  - Parameter binding errors
  - Type conversion errors
- All JDBC resources (Connection, PreparedStatement, ResultSet) are automatically closed

**Best Practices**:
- Always use parameterized queries (?) to prevent SQL injection
- Use connection pooling (HikariCP, Apache DBCP) for DataSource
- Keep queries simple and focused
- Consider indexing columns used in WHERE clauses
- Handle large result sets appropriately (pagination, streaming)

### JdbcUpdateTask

Executes SQL INSERT, UPDATE, or DELETE statements and returns the number of affected rows.

**Purpose**: Modify database data (insert, update, delete)

**Features**:
- Parameterized statement support
- Returns affected row count
- Transaction support (via DataSource configuration)
- Connection pooling support

**Builder Configuration**:
```
JdbcUpdateTask.builder()
    .dataSource(DataSource)              // JDBC DataSource (required)
    .readingSqlFrom(String)              // Context key for SQL statement (required)
    .readingParamsFrom(String)           // Context key for parameters (required)
    .writingRowsAffectedTo(String)       // Context key for row count (required)
    .build()
```

**Constructor**:
```java
JdbcUpdateTask(
    DataSource dataSource,
    String sqlKey,
    String paramsKey,
    String outputKey
);
```

**Examples**:

```java
// Update operation
public void updateUser() {
    WorkflowContext context = new WorkflowContext();
    context.put("updateSql", "UPDATE users SET status = ?, updated_at = ? WHERE id = ?");
    context.put("updateParams", Arrays.asList("INACTIVE", Timestamp.valueOf(LocalDateTime.now()), 101));
    
    JdbcUpdateTask task = JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("updateSql")
            .readingParamsFrom("updateParams")
            .writingRowsAffectedTo("rowsUpdated")
            .build();
    
    task.execute(context);
    
    Integer rowsAffected = (Integer) context.get("rowsUpdated");
    if (rowsAffected > 0) {
        System.out.println("User updated successfully");
    }
}

// Insert operation
public void insertUser() {
    context.put("insertSql", "INSERT INTO users (name, email, status, created_at) VALUES (?, ?, ?, ?)");
    context.put("insertParams", Arrays.asList(
        "John Doe",
        "john@example.com",
        "ACTIVE",
        Timestamp.valueOf(LocalDateTime.now())
    ));
    
    JdbcUpdateTask task = new JdbcUpdateTask(
            dataSource,
            "insertSql",
            "insertParams",
            "rowsInserted"
    );
    
    task.execute(context);
    Integer inserted = context.get("rowsInserted");
}

// Delete operation
public void deleteOldRecords() {
    context.put("deleteSql", "DELETE FROM logs WHERE created_at < ?");
    context.put("deleteParams", List.of(LocalDate.now().minusDays(90)));
    
    JdbcUpdateTask task = JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("deleteSql")
            .readingParamsFrom("deleteParams")
            .writingRowsAffectedTo("rowsDeleted")
            .build();
}

// Conditional update in workflow
public Workflow buildUserActivationWorkflow(DataSource dataSource) {
    JdbcUpdateTask updateTask = JdbcUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("activationSql")
            .readingParamsFrom("activationParams")
            .writingRowsAffectedTo("updateCount")
            .build();
    
    return SequentialWorkflow.builder()
            .name("ActivateUser")
            .task(context -> {
                // Validate and prepare
                Integer userId = context.getTyped("userId", Integer.class);
                context.put("activationSql", "UPDATE users SET status = 'ACTIVE' WHERE id = ?");
                context.put("activationParams", List.of(userId));
            })
            .task(updateTask)
            .task(context -> {
                Integer count = context.get("updateCount");
                if (count == 0) {
                    throw new RuntimeException("User not found");
                }
                // Send activation email...
            })
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - SQL statement string (from configured sqlKey)
  - Optional List<Object> parameters (from configured paramsKey, empty list if not present)
- **Outputs**: 
  - Integer rows affected count (at configured outputKey)

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Database connection failures
  - SQL syntax errors
  - Constraint violations (unique, foreign key, etc.)
  - Parameter binding errors
- All JDBC resources are automatically closed

**Best Practices**:
- Use parameterized statements to prevent SQL injection
- Check rowsAffected to verify operation success
- Consider using database transactions for multiple updates
- Handle constraint violations gracefully
- Use appropriate DataSource transaction isolation levels

### JdbcBatchUpdateTask

Executes multiple SQL statements in a batch for improved performance.

**Purpose**: Bulk insert, update, or delete operations

**Features**:
- Batch execution for better performance
- Multiple parameter sets with single SQL template
- Returns individual row counts for each statement
- Efficient for large data volumes

**Builder Configuration**:
```
JdbcBatchUpdateTask.builder()
    .dataSource(DataSource)              // JDBC DataSource (required)
    .readingSqlFrom(String)              // Context key for SQL template (required)
    .readingBatchParamsFrom(String)      // Context key for List<List<Object>> (required)
    .writingBatchResultsTo(String)       // Context key for int[] results (required)
    .build()
```

**Constructor**:
```java
JdbcBatchUpdateTask(
    DataSource dataSource,
    String sqlKey,
    String batchParamsKey,
    String outputKey
);
```

**Examples**:

```java
// Batch insert
public void batchInsertLogs() {
    WorkflowContext context = new WorkflowContext();
    context.put("batchSql", "INSERT INTO logs (level, message, timestamp) VALUES (?, ?, ?)");
    
    List<List<Object>> batchData = Arrays.asList(
        Arrays.asList("INFO", "Application started", Timestamp.valueOf(LocalDateTime.now())),
        Arrays.asList("DEBUG", "Processing request", Timestamp.valueOf(LocalDateTime.now())),
        Arrays.asList("ERROR", "Connection failed", Timestamp.valueOf(LocalDateTime.now()))
    );
    context.put("batchData", batchData);
    
    JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("batchSql")
            .readingBatchParamsFrom("batchData")
            .writingBatchResultsTo("batchResults")
            .build();
    
    task.execute(context);
    
    int[] results = (int[]) context.get("batchResults");
    int totalInserted = Arrays.stream(results).sum();
    System.out.println("Inserted " + totalInserted + " log entries");
}

// Batch update with loop
public void batchUpdatePrices() {
    List<Product> products = getProductsToUpdate();
    
    List<List<Object>> updates = new ArrayList<>();
    for (Product p : products) {
        updates.add(Arrays.asList(p.getNewPrice(), p.getId()));
    }
    
    context.put("updateSql", "UPDATE products SET price = ? WHERE id = ?");
    context.put("priceUpdates", updates);
    
    JdbcBatchUpdateTask task = new JdbcBatchUpdateTask(
            dataSource,
            "updateSql",
            "priceUpdates",
            "updateResults"
    );
    
    task.execute(context);
    int[] counts = context.get("updateResults");
}

// Efficient bulk data loading
public void bulkLoadUsers(List<User> users) {
    context.put("insertSql", 
        "INSERT INTO users (name, email, status, created_at) VALUES (?, ?, ?, ?)");
    
    List<List<Object>> userParams = users.stream()
            .map(u -> Arrays.asList(
                u.getName(),
                u.getEmail(),
                u.getStatus(),
                Timestamp.valueOf(LocalDateTime.now())
            ))
            .collect(Collectors.toList());
    
    context.put("userBatch", userParams);
    
    JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("insertSql")
            .readingBatchParamsFrom("userBatch")
            .writingBatchResultsTo("loadResults")
            .build();
}

// In a data migration workflow
public Workflow buildMigrationWorkflow(DataSource sourceDs, DataSource targetDs) {
    JdbcQueryTask extractTask = JdbcQueryTask.builder()
            .dataSource(sourceDs)
            .readingSqlFrom("extractSql")
            .readingParamsFrom("extractParams")
            .writingResultsTo("sourceData")
            .build();
    
    JdbcBatchUpdateTask loadTask = JdbcBatchUpdateTask.builder()
            .dataSource(targetDs)
            .readingSqlFrom("loadSql")
            .readingBatchParamsFrom("transformedData")
            .writingBatchResultsTo("loadResults")
            .build();
    
    return SequentialWorkflow.builder()
            .name("DataMigration")
            .task(context -> {
                context.put("extractSql", "SELECT * FROM legacy_users WHERE migrated = false");
                context.put("extractParams", Collections.emptyList());
            })
            .task(extractTask)
            .task(context -> {
                // Transform data
                List<Map<String, Object>> sourceData = context.get("sourceData");
                List<List<Object>> transformed = sourceData.stream()
                        .map(this::transformRow)
                        .collect(Collectors.toList());
                context.put("loadSql", "INSERT INTO users (name, email, status) VALUES (?, ?, ?)");
                context.put("transformedData", transformed);
            })
            .task(loadTask)
            .task(context -> {
                int[] results = context.get("loadResults");
                System.out.println("Migrated " + Arrays.stream(results).sum() + " users");
            })
            .build();
}

// Handle empty batch
public void handleEmptyBatch() {
    context.put("sql", "INSERT INTO items (name) VALUES (?)");
    context.put("items", Collections.emptyList()); // Empty batch
    
    JdbcBatchUpdateTask task = JdbcBatchUpdateTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("sql")
            .readingBatchParamsFrom("items")
            .writingBatchResultsTo("results")
            .build();
    
    task.execute(context);
    int[] results = context.get("results"); // Will be empty array: int[0]
}
```

**Context Usage**:
- **Inputs**: 
  - SQL template string (from configured sqlKey)
  - List<List<Object>> batch parameters (from configured batchParamsKey)
    - Each inner List represents parameters for one statement execution
- **Outputs**: 
  - int[] array of affected row counts (at configured outputKey)
  - Each element corresponds to one batch statement

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Database connection failures
  - SQL syntax errors
  - Constraint violations
  - Parameter binding errors
- All JDBC resources are automatically closed
- If any statement in batch fails, entire batch may be rolled back (depends on database and transaction settings)

**Performance Considerations**:
- Much faster than individual statements for bulk operations
- Reduces network round-trips to database
- Consider batch size limits (typically 1000-5000 rows per batch)
- May want to split very large datasets into multiple batches
- Some databases have specific batch optimizations

**Best Practices**:
- Use for bulk operations (100+ rows)
- Keep batch sizes reasonable (1000-5000 typical)
- Use transactions to ensure atomicity
- Monitor memory usage with large batches
- Test rollback behavior with your database
- Validate data before batching to avoid partial failures

### JdbcTypedQueryTask

Executes SQL SELECT queries with type-safe result mapping using a custom row mapper.

**Purpose**: Provides type-safe database query results by converting rows into domain objects

**Features**:
- Type-safe mapping to POJOs, DTOs, or records
- Custom RowMapper for flexible conversions
- Compile-time type checking
- Better IDE support and refactoring
- Reusable mappers across queries
- All benefits of JdbcQueryTask (parameterization, connection pooling, etc.)

**Builder Configuration**:
```
JdbcTypedQueryTask.<T>builder()
    .dataSource(DataSource)              // JDBC DataSource (required)
    .sql(String)                         // SQL query directly (optional)
    .params(List<Object>)                // Parameters directly (optional)
    .rowMapper(RowMapper<T>)             // Row mapper directly (optional)
    .readingSqlFrom(String)              // Context key for SQL (optional)
    .readingParamsFrom(String)           // Context key for parameters (optional)
    .readingRowMapperFrom(String)        // Context key for row mapper (optional)
    .writingResultsTo(String)            // Context key for results (required)
    .build()
```

**RowMapper Interface**:
```java
@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
}
```

**Examples**:

```java
public record User(Integer id, String name, String email, LocalDate createdAt) {}
public record Order(Integer id, String orderNumber, User user, BigDecimal total) { }

public void example() {
    // Direct Mode - Map to POJO
    RowMapper<User> userMapper = (rs, rowNum) -> new User(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getDate("created_at").toLocalDate()
    );

    JdbcTypedQueryTask<User> task = JdbcTypedQueryTask.<User>builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, email, created_at FROM users WHERE active = ?")
            .params(List.of(true))
            .rowMapper(userMapper)
            .writingResultsTo("users")
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    List<User> users = context.get("users");
    for (User user : users) {
        System.out.println(user.name() + " - " + user.email());
    }

    // Context Mode - Dynamic query
    context.put("userQuery", "SELECT id, name, email, created_at FROM users WHERE status = ?");
    context.put("queryParams", List.of("ACTIVE"));
    context.put("mapper", userMapper);


    JdbcTypedQueryTask<User> contextTask = JdbcTypedQueryTask.<User>builder()
            .dataSource(dataSource)
            .readingSqlFrom("userQuery")
            .readingParamsFrom("queryParams")
            .readingRowMapperFrom("mapper")
            .writingResultsTo("activeUsers")
            .build();

    task.execute(context);
    List<User> activeUsers = context.get("activeUsers");

    // Simple scalar mapping
    RowMapper<String> emailMapper = (rs, rowNum) -> rs.getString("email");

    JdbcTypedQueryTask<String> emailTask = JdbcTypedQueryTask.<String>builder()
            .dataSource(dataSource)
            .sql("SELECT email FROM users WHERE department = ?")
            .params(List.of("Engineering"))
            .rowMapper(emailMapper)
            .writingResultsTo("emails")
            .build();

    emailTask.execute(context);
    List<String> emails = context.get("emails");

    RowMapper<Order> orderMapper = (rs, rowNum) -> {
        User user = new User(
                rs.getInt("user_id"),
                rs.getString("user_name"),
                rs.getString("user_email"),
                rs.getDate("user_created").toLocalDate()
        );
        return new Order(
                rs.getInt("order_id"),
                rs.getString("order_number"),
                user,
                rs.getBigDecimal("total")
        );
    };

    JdbcTypedQueryTask<Order> orderTask = JdbcTypedQueryTask.<Order>builder()
            .dataSource(dataSource)
            .sql("SELECT o.id as order_id, o.order_number, o.total, " +
                    "u.id as user_id, u.name as user_name, u.email as user_email, u.created_at as user_created " +
                    "FROM orders o JOIN users u ON o.user_id = u.id WHERE o.status = ?")
            .params(List.of("COMPLETED"))
            .rowMapper(orderMapper)
            .writingResultsTo("completedOrders")
            .build();
}

// In a workflow
public Workflow buildUserProcessingWorkflow(DataSource dataSource) {
    RowMapper<User> mapper = (rs, rowNum) -> new User(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getString("email"),
        rs.getDate("created_at").toLocalDate()
    );
    
    JdbcTypedQueryTask<User> queryTask = JdbcTypedQueryTask.<User>builder()
        .dataSource(dataSource)
        .sql("SELECT * FROM users WHERE status = ?")
        .params(List.of("PENDING"))
        .rowMapper(mapper)
        .writingResultsTo("pendingUsers")
        .build();
    
    return SequentialWorkflow.builder()
        .name("ProcessUsers")
        .task(queryTask)
        .task(context -> {
            List<User> users = context.get("pendingUsers");
            for (User user : users) {
                processUser(user);
            }
        })
        .build();
}
```

**Context Usage**:
- **Inputs**: 
  - SQL query string (direct or from context key)
  - Optional List<Object> parameters (direct or from context key)
  - RowMapper<T> function (direct or from context key)
- **Outputs**: 
  - List<T> typed results (at configured outputKey)

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Database connection failures
  - SQL syntax errors
  - Parameter binding errors
  - Mapping errors (exceptions in RowMapper)
  - Type conversion errors
- All JDBC resources are automatically closed

**Best Practices**:
- Create reusable RowMapper instances for common entity types
- Use Java records for immutable data objects
- Handle null values appropriately in mapper
- Consider using static factory methods for complex mappers
- Validate data during mapping
- Keep mappers focused on single responsibility

### JdbcStreamingQueryTask

Executes SQL SELECT queries and processes results in a streaming fashion without loading all rows into memory.

**Purpose**: Efficiently process large database result sets that don't fit in memory

**Features**:
- Row-by-row processing using callbacks
- Memory efficient - only one row in memory at a time
- Configurable fetch size for optimization
- Forward-only cursor for maximum performance
- Early termination support
- Prevents OutOfMemoryError on large result sets
- Tracks total rows processed

**Builder Configuration**:
```
JdbcStreamingQueryTask.builder()
    .dataSource(DataSource)              // JDBC DataSource (required)
    .sql(String)                         // SQL query directly (optional)
    .params(List<Object>)                // Parameters directly (optional)
    .rowCallback(Consumer<Map<String, Object>>)  // Callback directly (optional)
    .readingSqlFrom(String)              // Context key for SQL (optional)
    .readingParamsFrom(String)           // Context key for parameters (optional)
    .readingRowCallbackFrom(String)      // Context key for callback (optional)
    .writingRowCountTo(String)           // Context key for row count (required)
    .fetchSize(int)                      // Fetch size optimization (default: 1000)
    .queryTimeout(int)                   // Query timeout in seconds (optional)
    .build()
```

**Examples**:

```java
public void example() {
    // Direct Mode - Print each row
    Consumer<Map<String, Object>> printCallback = row -> {
        Integer id = (Integer) row.get("id");
        String name = (String) row.get("name");
        System.out.println("User ID: " + id + ", Name: " + name);
    };

    JdbcStreamingQueryTask task = JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT id, name, email FROM users WHERE active = ?")
            .params(List.of(true))
            .rowCallback(printCallback)
            .writingRowCountTo("processedCount")
            .fetchSize(500)
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    Long totalProcessed = context.get("processedCount");
    System.out.println("Processed " + totalProcessed + " rows");

    // Context Mode - Dynamic callback
    context.put("querySql", "SELECT * FROM orders WHERE created_at >= ?");
    context.put("queryParams", List.of(LocalDate.now().minusDays(30)));

    Consumer<Map<String, Object>> orderCallback = row -> {
        // Process each order
        Integer orderId = (Integer) row.get("id");
        BigDecimal total = (BigDecimal) row.get("total");
        processOrder(orderId, total);
    };
    context.put("orderCallback", orderCallback);

    JdbcStreamingQueryTask contextTask = JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .readingSqlFrom("querySql")
            .readingParamsFrom("queryParams")
            .readingRowCallbackFrom("orderCallback")
            .writingRowCountTo("ordersProcessed")
            .fetchSize(1000)
            .build();

    contextTask.execute(context);

    // Export to CSV
    try (CSVWriter writer = new CSVWriter(new FileWriter("export.csv"))) {
        // Write header
        writer.writeNext(new String[]{"id", "name", "email", "created_at"});

        Consumer<Map<String, Object>> csvCallback = row -> {
            String[] values = {
                    String.valueOf(row.get("id")),
                    String.valueOf(row.get("name")),
                    String.valueOf(row.get("email")),
                    String.valueOf(row.get("created_at"))
            };
            writer.writeNext(values);
        };

        JdbcStreamingQueryTask exportTask = JdbcStreamingQueryTask.builder()
                .dataSource(dataSource)
                .sql("SELECT id, name, email, created_at FROM users")
                .params(Collections.emptyList())
                .rowCallback(csvCallback)
                .writingRowCountTo("exportedRows")
                .fetchSize(1000)
                .build();

        exportTask.execute(context);
    }

    // Aggregate processing with state
    AtomicInteger activeCount = new AtomicInteger(0);
    AtomicLong totalRevenue = new AtomicLong(0);

    Consumer<Map<String, Object>> aggregateCallback = row -> {
        String status = (String) row.get("status");
        BigDecimal amount = (BigDecimal) row.get("amount");

        if ("ACTIVE".equals(status)) {
            activeCount.incrementAndGet();
        }
        totalRevenue.addAndGet(amount.longValue());
    };

    JdbcStreamingQueryTask aggregateTask = JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT status, amount FROM orders")
            .params(Collections.emptyList())
            .rowCallback(aggregateCallback)
            .writingRowCountTo("totalOrders")
            .fetchSize(2000)
            .build();

    aggregateTask.execute(context);

    System.out.println("Active: " + activeCount.get());
    System.out.println("Total Revenue: " + totalRevenue.get());

    // Early termination - Stop after finding target
    AtomicBoolean found = new AtomicBoolean(false);

    Consumer<Map<String, Object>> findCallback = row -> {
        if (found.get()) {
            return; // Skip processing if already found
        }

        String email = (String) row.get("email");
        if ("target@example.com".equals(email)) {
            found.set(true);
            context.put("targetUser", row);
            throw new RuntimeException("Found target user"); // Stop processing
        }
    };

    try {
        JdbcStreamingQueryTask findTask = JdbcStreamingQueryTask.builder()
                .dataSource(dataSource)
                .sql("SELECT * FROM users ORDER BY created_at")
                .params(Collections.emptyList())
                .rowCallback(findCallback)
                .writingRowCountTo("scannedRows")
                .build();

        findTask.execute(context);
    } catch (TaskExecutionException e) {
        if (found.get()) {
            System.out.println("Found target user");
        } else {
            throw e;
        }
    }

    // Batch processing within stream
    List<Map<String, Object>> batch = new ArrayList<>();
    int batchSize = 100;

    Consumer<Map<String, Object>> batchCallback = row -> {
        batch.add(row);

        if (batch.size() >= batchSize) {
            processBatch(batch);
            batch.clear();
        }
    };

    JdbcStreamingQueryTask batchTask = JdbcStreamingQueryTask.builder()
            .dataSource(dataSource)
            .sql("SELECT * FROM events WHERE date >= ?")
            .params(List.of(LocalDate.now().minusDays(7)))
            .rowCallback(batchCallback)
            .writingRowCountTo("eventsProcessed")
            .fetchSize(1000)
            .queryTimeout(300)
            .build();

    batchTask.execute(context);

    // Process remaining items in batch
    if (!batch.isEmpty()) {
        processBatch(batch);
        batch.clear();
    }
}
```

**Context Usage**:
- **Inputs**: 
  - SQL query string (direct or from context key)
  - Optional List<Object> parameters (direct or from context key)
  - Consumer<Map<String, Object>> callback (direct or from context key)
- **Outputs**: 
  - Long row count (at configured rowCountKey)

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Database connection failures
  - SQL syntax errors
  - Parameter binding errors
  - Callback exceptions (processing stops immediately)
  - Query timeout
- All JDBC resources are automatically closed
- Processing stops on first callback exception

**Performance Optimization**:
- **Fetch Size**: Configures JDBC driver fetch size (default: 1000)
  - Larger values: Fewer network round-trips, more memory
  - Smaller values: More round-trips, less memory
  - Typical range: 100-5000 depending on row size
- **Forward-Only Cursor**: ResultSet configured for forward-only traversal
- **Read-Only Mode**: ResultSet in read-only mode for better performance
- **Query Timeout**: Optional timeout to prevent long-running queries

**Best Practices**:
- Use for queries returning >10,000 rows or large row sizes
- Set appropriate fetch size based on row size and memory
- Keep callback processing fast (avoid heavy I/O or blocking)
- Consider using for ETL operations
- Handle errors gracefully in callbacks
- Use for exports to files or external systems
- Monitor memory usage in production
- Consider batch processing within callback for efficiency

### JdbcCallableTask

Executes database stored procedures or functions using JDBC CallableStatement.

**Purpose**: Invoke database stored procedures and functions with IN/OUT/INOUT parameters

**Features**:
- Stored procedure and function support
- IN, OUT, and INOUT parameter handling
- Return value support for functions
- Multiple result set support
- Parameterized call statements
- Automatic type mapping between JDBC and Java types

**Builder Configuration**:
```
JdbcCallableTask.builder()
    .dataSource(DataSource)              // JDBC DataSource (required)
    .call(String)                        // Call statement directly (optional)
    .inParameters(Map<Integer, Object>)  // IN parameters directly (optional)
    .outParameters(Map<Integer, Integer>) // OUT parameters directly (optional)
    .readingCallFrom(String)             // Context key for call statement (optional)
    .readingInParametersFrom(String)     // Context key for IN parameters (optional)
    .readingOutParametersFrom(String)    // Context key for OUT parameters (optional)
    .writingResultSetsTo(String)         // Context key for result sets (optional)
    .writingOutValuesTo(String)          // Context key for OUT values (optional)
    .build()
```

**Call Statement Syntax**:
- Stored Procedure: `{call procedure_name(?, ?, ?)}`
- Function: `{? = call function_name(?, ?)}`
- Package Procedure (Oracle): `{call package.procedure_name(?, ?)}`

**Parameter Mapping**:
- **IN**: Map position (1-based) → value
- **OUT**: Map position (1-based) → SQL type constant from java.sql.Types
- **INOUT**: Specify in both IN and OUT maps

**Examples**:

```java
public void example() {
    // Simple stored procedure with IN parameters
    Map<Integer, Object> inParams = new HashMap<>();
    inParams.put(1, 101);  // product_id
    inParams.put(2, 50);   // quantity

    JdbcCallableTask task = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call update_inventory(?, ?)}")
            .inParameters(inParams)
            .build();

    WorkflowContext context = new WorkflowContext();
    task.execute(context);

    // Function with return value
    // CREATE FUNCTION calculate_tax(amount DECIMAL) RETURNS DECIMAL
    Map<Integer, Object> funcInParams = new HashMap<>();
    funcInParams.put(2, new BigDecimal("100.00"));  // amount (position 2)

    Map<Integer, Integer> funcOutParams = new HashMap<>();
    funcOutParams.put(1, Types.DECIMAL);  // Return value at position 1

    JdbcCallableTask funcTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{? = call calculate_tax(?)}")
            .inParameters(funcInParams)
            .outParameters(funcOutParams)
            .writingOutValuesTo("taxResult")
            .build();

    funcTask.execute(context);

    Map<Integer, Object> outValues = context.get("taxResult");
    BigDecimal tax = (BigDecimal) outValues.get(1);
    System.out.println("Tax: " + tax);

    // Procedure with - OUT parameters
    // CREATE PROCEDURE get_user_stats(IN user_id INT, OUT total_orders INT, OUT total_spent DECIMAL)
    Map<Integer, Object> statsInParams = new HashMap<>();
    statsInParams.put(1, 123);  // user_id

    Map<Integer, Integer> statsOutParams = new HashMap<>();
    statsOutParams.put(2, Types.INTEGER);  // total_orders
    statsOutParams.put(3, Types.DECIMAL);  // total_spent

    JdbcCallableTask statsTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_user_stats(?, ?, ?)}")
            .inParameters(statsInParams)
            .outParameters(statsOutParams)
            .writingOutValuesTo("userStats")
            .build();

    statsTask.execute(context);

    Map<Integer, Object> stats = context.get("userStats");
    Integer totalOrders = (Integer) stats.get(2);
    BigDecimal totalSpent = (BigDecimal) stats.get(3);

    // INOUT parameters
    // CREATE PROCEDURE increment_counter(INOUT counter INT)
    Map<Integer, Object> inoutInParams = new HashMap<>();
    inoutInParams.put(1, 10);  // Initial value

    Map<Integer, Integer> inoutOutParams = new HashMap<>();
    inoutOutParams.put(1, Types.INTEGER);  // Register as OUT

    JdbcCallableTask inoutTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call increment_counter(?)}")
            .inParameters(inoutInParams)
            .outParameters(inoutOutParams)
            .writingOutValuesTo("counterResult")
            .build();

    inoutTask.execute(context);

    Map<Integer, Object> result = context.get("counterResult");
    Integer newValue = (Integer) result.get(1);

    // Procedure with result sets
    // CREATE PROCEDURE get_order_details(IN order_id INT)
    // Returns multiple result sets
    Map<Integer, Object> orderParams = new HashMap<>();
    orderParams.put(1, 456);

    JdbcCallableTask resultSetTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .call("{call get_order_details(?)}")
            .inParameters(orderParams)
            .writingResultSetsTo("orderDetails")
            .build();

    resultSetTask.execute(context);

    // Result sets are List<List<Map<String, Object>>>
    List<List<Map<String, Object>>> resultSets = context.get("orderDetails");

    // First result set: order header
    List<Map<String, Object>> orderHeader = resultSets.get(0);
    Map<String, Object> order = orderHeader.getFirst();

    // Second result set: order items
    List<Map<String, Object>> orderItems = resultSets.get(1);

    // Context Mode - Dynamic procedure calls
    context.put("procCall", "{call process_payment(?, ?, ?)}");

    Map<Integer, Object> paymentParams = new HashMap<>();
    paymentParams.put(1, 789);  // user_id
    paymentParams.put(2, new BigDecimal("49.99"));  // amount
    paymentParams.put(3, "USD");  // currency
    context.put("paymentParams", paymentParams);

    Map<Integer, Integer> paymentOutParams = new HashMap<>();
    paymentOutParams.put(4, Types.VARCHAR);  // transaction_id
    context.put("paymentOutParams", paymentOutParams);

    JdbcCallableTask contextTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .readingCallFrom("procCall")
            .readingInParametersFrom("paymentParams")
            .readingOutParametersFrom("paymentOutParams")
            .writingOutValuesTo("paymentResult")
            .build();

    contextTask.execute(context);
}

// Complex workflow with stored procedures
public Workflow buildOrderProcessingWorkflow(DataSource dataSource) {
    // Validate order procedure
    JdbcCallableTask validateTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .readingCallFrom("validateCall")
            .readingInParametersFrom("validateParams")
            .readingOutParametersFrom("validateOut")
            .writingOutValuesTo("validationResult")
            .build();

    // Process order procedure
    JdbcCallableTask processTask = JdbcCallableTask.builder()
            .dataSource(dataSource)
            .readingCallFrom("processCall")
            .readingInParametersFrom("processParams")
            .writingOutValuesTo("processResult")
            .build();

    return SequentialWorkflow.builder()
            .name("ProcessOrder")
            .task(context -> {
                // Prepare validation
                Integer orderId = context.getTyped("orderId", Integer.class);
                context.put("validateCall", "{call validate_order(?, ?)}");

                Map<Integer, Object> validateParams = new HashMap<>();
                validateParams.put(1, orderId);
                context.put("validateParams", validateParams);

                Map<Integer, Integer> validateOut = new HashMap<>();
                validateOut.put(2, Types.BOOLEAN);  // is_valid
                context.put("validateOut", validateOut);
            })
            .task(validateTask)
            .task(context -> {
                Map<Integer, Object> validationResult = context.get("validationResult");
                Boolean isValid = (Boolean) validationResult.get(2);

                if (!isValid) {
                    throw new TaskExecutionException("Order validation failed");
                }

                // Prepare processing
                Integer orderId = context.getTyped("orderId", Integer.class);
                context.put("processCall", "{call process_order(?)}");

                Map<Integer, Object> processParams = new HashMap<>();
                processParams.put(1, orderId);
                context.put("processParams", processParams);
            })
            .task(processTask)
            .build();
}
```

**Context Usage**:
- **Inputs**: 
  - Call statement string (direct or from context key)
  - Map<Integer, Object> IN parameters (direct or from context key)
  - Map<Integer, Integer> OUT parameter types (direct or from context key)
- **Outputs**: 
  - Map<Integer, Object> OUT values (at configured outValuesKey)
  - List<List<Map<String, Object>>> result sets (at configured resultSetsKey)

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Database connection failures
  - SQL syntax errors
  - Stored procedure errors
  - Parameter binding errors
  - Type conversion errors
- All JDBC resources are automatically closed

**SQL Type Constants** (java.sql.Types):
- INTEGER, BIGINT, SMALLINT, TINYINT
- DECIMAL, NUMERIC, DOUBLE, FLOAT, REAL
- VARCHAR, CHAR, LONGVARCHAR, CLOB
- DATE, TIME, TIMESTAMP
- BOOLEAN, BIT
- BINARY, VARBINARY, BLOB
- ARRAY, STRUCT

**Best Practices**:
- Use stored procedures for complex business logic in database
- Document expected parameter positions and types
- Handle OUT parameter types correctly based on database
- Test stored procedures independently before workflow integration
- Use transactions when calling multiple procedures
- Consider error handling within stored procedures
- Use meaningful OUT parameter names in context

### JdbcTransactionTask

Executes multiple tasks within a single database transaction with automatic commit or rollback.

**Purpose**: Ensures ACID properties for multiple database operations - all succeed or all rollback

**Features**:
- Transaction management with automatic commit/rollback
- Multiple tasks execute within single transaction
- Connection sharing across all nested tasks
- Configurable transaction isolation level
- Auto-commit disabled during transaction
- Automatic rollback on any task failure
- Thread-safe with proper DataSource

**Builder Configuration**:
```
JdbcTransactionTask.builder()
    .dataSource(DataSource)              // JDBC DataSource (required)
    .task(Task)                          // Add task to transaction (multiple)
    .readingTasksFrom(String)            // Context key for task list (optional)
    .isolationLevel(int)                 // Transaction isolation level (optional)
    .build()
```

**Isolation Levels** (Connection constants):
- `Connection.TRANSACTION_READ_UNCOMMITTED` - Lowest isolation, allows dirty reads
- `Connection.TRANSACTION_READ_COMMITTED` - Prevents dirty reads (default for most DBs)
- `Connection.TRANSACTION_REPEATABLE_READ` - Prevents dirty and non-repeatable reads
- `Connection.TRANSACTION_SERIALIZABLE` - Highest isolation, fully isolated
- Default: Database default isolation level

**Special Context Key**:
- `"_jdbc_transaction_connection"` - Internal key used to share connection across tasks
- Nested tasks should use this connection if they need database access

**Context Usage**:
- **Inputs**: 
  - List<Task> tasks (direct or from context key)
  - All nested tasks read from shared context
- **Outputs**: 
  - Nested tasks write to context normally
  - Special key `"_jdbc_transaction_connection"` used internally

**Transaction Semantics**:
1. Acquires connection from DataSource
2. Disables auto-commit
3. Sets configured isolation level (if specified)
4. Stores connection in context for nested tasks
5. Executes all tasks sequentially
6. On success: Commits transaction
7. On failure: Rolls back transaction and throws exception
8. Always: Removes connection from context and closes it

**Error Handling**:
- Throws `TaskExecutionException` on:
  - Connection acquisition failure
  - Any nested task failure (triggers rollback)
  - Commit failure
  - Rollback failure (logged but original exception propagated)
- All JDBC resources are automatically closed
- Transaction always rolled back on any task failure

**Best Practices**:
- Keep transactions short to minimize lock contention
- Use appropriate isolation level for your use case:
  - READ_COMMITTED: Good default for most applications
  - REPEATABLE_READ: When you need consistent reads
  - SERIALIZABLE: For critical financial transactions
- Avoid long-running operations inside transactions
- Be aware of deadlock potential with multiple concurrent transactions
- Use connection pooling (HikariCP recommended)
- Test rollback behavior thoroughly
- Monitor transaction duration in production
- Consider retry logic for transient failures
- Don't mix transaction boundaries (nested tasks should not start new transactions)

**Deadlock Prevention**:
- Access tables in consistent order across transactions
- Keep transactions short
- Use appropriate isolation level (lower = less locking)
- Consider optimistic locking strategies
- Monitor for deadlocks in production

## Processing Tasks

### ShellCommandTask

Executes system shell commands.

**Purpose**: System integration, external tools

**Features**:
- Command execution
- Working directory support
- stdout/stderr capture
- Exit code checking

**Builder Configuration**:
```
ShellCommandTask.builder()
    .command(String...)           // Command and arguments
    .workingDirectory(Path)       // Working directory
    .stdoutContextKey(String)     // Key for stdout
    .stderrContextKey(String)     // Key for stderr
    .exitCodeContextKey(String)   // Key for exit code
    .build()
```

**Examples**:

```java
// Run script
ShellCommandTask task = ShellCommandTask.builder()
    .command("python3", "process.py", "input.json")
    .workingDirectory(Path.of("/opt/scripts"))
    .stdoutContextKey("scriptOutput")
    .stderrContextKey("scriptErrors")
    .build();

// Git operations
ShellCommandTask gitClone = ShellCommandTask.builder()
    .command("git", "clone", repoUrl)
    .workingDirectory(Path.of("/tmp"))
    .build();

// Data processing
ShellCommandTask csvProcess = ShellCommandTask.builder()
    .command("awk", "-F,", "{print $1,$3}", "data.csv")
    .stdoutContextKey("processedCsv")
    .build();
```

**Context Usage**:
- **Inputs**: Command arguments can reference context
- **Outputs**: 
  - stdout at specified key
  - stderr at specified key
  - exit code at specified key

**Security Considerations**:
- ⚠️ Be careful with user input in commands
- Validate command arguments
- Consider command injection risks
- Run with minimal privileges

## Utility Tasks

### NoOpTask

Does nothing - useful for testing and placeholders.

**Purpose**: Testing, placeholders

**Examples**:

```java
public void example() {
    // Placeholder in development
    Task placeholder = new NoOpTask();

    // Testing workflow structure
    SequentialWorkflow.builder()
            .task(new NoOpTask())
            .task(new NoOpTask())
            .task(new NoOpTask())
            .build();

    // Conditional default
    Task defaultBranch = new NoOpTask();
}
```

**Context Usage**:
- **Inputs**: None
- **Outputs**: None

## Creating Custom Tasks

### Extending AbstractTask

**Simple Task Template**:

```java
public class MyTask extends AbstractTask {
    private final String config;
    
    public MyTask(String config) {
        this.config = config;
    }
    
    @Override
    protected void doExecute(WorkflowContext context) 
        throws TaskExecutionException {
        
        // 1. Read inputs from context
        String input = context.getTyped("inputKey", String.class);
        
        // 2. Validate inputs
        if (input == null || input.isEmpty()) {
            throw new TaskExecutionException("Input is required");
        }
        
        // 3. Execute business logic
        String result = processData(input, config);
        
        // 4. Write outputs to context
        context.put("outputKey", result);
    }
    
    @Override
    public String getName() {
        return "MyTask";
    }
    
    private String processData(String input, String config) {
        // Implementation
        return input.toUpperCase();
    }
}
```

### Best Practices for Custom Tasks

1. **Single Responsibility**: One task, one job
2. **Idempotent**: Same input → same output, no side effects
3. **Fail Fast**: Validate inputs early
4. **Clear Errors**: Descriptive exception messages
5. **Document Context**: What keys are read/written
6. **Thread Safety**: If used in parallel workflows
7. **Resource Cleanup**: Release resources in finally blocks
8. **Naming**: Descriptive task names

### Example: Custom Integration Task

```java
public class SlackNotificationTask extends AbstractTask {
    private final String webhookUrl;
    private final HttpClient httpClient;
    
    public SlackNotificationTask(String webhookUrl, HttpClient httpClient) {
        this.webhookUrl = webhookUrl;
        this.httpClient = httpClient;
    }
    
    @Override
    protected void doExecute(WorkflowContext context) 
        throws TaskExecutionException {
        
        // Read message from context
        String message = context.getTyped("notificationMessage", String.class);
        if (message == null) {
            throw new TaskExecutionException("Message is required");
        }
        
        // Build Slack payload
        String payload = String.format("{\"text\":\"%s\"}", message);
        
        // Send notification
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new TaskExecutionException(
                    "Slack notification failed: " + response.body()
                );
            }
            
            // Store response
            context.put("notificationSent", true);
            context.put("notificationTimestamp", Instant.now());
            
        } catch (IOException | InterruptedException e) {
            throw new TaskExecutionException("Failed to send notification", e);
        }
    }
    
    @Override
    public String getName() {
        return "SlackNotification";
    }
}
```

## Task Composition Patterns

### Sequential Composition

```java
public void example() {
    SequentialWorkflow.builder()
            .task(task1)
            .task(task2)
            .task(task3)
            .build();
}
```

### Parallel Composition

```java
public void example() {
    ParallelWorkflow.builder()
            .task(task1)
            .task(task2)
            .task(task3)
            .build();
}
```

## Summary

The Workflow Engine provides a comprehensive set of tasks covering:

1. **HTTP Operations**: GET, POST, PUT, DELETE with full request/response handling
2. **File Operations**: Read and write with encoding support
3. **Control Flow**: Conditional, switch, delay for branching and timing
4. **Processing**: JavaScript and shell command execution
5. **Resilience**: Retry and timeout decorators
6. **Composition**: Sequential and parallel task composition
7. **Utilities**: Testing and placeholder tasks

All tasks follow consistent patterns:
- Read inputs from WorkflowContext
- Execute business logic
- Write outputs to WorkflowContext
- Throw TaskExecutionException on failure
- Provide descriptive names for logging

Tasks are composable, testable, and designed for both simple and complex workflow scenarios.
