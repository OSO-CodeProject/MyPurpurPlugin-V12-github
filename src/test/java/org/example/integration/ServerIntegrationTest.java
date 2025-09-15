package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/** Integration test that starts a Purpur server and interacts with the plugin via the console. */
public class ServerIntegrationTest {

  private static Process serverProcess;
  private static BufferedWriter serverInput;
  private static final StringBuilder serverOutput = new StringBuilder();

  private static final Path TEST_DIR = Paths.get("build", "test-server");
  private static final Path PLUGINS_DIR = TEST_DIR.resolve("plugins");
  private static final Path SERVER_JAR = TEST_DIR.resolve("purpur.jar");

  @BeforeAll
  static void startServer() throws Exception {
    Files.createDirectories(PLUGINS_DIR);
    downloadServerIfNeeded();
    copyPluginJar();
    Files.writeString(TEST_DIR.resolve("eula.txt"), "eula=true\n", StandardCharsets.UTF_8);

    ProcessBuilder pb =
        new ProcessBuilder("java", "-jar", SERVER_JAR.getFileName().toString(), "--nogui");
    pb.directory(TEST_DIR.toFile());
    pb.redirectErrorStream(true);
    serverProcess = pb.start();
    serverInput =
        new BufferedWriter(
            new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));

    Thread reader =
        new Thread(
            () -> {
              try (BufferedReader r =
                  new BufferedReader(
                      new InputStreamReader(
                          serverProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                  synchronized (serverOutput) {
                    serverOutput.append(line).append('\n');
                  }
                }
              } catch (IOException ignored) {
              }
            });
    reader.setDaemon(true);
    reader.start();

    waitForOutput("Done", 120_000);
  }

  @AfterAll
  static void stopServer() throws Exception {
    if (serverInput != null) {
      serverInput.write("stop\n");
      serverInput.flush();
    }
    if (serverProcess != null) {
      serverProcess.waitFor(1, TimeUnit.MINUTES);
      serverProcess.destroy();
    }
  }

  private static void downloadServerIfNeeded() throws IOException {
    if (Files.exists(SERVER_JAR)) {
      return;
    }
    try (InputStream in =
        new URL("https://api.purpurmc.org/v2/purpur/1.21.3/latest/download").openStream()) {
      Files.copy(in, SERVER_JAR);
    } catch (IOException ex) {
      Assumptions.assumeTrue(false, "Failed to download server jar: " + ex.getMessage());
    }
  }

  private static void copyPluginJar() throws IOException {
    Path pluginJar = Paths.get("build", "libs", "MyPurpurPlugin.jar");
    Files.copy(
        pluginJar, PLUGINS_DIR.resolve("MyPurpurPlugin.jar"), StandardCopyOption.REPLACE_EXISTING);
  }

  private static void waitForOutput(String token, long timeoutMillis) throws InterruptedException {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeoutMillis) {
      synchronized (serverOutput) {
        if (serverOutput.toString().contains(token)) {
          return;
        }
      }
      Thread.sleep(200);
    }
    throw new IllegalStateException("Timed out waiting for output: " + token);
  }

  @Test
  public void testTeamsFileAndConsoleCommand() throws Exception {
    Path teamsFile = PLUGINS_DIR.resolve("MyPurpurPlugin").resolve("teams.yml");
    assertTrue(Files.exists(teamsFile), "teams.yml should be created");

    serverInput.write("teamreload\n");
    serverInput.flush();
    waitForOutput("Конфигурация плагина успешно перезагружена", 15_000);
  }
}
