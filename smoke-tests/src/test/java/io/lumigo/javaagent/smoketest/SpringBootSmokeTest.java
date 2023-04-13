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

  @Override
  protected String getTargetImage(int jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
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
    startTarget(8, null);

    // check if the debug log is printed
    Assertions.assertTrue(
        target.getLogs().contains("DEBUG io.opentelemetry.javaagent.tooling.AgentInstaller"));

    String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    Request request = new Request.Builder().url(url).get().build();

    String currentAgentVersion =
        (String)
            new JarFile(agentPath)
                .getManifest()
                .getMainAttributes()
                .get(Attributes.Name.IMPLEMENTATION_VERSION);

    Response response = client.newCall(request).execute();

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
