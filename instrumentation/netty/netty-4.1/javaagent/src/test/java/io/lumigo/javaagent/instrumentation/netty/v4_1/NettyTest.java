/*
 * Copyright 2024 Lumigo LTD
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
package io.lumigo.javaagent.instrumentation.netty.v4_1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class NettyTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static NettyServer server;
  private static int serverPort;

  @BeforeAll
  public static void setup() throws InterruptedException {
    serverPort = PortUtils.findOpenPort();
    server = new NettyServer(serverPort);
    server.start();
    Thread.sleep(1000); // Wait briefly to ensure the server is fully initialized
  }

  @Test
  public void testGetResponse() throws InterruptedException {
    NettyHttpClient client = new NettyHttpClient("localhost", serverPort);
    var response = client.sendGetRequest(); // New GET request method

    // Validate the HTTP response
    assertEquals(HttpResponseStatus.OK, response.getStatus());
    String responseBody = response.content().toString(StandardCharsets.UTF_8);
    assertEquals("Hello, World!", responseBody);

    TracesAssert.assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasName("GET").hasKind(SpanKind.CLIENT);
                        },
                        span -> {
                          span.hasName("GET")
                              .hasKind(SpanKind.SERVER)
                              .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                              .hasAttribute(
                                  AttributeKey.longKey("http.response_content_length"),
                                  (long) responseBody.length())
                              .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                              .hasAttribute(
                                  AttributeKey.stringKey("http.response.body"), responseBody);
                        }));
  }

  @Test
  public void testPostResponse() throws InterruptedException {
    NettyHttpClient client = new NettyHttpClient("localhost", serverPort);
    String requestBody = "This is a test message";
    var response = client.sendPostRequest(requestBody); // New POST request method

    // Validate the HTTP response
    assertEquals(HttpResponseStatus.OK, response.getStatus());
    String responseBody = response.content().toString(StandardCharsets.UTF_8);
    assertEquals("Hello, World!", responseBody);

    TracesAssert.assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasName("POST").hasKind(SpanKind.CLIENT);
                        },
                        span -> {
                          span.hasName("POST")
                              .hasKind(SpanKind.SERVER)
                              .hasAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                              .hasAttribute(
                                  AttributeKey.longKey("http.response_content_length"),
                                  (long) responseBody.length())
                              .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                              .hasAttribute(
                                  AttributeKey.stringKey("http.request.body"), requestBody)
                              .hasAttribute(
                                  AttributeKey.stringKey("http.response.body"), responseBody);
                        }));
  }

  @AfterAll
  public static void teardown() {
    server.stop();
  }
}
