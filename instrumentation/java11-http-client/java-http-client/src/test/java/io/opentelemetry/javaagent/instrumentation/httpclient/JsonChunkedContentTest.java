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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JsonChunkedContentTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension mockServer = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().gzipDisabled(true))
      .build();

  @Test
  void testJsonResponse() {
    final String jsonBody = "{\"fact\":\"A cat\\u2019s jaw can\\u2019t move sideways, so a cat can\\u2019t chew large chunks of food.\",\"length\":74}";
    final String urlPath = "/response";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(jsonBody)
        )
    );

    HttpRequest request = HttpRequest
        .newBuilder(URI.create(mockServer.url(urlPath)))
        .GET()
        .header("Content-Type", "application/json")
        .build();

    try {
      HttpResponse<String> response = HttpClient.newHttpClient().send(request,
          HttpResponse.BodyHandlers.ofString());

      Assertions.assertThat(response.body()).isEqualTo(jsonBody);
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
                          .hasAttribute(AttributeKey.stringArrayKey("http.response.header.content_type"),
                              List.of("application/json"))
                          .hasAttribute(AttributeKey.stringKey("http.response.body"), jsonBody)
                          .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                  ));
  }

  @Test
  void testJsonRequestResponse() {
    final String requestBody = "{\"name\":\"gary\"}";
    final String responseBody = "{\"message\":\"Welcome Gary!\"}";
    final String urlPath = "/request-response";

    mockServer.stubFor(
        post(urlPathEqualTo(urlPath))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(equalToJson(requestBody))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)
        )
    );

    HttpRequest request = HttpRequest
        .newBuilder(URI.create(mockServer.url(urlPath)))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .header("Content-Type", "application/json")
        .build();

    try {
      HttpResponse<String> response = HttpClient.newHttpClient().send(request,
          HttpResponse.BodyHandlers.ofString());

      Assertions.assertThat(response.body()).isEqualTo(responseBody);
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
                            .hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(AttributeKey.stringKey("http.method"), "POST")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasAttribute(AttributeKey.stringKey("http.request.body"), requestBody)
                            .hasAttribute(AttributeKey.stringArrayKey("http.response.header.content_type"),
                                List.of("application/json"))
                            .hasAttribute(AttributeKey.stringKey("http.response.body"), responseBody)
                            .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                    ));
  }
}
