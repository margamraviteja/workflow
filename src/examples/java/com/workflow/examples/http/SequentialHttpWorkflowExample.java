package com.workflow.examples.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.SequentialWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.JsonProcessingException;
import com.workflow.exception.TaskExecutionException;
import com.workflow.helper.JsonUtils;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.GetHttpTask;
import com.workflow.task.PostHttpTask;
import com.workflow.task.TaskDescriptor;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Sequential Workflow Example using HTTP Tasks with JSONPlaceholder API.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Sequential workflow execution with HTTP GET and POST tasks
 *   <li>Using response mappers to parse JSON responses into typed Lists and Maps
 *   <li>Passing data between tasks through WorkflowContext
 *   <li>Retry and timeout policies for HTTP resilience
 *   <li>Custom response mappers for complex JSON transformations
 * </ul>
 *
 * <p>JSONPlaceholder API endpoints used:
 *
 * <ul>
 *   <li>GET /users - Fetch list of users
 *   <li>GET /users/{id} - Fetch single user by ID
 *   <li>GET /posts?userId={id} - Fetch posts for a specific user
 *   <li>POST /posts - Create a new post
 * </ul>
 */
@Slf4j
@UtilityClass
public class SequentialHttpWorkflowExample {

  private static final String BASE_URL = "https://jsonplaceholder.typicode.com";

  private static final String USERS_KEY = "users";
  private static final String SELECTED_USER_KEY = "selectedUser";
  private static final String USER_POSTS_KEY = "userPosts";
  private static final String NEW_POST_KEY = "newPost";
  public static final String TITLE = "title";
  public static final String POSTS = "posts";

  /** User DTO matching JSONPlaceholder /users response structure. */
  @Data
  public static class User {
    private Long id;
    private String name;
    private String username;
    private String email;
    private Address address;
    private String phone;
    private String website;
    private Company company;
  }

  @Data
  public static class Address {
    private String street;
    private String suite;
    private String city;
    private String zipcode;
    private Geo geo;
  }

  @Data
  public static class Geo {
    private String lat;
    private String lng;
  }

  @Data
  public static class Company {
    private String name;
    private String catchPhrase;
    private String bs;
  }

  /** Post DTO matching JSONPlaceholder /posts response structure. */
  @Data
  public static class Post {
    private Long id;
    private Long userId;
    private String title;
    private String body;
  }

  /**
   * Response mapper that parses JSON array into a List of Users. Uses TypeReference for proper
   * generic type handling.
   */
  public static Function<HttpResponse<String>, List<User>> userListResponseMapper() {
    return response -> {
      String body = response.body();
      if (body == null || body.isBlank()) {
        return List.of();
      }
      try {
        return JsonUtils.fromJson(body, new TypeReference<>() {});
      } catch (IOException e) {
        log.error("Failed to parse users list: {}", e.getMessage());
        throw new JsonProcessingException("Failed to parse users response", e);
      }
    };
  }

  /** Response mapper that parses JSON array into a List of Posts. */
  public static Function<HttpResponse<String>, List<Post>> postListResponseMapper() {
    return response -> {
      String body = response.body();
      if (body == null || body.isBlank()) {
        return List.of();
      }
      try {
        return JsonUtils.fromJson(body, new TypeReference<>() {});
      } catch (IOException e) {
        log.error("Failed to parse posts list: {}", e.getMessage());
        throw new JsonProcessingException("Failed to parse posts response", e);
      }
    };
  }

  /**
   * Response mapper that parses JSON object into a Map. Useful when you want raw map access without
   * defining a DTO.
   */
  public static Function<HttpResponse<String>, Map<String, Object>> mapResponseMapper() {
    return response -> {
      String body = response.body();
      if (body == null || body.isBlank()) {
        return Map.of();
      }
      try {
        return JsonUtils.fromJson(body, new TypeReference<>() {});
      } catch (IOException e) {
        log.error("Failed to parse response as map: {}", e.getMessage());
        throw new JsonProcessingException("Failed to parse response as map", e);
      }
    };
  }

