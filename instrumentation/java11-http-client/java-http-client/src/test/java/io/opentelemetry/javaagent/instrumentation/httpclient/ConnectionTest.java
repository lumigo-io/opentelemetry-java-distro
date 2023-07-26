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
package io.opentelemetry.javaagent.instrumentation.httpclient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ConnectionTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension mockServer = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().gzipDisabled(true))
      .build();

  @Test
  void testConnectionReset() {
    final String urlPath = "/reset";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER)
        )
    );

    Throwable thrown = catchThrowable(() -> {
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(mockServer.url(urlPath)))
          .GET()
          .header("Content-Type", "application/json")
          .build();

      HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    });

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span -> span
                          .hasName("GET")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttribute(AttributeKey.stringKey("http.method"), "GET")
                          .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                              List.of("application/json"))
                          .hasException(thrown)
                          .hasStatus(StatusData.error())
                  ));
  }

  @Test
  void testMalformedChunk() {
    final String urlPath = "/malformed";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
        )
    );

    Throwable thrown = catchThrowable(() -> {
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(mockServer.url(urlPath)))
          .GET()
          .header("Content-Type", "application/json")
          .build();

      HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    });

    Assertions.assertThat(thrown).hasMessageContaining("protocol error:");

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> span
                            .hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(AttributeKey.stringKey("http.method"), "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasException(thrown)
                            .hasStatus(StatusData.error())
                    ));
  }

  @Test
  void testGarbageResponse() {
    final String urlPath = "/garbage";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
        )
    );

    Throwable thrown = catchThrowable(() -> {
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(mockServer.url(urlPath)))
          .GET()
          .header("Content-Type", "application/json")
          .build();

      HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    });

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> span
                            .hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(AttributeKey.stringKey("http.method"), "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasException(thrown)
                            .hasStatus(StatusData.error())
                    ));
  }

  @Test
  void testServerError() {
    final String urlPath = "/server-error";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withStatus(500)
        )
    );

    HttpRequest request = HttpRequest
        .newBuilder(URI.create(mockServer.url(urlPath)))
        .GET()
        .header("Content-Type", "application/json")
        .build();

    try {
      HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> span
                            .hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(AttributeKey.stringKey("http.method"), "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasAttribute(AttributeKey.longKey("http.status_code"), 500L)
                            .hasStatus(StatusData.error())
                    ));
  }
}
