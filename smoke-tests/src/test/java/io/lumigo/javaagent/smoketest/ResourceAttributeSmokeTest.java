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

import static io.lumigo.javaagent.spandump.SpanMatchers.hasResourceAttributeOfTypeString;
import static io.lumigo.javaagent.spandump.SpanMatchers.hasSpanName;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.lumigo.javaagent.junitextensions.OkHttpClientExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension;
import io.lumigo.javaagent.spandump.SpanDumpEntry;
import io.opentelemetry.api.common.AttributeKey;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.awaitility.Awaitility;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({TestAppExtension.class, OkHttpClientExtension.class})
public class ResourceAttributeSmokeTest {
  private static final String CONTAINER_NAME_KEY = "lumigo.container.name";
  private static final String CONTAINER_NAME_VALUE = "my-cube";

  private static final String LUMIGO_TAG_KEY = "lumigo.tag";
  private static final String LUMIGO_TAG_VALUE = "my_app_tag";
  private static final String LUMIGO_TAG_INVALID_VALUE = "my_app_tag;";

  @Test
  void defaultConfig(final TestAppExtension.TestApplication target, final OkHttpClient client)
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
              assertThat(
                  serverSpan
                      .getResource()
                      .getAttribute(AttributeKey.stringKey("k8s.container.name")),
                  nullValue());
              assertThat(
                  serverSpan.getResource().getAttribute(AttributeKey.stringKey("lumigo.tag")),
                  nullValue());
            });
  }

  @Test
  public void k8sContainerName(
      final @TestAppExtension.Configuration(
              env = {
                @TestAppExtension.EnvVar(key = CONTAINER_NAME_KEY, value = CONTAINER_NAME_VALUE)
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
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
              assertThat(serverSpan, hasResourceAttributeOfTypeString("k8s.container.name"));
              assertThat(
                  serverSpan
                      .getResource()
                      .getAttribute(AttributeKey.stringKey("k8s.container.name")),
                  is(CONTAINER_NAME_VALUE));
            });
  }

  @Test
  public void lumigoTag(
      final @TestAppExtension.Configuration(
              env = {@TestAppExtension.EnvVar(key = LUMIGO_TAG_KEY, value = LUMIGO_TAG_VALUE)})
          TestAppExtension.TestApplication target,
      final OkHttpClient client)
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
              assertThat(serverSpan, hasResourceAttributeOfTypeString(LUMIGO_TAG_KEY));
              assertThat(
                  serverSpan.getResource().getAttribute(AttributeKey.stringKey(LUMIGO_TAG_KEY)),
                  is(LUMIGO_TAG_VALUE));
            });
  }

  @Test
  void invalidLumigoTag(
      final @TestAppExtension.Configuration(
              env = {
                @TestAppExtension.EnvVar(key = LUMIGO_TAG_KEY, value = LUMIGO_TAG_INVALID_VALUE)
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
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
              assertThat(
                  serverSpan.getResource().getAttribute(AttributeKey.stringKey("lumigo.tag")),
                  nullValue());
            });

    assertThat(target.getLogs(), containsString("Lumigo tag cannot contain the ';' character"));
  }

  private static SpanDumpEntry findSpan(
      List<SpanDumpEntry> entries, TypeSafeMatcher<SpanDumpEntry> matcher) {
    return entries.stream().filter(matcher::matches).findFirst().orElseThrow();
  }
}
