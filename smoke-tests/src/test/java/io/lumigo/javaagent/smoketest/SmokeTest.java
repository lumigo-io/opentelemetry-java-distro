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
package io.lumigo.javaagent.smoketest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

abstract class SmokeTest {
  private static final Logger logger = LoggerFactory.getLogger(SmokeTest.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected static OkHttpClient client = OkHttpUtils.client();

  private static final Network network = Network.newNetwork();
  protected static final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  protected abstract String getTargetImage(int jdk);

  private static GenericContainer backend;

  @BeforeAll
  static void setupSpec() {
    backend =
        new GenericContainer<>(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20221127.3559314891")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withExposedPorts(8080)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx128m")
            .waitingFor(Wait.forHttp("/health").forPort(8080))
            .withNetwork(network)
            .withNetworkAliases("backend")
            .withLogConsumer(new Slf4jLogConsumer(logger));
    backend.start();
  }

  protected GenericContainer target;

  private static final String SPANDUMP_FILE = "/opt/lumigo.log";

  void startTarget(int jdk, Map<String, String> extraEnv) {
    target =
        new GenericContainer<>(getTargetImage(jdk))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withExposedPorts(8080)
            .withNetwork(network)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent.jar")
            .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent.jar")
            .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://backend:8080")
            .withEnv("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
            .withEnv("OTEL_BSP_MAX_EXPORT_BATCH", "1")
            .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10ms")
            .withEnv("LUMIGO_TRACER_TOKEN", "test-123")
            .withEnv("LUMIGO_DEBUG", "true")
            .withEnv("LUMIGO_DEBUG_SPANDUMP", SPANDUMP_FILE);

    if (extraEnv != null) {
      target = target.withEnv(extraEnv);
    }
    target.start();
  }

  protected String fetchSpanDumpFromTarget() {
    try {
      Path temp = Files.createTempFile("spandump", ".log");
      target.copyFileFromContainer(SPANDUMP_FILE, temp.toString());

      return Files.readString(temp);
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  @AfterEach
  void cleanup() throws IOException {
    client
        .newCall(
            new Request.Builder()
                .url(String.format("http://localhost:%d/clear", backend.getMappedPort(8080)))
                .build())
        .execute()
        .close();
  }

  void stopTarget() {
    target.stop();
  }

  @AfterAll
  static void cleanupSpec() {
    backend.stop();
  }

  protected static int countResourcesByValue(
      Collection<ExportTraceServiceRequest> traces, String resourceName, String value) {
    return (int)
        traces.stream()
            .flatMap(it -> it.getResourceSpansList().stream())
            .flatMap(it -> it.getResource().getAttributesList().stream())
            .filter(
                kv ->
                    kv.getKey().equals(resourceName)
                        && kv.getValue().getStringValue().equals(value))
            .count();
  }

  protected static int countSpansByName(
      Collection<ExportTraceServiceRequest> traces, String spanName) {
    return (int) getSpanStream(traces).filter(it -> it.getName().equals(spanName)).count();
  }

  protected static int countSpansByAttributeValue(
      Collection<ExportTraceServiceRequest> traces, String attributeName, String attributeValue) {
    return (int)
        getSpanStream(traces)
            .flatMap(it -> it.getAttributesList().stream())
            .filter(
                kv ->
                    kv.getKey().equals(attributeName)
                        && kv.getValue().getStringValue().equals(attributeValue))
            .count();
  }

  protected static Stream<Span> getSpanStream(Collection<ExportTraceServiceRequest> traces) {
    return traces.stream()
        .flatMap(it -> it.getResourceSpansList().stream())
        .flatMap(it -> it.getScopeSpansList().stream())
        .flatMap(it -> it.getSpansList().stream());
  }

  protected Collection<ExportTraceServiceRequest> tracesFromSpanDump() {
    String spanDump = fetchSpanDumpFromTarget();
    return Stream.of(spanDump.split(System.lineSeparator()))
        .map(
            it -> {
              ExportTraceServiceRequest.Builder builder = ExportTraceServiceRequest.newBuilder();
              try {
                JsonFormat.parser().merge(it, builder);
              } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
                Assertions.fail("Failed to parse span dump: " + e.getMessage());
              }
              return builder.build();
            })
        .collect(Collectors.toList());
  }

  protected Collection<ExportTraceServiceRequest> waitForTraces()
      throws IOException, InterruptedException {
    String content = waitForContent();

    return StreamSupport.stream(OBJECT_MAPPER.readTree(content).spliterator(), false)
        .map(
            it -> {
              ExportTraceServiceRequest.Builder builder = ExportTraceServiceRequest.newBuilder();
              // TODO(anuraaga): Register parser into object mapper to avoid de -> re ->
              // deserialize.
              try {
                JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder);
              } catch (InvalidProtocolBufferException | JsonProcessingException e) {
                e.printStackTrace();
              }
              return builder.build();
            })
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

      try (ResponseBody body = client.newCall(request).execute().body()) {
        content = body.string();
      }

      if (content.length() > 2 && content.length() == previousSize) {
        break;
      }
      previousSize = content.length();
      System.out.printf("Current content size %d%n", previousSize);
      TimeUnit.MILLISECONDS.sleep(500);
    }

    return content;
  }
}