  /**
   * Example 1: Fetch all users, select first user, fetch their posts. Demonstrates sequential HTTP
   * calls with data passing between tasks.
   */
  public static void runFetchUserPostsWorkflow() {
    log.info("=== Starting Fetch User Posts Workflow ===");

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // Task 1: Fetch all users - response mapped to List<User>
    GetHttpTask<List<User>> fetchUsersTask =
        new GetHttpTask.Builder<List<User>>(httpClient)
            .url(BASE_URL + "/users")
            .responseMapper(userListResponseMapper())
            .responseContextKey(USERS_KEY)
            .timeout(Duration.ofSeconds(15))
            .build();

    // Task 2: Fetch single user by ID - response mapped to User object
    GetHttpTask<User> fetchUserTask =
        new GetHttpTask.Builder<User>(httpClient)
            .urlFromContext("userUrl")
            .responseType(User.class)
            .responseContextKey(SELECTED_USER_KEY)
            .timeout(Duration.ofSeconds(10))
            .build();

    // Task 3: Fetch posts for user - response mapped to List<Post>
    GetHttpTask<List<Post>> fetchPostsTask =
        new GetHttpTask.Builder<List<Post>>(httpClient)
            .urlFromContext("postsUrl")
            .responseMapper(postListResponseMapper())
            .responseContextKey(USER_POSTS_KEY)
            .timeout(Duration.ofSeconds(15))
            .build();

    // Build sequential workflow
    SequentialWorkflow workflow =
        SequentialWorkflow.builder()
            .name("FetchUserPostsWorkflow")
            // Step 1: Fetch all users
            .task(
                TaskDescriptor.builder()
                    .task(fetchUsersTask)
                    .retryPolicy(RetryPolicy.limitedRetries(3))
                    .timeoutPolicy(TimeoutPolicy.ofMillis(20000))
                    .build())
            // Step 2: Select first user and prepare URL for next task
            .task(
                context -> {
                  @SuppressWarnings("unchecked")
                  List<User> users = (List<User>) context.get(USERS_KEY);
                  if (users != null && !users.isEmpty()) {
                    User firstUser = users.getFirst();
                    log.info("Selected user: {} (ID: {})", firstUser.getName(), firstUser.getId());
                    context.put("userUrl", BASE_URL + "/users/" + firstUser.getId());
                    context.put("selectedUserId", firstUser.getId());
                  } else {
                    throw new TaskExecutionException("No users found");
                  }
                })
            // Step 3: Fetch selected user details
            .task(fetchUserTask)
            // Step 4: Prepare posts URL
            .task(
                context -> {
                  Long userId = (Long) context.get("selectedUserId");
                  context.put("postsUrl", BASE_URL + "/posts?userId=" + userId);
                })
            // Step 5: Fetch user's posts
            .task(
                TaskDescriptor.builder()
                    .task(fetchPostsTask)
                    .retryPolicy(RetryPolicy.limitedRetries(2))
                    .build())
            // Step 6: Log summary
            .task(
                context -> {
                  User user = (User) context.get(SELECTED_USER_KEY);
                  @SuppressWarnings("unchecked")
                  List<Post> posts = (List<Post>) context.get(USER_POSTS_KEY);
                  log.info("=== Workflow Summary ===");
                  log.info(
                      "User: {} ({}) from {}",
                      user.getName(),
                      user.getEmail(),
                      user.getAddress().getCity());
                  log.info("Company: {}", user.getCompany().getName());
                  log.info("Number of posts: {}", posts != null ? posts.size() : 0);
                  if (posts != null && !posts.isEmpty()) {
                    log.info("First post title: {}", posts.getFirst().getTitle());
                  }
                })
            .build();

    // Execute workflow
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    log.info("Workflow completed with status: {}", result.getStatus());
    log.info("Execution duration: {}ms", result.getDuration().toMillis());

    if (result.getStatus() == WorkflowStatus.FAILED) {
      log.error("Workflow failed", result.getError());
    }
  }

