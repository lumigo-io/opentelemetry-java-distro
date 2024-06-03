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
package io.lumigo.javaagent.instrumentation.storm;

import io.lumigo.storm.testing.WordCountTopologyLocal;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestStormTopologyLocalCluster {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();


  private static final String contextPath = "/spring-web";

  private static String serverUrl;

  @BeforeAll
  static void setUp() throws Exception{
    WordCountTopologyLocal.submitTopology();
  }

  @AfterAll
  static void tearDown() throws Exception {
    WordCountTopologyLocal.close();
  }

  @Test
  void testStorm() {
    TracesAssert.assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasName("POST /spring-web/greeting/post")
                              .hasKind(SpanKind.SERVER)
                              .hasAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                              .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                              .hasAttribute(
                                  AttributeKey.stringKey("http.request.body"), "")
                              .hasAttribute(
                                  AttributeKey.stringKey("http.response.body"), "");
                        },
                        span -> {
                          span.hasName("GreetingController.greeting").hasKind(SpanKind.INTERNAL);
                        }));
  }
}
