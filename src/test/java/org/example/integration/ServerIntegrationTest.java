package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;

/**
 * Integration test that interacts with a locally provisioned Purpur server via the console.
 *
 * <p>The test is tagged as {@code realServer} and will be skipped unless the Gradle property {@code
 * enableRealServerTests=true} is provided and the {@code PURPUR_SERVER_JAR} environment variable
 * (or {@code purpur.serverJar} system property) points to an existing Purpur server jar.
 */
@Tag("realServer")
public class ServerIntegrationTest {

  private static final Pattern LOG_PATTERN =
      Pattern.compile("^\\[(?<time>[^]]+)] \\[(?<thread>[^]]+)] (?<level>[A-Z]+): (?<message>.*)$");

  private static Process serverProcess;
  private static BufferedWriter serverInput;
  private static final Object logLock = new Object();
  private static final List<LogEvent> logEvents = new ArrayList<>();

  private static final Path TEST_DIR = Paths.get("build", "test-server");
  private static final Path PLUGINS_DIR = TEST_DIR.resolve("plugins");
  private static final Path SERVER_JAR = TEST_DIR.resolve("purpur.jar");

  private record LogEvent(String thread, String level, String message, String rawLine) {}

  @BeforeAll
  static void startServer() throws Exception {
    Assumptions.assumeTrue(
        Boolean.getBoolean("enableRealServerTests")
            || "true".equalsIgnoreCase(System.getProperty("enableRealServerTests"))
            || "true".equalsIgnoreCase(System.getenv("ENABLE_REAL_SERVER_TESTS")),
        "Real server tests are disabled. Set enableRealServerTests=true to run.");

    Path provisionedJar = resolveProvisionedServerJar();

    Files.createDirectories(PLUGINS_DIR);
    Files.copy(provisionedJar, SERVER_JAR, StandardCopyOption.REPLACE_EXISTING);
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
                  LogEvent event = parseLogLine(line);
                  synchronized (logLock) {
                    logEvents.add(event);
                    logLock.notifyAll();
                  }
                }
              } catch (IOException ignored) {
              }
            });
    reader.setDaemon(true);
    reader.start();

    waitForLogMessage(message -> message.startsWith("Done ("), Duration.ofSeconds(90));
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

  private static Path resolveProvisionedServerJar() {
    String configuredPath = System.getProperty("purpur.serverJar");
    if (configuredPath == null || configuredPath.isBlank()) {
      configuredPath = System.getenv("PURPUR_SERVER_JAR");
    }

    Assumptions.assumeTrue(
        configuredPath != null && !configuredPath.isBlank(),
        "PURPUR_SERVER_JAR environment variable or purpur.serverJar system property must point to a Purpur server jar");

    final String resolvedPath = configuredPath;
    Path jarPath = Paths.get(resolvedPath);
    Assumptions.assumeTrue(
        Files.isRegularFile(jarPath),
        () ->
            "Purpur server jar not found at "
                + resolvedPath
                + ". Please download it manually before running the integration test.");
    return jarPath;
  }

  private static void copyPluginJar() throws IOException {
    Path pluginJar = Paths.get("build", "libs", "MyPurpurPlugin.jar");
    Files.copy(
        pluginJar, PLUGINS_DIR.resolve("MyPurpurPlugin.jar"), StandardCopyOption.REPLACE_EXISTING);
  }

  private static LogEvent parseLogLine(String line) {
    Matcher matcher = LOG_PATTERN.matcher(line);
    if (matcher.matches()) {
      return new LogEvent(
          matcher.group("thread"), matcher.group("level"), matcher.group("message"), line);
    }
    return new LogEvent("unknown", "INFO", line, line);
  }

  private static void waitForLogMessage(
      java.util.function.Predicate<String> predicate, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    int cursor = 0;
    synchronized (logLock) {
      while (true) {
        for (; cursor < logEvents.size(); cursor++) {
          String message = logEvents.get(cursor).message();
          if (predicate.test(message)) {
            return;
          }
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          String capturedLogs =
              logEvents.stream().map(LogEvent::rawLine).collect(Collectors.joining("\n"));
          throw new IllegalStateException(
              "Timed out waiting for log message. Captured logs:\n" + capturedLogs);
        }
        TimeUnit.NANOSECONDS.timedWait(logLock, remaining);
      }
    }
  }

  @Test
  public void testTeamsFileAndConsoleCommand() throws Exception {
    Path teamsFile = PLUGINS_DIR.resolve("MyPurpurPlugin").resolve("teams.yml");
    assertTrue(Files.exists(teamsFile), "teams.yml should be created");

    serverInput.write("teamreload\n");
    serverInput.flush();
    waitForLogMessage(
        message -> message.contains("Конфигурация плагина успешно перезагружена"),
        Duration.ofSeconds(20));
  }
}
