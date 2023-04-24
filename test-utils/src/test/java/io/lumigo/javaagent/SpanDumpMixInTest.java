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
package io.lumigo.javaagent;

import static io.lumigo.javaagent.spandump.SpanMatchers.*;
import static io.opentelemetry.api.common.AttributeKey.*;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.lumigo.javaagent.spandump.Span;
import io.lumigo.javaagent.spandump.SpanDumpEntry;
import io.lumigo.javaagent.spandump.SpanDumpMixIn;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SpanDumpMixInTest {

  @Test
  public void testSingleEntry() throws Exception {
    SpanDumpEntry entry =
        SpanDumpMixIn.OBJECT_MAPPER.readValue(
            new File("src/test/resources/single_entry.spandump"), SpanDumpEntry.class);

    Span span = entry.getSpan();
    assertThat(span, notNullValue());

    assertThat(span.getName(), equalTo("WebController.withSpan"));
    assertThat(entry, hasSpanName("WebController.withSpan"));

    assertThat(span.getTraceId(), equalTo("49a117f265f1eea9c44c417700d7b621"));
    assertThat(entry, hasTraceId("49a117f265f1eea9c44c417700d7b621"));

    assertThat(span.getSpanId(), equalTo("c4c176857c601fb8"));
    assertThat(entry, hasSpanId("c4c176857c601fb8"));

    assertThat(span.getParentSpanId(), equalTo("f291d2c3c13d356f"));
    assertThat(entry, hasParentSpanId("f291d2c3c13d356f"));

    assertThat(span.getKind(), equalTo(SpanKind.SERVER));
    assertThat(entry, hasSpanKind(SpanKind.SERVER));

    assertThat(span.getStartTimeUnixNano(), equalTo(1682175628455443917L));
    assertThat(entry, hasStartTime(1682175628455443917L));

    assertThat(span.getEndTimeUnixNano(), equalTo(1682175628458434334L));
    assertThat(entry, hasEndTime(1682175628458434334L));

    assertThat(span.getStatus(), equalTo(StatusData.unset()));
    assertThat(entry, hasSpanStatus(StatusData.unset()));

    assertThat(span.getEvents(), equalTo(Collections.emptyList()));
    assertThat(span.getLinks(), equalTo(Collections.emptyList()));

    assertThat(span.getAttributes().get(stringKey("thread.name")), equalTo("http-nio-8080-exec-1"));
    assertThat(entry, hasAttribute("thread.name", "http-nio-8080-exec-1"));

    assertThat(span.getAttributes().get(longKey("thread.id")), equalTo(19L));
    assertThat(entry, hasAttribute("thread.id", 19L));

    Resource resource = entry.getResource();
    assertThat(
        resource.getAttributes().get(stringKey("container.id")),
        equalTo("979f62f6207cb932c1ed1b8ae1485c13baf912dd8094129563dd3043fd14a1e6"));
    assertThat(
        entry,
        hasResourceAttribute(
            stringKey("container.id"),
            "979f62f6207cb932c1ed1b8ae1485c13baf912dd8094129563dd3043fd14a1e6"));
  }

  @Test
  public void testSpringBootHttpExample() throws Exception {
    List<SpanDumpEntry> entries =
        Files.readAllLines(Paths.get("src/test/resources/springboot_http_example.spandump"))
            .stream()
            .map(
                line -> {
                  try {
                    return SpanDumpMixIn.OBJECT_MAPPER.readValue(line, SpanDumpEntry.class);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList();

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
    assertThat(internalSpan, hasAttributeOfTypeLong("thread.id"));
    assertThat(
        internalSpan,
        hasResourceAttribute(
            "container.id",
            serverSpan.getResource().getAttribute(AttributeKey.stringKey("container.id"))));
    assertThat(
        internalSpan,
        hasResourceAttribute(
            "lumigo.distro.version",
            serverSpan
                .getResource()
                .getAttribute(AttributeKey.stringKey("lumigo.distro.version"))));
  }
}
