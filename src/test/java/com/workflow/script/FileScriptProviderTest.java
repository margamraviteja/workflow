package com.workflow.script;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.exception.ScriptLoadException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScriptProviderTest {

  @TempDir Path tempDir;

  @Test
  void testLoadScript_SimpleFile() throws IOException {
    String script = "context.put('result', 'success');";
    Path scriptFile = tempDir.resolve("test.js");
    Files.writeString(scriptFile, script);

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
  }

  @Test
  void testLoadScript_MultiLineFile() throws IOException {
    String script =
        """
        var user = context.get('user');
        var isValid = user && user.email;
        context.put('valid', isValid);
        """;
    Path scriptFile = tempDir.resolve("multiline.js");
    Files.writeString(scriptFile, script);

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
  }

  @Test
  void testLoadScript_EmptyFile() throws IOException {
    Path scriptFile = tempDir.resolve("empty.js");
    Files.writeString(scriptFile, "");

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals("", loaded.content());
  }

  @Test
  void testLoadScript_FileNotFound() {
    Path nonExistentFile = tempDir.resolve("nonexistent.js");

    FileScriptProvider provider = new FileScriptProvider(nonExistentFile);

    assertThrows(ScriptLoadException.class, provider::loadScript);
  }

  @Test
  void testLoadScript_HotReload() throws IOException {
    Path scriptFile = tempDir.resolve("hotreload.js");
    Files.writeString(scriptFile, "var version = 1;");

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource firstLoad = provider.loadScript();
    assertEquals("var version = 1;", firstLoad.content());

    // Update the file
    Files.writeString(scriptFile, "var version = 2;");

    ScriptProvider.ScriptSource secondLoad = provider.loadScript();
    assertEquals("var version = 2;", secondLoad.content());
  }

  @Test
  void testLoadScript_LargeFile() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      sb.append("// Line ").append(i).append("\n");
    }
    Path scriptFile = tempDir.resolve("large.js");
    Files.writeString(scriptFile, sb.toString());

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertTrue(loaded.content().contains("// Line 0"));
    assertTrue(loaded.content().contains("// Line 9999"));
  }

  @Test
  void testLoadScript_UnicodeContent() throws IOException {
    String script = "var message = '你好世界 🌍';";
    Path scriptFile = tempDir.resolve("unicode.js");
    Files.writeString(scriptFile, script);

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals(script, loaded.content());
  }

  @Test
  void testLoadScript_SpecialCharacters() throws IOException {
    String script = "var data = 'Line1\\nLine2\\tTabbed\\r\\nCRLF';";
    Path scriptFile = tempDir.resolve("special.js");
    Files.writeString(scriptFile, script);

    FileScriptProvider provider = new FileScriptProvider(scriptFile);
    assertEquals(script, provider.loadScript().content());
  }

  @Test
  void testLoadScript_RelativePath() throws IOException {
    Path scriptFile = tempDir.resolve("relative.js");
    Files.writeString(scriptFile, "// relative");

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource loaded = provider.loadScript();

    assertEquals("// relative", loaded.content());
  }

  @Test
  void testLoadScript_ConsecutiveCalls() throws IOException {
    Path scriptFile = tempDir.resolve("consecutive.js");
    Files.writeString(scriptFile, "var x = 1;");

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    ScriptProvider.ScriptSource first = provider.loadScript();
    ScriptProvider.ScriptSource second = provider.loadScript();
    ScriptProvider.ScriptSource third = provider.loadScript();

    assertEquals(first.content(), second.content());
    assertEquals(second.content(), third.content());
  }

  @Test
  void testRecordEquality() throws IOException {
    Path scriptFile = tempDir.resolve("test.js");
    Files.writeString(scriptFile, "test");

    FileScriptProvider provider1 = new FileScriptProvider(scriptFile);
    FileScriptProvider provider2 = new FileScriptProvider(scriptFile);
    FileScriptProvider provider3 = new FileScriptProvider(tempDir.resolve("other.js"));

    assertEquals(provider1, provider2);
    assertNotEquals(provider1, provider3);
  }

  @Test
  void testRecordHashCode() throws IOException {
    Path scriptFile = tempDir.resolve("test.js");
    Files.writeString(scriptFile, "test");

    FileScriptProvider provider1 = new FileScriptProvider(scriptFile);
    FileScriptProvider provider2 = new FileScriptProvider(scriptFile);

    assertEquals(provider1.hashCode(), provider2.hashCode());
  }

  @Test
  void testFilePathAccessor() throws IOException {
    Path scriptFile = tempDir.resolve("test.js");
    Files.writeString(scriptFile, "test");

    FileScriptProvider provider = new FileScriptProvider(scriptFile);

    assertEquals(scriptFile, provider.filePath());
  }

  @Test
  void testLoadScript_ScriptLoadExceptionMessage() {
    Path nonExistentFile = tempDir.resolve("missing.js");
    FileScriptProvider provider = new FileScriptProvider(nonExistentFile);

    ScriptLoadException exception = assertThrows(ScriptLoadException.class, provider::loadScript);

    assertNotNull(exception.getMessage());
    assertTrue(exception.getMessage().contains("Error while loading script"));
  }
}
