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
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.lumigo.javaagent.junitextensions.OkHttpClientExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension.Configuration;
import io.lumigo.javaagent.junitextensions.TestAppExtension.EnvVar;
import io.lumigo.javaagent.spandump.SpanDumpEntry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
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

    List<SpanDumpEntry> entries = target.getSpanDump();
    assertThat(entries.size(), equalTo(1));

    SpanDumpEntry entry = entries.get(0);
    assertThat(entry, hasSpanName("WebController.withSpan"));
    assertThat(entry, hasSpanKind(SpanKind.SERVER));
    assertThat(entry, hasSpanStatus(StatusData.unset()));
    assertThat(entry, hasAttribute("code.function", "withSpan"));
    assertThat(entry, hasAttributeString("thread.name"));
    assertThat(entry, hasAttributeLong("thread.id"));
    assertThat(entry, hasResourceAttributeString("lumigo.distro.version"));
    assertThat(entry, hasResourceAttributeString("container.id"));
  }
}
