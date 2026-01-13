package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link HttpTaskBodyHelper}. Tests form encoding, URL encoding, body
 * precedence, and form validation.
 */
@DisplayName("HttpTaskBodyHelper Tests")
class HttpTaskBodyHelperTest {

  @Nested
  @DisplayName("Form Encoding Tests")
  class FormEncodingTests {

    @Test
    @DisplayName("encodeForm should encode simple key-value pairs")
    void encodeSimpleForm() {
      Map<String, String> form = Map.of("username", "john", "password", "secret");
      String encoded = HttpTaskBodyHelper.encodeForm(form);
      assertTrue(encoded.contains("username=john"));
      assertTrue(encoded.contains("password=secret"));
      assertTrue(encoded.contains("&"));
    }

    @Test
    @DisplayName("encodeForm should handle special characters")
    void encodeSpecialCharacters() {
      Map<String, String> form = Map.of("email", "user@example.com", "message", "hello world");
      String encoded = HttpTaskBodyHelper.encodeForm(form);
      // Special chars should be URL encoded
      assertTrue(encoded.contains("%40")); // @ symbol
      assertTrue(encoded.contains("%20")); // space (not +)
    }

    @Test
    @DisplayName("encodeForm should preserve insertion order")
    void encodePreservesOrder() {
      Map<String, String> form = new LinkedHashMap<>();
      form.put("a", "1");
      form.put("b", "2");
      form.put("c", "3");
      String encoded = HttpTaskBodyHelper.encodeForm(form);
      // Order should be preserved
      int aPos = encoded.indexOf("a=1");
      int bPos = encoded.indexOf("b=2");
      int cPos = encoded.indexOf("c=3");
      assertTrue(aPos < bPos && bPos < cPos);
    }

    @Test
    @DisplayName("encodeForm should handle empty form data")
    void encodeEmptyForm() {
      String encoded = HttpTaskBodyHelper.encodeForm(new LinkedHashMap<>());
      assertEquals("", encoded);
    }

    @Test
    @DisplayName("encodeForm should handle null form data")
    void encodeNullForm() {
      String encoded = HttpTaskBodyHelper.encodeForm(null);
      assertEquals("", encoded);
    }

    @Test
    @DisplayName("encodeForm should handle form with ampersand in value")
    void encodeFormWithAmpersand() {
      Map<String, String> form = Map.of("text", "a&b");
      String encoded = HttpTaskBodyHelper.encodeForm(form);
      // & should be encoded as %26
      assertTrue(encoded.contains("%26"));
    }

    @Test
    @DisplayName("encodeForm should handle form with equals in value")
    void encodeFormWithEquals() {
      Map<String, String> form = Map.of("expr", "a=b");
      String encoded = HttpTaskBodyHelper.encodeForm(form);
      // Only the first = should be unencoded (key=value separator)
      int equalsCount = (int) encoded.chars().filter(ch -> ch == '=').count();
      assertEquals(1, equalsCount); // One unencoded separator
      assertTrue(encoded.contains("%3D")); // Encoded = in value
    }
  }

  @Nested
  @DisplayName("URL Encoding Tests")
  class UrlEncodingTests {

    @Test
    @DisplayName("encode should handle null input")
    void encodeNull() {
      String encoded = HttpTaskBodyHelper.encode(null);
      assertEquals("", encoded);
    }

    @Test
    @DisplayName("encode should encode spaces as %20 not +")
    void encodeSpacesAsPercent20() {
      String encoded = HttpTaskBodyHelper.encode("hello world");
      assertEquals("hello%20world", encoded);
      assertFalse(encoded.contains("+"));
    }

    @Test
    @DisplayName("encode should handle special characters")
    void encodeSpecialChars() {
      String encoded = HttpTaskBodyHelper.encode("user@example.com");
      assertTrue(encoded.contains("%40")); // @
    }

