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

import static io.lumigo.javaagent.spandump.SpanMatchers.*;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lumigo.javaagent.junitextensions.OkHttpClientExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension.Configuration;
import io.lumigo.javaagent.junitextensions.TestAppExtension.EnvVar;
import io.lumigo.javaagent.spandump.SpanDumpEntry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.awaitility.Awaitility;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({TestAppExtension.class, OkHttpClientExtension.class})
class SpringBootSmokeTest {

  @Test
  void testDefaultSettings(final TestAppExtension.TestApplication target) {
    final String logs = target.getLogs();

    /*
     * Max span attribute length is 1024, match also the closing curly bracket to avoid
     * false positives.
     */
    assertThat(logs, containsString("maxAttributeValueLength=1024}"));
    /*
     * Batch schedule delay; matching also the comma, as the default value is 10 times
     * what we set (""scheduleDelayNanos=5000000000", mind the zeroes) and, without
     * matching the trailing comma, we might mistake the default value for a match.
     *
     * See https://github.com/open-telemetry/opentelemetry-java/blob/f92e02e4caffab0d964c02a32fe305d6d6ba372e/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/export/BatchSpanProcessor.java#L133
     */
    assertThat(logs, containsString("scheduleDelayNanos=10000000,"));
    /*
     * Batch max size; matching also the trailing comma to avoid false positives.
     *
     * See https://github.com/open-telemetry/opentelemetry-java/blob/f92e02e4caffab0d964c02a32fe305d6d6ba372e/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/export/BatchSpanProcessor.java#L133
     */
    assertThat(logs, containsString("maxExportBatchSize=100,"));
    /*
     * Batch export timeout; matching also the following closing curly bracket to avoid
     * false positives.
     *
     * See https://github.com/open-telemetry/opentelemetry-java/blob/f92e02e4caffab0d964c02a32fe305d6d6ba372e/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/export/BatchSpanProcessor.java#L133
     */
    assertThat(logs, containsString("exporterTimeoutNanos=1000000000}"));
  }

  @Test
  void testInvalidSpanDump(
      final @Configuration(env = {@EnvVar(key = "LUMIGO_DEBUG_SPANDUMP", value = "invalid")})
          TestAppExtension.TestApplication target) {
    final String logs = target.getLogs();
    assertThat(logs, containsString("Spandump path 'invalid' is not valid; spandump is disabled"));
    assertThat(logs, not(containsString("spanExporter=io.lumigo.javaagent.FileSpanExporter")));
  }

  @Test
  void testSwitchOff(
      final @Configuration(env = {@EnvVar(key = "LUMIGO_SWITCH_OFF", value = "true")})
          TestAppExtension.TestApplication target) {
    assertThat(target.getLogs(), containsString("Lumigo OpenTelemetry Java distribution disabled"));
  }

  @Test
  void testCustomHttpFilter(
      final @Configuration(
              env = {
                @EnvVar(
                    key = "LUMIGO_AUTO_FILTER_HTTP_ENDPOINTS_REGEX",
                    value = "[\".*/greeting.*\", \".*/actuator.*\"]")
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    assertThat(
        target.getLogs(),
        containsString(
            "sampler=RuleBasedRoutingSampler{rules=[SamplingRule{attributeKey=url.full, delegate=AlwaysOffSampler, pattern=.*/greeting.*}"));

    final String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    final Request request = new Request.Builder().url(url).get().build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.body(), is(notNullValue()));
      assertThat(response.code(), is(200));
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanDumpEntry> entries = target.getSpanDump();
              assertThat(entries.size(), is(0));

              TypeSafeMatcher<SpanDumpEntry> hasSpanName = hasSpanName("GET /greeting");
              assertThrows(NoSuchElementException.class, () -> findSpan(entries, hasSpanName));
            });
  }

  @Test
  void testSpanDump(final TestAppExtension.TestApplication target, final OkHttpClient client)
      throws IOException {
    assertThat(
        target.getLogs(),
        containsString("SimpleSpanProcessor{spanExporter=io.lumigo.javaagent.FileSpanExporter"));

    final String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    final Request request = new Request.Builder().url(url).get().build();

    try (final Response response = client.newCall(request).execute()) {
      ResponseBody body = response.body();
      assertThat(body, is(notNullValue()));
      assertThat(body.string(), is("Hi!"));
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanDumpEntry> entries = target.getSpanDump();

              SpanDumpEntry serverSpan = findSpan(entries, hasSpanName("GET /greeting"));
              assertThat(serverSpan, hasSpanKind(SERVER));
              assertThat(serverSpan, hasSpanStatus(StatusData.unset()));
              assertThat(serverSpan, hasAttribute("http.target", "/greeting"));
              assertThat(serverSpan, hasAttribute("http.route", "/greeting"));
              assertThat(serverSpan, hasAttribute("http.status_code", 200L));
              assertThat(serverSpan, hasAttribute("http.response.body", "Hi!"));
              assertThat(serverSpan, hasAttributeOfTypeString("thread.name"));
              assertThat(serverSpan, hasAttributeOfTypeLong("thread.id"));
              assertThat(serverSpan, hasResourceAttributeOfTypeString("lumigo.distro.version"));
              assertThat(serverSpan, hasResourceAttributeOfTypeString("container.id"));

              SpanDumpEntry internalSpan = findSpan(entries, hasSpanName("WebController.greeting"));
              assertThat(internalSpan, hasSpanKind(INTERNAL));
              assertThat(internalSpan, hasTraceId(serverSpan.getSpan().getTraceId()));
              assertThat(internalSpan, hasParentSpanId(serverSpan.getSpan().getSpanId()));
              assertThat(internalSpan, hasAttributeOfTypeString("thread.name"));
              assertThat(internalSpan, hasAttributeOfTypeLong("thread.id"));

              final String expectedContainerId =
                  serverSpan.getResource().getAttribute(AttributeKey.stringKey("container.id"));
              assertThat(internalSpan, hasResourceAttribute("container.id", expectedContainerId));
            });
  }

  private static SpanDumpEntry findSpan(
      List<SpanDumpEntry> entries, TypeSafeMatcher<SpanDumpEntry> matcher) {
    return entries.stream().filter(matcher::matches).findFirst().orElseThrow();
  }
}
