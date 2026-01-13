package com.workflow.task;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.helper.ResponseMappers;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for GetHttpTask, PostHttpTask, PutHttpTask, DeleteHttpTask using an in-process WireMock
 * server. The tests use a dynamic port to avoid collisions.
 */
class HttpTasksTest {

  private WireMockServer wm;
  private WorkflowContext context;
  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void startWireMock() {
    wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wm.start();
    httpClient = HttpClient.newHttpClient();
    baseUrl = "http://localhost:" + wm.port();
    context = new WorkflowContext();
  }

  @AfterEach
  void stopWireMock() {
    if (wm != null && wm.isRunning()) {
      wm.stop();
    }
  }

  // ---------- GET ----------

  @Test
  void getTask_defaultAcceptHeaderApplied_andResponseStored() {
    wm.stubFor(
        get(urlPathEqualTo("/test/get")).willReturn(aResponse().withStatus(200).withBody("ok")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient).url(baseUrl + "/test/get").build();

    task.execute(context);

    // verify WireMock received the request with Accept header
    wm.verify(
        getRequestedFor(urlPathEqualTo("/test/get"))
            .withHeader("Accept", equalTo("application/json")));

    assertEquals("ok", context.getTyped("httpResponse", String.class));
  }

  @Test
  void getTask_respectsProvidedAcceptHeader_caseInsensitive() {
    wm.stubFor(
        get(urlPathEqualTo("/test/get2")).willReturn(aResponse().withStatus(200).withBody("ok2")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/get2")
            .header("accept", "text/plain")
            .build();

    task.execute(context);

    wm.verify(
        getRequestedFor(urlPathEqualTo("/test/get2")).withHeader("accept", equalTo("text/plain")));
  }

  @Test
  void getTask_queryParams_mergeAndEncode() {
    wm.stubFor(
        get(urlPathEqualTo("/test/q"))
            .withQueryParam("a", equalTo("fromBuilder"))
            .withQueryParam("b", equalTo("ctxB"))
            .withQueryParam("c", equalTo("builderC"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    Map<String, String> ctxParams = new LinkedHashMap<>();
    ctxParams.put("a", "fromContext");
    ctxParams.put("b", "ctxB");
    context.put("queryParams", ctxParams);

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/q")
            .queryParam("a", "fromBuilder")
            .queryParam("c", "builderC")
            .build();

    task.execute(context);

    wm.verify(getRequestedFor(urlPathEqualTo("/test/q")));
    assertEquals("ok", context.getTyped("httpResponse", String.class));
  }

  // ---------- POST ----------

  @Test
  void postTask_bodyPrecedence_overFormAndContext() {
    wm.stubFor(
        post(urlPathEqualTo("/test/post"))
            .withRequestBody(equalToJson("{\"x\":1}"))
            .withHeader("Content-Type", containing("application/json"))
            .willReturn(aResponse().withStatus(201).withBody("created")));

    Map<String, String> form = Map.of("k", "v with space");
    PostHttpTask<String> task =
        new PostHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/post")
            .form(form)
            .body("{\"x\":1}")
            .build();

    context.put("requestBody", "{\"x\":999}");

    task.execute(context);

    wm.verify(
        postRequestedFor(urlPathEqualTo("/test/post"))
            .withRequestBody(equalToJson("{\"x\":1}"))
            .withHeader("Content-Type", containing("application/json")));
    assertEquals("created", context.getTyped("httpResponse", String.class));
  }

  @Test
  void postTask_usesContextRequestBody_whenNoExplicitBody() {
    wm.stubFor(
        post(urlPathEqualTo("/test/post2"))
            .withRequestBody(equalToJson("{\"from\":\"ctx\"}"))
            .withHeader("Content-Type", containing("application/json"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    PostHttpTask<String> task =
        new PostHttpTask.Builder<String>(httpClient).url(baseUrl + "/test/post2").build();

    context.put("requestBody", "{\"from\":\"ctx\"}");

    task.execute(context);

    wm.verify(
        postRequestedFor(urlPathEqualTo("/test/post2"))
            .withRequestBody(equalToJson("{\"from\":\"ctx\"}")));
  }

  @Test
  void postTask_formEncoding_spacesBecomePercent20_andReservedCharsEncoded() {
    // WireMock will receive the raw form body; assert it contains encoded pieces
    wm.stubFor(
        post(urlPathEqualTo("/test/post3"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    Map<String, String> form = new LinkedHashMap<>();
    form.put("a key", "value+plus & equals=");
    PostHttpTask<String> task =
        new PostHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/post3")
            .form(form)
            .build();

    task.execute(context);

    // fetch the last request body from WireMock's request journal
    String body =
        wm.findAll(postRequestedFor(urlPathEqualTo("/test/post3"))).getFirst().getBodyAsString();
    assertTrue(body.contains("a%20key="));
    assertTrue(body.contains("value%2Bplus%20%26%20equals%3D"));
  }

  @Test
  void postTask_doesNotOverwriteUserContentType_headerCaseInsensitive() {
    wm.stubFor(
        post(urlPathEqualTo("/test/post4"))
            .withHeader("Content-Type", equalTo("application/custom"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    PostHttpTask<String> task =
        new PostHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/post4")
            .header("content-type", "application/custom")
            .build();

    task.execute(context);

    wm.verify(
        postRequestedFor(urlPathEqualTo("/test/post4"))
            .withHeader("Content-Type", equalTo("application/custom")));
  }

  // ---------- PUT ----------

  @Test
  void putTask_bodyPrecedence_and_formImmutableCopy() {
    wm.stubFor(
        put(urlPathEqualTo("/test/put"))
            .withRequestBody(equalToJson("{\"put\":true}"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    Map<String, String> form = new LinkedHashMap<>();
    form.put("k", "v");
    PutHttpTask<String> task =
        new PutHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/put")
            .form(form)
            .body("{\"put\":true}")
            .build();

    // mutate original map after build to ensure immutability
    form.put("k2", "v2");

    task.execute(context);

    wm.verify(
        putRequestedFor(urlPathEqualTo("/test/put"))
            .withRequestBody(equalToJson("{\"put\":true}")));
  }

  @Test
  void putTask_usesForm_whenNoBodyOrContext() {
    wm.stubFor(
        put(urlPathEqualTo("/test/put2"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(containing("x=y"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    Map<String, String> form = Map.of("x", "y");
    PutHttpTask<String> task =
        new PutHttpTask.Builder<String>(httpClient).url(baseUrl + "/test/put2").form(form).build();

    task.execute(context);

    wm.verify(putRequestedFor(urlPathEqualTo("/test/put2")).withRequestBody(containing("x=y")));
  }

  // ---------- DELETE ----------

  @Test
  void deleteTask_sendsExplicitBody_whenProvided() {
    wm.stubFor(
        any(urlPathEqualTo("/test/delete"))
            .withRequestBody(equalToJson("{\"id\":1}"))
            .willReturn(aResponse().withStatus(200).withBody("deleted")));

    DeleteHttpTask<String> task =
        new DeleteHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/delete")
            .body("{\"id\":1}")
            .build();

    task.execute(context);

    wm.verify(
        deleteRequestedFor(urlPathEqualTo("/test/delete"))
            .withRequestBody(equalToJson("{\"id\":1}"))
            .withHeader("Content-Type", containing("application/json")));

    assertEquals("deleted", context.getTyped("httpResponse", String.class));
  }

  @Test
  void deleteTask_usesContextBody_whenNoExplicitBody() {
    wm.stubFor(
        any(urlPathEqualTo("/test/delete2"))
            .withRequestBody(equalToJson("{\"from\":\"ctx\"}"))
            .willReturn(aResponse().withStatus(200).withBody("deleted")));

    DeleteHttpTask<String> task =
        new DeleteHttpTask.Builder<String>(httpClient).url(baseUrl + "/test/delete2").build();

    context.put("requestBody", "{\"from\":\"ctx\"}");

    task.execute(context);

    wm.verify(
        deleteRequestedFor(urlPathEqualTo("/test/delete2"))
            .withRequestBody(equalToJson("{\"from\":\"ctx\"}")));
  }

  @Test
  void deleteTask_noBody_usesDeleteMethod() {
    wm.stubFor(
        delete(urlPathEqualTo("/test/delete3"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    DeleteHttpTask<String> task =
        new DeleteHttpTask.Builder<String>(httpClient).url(baseUrl + "/test/delete3").build();

    task.execute(context);

    wm.verify(deleteRequestedFor(urlPathEqualTo("/test/delete3")));
  }

  // ---------- Strict mapper non-2xx handling ----------

  @Test
  void task_throwsTaskExecutionException_onNon2xx_whenStrictMapperUsed() {
    wm.stubFor(
        get(urlPathEqualTo("/test/error"))
            .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

    GetHttpTask.Builder<String> b =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/error")
            .responseMapper(ResponseMappers.strictTypedMapper(String.class));

    GetHttpTask<String> task = b.build();

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertNotNull(ex.getMessage());
  }

  @Test
  void task_throwsTaskExecutionException_onHttpTimeout() {
    // WireMock will delay the response beyond the client's timeout
    wm.stubFor(
        get(urlPathEqualTo("/test/slow"))
            .willReturn(aResponse().withStatus(200).withBody("slow").withFixedDelay(500)));

    // HttpClient default is fine; set a short timeout on the task builder
    GetHttpTask.Builder<String> b =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/slow")
            .timeout(Duration.ofMillis(100)); // very short timeout

    GetHttpTask<String> task = b.build();

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertNotNull(ex.getMessage());
  }

  @Test
  void queryParam_encoding_handlesSpecialCharacters() {
    wm.stubFor(
        get(urlPathEqualTo("/test/q2"))
            .withQueryParam("space", equalTo("a b"))
            .withQueryParam("plus", equalTo("a+b"))
            .withQueryParam("unicode", equalTo("✓"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/q2")
            .queryParam("space", "a b")
            .queryParam("plus", "a+b")
            .queryParam("unicode", "✓")
            .build();

    task.execute(context);

    wm.verify(
        getRequestedFor(urlPathEqualTo("/test/q2"))
            .withQueryParam("space", equalTo("a b"))
            .withQueryParam("plus", equalTo("a+b"))
            .withQueryParam("unicode", equalTo("✓")));
  }

  @Test
  void postTask_utf8Body_isSentAsUtf8() {
    wm.stubFor(
        post(urlPathEqualTo("/test/post-utf8"))
            .withRequestBody(equalToJson("{\"emoji\":\"😊\"}"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    PostHttpTask<String> task =
        new PostHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/post-utf8")
            .body("{\"emoji\":\"😊\"}")
            .build();

    task.execute(context);

    wm.verify(
        postRequestedFor(urlPathEqualTo("/test/post-utf8"))
            .withRequestBody(equalToJson("{\"emoji\":\"😊\"}")));
  }

  @Test
  void putTask_builderValidation_rejectsInvalidFormEntries() {
    PutHttpTask.Builder<String> b =
        new PutHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/invalid-form")
            .form(Map.of(" ", "value")); // invalid key (blank)

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, b::build);
    assertTrue(ex.getMessage().toLowerCase().contains("form key"));
  }

  @Test
  void defaultTypedResponseMapper_returnsNull_forEmptyBody() {
    // stub returns 200 with empty body
    wm.stubFor(
        get(urlPathEqualTo("/test/empty")).willReturn(aResponse().withStatus(200).withBody("")));

    // simple POJO for deserialization
    class Foo {
      public String x;
    }

    GetHttpTask<Foo> task =
        new GetHttpTask.Builder<Foo>(httpClient)
            .url(baseUrl + "/test/empty")
            .responseType(Foo.class)
            .build();

    task.execute(context);

    // mapped value should be null for empty body
    assertNull(context.getTyped("httpResponse", Foo.class));
  }

  @Test
  void strictMapper_truncatesLargeBody_inExceptionMessage() {
    // create a large body > 2048 chars to ensure truncation path is exercised
    String largeBody = "x".repeat(3000);

    wm.stubFor(
        get(urlPathEqualTo("/test/large-error"))
            .willReturn(aResponse().withStatus(500).withBody(largeBody)));

    GetHttpTask.Builder<String> b =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/large-error")
            .responseMapper(ResponseMappers.strictTypedMapper(String.class));

    GetHttpTask<String> task = b.build();

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    // message should include "HTTP 500" and be present; strict mapper truncates body before
    // throwing
    assertTrue(ex.getMessage().contains("HTTP 500"));
    // we don't assert exact truncation string here because the exception is wrapped by
    // AbstractHttpTask,
    // but ensure the message length is reasonable (not the full 3000 chars)
    assertTrue(ex.getMessage().length() < 2000);
  }

  @Test
  void getTask_throwsExceptionWhenUrlIsNull() {
    GetHttpTask<String> task = new GetHttpTask.Builder<String>(httpClient).build();

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(ex.getMessage().contains("No URL provided"));
  }

  @Test
  void getTask_throwsExceptionWhenUrlIsBlank() {
    GetHttpTask<String> task = new GetHttpTask.Builder<String>(httpClient).url("   ").build();

    TaskExecutionException ex =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));
    assertTrue(ex.getMessage().contains("No URL provided"));
  }

  @Test
  void getTask_resolvesUrlFromContext() {
    wm.stubFor(
        get(urlPathEqualTo("/test/dynamic"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient).urlFromContext("dynamicUrl").build();
    context.put("dynamicUrl", baseUrl + "/test/dynamic");

    task.execute(context);

    wm.verify(getRequestedFor(urlPathEqualTo("/test/dynamic")));
    assertEquals("ok", context.getTyped("httpResponse", String.class));
  }

  @Test
  void getTask_storesResponseInCustomContextKey() {
    wm.stubFor(
        get(urlPathEqualTo("/test/custom-key"))
            .willReturn(aResponse().withStatus(200).withBody("custom")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/custom-key")
            .responseContextKey("myResponse")
            .build();

    task.execute(context);

    assertEquals("custom", context.getTyped("myResponse", String.class));
    assertNull(context.get("httpResponse"));
  }

  @Test
  void getTask_doesNotStoreNullResponse() {
    wm.stubFor(
        get(urlPathEqualTo("/test/null-response"))
            .willReturn(aResponse().withStatus(200).withBody("")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/null-response")
            .responseMapper(_ -> null)
            .build();

    task.execute(context);

    assertFalse(context.containsKey("httpResponse"));
  }

  @Test
  void postTask_throwsExceptionForInvalidFormKeyValue() {
    Map<String, String> invalidForm = new LinkedHashMap<>();
    invalidForm.put("key", null);

    PostHttpTask.Builder<String> builder =
        new PostHttpTask.Builder<String>(httpClient).url(baseUrl + "/test").form(invalidForm);

    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  void getTask_appliesRequestCustomizer() {
    wm.stubFor(
        get(urlPathEqualTo("/test/customizer"))
            .withHeader("X-Custom", equalTo("customized"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    GetHttpTask<String> task =
        new GetHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/customizer")
            .requestCustomizer((builder, _) -> builder.header("X-Custom", "customized"))
            .build();

    task.execute(context);

    wm.verify(
        getRequestedFor(urlPathEqualTo("/test/customizer"))
            .withHeader("X-Custom", equalTo("customized")));
  }

  @Test
  void deleteTask_sendsBodyFromContext() {
    wm.stubFor(
        delete(urlPathEqualTo("/test/delete-ctx"))
            .withRequestBody(equalToJson("{\"id\":99}"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    DeleteHttpTask<String> task =
        new DeleteHttpTask.Builder<String>(httpClient).url(baseUrl + "/test/delete-ctx").build();

    context.put("requestBody", "{\"id\":99}");
    task.execute(context);

    wm.verify(
        deleteRequestedFor(urlPathEqualTo("/test/delete-ctx"))
            .withRequestBody(equalToJson("{\"id\":99}")));
  }

  @Test
  void putTask_contextBodyOverridesForm() {
    wm.stubFor(
        put(urlPathEqualTo("/test/put-ctx"))
            .withRequestBody(equalToJson("{\"context\":true}"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    PutHttpTask<String> task =
        new PutHttpTask.Builder<String>(httpClient)
            .url(baseUrl + "/test/put-ctx")
            .form(Map.of("key", "value"))
            .build();

    context.put("requestBody", "{\"context\":true}");
    task.execute(context);

    wm.verify(
        putRequestedFor(urlPathEqualTo("/test/put-ctx"))
            .withRequestBody(equalToJson("{\"context\":true}")));
  }
}
