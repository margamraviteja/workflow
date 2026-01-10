package com.workflow.script;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InlineScriptProviderTest {

  @Test
  void testLoadScript_SimpleScript() {
    String script = "context.put('result', 'test-value');";
    InlineScriptProvider provider = new InlineScriptProvider(script);
    assertEquals(script, provider.loadScript().content());
  }

  @Test
  void testLoadScript_MultiLineScript() {
    String script =
        """
        var user = context.get('user');
        var isAdmin = user.roles.includes('ADMIN');
        context.put('hasAccess', isAdmin);
        """;
    InlineScriptProvider provider = new InlineScriptProvider(script);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
  }

  @Test
  void testLoadScript_EmptyScript() {
    InlineScriptProvider provider = new InlineScriptProvider("");

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals("", loaded.content());
  }

  @Test
  void testLoadScript_ScriptWithSpecialCharacters() {
    String script = "var message = 'Hello\\nWorld\\t\"Test\"';";
    InlineScriptProvider provider = new InlineScriptProvider(script);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
  }

  @Test
  void testLoadScript_ConsistentResults() {
    String script = "context.put('counter', 42);";
    InlineScriptProvider provider = new InlineScriptProvider(script);

    ScriptProvider.ScriptSource first = provider.loadScript();
    ScriptProvider.ScriptSource second = provider.loadScript();
    ScriptProvider.ScriptSource third = provider.loadScript();

    assertEquals(first.content(), second.content());
    assertEquals(second.content(), third.content());
  }

  @Test
  void testLoadScript_LargeScript() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.append("context.put('key").append(i).append("', ").append(i).append(");");
    }
    String script = sb.toString();
    InlineScriptProvider provider = new InlineScriptProvider(script);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
    assertTrue(loaded.content().length() > 10000);
  }

  @Test
  void testLoadScript_ScriptWithUnicode() {
    String script = "var greeting = '你好世界 🌍 مرحبا';";
    InlineScriptProvider provider = new InlineScriptProvider(script);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
  }

  @Test
  void testRecordEquality() {
    InlineScriptProvider provider1 = new InlineScriptProvider("test");
    InlineScriptProvider provider2 = new InlineScriptProvider("test");
    InlineScriptProvider provider3 = new InlineScriptProvider("different");

    assertEquals(provider1, provider2);
    assertNotEquals(provider1, provider3);
  }

  @Test
  void testRecordHashCode() {
    InlineScriptProvider provider1 = new InlineScriptProvider("test");
    InlineScriptProvider provider2 = new InlineScriptProvider("test");

    assertEquals(provider1.hashCode(), provider2.hashCode());
  }

  @Test
  void testRecordToString() {
    InlineScriptProvider provider = new InlineScriptProvider("test-script");

    String toString = provider.toString();

    assertTrue(toString.contains("InlineScriptProvider"));
    assertTrue(toString.contains("test-script"));
  }

  @Test
  void testScriptContentAccessor() {
    String script = "var x = 10;";
    InlineScriptProvider provider = new InlineScriptProvider(script);

    assertEquals(script, provider.scriptContent());
  }
}