    @Test
    @DisplayName("encode should be idempotent for already encoded strings")
    void encodeIdempotent() {
      String original = "hello%20world";
      String firstEncode = HttpTaskBodyHelper.encode(original);
      // Second encoding should double-encode
      String secondEncode = HttpTaskBodyHelper.encode(firstEncode);
      assertNotEquals(firstEncode, secondEncode);
    }

    @Test
    @DisplayName("encode should handle unicode characters")
    void encodeUnicode() {
      String encoded = HttpTaskBodyHelper.encode("café");
      assertNotNull(encoded);
      assertTrue(encoded.length() > "café".length()); // Should be longer due to encoding
    }
  }

  @Nested
  @DisplayName("Form Validation Tests")
  class FormValidationTests {

    @Test
    @DisplayName("validateFormData should accept valid form data")
    void validateValidForm() {
      Map<String, String> form = Map.of("key1", "value1", "key2", "value2");
      assertDoesNotThrow(() -> HttpTaskBodyHelper.validateFormData(form));
    }

    @Test
    @DisplayName("validateFormData should throw for null key")
    void validateNullKey() {
      Map<String, String> form = new HashMap<>();
      form.put(null, "value");
      assertThrows(IllegalArgumentException.class, () -> HttpTaskBodyHelper.validateFormData(form));
    }

    @Test
    @DisplayName("validateFormData should throw for blank key")
    void validateBlankKey() {
      Map<String, String> form = Map.of("", "value");
      assertThrows(IllegalArgumentException.class, () -> HttpTaskBodyHelper.validateFormData(form));
    }

    @Test
    @DisplayName("validateFormData should throw for null value")
    void validateNullValue() {
      Map<String, String> form = new HashMap<>();
      form.put("key", null);
      assertThrows(IllegalArgumentException.class, () -> HttpTaskBodyHelper.validateFormData(form));
    }

    @Test
    @DisplayName("validateFormData should accept null form data")
    void validateNullForm() {
      assertDoesNotThrow(() -> HttpTaskBodyHelper.validateFormData(null));
    }

    @Test
    @DisplayName("validateFormData should accept empty form data")
    void validateEmptyForm() {
      assertDoesNotThrow(() -> HttpTaskBodyHelper.validateFormData(new LinkedHashMap<>()));
    }

    @Test
    @DisplayName("validateFormData should throw with helpful message")
    void validateErrorMessage() {
      Map<String, String> form = new HashMap<>();
      form.put("username", null);
      Exception ex =
          assertThrows(
              IllegalArgumentException.class, () -> HttpTaskBodyHelper.validateFormData(form));
      assertTrue(ex.getMessage().contains("username"));
      assertTrue(ex.getMessage().toLowerCase().contains("null"));
    }
  }

  @Nested
  @DisplayName("Body Precedence Tests")
  class BodyPrecedenceTests {

    @Test
    @DisplayName("setBodyWithContentType should prefer explicit body over context body")
    void precedenceExplicitOverContext() {
      StringBuilder contentType = new StringBuilder();
      StringBuilder method = new StringBuilder();

      HttpTaskBodyHelper.setBodyWithContentType(
          "explicit body", null, null, _ -> method.append("SET"), contentType::append);

      assertEquals("application/json; charset=UTF-8", contentType.toString());
      assertEquals("SET", method.toString());
    }

    @Test
    @DisplayName("setBodyWithContentType should use form data when no explicit body")
    void precedenceFormData() {
      StringBuilder contentType = new StringBuilder();
      StringBuilder method = new StringBuilder();
      Map<String, String> form = Map.of("key", "value");

      HttpTaskBodyHelper.setBodyWithContentType(
          null, form, null, _ -> method.append("SET"), contentType::append);

      assertEquals("application/x-www-form-urlencoded; charset=UTF-8", contentType.toString());
      assertEquals("SET", method.toString());
    }

    @Test
    @DisplayName("setBodyWithContentType should use empty body as fallback")
    void precedenceFallback() {
      StringBuilder method = new StringBuilder();

      HttpTaskBodyHelper.setBodyWithContentType(
          null, null, null, _ -> method.append("SET"), _ -> {});

      assertEquals("SET", method.toString());
    }
  }
}
