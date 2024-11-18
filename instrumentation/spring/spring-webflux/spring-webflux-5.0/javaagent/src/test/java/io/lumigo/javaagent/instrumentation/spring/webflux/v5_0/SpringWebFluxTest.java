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
package io.lumigo.javaagent.instrumentation.spring.webflux.v5_0;

import io.lumigo.spring.webflux.WebfluxApplication;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class SpringWebFluxTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext context;

  private static String serverUrl;

  @BeforeAll
  static void setUp() {
    int port = PortUtils.findOpenPort();
    SpringApplication app = new SpringApplication(WebfluxApplication.class);
    app.setDefaultProperties(
        Map.of(
            "server.port", port,
            "server.error.include-message", "always"
            )
    );
    context = app.run();
    serverUrl = "http://localhost:" + port + "/";
  }

  @AfterAll
  static void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void testHttpPost() {
    String jsonRequestBody = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":42}";
    String jsonResponse = "{\"count\":1,\"message\":\"Hello John Doe!\"}";

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(serverUrl + "greet"))
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody))
            .header("Content-Type", "application/json")
            .build();

    try {
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      Assertions.assertThat(response.body()).isEqualTo(jsonResponse);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    TracesAssert.assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasName("POST /greet")
                              .hasKind(SpanKind.SERVER)
                              .hasAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                              .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                              .hasAttribute(
                                  AttributeKey.stringKey("http.request.body"), jsonRequestBody)
                              .hasAttribute(
                                  AttributeKey.stringKey("http.response.body"), jsonResponse);
                        },
                        span -> {
                          span.hasName("GreetingController.greet").hasKind(SpanKind.INTERNAL);
                        }
                        ))
    ;
  }
}