  /**
   * Example 2: Fetch users, filter by criteria, and create a new post. Demonstrates POST request
   * with JSON body and Map response parsing.
   */
  public static void runCreatePostWorkflow() {
    log.info("=== Starting Create Post Workflow ===");

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // Task 1: Fetch all users as List<Map>
    GetHttpTask<List<Map<String, Object>>> fetchUsersAsMapTask =
        new GetHttpTask.Builder<List<Map<String, Object>>>(httpClient)
            .url(BASE_URL + "/users")
            .responseMapper(
                response -> {
                  try {
                    return JsonUtils.fromJson(response.body(), new TypeReference<>() {});
                  } catch (IOException e) {
                    throw new JsonProcessingException("Failed to parse users", e);
                  }
                })
            .responseContextKey("usersMap")
            .build();

    // Build workflow
    SequentialWorkflow workflow =
        SequentialWorkflow.builder()
            .name("CreatePostWorkflow")
            // Step 1: Fetch users as List<Map>
            .task(fetchUsersAsMapTask)
            // Step 2: Select a user from the map and prepare post data
            .task(
                context -> {
                  @SuppressWarnings("unchecked")
                  List<Map<String, Object>> users =
                      (List<Map<String, Object>>) context.get("usersMap");
                  if (users == null || users.isEmpty()) {
                    throw new TaskExecutionException("No users available");
                  }

                  // Select user with specific criteria (e.g., username contains 'Bret')
                  Map<String, Object> selectedUser =
                      users.stream()
                          .filter(u -> u.get("username").toString().contains("Bret"))
                          .findFirst()
                          .orElse(users.getFirst());

                  log.info(
                      "Selected user for posting: {} ({})",
                      selectedUser.get("name"),
                      selectedUser.get("username"));
                  context.put("postUserId", ((Number) selectedUser.get("id")).longValue());
                })
            // Step 3: Create new post using POST request
            .task(
                context -> {
                  Long userId = (Long) context.get("postUserId");
                  HttpClient client = HttpClient.newHttpClient();

                  String postBody;
                  try {
                    postBody =
                        JsonUtils.toJson(
                            Map.of(
                                TITLE,
                                "My New Post from Workflow",
                                "body",
                                "This is a post created by SequentialHttpWorkflowExample demonstrating HTTP task capabilities.",
                                "userId",
                                userId));
                  } catch (IOException e) {
                    throw new JsonProcessingException("Failed to serialize post body", e);
                  }

                  PostHttpTask<Map<String, Object>> createPostTask =
                      new PostHttpTask.Builder<Map<String, Object>>(client)
                          .url(BASE_URL + "/posts")
                          .body(postBody)
                          .header("Content-Type", "application/json")
                          .responseMapper(mapResponseMapper())
                          .responseContextKey(NEW_POST_KEY)
                          .build();

                  createPostTask.execute(context);
                })
            // Step 4: Verify and log created post
            .task(
                context -> {
                  @SuppressWarnings("unchecked")
                  Map<String, Object> newPost = (Map<String, Object>) context.get(NEW_POST_KEY);
                  log.info("=== Post Created Successfully ===");
                  log.info("Post ID: {}", newPost.get("id"));
                  log.info("Title: {}", newPost.get(TITLE));
                  log.info("User ID: {}", newPost.get("userId"));
                  log.info(
                      "Body preview: {}...",
                      newPost
                          .get("body")
                          .toString()
                          .substring(0, Math.min(50, newPost.get("body").toString().length())));
                })
            .build();

    // Execute
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    log.info("Create Post Workflow completed: {}", result.getStatus());
    if (result.getStatus() == WorkflowStatus.FAILED) {
      log.error("Error in create post workflow", result.getError());
    }
  }

  /**
   * Example 3: Chain multiple API calls - get user, get their posts, get comments for first post.
   * Demonstrates deep chaining with multiple response types.
   */
  public static void runChainedApiCallsWorkflow() {
    log.info("=== Starting Chained API Calls Workflow ===");

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    SequentialWorkflow workflow =
        SequentialWorkflow.builder()
            .name("ChainedApiCallsWorkflow")
            // Step 1: Get user by ID
            .task(
                new GetHttpTask.Builder<User>(httpClient)
                    .url(BASE_URL + "/users/1")
                    .responseType(User.class)
                    .responseContextKey("user")
                    .build())
            // Step 2: Log user info
            .task(
                context -> {
                  User user = (User) context.get("user");
                  log.info("Fetched user: {} ({})", user.getName(), user.getEmail());
                })
            // Step 3: Get posts for user
            .task(
                new GetHttpTask.Builder<List<Post>>(httpClient)
                    .url(BASE_URL + "/posts?userId=1")
                    .responseMapper(postListResponseMapper())
                    .responseContextKey(POSTS)
                    .build())
            // Step 4: Select first post
            .task(
                context -> {
                  @SuppressWarnings("unchecked")
                  List<Post> posts = (List<Post>) context.get(POSTS);
                  if (posts != null && !posts.isEmpty()) {
                    Post firstPost = posts.getFirst();
                    context.put("selectedPost", firstPost);
                    context.put("commentsUrl", BASE_URL + "/comments?postId=" + firstPost.getId());
                    log.info(
                        "Selected post: '{}' (ID: {})", firstPost.getTitle(), firstPost.getId());
                  }
                })
            // Step 5: Get comments for selected post
            .task(
                new GetHttpTask.Builder<List<Map<String, Object>>>(httpClient)
                    .urlFromContext("commentsUrl")
                    .responseMapper(
                        response -> {
                          try {
                            return JsonUtils.fromJson(response.body(), new TypeReference<>() {});
                          } catch (IOException e) {
                            throw new JsonProcessingException("Failed to parse comments", e);
                          }
                        })
                    .responseContextKey("comments")
                    .build())
            // Step 6: Summary
            .task(
                context -> {
                  User user = (User) context.get("user");
                  @SuppressWarnings("unchecked")
                  List<Post> posts = (List<Post>) context.get(POSTS);
                  @SuppressWarnings("unchecked")
                  List<Map<String, Object>> comments =
                      (List<Map<String, Object>>) context.get("comments");
                  Post selectedPost = (Post) context.get("selectedPost");

                  log.info("=== Chained API Calls Summary ===");
                  log.info("User: {} from {}", user.getName(), user.getAddress().getCity());
                  log.info("Total posts: {}", posts != null ? posts.size() : 0);
                  log.info(
                      "Selected post title: {}",
                      selectedPost != null ? selectedPost.getTitle() : "N/A");
                  log.info("Comments on selected post: {}", comments != null ? comments.size() : 0);

                  if (comments != null && !comments.isEmpty()) {
                    log.info(
                        "Sample comment by: {} - {}",
                        comments.getFirst().get("name"),
                        comments.getFirst().get("email"));
                  }
                })
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    log.info(
        "Chained API Calls Workflow completed: {} in {}ms",
        result.getStatus(),
        result.getDuration().toMillis());
  }

