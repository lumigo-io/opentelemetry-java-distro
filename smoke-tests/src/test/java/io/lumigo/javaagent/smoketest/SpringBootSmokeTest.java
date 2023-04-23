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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({TestAppExtension.class, OkHttpClientExtension.class})
class SpringBootSmokeTest {

  @Test
  public void testInvalidSpanDump(
      final @Configuration(env = {@EnvVar(key = "LUMIGO_DEBUG_SPANDUMP", value = "invalid")})
          TestAppExtension.TestApplication target) {
    assertThat(
        target.getLogs(),
        containsString("Spandump path 'invalid' is not valid; spandump is disabled"));
  }

  @Test
  public void testSwitchOff(
      final @Configuration(env = {@EnvVar(key = "LUMIGO_SWITCH_OFF", value = "true")})
          TestAppExtension.TestApplication target) {
    assertThat(target.getLogs(), containsString("Lumigo OpenTelemetry Java distribution disabled"));
  }

  @Test
  public void testSpanDump(final TestAppExtension.TestApplication target, final OkHttpClient client)
      throws IOException {
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

              SpanDumpEntry serverSpan =
                  entries.stream()
                      .filter(entry -> "GET /greeting".equals(entry.getSpan().getName()))
                      .findFirst()
                      .orElseThrow();

              assertThat(serverSpan, hasSpanName("GET /greeting"));
              assertThat(serverSpan, hasSpanKind(SERVER));
              assertThat(serverSpan, hasSpanStatus(StatusData.unset()));
              assertThat(serverSpan, hasAttribute("http.target", "/greeting"));
              assertThat(serverSpan, hasAttribute("http.route", "/greeting"));
              assertThat(serverSpan, hasAttribute("http.status_code", 200L));
              assertThat(serverSpan, hasAttributeOfTypeString("thread.name"));
              assertThat(serverSpan, hasAttributeOfTypeLong("thread.id"));
              assertThat(serverSpan, hasResourceAttributeOfTypeString("lumigo.distro.version"));
              assertThat(serverSpan, hasResourceAttributeOfTypeString("container.id"));

              SpanDumpEntry internalSpan =
                  entries.stream()
                      .filter(entry -> "WebController.greeting".equals(entry.getSpan().getName()))
                      .findFirst()
                      .orElseThrow();

              assertThat(internalSpan, hasSpanName("WebController.greeting"));
              assertThat(internalSpan, hasSpanKind(INTERNAL));
              assertThat(internalSpan, hasTraceId(serverSpan.getSpan().getTraceId()));
              assertThat(internalSpan, hasParentSpanId(serverSpan.getSpan().getSpanId()));
              assertThat(internalSpan, hasAttributeOfTypeString("thread.name"));
              assertThat(internalSpan, hasAttributeOfTypeString("thread.id"));
              assertThat(
                  internalSpan,
                  hasResourceAttribute(
                      "container.id",
                      serverSpan
                          .getResource()
                          .getAttribute(AttributeKey.stringKey("container.id"))));
              assertThat(
                  internalSpan,
                  hasResourceAttribute(
                      "lumigo.distro.version",
                      serverSpan
                          .getResource()
                          .getAttribute(AttributeKey.stringKey("container.id"))));
            });
  }
}
