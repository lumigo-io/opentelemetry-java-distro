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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SpringBootSmokeTest extends SmokeTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  protected String getTargetImage(int jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
  }

  @Test
  public void testEnvResource() throws IOException, InterruptedException {
    startTarget(
        8,
        Map.of(
            "LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT", "[\"DONT_TELL\"]",
            "DONT_TELL", "SECRET",
            "HELLO", "WORLD",
            "LUMIGO_SECRET_MASKING_REGEX", "[\"TOP_SECRET\"]",
            "TOP_SECRET", "shhhh",
            "MY_PASS", "1234"));

    String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    Request request = new Request.Builder().url(url).get().build();
    Response response = client.newCall(request).execute();

    Collection<ExportTraceServiceRequest> traces = waitForTraces();

    Assertions.assertNotEquals(0, traces.size());
    Assertions.assertNotEquals(0, countResourcesByName(traces, "process.environ"));

    traces.stream()
        .flatMap(it -> it.getResourceSpansList().stream())
        .flatMap(it -> it.getResource().getAttributesList().stream())
        .filter(it -> it.getKey().equals("process.environ"))
        .map(it -> parseEnv(it.getValue().getStringValue()))
        .forEach(
            envs -> {
              Assertions.assertEquals("WORLD", envs.get("HELLO"));
              Assertions.assertEquals("****", envs.get("DONT_TELL"));
              Assertions.assertEquals("****", envs.get("TOP_SECRET"));
            });
  }

  @Test
  public void testEnvResourceWithDefault() throws IOException, InterruptedException {
    startTarget(8, Map.of("MY_PASS", "1234"));

    String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    Request request = new Request.Builder().url(url).get().build();
    Response response = client.newCall(request).execute();

    Collection<ExportTraceServiceRequest> traces = waitForTraces();

    Assertions.assertNotEquals(0, traces.size());
    Assertions.assertNotEquals(0, countResourcesByName(traces, "process.environ"));

    traces.stream()
        .flatMap(it -> it.getResourceSpansList().stream())
        .flatMap(it -> it.getResource().getAttributesList().stream())
        .filter(it -> it.getKey().equals("process.environ"))
        .map(it -> parseEnv(it.getValue().getStringValue()))
        .forEach(
            envs -> {
              Assertions.assertEquals("****", envs.get("MY_PASS"));
            });
  }

  public static Map<String, String> parseEnv(String value) {
    try {
      return OBJECT_MAPPER.readValue(
          value,
          TypeFactory.defaultInstance().constructMapType(Map.class, String.class, String.class));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testInvalidSpanDump() throws IOException, InterruptedException {
    startTarget(8, Map.of("LUMIGO_DEBUG_SPANDUMP", "invalid"));

    Assertions.assertTrue(target.getLogs().contains("Lumigo debug span dump is not a valid path"));
    stopTarget();
  }

  @Test
  public void testSwitchoff() throws IOException, InterruptedException {
    startTarget(8, Map.of("LUMIGO_SWITCH_OFF", "true"));

    Assertions.assertTrue(
        target.getLogs().contains("Lumigo OpenTelemetry JavaAgent distribution disabled"));
  }

  @Test
  public void springBootSmokeTestOnJDK() throws IOException, InterruptedException {
    startTarget(8, Map.of("LUMIGO_DEBUG", "true"));

    // check if the debug log is printed
    Assertions.assertTrue(
        target.getLogs().contains("DEBUG io.opentelemetry.javaagent.tooling.AgentInstaller"));

    String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    Request request = new Request.Builder().url(url).get().build();
    Response response = client.newCall(request).execute();

    String currentAgentVersion =
        (String)
            new JarFile(agentPath)
                .getManifest()
                .getMainAttributes()
                .get(Attributes.Name.IMPLEMENTATION_VERSION);

    Collection<ExportTraceServiceRequest> traces = waitForTraces();

    Assertions.assertEquals("Hi!", response.body().string());
    Assertions.assertEquals(1, countSpansByName(traces, "GET /greeting"));
    Assertions.assertEquals(1, countSpansByName(traces, "WebController.greeting"));
    Assertions.assertEquals(1, countSpansByName(traces, "WebController.withSpan"));
    Assertions.assertNotEquals(
        0, countResourcesByValue(traces, "telemetry.auto.version", currentAgentVersion));
    Assertions.assertNotEquals(
        0, countResourcesByValue(traces, "lumigo.distro.version", "dev-SNAPSHOT"));

    Assertions.assertNotEquals(0, countResourcesByValue(traces, "telemetry.sdk.language", "java"));
    Assertions.assertNotEquals(
        0, countResourcesByValue(traces, "telemetry.sdk.name", "opentelemetry"));
    Assertions.assertNotEquals(0, countResourcesByName(traces, "process.runtime.description"));
    Assertions.assertNotEquals(0, countResourcesByName(traces, "process.runtime.name"));
    Assertions.assertNotEquals(0, countResourcesByName(traces, "process.runtime.version"));

    Collection<ExportTraceServiceRequest> dumpTraces = tracesFromSpanDump();
    Assertions.assertEquals(1, countSpansByName(dumpTraces, "GET /greeting"));
    Assertions.assertNotEquals(
        0, countResourcesByValue(dumpTraces, "lumigo.distro.version", "dev-SNAPSHOT"));

    stopTarget();
  }
}