  /**
   * Example 4: Fetch todos with filtering using response mapper transformation. Demonstrates using
   * response mapper to filter and transform data inline.
   */
  public static void runTodosFilteringWorkflow() {
    log.info("=== Starting Todos Filtering Workflow ===");

    HttpClient httpClient = HttpClient.newHttpClient();

    Function<HttpResponse<String>, List<Map<String, Object>>> completedTodosMapper =
        response -> {
          try {
            List<Map<String, Object>> allTodos =
                JsonUtils.fromJson(response.body(), new TypeReference<>() {});
            return allTodos.stream()
                .filter(todo -> Boolean.TRUE.equals(todo.get("completed")))
                .toList();
          } catch (IOException e) {
            throw new JsonProcessingException("Failed to parse todos", e);
          }
        };

    Function<HttpResponse<String>, List<Map<String, Object>>> pendingTodosMapper =
        response -> {
          try {
            List<Map<String, Object>> allTodos =
                JsonUtils.fromJson(response.body(), new TypeReference<>() {});
            return allTodos.stream()
                .filter(todo -> Boolean.FALSE.equals(todo.get("completed")))
                .toList();
          } catch (IOException e) {
            throw new JsonProcessingException("Failed to parse todos", e);
          }
        };

    SequentialWorkflow workflow =
        SequentialWorkflow.builder()
            .name("TodosFilteringWorkflow")
            // Step 1: Fetch completed todos for user 1
            .task(
                new GetHttpTask.Builder<List<Map<String, Object>>>(httpClient)
                    .url(BASE_URL + "/todos?userId=1")
                    .responseMapper(completedTodosMapper)
                    .responseContextKey("completedTodos")
                    .build())
            // Step 2: Fetch pending todos for user 1
            .task(
                new GetHttpTask.Builder<List<Map<String, Object>>>(httpClient)
                    .url(BASE_URL + "/todos?userId=1")
                    .responseMapper(pendingTodosMapper)
                    .responseContextKey("pendingTodos")
                    .build())
            // Step 3: Summary
            .task(
                context -> {
                  @SuppressWarnings("unchecked")
                  List<Map<String, Object>> completed =
                      (List<Map<String, Object>>) context.get("completedTodos");
                  @SuppressWarnings("unchecked")
                  List<Map<String, Object>> pending =
                      (List<Map<String, Object>>) context.get("pendingTodos");

                  log.info("=== Todos Summary for User 1 ===");
                  log.info("Completed todos: {}", completed != null ? completed.size() : 0);
                  log.info("Pending todos: {}", pending != null ? pending.size() : 0);

                  if (pending != null && !pending.isEmpty()) {
                    log.info("First pending todo: {}", pending.getFirst().get(TITLE));
                  }
                  if (completed != null && !completed.isEmpty()) {
                    log.info("Last completed todo: {}", completed.getLast().get(TITLE));
                  }
                })
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    log.info("Todos Filtering Workflow completed: {}", result.getStatus());
  }

  /** Main method to run all examples. */
  static void main() {
    log.info("Starting Sequential HTTP Workflow Examples with JSONPlaceholder API\n");

    // Example 1: Fetch users and their posts
    runFetchUserPostsWorkflow();
    log.info("\n");

    // Example 2: Create a new post
    runCreatePostWorkflow();
    log.info("\n");

    // Example 3: Chained API calls
    runChainedApiCallsWorkflow();
    log.info("\n");

    // Example 4: Todos filtering
    runTodosFilteringWorkflow();

    log.info("\n=== All examples completed ===");
  }
}
