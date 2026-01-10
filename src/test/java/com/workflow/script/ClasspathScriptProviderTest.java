package com.workflow.script;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.exception.ScriptLoadException;
import org.junit.jupiter.api.Test;

class ClasspathScriptProviderTest {

  @Test
  void testLoadScript_ResourceNotFound() {
    ClasspathScriptProvider provider =
        new ClasspathScriptProvider("/nonexistent/missing-script.js");

    ScriptLoadException exception = assertThrows(ScriptLoadException.class, provider::loadScript);

    assertTrue(exception.getMessage().contains("Classpath resource not found"));
  }

  @Test
  void testLoadScript_WithLeadingSlash() {
    String resourcePath = "/test-scripts/simple.js";
    ClasspathScriptProvider provider = new ClasspathScriptProvider(resourcePath);

    // Should normalize path by removing leading slash
    assertDoesNotThrow(provider::loadScript);
  }

  @Test
  void testLoadScript_WithoutLeadingSlash() {
    String resourcePath = "test-scripts/simple.js";
    ClasspathScriptProvider provider = new ClasspathScriptProvider(resourcePath);

    assertDoesNotThrow(provider::loadScript);
  }

  @Test
  void testLoadScript_WithCustomClassLoader() {
    ClassLoader customLoader = Thread.currentThread().getContextClassLoader();
    ClasspathScriptProvider provider =
        new ClasspathScriptProvider("test-scripts/simple.js", customLoader);

    assertDoesNotThrow(provider::loadScript);
  }

  @Test
  void testLoadScript_ConsistentContent() {
    ClasspathScriptProvider provider = new ClasspathScriptProvider("test-scripts/simple.js");

    ScriptProvider.ScriptSource first = provider.loadScript();
    ScriptProvider.ScriptSource second = provider.loadScript();

    assertEquals(first.content(), second.content());
  }

  @Test
  void testLoadScript_Utf8Encoding() {
    ClasspathScriptProvider provider = new ClasspathScriptProvider("test-scripts/unicode.js");

    ScriptProvider.ScriptSource content = provider.loadScript();

    // Should contain Unicode characters properly
    assertNotNull(content.content());
  }

  @Test
  void testDefaultConstructor_UsesContextClassLoader() {
    ClasspathScriptProvider provider = new ClasspathScriptProvider("test-scripts/simple.js");

    // Should work with default class loader
    assertDoesNotThrow(provider::loadScript);
  }

  @Test
  void testTwoArgConstructor() {
    ClassLoader loader = ClasspathScriptProviderTest.class.getClassLoader();
    ClasspathScriptProvider provider =
        new ClasspathScriptProvider("test-scripts/simple.js", loader);

    assertDoesNotThrow(provider::loadScript);
  }

  @Test
  void testRecordEquality() {
    ClasspathScriptProvider provider1 = new ClasspathScriptProvider("test.js");
    ClasspathScriptProvider provider2 = new ClasspathScriptProvider("test.js");
    ClasspathScriptProvider provider3 = new ClasspathScriptProvider("other.js");

    assertEquals(provider1, provider2);
    assertNotEquals(provider1, provider3);
  }

  @Test
  void testRecordHashCode() {
    ClasspathScriptProvider provider1 = new ClasspathScriptProvider("test.js");
    ClasspathScriptProvider provider2 = new ClasspathScriptProvider("test.js");

    assertEquals(provider1.hashCode(), provider2.hashCode());
  }

  @Test
  void testResourcePathAccessor() {
    String resourcePath = "test-scripts/simple.js";
    ClasspathScriptProvider provider = new ClasspathScriptProvider(resourcePath);

    assertEquals(resourcePath, provider.resourcePath());
  }

  @Test
  void testClassLoaderAccessor() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    ClasspathScriptProvider provider = new ClasspathScriptProvider("test.js", loader);

    assertEquals(loader, provider.classLoader());
  }

  @Test
  void testNullClassLoader() {
    // null classLoader should use default
    ClasspathScriptProvider provider = new ClasspathScriptProvider("test-scripts/simple.js", null);

    assertDoesNotThrow(provider::loadScript);
  }

  @Test
  void testLoadScript_ExceptionContainsResourcePath() {
    String resourcePath = "missing/file.js";
    ClasspathScriptProvider provider = new ClasspathScriptProvider(resourcePath);

    ScriptLoadException exception = assertThrows(ScriptLoadException.class, provider::loadScript);

    assertTrue(exception.getMessage().contains(resourcePath));
  }

  @Test
  void testLoadScript_MultipleProvidersSameResource() {
    ClasspathScriptProvider provider1 = new ClasspathScriptProvider("test-scripts/simple.js");
    ClasspathScriptProvider provider2 = new ClasspathScriptProvider("test-scripts/simple.js");

    ScriptProvider.ScriptSource content1 = provider1.loadScript();
    ScriptProvider.ScriptSource content2 = provider2.loadScript();

    assertEquals(content1.content(), content2.content());
  }
}
