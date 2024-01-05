/*
 * Copyright 2023 Lumigo LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.lumigo.javaagent.junitextensions;

import com.fasterxml.jackson.databind.JsonNode;
import io.lumigo.javaagent.spandump.SpanDumpEntry;
import io.lumigo.javaagent.spandump.SpanDumpMixIn;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

public class TestAppExtension
    implements ParameterResolver, BeforeAllCallback, AfterAllCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestAppExtension.class);

  private static final Network NETWORK = Network.newNetwork();

  private static final String SPANDUMP_FILE = "/opt/lumigo.log";

  protected static final String AGENT_PATH =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  private GenericContainer<?> backend;
  private GenericContainer<?> testApp;

  protected static OkHttpClient client = clientBuilder().build();

  /*
   * If this is true, we will be running Docker in emulation, and it is
   * *much* slower.
   */
  protected static final boolean IS_ARCH_AMD_64 = "amd64".equals(System.getProperty("os.arch"));

  protected static final Duration BACKEND_CONTAINER_START_TIMEOUT =
      Duration.ofMinutes(IS_ARCH_AMD_64 ? 3 : 15);
  protected static final Duration APP_CONTAINER_START_TIMEOUT =
      Duration.ofMinutes(IS_ARCH_AMD_64 ? 2 : 15);

  static OkHttpClient.Builder clientBuilder() {
    TimeUnit unit = TimeUnit.MINUTES;
    return new OkHttpClient.Builder()
        .connectTimeout(1, unit)
        .writeTimeout(1, unit)
        .readTimeout(1, unit);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @java.lang.annotation.Target(ElementType.PARAMETER)
  public @interface Configuration {
    String jdkVersion() default "8";

    EnvVar[] env();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @java.lang.annotation.Target(ElementType.PARAMETER)
  public @interface EnvVar {
    String key();

    String value();
  }

  public interface TestApplication {
    String getLogs();

    String getAgentVersion() throws IOException;

    List<SpanDumpEntry> getSpanDump() throws IOException;

    List<JsonNode> getTraces() throws IOException, InterruptedException;

    int getMappedPort(int originalPort);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    this.backend =
        new GenericContainer<>(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20221127.3559314891")
            .withStartupTimeout(BACKEND_CONTAINER_START_TIMEOUT)
            .withExposedPorts(8080)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx128m")
            .waitingFor(Wait.forHttp("/health").forPort(8080))
            .withNetwork(NETWORK)
            .withNetworkAliases("backend")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    this.backend.start();
  }

  @Override
  public void afterEach(ExtensionContext context) throws IOException {
    client
        .newCall(
            new Request.Builder()
                .url(String.format("http://localhost:%d/clear", backend.getMappedPort(8080)))
                .build())
        .execute()
        .close();

    try {
      if (this.testApp != null) {
        this.testApp.stop();
      }
    } finally {
      this.testApp = null;
    }
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    try {
      if (this.testApp != null) {
        this.testApp.stop();
      }
      if (this.backend != null) {
        this.backend.stop();
      }
    } finally {
      this.testApp = null;
      this.backend = null;
      NETWORK.close();
    }
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return TestApplication.class.equals(parameterContext.getParameter().getType());
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {

    final String jdkVersion =
        parameterContext
            .findAnnotation(Configuration.class)
            .map(Configuration::jdkVersion)
            .orElse("8");

    final String testAppImage =
        "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
            + jdkVersion
            + "-20211213.1570880324";

    this.testApp =
        new GenericContainer<>(testAppImage)
            .withStartupTimeout(APP_CONTAINER_START_TIMEOUT)
            .withExposedPorts(8080)
            .withNetwork(NETWORK)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withCopyFileToContainer(
                MountableFile.forHostPath(AGENT_PATH), "/opentelemetry-javaagent.jar")
            .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent.jar")
            .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://backend:8080")
            .withEnv("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
            .withEnv("OTEL_BSP_MAX_EXPORT_BATCH", "1")
            .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10ms")
            .withEnv("LUMIGO_TRACER_TOKEN", "test-123")
            .withEnv("LUMIGO_DEBUG", "true")
            .withEnv("LUMIGO_DEBUG_SPANDUMP", SPANDUMP_FILE);

    parameterContext
        .findAnnotation(Configuration.class)
        .ifPresent(
            (configuration) -> {
              if (configuration.env() != null) {
                for (final EnvVar envVar : configuration.env()) {
                  TestAppExtension.this.testApp.addEnv(envVar.key(), envVar.value());
                }
              }
            });

    this.testApp.start();

    return new TestApplication() {
      @Override
      public String getLogs() {
        return TestAppExtension.this.testApp.getLogs();
      }

      @Override
      public String getAgentVersion() throws IOException {
        try (final JarFile jarFile = new JarFile(AGENT_PATH)) {
          return (String)
              jarFile.getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
        }
      }

      @Override
      public List<SpanDumpEntry> getSpanDump() throws IOException {
        // Pause briefly to ensure that the agent has had time to write the span dump
        try {
          Thread.sleep(1500);
        } catch (InterruptedException e) {
          // Ignore
        }

        Path temp = null;
        try {
          temp = Files.createTempFile("spandump", ".log");
          testApp.copyFileFromContainer(SPANDUMP_FILE, temp.toString());

          return Files.readAllLines(temp).stream()
              .filter(line -> !line.isEmpty())
              .map(
                  line -> {
                    try {
                      return SpanDumpMixIn.OBJECT_MAPPER.readValue(line, SpanDumpEntry.class);
                    } catch (Exception e) {
                      throw new IllegalArgumentException(
                          "Could not parse line as SpanDumpEntry: " + line, e);
                    }
                  })
              .collect(Collectors.toList());
        } finally {
          if (Objects.nonNull(temp)) {
            Files.deleteIfExists(temp);
          }
        }
      }

      @Override
      public List<JsonNode> getTraces() throws IOException, InterruptedException {
        String content = waitForContent();

        return StreamSupport.stream(
                SpanDumpMixIn.OBJECT_MAPPER.readTree(content).spliterator(), false)
            .collect(Collectors.toList());
      }

      private String waitForContent() throws IOException, InterruptedException {
        long previousSize = 0;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        String content = "[]";
        while (System.currentTimeMillis() < deadline) {
          Request request =
              new Request.Builder()
                  .url(String.format("http://localhost:%d/get-traces", backend.getMappedPort(8080)))
                  .build();

          try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body != null) {
              content = body.string();
            }
          }

          if (content.length() > 2 && content.length() == previousSize) {
            break;
          }
          previousSize = content.length();
          TimeUnit.MILLISECONDS.sleep(500);
        }

        return content;
      }

      @Override
      public int getMappedPort(int originalPort) {
        return testApp.getMappedPort(originalPort);
      }
    };
  }
}
