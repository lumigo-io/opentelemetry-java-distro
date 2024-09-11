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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import io.opentelemetry.semconv.SemanticAttributes;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JsonNonChunkedContentTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension mockServer = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort()
          .gzipDisabled(true)
          .useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.NEVER))
      .build();

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testJsonResponse() {
    final String jsonBody = "{\"message\":\"Welcome Gary!\"}";
    final String urlPath = "/response";

    mockServer.stubFor(get(urlPathEqualTo(urlPath))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(jsonBody)
        )
    );

    AtomicReference<String> responsePayload = new AtomicReference<>();

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final ClassicHttpRequest httpGet = ClassicRequestBuilder.get(mockServer.url(urlPath)).addHeader("Content-Type", "application/json").build();
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
                          .hasAttribute(AttributeKey.stringKey("http.request.method"), "GET")
                          // Buffer size differences between local and GH Actions prevent us from
                          // using this assertion as response content differs
                          // .hasAttribute(AttributeKey.stringKey("http.response.body"), jsonBody)
                          .hasAttribute(AttributeKey.longKey("http.response.status_code"), 200L)
                  ));

    Assertions.assertThat(responsePayload.get()).isEqualTo(jsonBody);
  }

  @Test
  //TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void testJsonRequestResponse() {
    final String requestBody = "{\"name\":\"gary\"}";
    final String responseBody = "{\"message\":\"Welcome Gary!\"}";
    final String urlPath = "/request-response";

    mockServer.stubFor(
        get(urlPathEqualTo(urlPath))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(equalToJson(requestBody))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)
        )
    );

    AtomicReference<String> responsePayload = new AtomicReference<>();

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final ClassicHttpRequest httpGet = ClassicRequestBuilder
          .get(mockServer.url(urlPath))
          .addHeader("Content-Type", "application/json")
          .setEntity(requestBody)
          .build();
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
                            .hasAttribute(AttributeKey.stringKey("http.request.method"), "GET")
                            // Buffer size differences between local and GH Actions prevent us from
                            // using this assertion as response content differs
                            // .hasAttribute(AttributeKey.stringKey("http.response.body"), responseBody)
                            .hasAttribute(AttributeKey.longKey("http.response.status_code"), 200L)
                    ));

    Assertions.assertThat(responsePayload.get()).isEqualTo(responseBody);
  }
}
