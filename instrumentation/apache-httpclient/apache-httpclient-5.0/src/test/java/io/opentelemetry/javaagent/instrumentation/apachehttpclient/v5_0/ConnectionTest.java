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
package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class ConnectionTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension mockServer = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().gzipDisabled(true))
      .build();

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testConnectionReset() {
    final String urlPath = "/reset";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER)
        )
    );

    Throwable thrown = catchThrowable(() -> {
      try (CloseableHttpClient client = HttpClients.createDefault()) {
        final ClassicHttpRequest httpGet = ClassicRequestBuilder.get(mockServer.url(urlPath))
            .addHeader("Content-Type", "application/json").build();
        client.execute(httpGet, response -> null);
      }
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
                          .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                          .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                              List.of("application/json"))
                          .hasAttribute(AttributeKey.stringKey("http.request.body"), "null")
                          .hasException(thrown)
                          .hasStatus(StatusData.error())
                  ));
  }

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testMalformedChunk() {
    final String urlPath = "/malformed";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
        )
    );

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final ClassicHttpRequest httpGet = ClassicRequestBuilder.get(mockServer.url(urlPath))
          .addHeader("Content-Type", "application/json").build();
      client.execute(httpGet, response -> null);
    } catch (MalformedChunkCodingException mcce) {
      // Expecting this one
    } catch (IOException e) {
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
                            .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasAttribute(AttributeKey.stringKey("http.request.body"), "null")
                            .hasAttribute(AttributeKey.stringKey("http.response.body"), "")
                            .hasAttribute(AttributeKey.stringArrayKey("http.response.header.transfer_encoding"),
                                List.of("chunked"))
                            .hasAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
                            .hasStatus(StatusData.unset())
                    ));
  }

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testGarbageResponse() {
    final String urlPath = "/garbage";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
        )
    );

    Throwable thrown = catchThrowable(() -> {
      try (CloseableHttpClient client = HttpClients.createDefault()) {
        final ClassicHttpRequest httpGet = ClassicRequestBuilder.get(mockServer.url(urlPath))
            .addHeader("Content-Type", "application/json").build();
        client.execute(httpGet, response -> null);
      }
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
                            .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasAttribute(AttributeKey.stringKey("http.request.body"), "null")
                            .hasException(thrown)
                            .hasStatus(StatusData.error())
                    ));
  }

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testServerError() {
    final String urlPath = "/server-error";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withStatus(500)
        )
    );

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final ClassicHttpRequest httpGet = ClassicRequestBuilder.get(mockServer.url(urlPath))
          .addHeader("Content-Type", "application/json").build();
      client.execute(httpGet, response -> null);
    } catch (IOException e) {
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
                            .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/json"))
                            .hasAttribute(AttributeKey.stringKey("http.request.body"), "null")
                            .hasAttribute(SemanticAttributes.HTTP_STATUS_CODE, 500L)
                            .hasStatus(StatusData.error())
                    ));
  }

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testNullResponse() {
    final String jsonBody = "\"null\"";
    final String urlPath = "/response";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/text")
            .withBody(jsonBody)
        )
    );

    AtomicReference<String> responsePayload = new AtomicReference<>();

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final ClassicHttpRequest httpGet = ClassicRequestBuilder.get(mockServer.url(urlPath)).addHeader("Content-Type", "application/text").build();
      client.execute(httpGet, response -> {
        final HttpEntity entity1 = response.getEntity();
        responsePayload.set(EntityUtils.toString(entity1));
        return null;
      });
    }
    catch (IOException e) {
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
                            .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                            .hasAttribute(AttributeKey.stringArrayKey("http.request.header.content_type"),
                                List.of("application/text"))
                            .hasAttribute(AttributeKey.stringKey("http.request.body"), "null")
                            .hasAttribute(AttributeKey.stringArrayKey("http.response.header.content_type"),
                                List.of("application/text"))
                            // Buffer size differences between local and GH Actions prevent us from
                            // using this assertion as response content differs
                            // .hasAttribute(AttributeKey.stringKey("http.response.body"), jsonBody)
                            .hasAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
                    ));

    Assertions.assertThat(responsePayload.get()).isEqualTo(jsonBody);
  }
}
