# Workflow Engine - Rate Limiting Guide

## Table of Contents
- [Overview](#overview)
- [Rate Limiting Strategies](#rate-limiting-strategies)
- [Fixed Window Rate Limiter](#fixed-window-rate-limiter)
- [Sliding Window Rate Limiter](#sliding-window-rate-limiter)
- [Token Bucket Rate Limiter](#token-bucket-rate-limiter)
- [Leaky Bucket Rate Limiter](#leaky-bucket-rate-limiter)
- [Resilience4j Rate Limiter](#resilience4j-rate-limiter)
- [Rate Limited Workflow](#rate-limited-workflow)
- [Use Cases](#use-cases)
- [Best Practices](#best-practices)
- [Performance Considerations](#performance-considerations)

## Overview

Rate limiting controls the frequency of workflow executions within a time window. The Workflow Engine provides a flexible rate limiting framework with multiple algorithms to suit different requirements.

### Why Rate Limiting?

- **API Compliance**: Respect external API rate limits
- **Resource Protection**: Prevent system overload
- **Fair Usage**: Distribute resources fairly among users
- **Cost Control**: Control usage of metered services
- **Stability**: Maintain system stability under load

### Core Concepts

```java
// 1. Choose a rate limiting strategy
RateLimitStrategy limiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));

// 2. Wrap your workflow
Workflow rateLimited = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// 3. Execute - automatically rate limited
public void processWithRateLimit(Workflow rateLimited, WorkflowContext context) {
    rateLimited.execute(context);
}
```

## Rate Limiting Strategies

The framework provides six rate limiting strategies:

| Strategy            | Best For          | Burst Support       | Accuracy  | Memory  | External Lib    |
|---------------------|-------------------|---------------------|-----------|---------|-----------------|
| **Fixed Window**    | Simple cases      | Yes (at boundaries) | Moderate  | Low     | None            |
| **Sliding Window**  | Accurate limiting | No                  | High      | Medium  | None            |
| **Token Bucket**    | Burst tolerance   | Yes                 | High      | Low     | None            |
| **Leaky Bucket**    | Steady rate       | Limited             | High      | Low     | None            |
| **Resilience4j**    | Production use    | Configurable        | High      | Low     | Resilience4j    |
| **Bucket4j**        | High performance  | Yes                 | High      | Low     | Bucket4j        |

### Strategy Interface

```java
public interface RateLimitStrategy {
    void acquire() throws InterruptedException;           // Blocking acquire
    boolean tryAcquire();                                 // Non-blocking attempt
    boolean tryAcquire(long timeoutMillis);               // Timed acquire
    int availablePermits();                               // Current permits
    void reset();                                         // Reset state
}
```

## Fixed Window Rate Limiter

Divides time into fixed windows and allows a maximum number of requests per window.

### Characteristics

- ✅ Simple and efficient
- ✅ Low memory overhead
- ✅ Allows bursts at window boundaries
- ⚠️ Can allow 2x limit at boundaries

### Example: Boundary Effect

```
Time:     0s -------- 59s | 60s -------- 119s
Window:   [  Window 1    ] [   Window 2    ]
Requests: 100 at 59s      | 100 at 60s
Total:    200 requests in 1 second
```

### Usage

```java
// Allow 100 requests per minute
RateLimitStrategy limiter = new FixedWindowRateLimiter(
    100,                        // Max requests
    Duration.ofMinutes(1)       // Window size
);

Workflow rateLimited = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Configuration Options

```java
// Constructor
FixedWindowRateLimiter(int maxRequests, Duration windowSize);

// Methods
int getMaxRequests();              // Get max requests per window
long getWindowSizeMillis();        // Get window size in milliseconds
int availablePermits();            // Current available permits
```

### Example: Simple API Rate Limiting

```java
// GitHub API: 60 requests per hour
RateLimitStrategy githubLimiter = new FixedWindowRateLimiter(
    60,
    Duration.ofHours(1)
);

Task fetchRepoTask = new GetTask.Builder<>(client)
    .url("https://api.github.com/repos/owner/repo")
    .header("Authorization", "token " + token)
    .build();

Workflow rateLimited = RateLimitedWorkflow.builder()
    .name("GitHubAPI")
    .workflow(new TaskWorkflow(fetchRepoTask))
    .rateLimitStrategy(githubLimiter)
    .build();
```

## Sliding Window Rate Limiter

Maintains a sliding window by tracking individual request timestamps.

### Characteristics

- ✅ Accurate rate limiting
- ✅ No boundary effects
- ✅ Smooth request distribution
- ⚠️ Higher memory usage (stores timestamps)

### Example: Sliding Window vs Fixed

```
Fixed Window (100/min):
[-- 100 requests --][-- 100 requests --]
Can get 200 in 1 second at boundary

Sliding Window (100/min):
[---- 60 seconds ----] Always exactly 100
[---- 60 seconds ----] in any 60s period
```

### Usage

```java
// Allow 100 requests per minute with sliding window
RateLimitStrategy limiter = new SlidingWindowRateLimiter(
    100,                        // Max requests
    Duration.ofMinutes(1)       // Window size
);

Workflow rateLimited = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Configuration Options

```java
// Constructor
SlidingWindowRateLimiter(int maxRequests, Duration windowSize);

// Methods
int getMaxRequests();              // Get max requests
long getWindowSizeMillis();        // Get window size
int getCurrentRequestCount();      // Current requests in window
int availablePermits();            // Available permits
```

### Example: Accurate API Throttling

```java
// Twitter API: 900 requests per 15 minutes
RateLimitStrategy twitterLimiter = new SlidingWindowRateLimiter(
    900,
    Duration.ofMinutes(15)
);

Workflow twitterWorkflow = RateLimitedWorkflow.builder()
    .name("TwitterAPI")
    .workflow(fetchTweetsWorkflow)
    .rateLimitStrategy(twitterLimiter)
    .build();

// No boundary issues - always respects limit
```

## Token Bucket Rate Limiter

Uses token bucket algorithm: tokens refill at a constant rate, requests consume tokens.

### Characteristics

- ✅ Allows controlled bursts
- ✅ Maintains average rate
- ✅ Flexible capacity
- ✅ Low memory overhead

### How It Works

```
Bucket Capacity: 200 tokens
Refill Rate: 100 tokens/second

[========] 200 tokens → Can burst 200 requests
[====    ] 100 tokens → Normal operation  
[        ] 0 tokens → Must wait for refill

Time progression:
T=0: [========] 200 → Burst of 150 requests
T=0: [==      ] 50 remaining
T=1: [======  ] 150 (refilled 100)
```

### Usage

```java
// Allow 100 req/sec with burst capacity of 200
RateLimitStrategy limiter = new TokenBucketRateLimiter(
    100,                        // Tokens per second
    200,                        // Bucket capacity (burst)
    Duration.ofSeconds(1)       // Refill period
);

// Or use capacity = refill rate
RateLimitStrategy simple = new TokenBucketRateLimiter(
    100,                        // Tokens per second (also capacity)
    Duration.ofSeconds(1)
);
```

### Configuration Options

```java
// Constructors
TokenBucketRateLimiter(double tokensPerRefill, int capacity, Duration period);
TokenBucketRateLimiter(int tokensPerRefill, Duration period);

// Methods
int getCapacity();                 // Get bucket capacity
double getTokensPerRefill();       // Get refill rate
double getCurrentTokens();         // Current tokens in bucket
```

### Example: API with Burst Support

```java
// Allow 10 req/sec average, burst up to 50
RateLimitStrategy limiter = new TokenBucketRateLimiter(
    10,     // 10 tokens per second
    50,     // Burst capacity
    Duration.ofSeconds(1)
);

Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(processWorkflow)
    .rateLimitStrategy(limiter)
    .build();

public void simulateTraffic(Workflow workflow, WorkflowContext context) {
    // Initial burst of 50 requests (fast)
    for (int i = 0; i < 50; i++) {
        workflow.execute(context); // Quick burst
    }

    // Subsequent requests throttled to 10/sec
    for (int i = 0; i < 20; i++) {
        workflow.execute(context); // Steady rate
    }
}
```

## Leaky Bucket Rate Limiter

Ensures constant output rate by "leaking" requests at a steady pace.

### Characteristics

- ✅ Perfectly constant output rate
- ✅ Smooth burst handling
- ✅ Predictable behavior
- ⚠️ Less burst tolerance than token bucket

### How It Works

```
Input (bursty):    10  0  0  0  50  0  0  0  30
                    ↓  ↓  ↓  ↓   ↓  ↓  ↓  ↓   ↓
                 ┌──────────────────────────┐
                 │   Bucket (capacity=20)   │
                 │  [=================]     │
                 └──────────┬───────────────┘
                            ↓ (constant leak)
Output (smooth):  10 10 10 10  10 10 10 10  10

Excess rejected when bucket full
```

### Usage

```java
// Process 10 requests per second, bucket capacity 20
RateLimitStrategy limiter = new LeakyBucketRateLimiter(
    10,                         // Requests per second
    20,                         // Bucket capacity
    Duration.ofSeconds(1)       // Rate period
);

// Or use capacity = rate
RateLimitStrategy simple = new LeakyBucketRateLimiter(
    10,                         // Requests per second (also capacity)
    Duration.ofSeconds(1)
);
```

### Configuration Options

```java
// Constructors
LeakyBucketRateLimiter(double requestsPerPeriod, int capacity, Duration period);
LeakyBucketRateLimiter(int requestsPerPeriod, Duration period);

// Methods
int getCapacity();                 // Get bucket capacity
double getRequestsPerPeriod();     // Get leak rate
double getCurrentWaterLevel();     // Current water level
```

### Example: Steady State Processing

```java
// Ensure exactly 100 req/min output rate
RateLimitStrategy limiter = new LeakyBucketRateLimiter(
    100,
    Duration.ofMinutes(1)
);

Workflow steadyWorkflow = RateLimitedWorkflow.builder()
    .workflow(externalApiWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// Input can be bursty, output is always steady
```

## Resilience4j Rate Limiter

Production-ready rate limiter using the Resilience4j library.

### Characteristics

- ✅ Battle-tested implementation
- ✅ Fully thread-safe
- ✅ Event monitoring support
- ✅ Metrics and observability
- ✅ Integration with existing Resilience4j setup

### Why Use Resilience4j?

Resilience4j is a lightweight, easy-to-use fault tolerance library designed for Java 8+ and functional programming. It provides:

- **Production-Ready**: Extensively tested in production environments
- **Lightweight**: Minimal dependencies, no Spring required
- **Observable**: Built-in event publishing and metrics
- **Configurable**: Flexible configuration options
- **Ecosystem Integration**: Works with Spring Boot, Micrometer, Prometheus

### How It Works

```
Cycle-based rate limiting:

Cycle 1 (1s): [=== 100 permits ===]
              Permits refreshed at cycle start
              
Cycle 2 (1s): [=== 100 permits ===]
              All threads compete for permits
              
Threads wait when permits exhausted
until next cycle begins
```

### Usage

```java
// Basic usage: 100 requests per second
RateLimitStrategy limiter = new Resilience4jRateLimiter(
    100,                        // Limit for period
    Duration.ofSeconds(1)       // Refresh period
);

// With custom timeout
RateLimitStrategy limiter = new Resilience4jRateLimiter(
    100,                        // Limit for period
    Duration.ofSeconds(1),      // Refresh period
    Duration.ofSeconds(5)       // Timeout duration
);

// Wrap workflow
Workflow rateLimited = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Configuration Options

```java
// Constructors
Resilience4jRateLimiter(int limitForPeriod, Duration limitRefreshPeriod);
Resilience4jRateLimiter(int limitForPeriod, Duration limitRefreshPeriod, Duration timeoutDuration);
Resilience4jRateLimiter(String name, int limitForPeriod, Duration limitRefreshPeriod, Duration timeoutDuration);
Resilience4jRateLimiter(String name, RateLimiterConfig config);
Resilience4jRateLimiter(RateLimiter rateLimiter);

// Methods
int getLimitForPeriod();           // Get limit per refresh period
Duration getLimitRefreshPeriod();  // Get refresh period
Duration getTimeoutDuration();     // Get timeout duration
String getName();                  // Get rate limiter name
RateLimiter getRateLimiter();      // Get underlying Resilience4j instance
int getNumberOfWaitingThreads();   // Get waiting thread count
```

### Example: Custom Configuration

```java
// Advanced configuration with custom settings
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(100)                           // 100 permits per period
    .limitRefreshPeriod(Duration.ofSeconds(1))     // Refresh every second
    .timeoutDuration(Duration.ofSeconds(10))       // Wait up to 10 seconds
    .build();

RateLimitStrategy limiter = new Resilience4jRateLimiter("myAPI", config);

Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(externalApiWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Example: Event Monitoring

```java
// Create Resilience4j rate limiter with monitoring
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(50)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ofSeconds(5))
    .build();

RateLimiter r4jLimiter = RateLimiter.of("monitoredAPI", config);

// Register event listeners
public void listeners() {
    r4jLimiter.getEventPublisher()
            .onSuccess(event -> log.info("Request permitted - Available: {}",
                    event.getAvailablePermissions()))
            .onFailure(event -> log.warn("Request rejected - Rate limit exceeded"));
}

// Wrap in workflow strategy
RateLimitStrategy limiter = new Resilience4jRateLimiter(r4jLimiter);

Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// Execute with monitoring
public void example() {
    listeners();
    workflow.execute(context);
}

// Access metrics
int available = r4jLimiter.getMetrics().getAvailablePermissions();
int waiting = r4jLimiter.getMetrics().getNumberOfWaitingThreads();
```

### Example: Integration with Existing Setup

```java
// If you already use Resilience4j in your application,
// you can reuse existing rate limiter instances

// Your existing application setup
RateLimiter existingLimiter = RateLimiter.of("appLimiter", myConfig);

// Integrate with workflow engine
RateLimitStrategy limiter = new Resilience4jRateLimiter(existingLimiter);

Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(businessWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// Now your workflow uses the same rate limiter as the rest of your app
```

### Example: Production API Throttling

```java
public void example() {
    // Real-world API rate limiting (GitHub API: 5000 req/hour)
    RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(5000)
            .limitRefreshPeriod(Duration.ofHours(1))
            .timeoutDuration(Duration.ofMinutes(5))
            .build();

    RateLimiter r4jLimiter = RateLimiter.of("githubAPI", config);

    // Add monitoring
    r4jLimiter.getEventPublisher().onEvent(event -> {
        if (event.getEventType() == RateLimiterEvent.Type.SUCCESSFUL_ACQUIRE) {
            log.debug("API call permitted");
        } else if (event.getEventType() == RateLimiterEvent.Type.FAILED_ACQUIRE) {
            log.warn("API rate limit reached - request blocked");
        }
    });

    RateLimitStrategy limiter = new Resilience4jRateLimiter(r4jLimiter);

    // Create workflow
    Workflow githubApiWorkflow = RateLimitedWorkflow.builder()
            .name("GitHubAPI")
            .workflow(fetchRepositoriesWorkflow)
            .rateLimitStrategy(limiter)
            .build();
}
```

### Metrics and Observability

Resilience4j provides rich metrics that can be exported to monitoring systems:

```java
Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(100, Duration.ofSeconds(1));

// Access Resilience4j metrics
RateLimiter r4j = limiter.getRateLimiter();
RateLimiter.Metrics metrics = r4j.getMetrics();

// Available metrics:
int availablePermissions = metrics.getAvailablePermissions();
int waitingThreads = metrics.getNumberOfWaitingThreads();

// Integration with Micrometer (for Prometheus, etc.)
// If using Spring Boot with Resilience4j starter:
// The metrics are automatically exported to your monitoring system
```

### When to Use Resilience4j Rate Limiter

✅ **Use Resilience4j when:**
- Building production applications
- Already using Resilience4j for circuit breakers, retry, etc.
- Need observability and metrics integration
- Require battle-tested, proven implementation
- Using Spring Boot with Resilience4j starter
- Need event-driven monitoring

❌ **Consider other implementations when:**
- Want zero external dependencies
- Need specific algorithm characteristics (sliding window, leaky bucket)
- Have very simple rate limiting needs
- Want to minimize library footprint

## Bucket4j Rate Limiter

High-performance rate limiter using the Bucket4j library based on token bucket algorithm.

### Characteristics

- ✅ High performance with lock-free implementation
- ✅ Token bucket algorithm with burst support
- ✅ Multiple refill strategies (greedy vs intervally)
- ✅ Flexible bandwidth configuration
- ✅ Fully thread-safe with minimal contention
- ✅ Distributed rate limiting support (via extensions)

### Why Use Bucket4j?

Bucket4j is a Java rate-limiting library specifically designed for high-performance scenarios. It provides:

- **Performance**: Lock-free atomic operations for minimal contention
- **Flexibility**: Rich API for complex rate limiting scenarios
- **Token Bucket**: Classic algorithm with proven characteristics
- **Burst Handling**: Configurable burst capacity
- **Production Ready**: Battle-tested in high-throughput systems
- **Distributed Support**: Extensions for distributed rate limiting (Redis, Hazelcast, etc.)

### How It Works

```
Token Bucket Algorithm:

Bucket Capacity: 100 tokens
Refill Rate: 50 tokens/second

[========] 100 tokens → Can handle burst of 100 requests
[====    ] 50 tokens  → Normal operation
[        ] 0 tokens   → Must wait for refill

Time progression:
T=0s: [========] 100 tokens → Burst of 80 requests
T=0s: [==      ] 20 tokens remaining
T=1s: [======  ] 70 tokens (refilled 50)
T=2s: [========] 100 tokens (capped at capacity)
```

### Refill Strategies

Bucket4j supports two refill strategies:

**GREEDY Refill** (default):
- All tokens are added immediately when the refill period occurs
- Best for scenarios where burst traffic is acceptable
- Simple and predictable

```
Time:  0s   0.5s  1.0s  1.5s  2.0s
Tokens: 100 → 50 → 100 → 50 → 100
              ↑    ↑        ↑    ↑
              Use  Refill  Use  Refill all at once
```

**INTERVALLY Refill**:
- Tokens are added gradually at fixed intervals
- Better for smoothing out traffic over time
- More evenly distributed

```
Time:  0s   0.5s  1.0s  1.5s  2.0s
Tokens: 100 → 75 → 100 → 75 → 100
              ↑         ↑
              Gradual   Gradual
              refill    refill
```

### Usage

```java
// Basic usage: 100 requests per second
RateLimitStrategy limiter = new Bucket4jRateLimiter(
    100,                        // Capacity
    Duration.ofSeconds(1)       // Refill period
);

// With burst capacity different from refill rate
RateLimitStrategy burstLimiter = new Bucket4jRateLimiter(
    200,                        // Burst capacity
    100,                        // Refill tokens per period
    Duration.ofSeconds(1),      // Refill period
    Bucket4jRateLimiter.RefillStrategy.GREEDY
);

// Wrap workflow
Workflow rateLimited = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Configuration Options

```java
// Constructors
Bucket4jRateLimiter(long capacity, Duration refillPeriod);
Bucket4jRateLimiter(long capacity, long refillTokens, Duration refillPeriod, RefillStrategy refillStrategy);
Bucket4jRateLimiter(Bandwidth bandwidth);
Bucket4jRateLimiter(BucketConfiguration configuration);
Bucket4jRateLimiter(LocalBucket bucket);

// Methods
long getCapacity();                // Get bucket capacity
int availablePermits();            // Get available tokens
LocalBucket getBucket();           // Get underlying Bucket4j bucket
Bandwidth getBandwidth();          // Get bandwidth configuration
```

### Example: Simple Rate Limiting

```java
// Allow 50 API calls per minute
RateLimitStrategy limiter = new Bucket4jRateLimiter(50, Duration.ofMinutes(1));

Workflow apiWorkflow = RateLimitedWorkflow.builder()
    .workflow(callExternalApiWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// Execute - automatically rate limited
for (int i = 0; i < 100; i++) {
    apiWorkflow.execute(context); // First 50 succeed, rest wait for refill
}
```

### Example: Burst Capacity

```java
// Allow bursts of 100, but sustained rate of 50/second
RateLimitStrategy limiter = new Bucket4jRateLimiter(
    100,                                           // Burst capacity
    50,                                            // Refill rate
    Duration.ofSeconds(1),
    Bucket4jRateLimiter.RefillStrategy.GREEDY
);

Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(imageProcessingWorkflow)
    .rateLimitStrategy(limiter)
    .build();

// First 100 requests process immediately (burst)
// Then limited to 50/second
```

### Example: Custom Bandwidth Configuration

```java
// Use Bucket4j's Bandwidth API directly for advanced control
Bandwidth bandwidth = Bandwidth.builder()
    .capacity(100)
    .refillGreedy(100, Duration.ofSeconds(1))
    .build();

RateLimitStrategy limiter = new Bucket4jRateLimiter(bandwidth);

Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(dataProcessingWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Example: Multiple Bandwidth Limits

```java
// Bucket4j supports multiple bandwidth limits on a single bucket
// This allows complex scenarios like "100/sec AND 1000/min"

LocalBucket bucket = Bucket.builder()
    .addLimit(Bandwidth.builder()
        .capacity(100)
        .refillGreedy(100, Duration.ofSeconds(1))
        .build())
    .addLimit(Bandwidth.builder()
        .capacity(1000)
        .refillGreedy(1000, Duration.ofMinutes(1))
        .build())
    .build();

RateLimitStrategy limiter = new Bucket4jRateLimiter(bucket);

// Now limited by both: 100/sec AND 1000/min
Workflow workflow = RateLimitedWorkflow.builder()
    .workflow(apiWorkflow)
    .rateLimitStrategy(limiter)
    .build();
```

### Example: Intervally vs Greedy Refill

```java
// GREEDY: Best for burst tolerance
RateLimitStrategy greedyLimiter = new Bucket4jRateLimiter(
    100, 100, Duration.ofSeconds(1),
    Bucket4jRateLimiter.RefillStrategy.GREEDY
);

// INTERVALLY: Best for smooth traffic distribution
RateLimitStrategy intervallyLimiter = new Bucket4jRateLimiter(
    100, 100, Duration.ofSeconds(1),
    Bucket4jRateLimiter.RefillStrategy.INTERVALLY
);

// Use based on your traffic patterns
Workflow burstWorkflow = RateLimitedWorkflow.builder()
    .workflow(handleBurstyTraffic)
    .rateLimitStrategy(greedyLimiter)
    .build();

Workflow steadyWorkflow = RateLimitedWorkflow.builder()
    .workflow(handleSteadyTraffic)
    .rateLimitStrategy(intervallyLimiter)
    .build();
```

### Example: Production API with Multiple Tiers

```java
public void example() {
    // Tier 1: Free users - 10 req/min
    RateLimitStrategy freeTierLimiter = new Bucket4jRateLimiter(10, Duration.ofMinutes(1));

    // Tier 2: Premium users - 100 req/min with burst of 150
    RateLimitStrategy premiumTierLimiter = new Bucket4jRateLimiter(
            150, 100, Duration.ofMinutes(1),
            Bucket4jRateLimiter.RefillStrategy.GREEDY
    );

    // Tier 3: Enterprise users - 1000 req/min with high burst
    RateLimitStrategy enterpriseTierLimiter = new Bucket4jRateLimiter(
            1500, 1000, Duration.ofMinutes(1),
            Bucket4jRateLimiter.RefillStrategy.GREEDY
    );

    // Apply based on user tier
    RateLimitStrategy limiter = getUserTier() == "free" ? freeTierLimiter :
            getUserTier() == "premium" ? premiumTierLimiter :
                    enterpriseTierLimiter;

    Workflow apiWorkflow = RateLimitedWorkflow.builder()
            .workflow(businessLogicWorkflow)
            .rateLimitStrategy(limiter)
            .build();
}
```

### Performance Characteristics

Bucket4j is designed for high-performance scenarios:

- **Lock-free**: Uses atomic operations for minimal thread contention
- **No GC pressure**: Minimal object allocation
- **CPU efficient**: Simple arithmetic operations
- **Memory efficient**: Compact state representation

Performance comparison (approximate):
```
Operation: tryAcquire()
Bucket4j:          ~50-100 ns per operation
Token Bucket:      ~100-200 ns per operation (with locks)
Fixed Window:      ~50-100 ns per operation
Resilience4j:      ~100-200 ns per operation
```

### When to Use Bucket4j Rate Limiter

✅ **Use Bucket4j when:**
- Need high-performance rate limiting with minimal overhead
- Want token bucket algorithm with burst support
- Require flexible burst capacity configuration
- Building high-throughput APIs
- Need multiple bandwidth limits per bucket
- Want lock-free implementation for low latency
- May need distributed rate limiting in the future

❌ **Consider other implementations when:**
- Want zero external dependencies
- Need specific algorithms (sliding window, leaky bucket)
- Have very simple rate limiting needs
- Prefer cycle-based over token-based limiting

### Distributed Rate Limiting

While this integration uses local buckets, Bucket4j supports distributed rate limiting through extensions:

```java
// Example: Distributed rate limiting with Redis (requires bucket4j-redis extension)
// RedissonClient redisson = ...
// BucketConfiguration config = ...
// Bucket distributedBucket = redisson.getBucket("rate-limit-key", config);
// RateLimitStrategy limiter = new Bucket4jRateLimiter((LocalBucket) distributedBucket);
```

Supported distributed backends:
- Redis (via Redisson)
- Hazelcast
- Apache Ignite
- Coherence
- Infinispan

## Rate Limited Workflow

Wraps any workflow with rate limiting.

### Basic Usage

```java
Workflow rateLimited = RateLimitedWorkflow.builder()
    .name("RateLimitedAPI")              // Optional name
    .workflow(innerWorkflow)              // Workflow to rate limit
    .rateLimitStrategy(limiter)           // Rate limit strategy
    .build();
```

### Features

- **Transparent**: Works with any workflow
- **Blocking**: Waits when rate limit exceeded
- **Context Passthrough**: Preserves context
- **Error Propagation**: Preserves inner workflow errors

### Example: Sequential Pipeline

```java
Workflow pipeline = SequentialWorkflow.builder()
    .name("DataPipeline")
    
    // Rate limit data fetch
    .workflow(RateLimitedWorkflow.builder()
        .workflow(fetchWorkflow)
        .rateLimitStrategy(new FixedWindowRateLimiter(10, Duration.ofSeconds(1)))
        .build())
    
    // Process (no rate limit)
    .workflow(processWorkflow)
    
    // Rate limit data save
    .workflow(RateLimitedWorkflow.builder()
        .workflow(saveWorkflow)
        .rateLimitStrategy(new FixedWindowRateLimiter(5, Duration.ofSeconds(1)))
        .build())
    
    .build();
```

### Example: Parallel with Shared Limiter

```java
// Shared rate limiter for all parallel workflows
RateLimitStrategy sharedLimiter = new TokenBucketRateLimiter(
    100,
    200,
    Duration.ofSeconds(1)
);

Workflow parallel = ParallelWorkflow.builder()
    .workflow(RateLimitedWorkflow.builder()
        .workflow(workflow1)
        .rateLimitStrategy(sharedLimiter)
        .build())
    .workflow(RateLimitedWorkflow.builder()
        .workflow(workflow2)
        .rateLimitStrategy(sharedLimiter)
        .build())
    .workflow(RateLimitedWorkflow.builder()
        .workflow(workflow3)
        .rateLimitStrategy(sharedLimiter)
        .build())
    .build();

// All workflows share the same 100 req/sec limit
```

## Use Cases

### 1. API Rate Limiting

```java
// External API: 1000 requests per hour
RateLimitStrategy apiLimiter = new SlidingWindowRateLimiter(
    1000,
    Duration.ofHours(1)
);

Workflow apiWorkflow = RateLimitedWorkflow.builder()
    .workflow(externalApiCallWorkflow)
    .rateLimitStrategy(apiLimiter)
    .build();
```

### 2. Database Connection Pooling

```java
// Limit concurrent database operations
RateLimitStrategy dbLimiter = new TokenBucketRateLimiter(
    50,     // 50 queries per second
    100,    // Burst up to 100
    Duration.ofSeconds(1)
);

Workflow dbWorkflow = RateLimitedWorkflow.builder()
    .workflow(databaseQueryWorkflow)
    .rateLimitStrategy(dbLimiter)
    .build();
```

### 3. Email Sending

```java
// Email provider: 100 emails per minute
RateLimitStrategy emailLimiter = new LeakyBucketRateLimiter(
    100,
    Duration.ofMinutes(1)
);

Workflow emailWorkflow = RateLimitedWorkflow.builder()
    .workflow(sendEmailWorkflow)
    .rateLimitStrategy(emailLimiter)
    .build();
```

### 4. File Processing

```java
// Limit file processing to avoid disk I/O saturation
RateLimitStrategy fileLimiter = new FixedWindowRateLimiter(
    10,     // 10 files per second
    Duration.ofSeconds(1)
);

Workflow fileWorkflow = RateLimitedWorkflow.builder()
    .workflow(fileProcessingWorkflow)
    .rateLimitStrategy(fileLimiter)
    .build();
```

### 5. Multi-Tenant Resource Sharing

```java
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiTenantManager {
    // 1. Store limiters as a field to persist them across requests
    private final Map<String, RateLimitStrategy> tenantLimiters = new HashMap<>();

    public void initializeLimiters(List<String> tenants) {
        for (String tenant : tenants) {
            // Each tenant gets their own "bucket" of 100 requests
            tenantLimiters.put(tenant, new TokenBucketRateLimiter(
                    100,
                    Duration.ofMinutes(1)
            ));
        }
    }

    public Workflow getWorkflowForTenant(String currentTenant, Workflow processingWorkflow) {
        // 2. Retrieve the specific limiter for this tenant
        RateLimitStrategy limiter = tenantLimiters.get(currentTenant);

        // 3. Wrap the shared workflow with the tenant-specific rate limit
        return RateLimitedWorkflow.builder()
                .workflow(processingWorkflow)
                .rateLimitStrategy(limiter)
                .build();
    }
}
```

## Best Practices

### 1. Choose the Right Strategy

```java
import java.time.Duration;

public class RateLimiterConfig {
    // Define these as constants or fields
    private final RateLimitStrategy fixed = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
    private final RateLimitStrategy sliding = new SlidingWindowRateLimiter(100, Duration.ofMinutes(1));

    // 100 refill rate, 200 max burst capacity
    private final RateLimitStrategy tokenBucket = new TokenBucketRateLimiter(100, 200, Duration.ofSeconds(1));

    public void applyLimiter(RateLimitedWorkflow.Builder builder) {
        // Use them in a method
        builder.rateLimitStrategy(new LeakyBucketRateLimiter(100, Duration.ofMinutes(1)));
    }
}
```

### 2. Share Limiters When Appropriate

```java
// Don't: Create separate limiters for same resource
RateLimitStrategy limiter1 = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
RateLimitStrategy limiter2 = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
// Result: 200 req/min total (not desired)

// Do: Share limiter for same resource
RateLimitStrategy sharedLimiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
// Use sharedLimiter for all workflows accessing the resource
```

### 3. Handle Interruptions

```java
public void runWithGracefulHandling(Workflow rateLimitedWorkflow, WorkflowContext context) {
    try {
        WorkflowResult result = rateLimitedWorkflow.execute(context);
        if (result.getStatus() == WorkflowStatus.FAILED
                && result.getError() instanceof InterruptedException) {
            log.warn("Workflow was interrupted while waiting for rate limit");
            // Handle gracefully
        }
    } catch (Exception e) {
        log.error("Execution failed", e);
    }
}
```

### 4. Monitor Rate Limits

```java
public void monitorCapacity() {
    RateLimitStrategy limiter = new TokenBucketRateLimiter(100, 200, Duration.ofSeconds(1));

    // Check available permits before critical operations
    int available = limiter.availablePermits();
    if (available < 10) {
        log.warn("Rate limit nearly exhausted: {} permits remaining", available);
    }
}
```

### 5. Test Rate Limiting

```java
@Test
void testRateLimiting() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(1));
    
    // Should allow 5 requests
    for (int i = 0; i < 5; i++) {
        assertTrue(limiter.tryAcquire());
    }
    
    // 6th should be denied
    assertFalse(limiter.tryAcquire());
    
    // Reset and verify
    limiter.reset();
    assertTrue(limiter.tryAcquire());
}
```

### 6. Gradual Rollout

```java
// Start conservative, increase gradually
RateLimitStrategy conservative = new FixedWindowRateLimiter(
    50,     // Start at 50 req/min
    Duration.ofMinutes(1)
);

// Monitor and adjust based on system performance
// Later increase to 100, then 200, etc.
```

## Performance Considerations

### 1. Strategy Performance

| Strategy       | Acquire Cost  | Memory   | Best For        |
|----------------|---------------|----------|-----------------|
| Fixed Window   | O(1)          | O(1)     | High throughput |
| Sliding Window | O(n)          | O(n)     | Accuracy        |
| Token Bucket   | O(1)          | O(1)     | Balance         |
| Leaky Bucket   | O(1)          | O(1)     | Steady rate     |

### 2. Blocking Behavior

```java
public void handleTraffic(RateLimitStrategy limiter) {
    // acquire() blocks until permit available
    limiter.acquire();  // May block

    // tryAcquire() returns immediately
    if (limiter.tryAcquire()) {
        // Process
        process();
    } else {
        // Handle rate limit
        handleRateLimit();
    }

    // tryAcquire(timeout) waits up to timeout
    if (limiter.tryAcquire(1000)) {  // Wait up to 1 second
        // Process
        process();
    }
}
```

### 3. Overhead

- **Fixed Window**: Minimal overhead (~1μs per acquire)
- **Sliding Window**: Moderate overhead (~10μs, depends on window size)
- **Token Bucket**: Minimal overhead (~2μs per acquire)
- **Leaky Bucket**: Minimal overhead (~2μs per acquire)

### 4. Contention

```java
// High contention scenario
RateLimitStrategy limiter = new TokenBucketRateLimiter(1000, Duration.ofSeconds(1));

// Many threads competing
// Token bucket and fixed window perform best under contention
```

## Summary

The Workflow Engine provides comprehensive rate limiting support:

1. **Four Strategies**: Fixed Window, Sliding Window, Token Bucket, Leaky Bucket
2. **Flexible Integration**: Works with any workflow
3. **Thread-Safe**: All implementations are thread-safe
4. **Shared Limiters**: Support for shared rate limits
5. **Non-Blocking Options**: tryAcquire() variants
6. **Easy to Test**: Mockable and resettable

Choose the right strategy based on your requirements:
- **Simple needs** → Fixed Window
- **Accuracy** → Sliding Window
- **Bursts** → Token Bucket
- **Steady rate** → Leaky Bucket
