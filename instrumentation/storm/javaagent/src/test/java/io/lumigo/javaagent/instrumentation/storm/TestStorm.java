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
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.storm.Testing;
import org.apache.storm.testing.MkClusterParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestStorm {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  void assertExecutor(
      SpanDataAssert span, String serviceName, String destinationName, List<String> values) {
    span.hasName("Storm Executor")
        .hasKind(SpanKind.INTERNAL)
        .hasAttribute(AttributeKey.stringKey(StormUtils.COMPONENT_NAME_KEY), serviceName)
        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName)
        .hasAttribute(AttributeKey.stringArrayKey(StormUtils.STORM_TUPLE_VALUES_KEY), values)
        .hasAttributesSatisfying(
            attributes ->
                Objects.requireNonNull(attributes.get(SemanticAttributes.MESSAGING_MESSAGE_ID)));
  }

  void assertWordCount(TraceAssert trace, List<String> inputValues) {
    trace
        .hasSize(2)
        .hasSpansSatisfyingExactlyInAnyOrder(
            span -> {
              span.hasName("Storm Bolt")
                  .hasKind(SpanKind.INTERNAL)
                  .hasAttribute(AttributeKey.stringKey(StormUtils.STORM_TYPE_KEY), "bolt")
                  .hasAttribute(AttributeKey.stringKey(StormUtils.COMPONENT_NAME_KEY), "wordCount")
                  .hasAttribute(AttributeKey.stringKey(StormUtils.SOURCE_COMPONENT_KEY), "split")
                  .hasAttribute(
                      AttributeKey.stringArrayKey(StormUtils.STORM_TUPLE_VALUES_KEY), inputValues)
                  .hasAttributesSatisfying(
                      attributes ->
                          Objects.requireNonNull(
                              attributes.get(SemanticAttributes.MESSAGING_MESSAGE_ID)));
            },
            span -> {});
  }

  @Test
  void testStorm() {
    MkClusterParam param = new MkClusterParam();
    param.setSupervisors(1);
    Testing.withLocalCluster(
        param,
        (cluster) -> {
          WordCountTopologyLocal.runLocalClusterTest(cluster);
          Thread.sleep(1000); // Storm test is flaky and sometime doesn't wait for all spans
          // Test we generate the expected spans
          TracesAssert.assertThat(instrumentation.waitForTraces(28))
              .hasSizeGreaterThanOrEqualTo(28)
              .filteredOn(
                  trace ->
                      trace.stream()
                          .anyMatch(span -> !Objects.equals(span.getName(), "Storm Spout")))
              // verify the spout spans
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans ->
                                spans.hasSpansSatisfyingExactlyInAnyOrder(
                                    span -> {
                                      span.hasName("Storm Spout")
                                          .hasKind(SpanKind.INTERNAL)
                                          .hasAttribute(
                                              AttributeKey.stringKey(StormUtils.STORM_TYPE_KEY),
                                              "spout")
                                          .hasAttribute(
                                              AttributeKey.stringKey(StormUtils.COMPONENT_NAME_KEY),
                                              "spout");
                                    },
                                    span -> {
                                      assertExecutor(
                                          span,
                                          "spout",
                                          "split",
                                          List.of("the cow jumped over the moon"));
                                    },
                                    span -> {}));
                  })
              // verify the split bolt spans
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans ->
                                spans
                                    .hasSize(13)
                                    .hasSpansSatisfyingExactlyInAnyOrder(
                                        span -> {
                                          span.hasName("Storm Bolt")
                                              .hasKind(SpanKind.INTERNAL)
                                              .hasAttribute(
                                                  AttributeKey.stringKey(StormUtils.STORM_TYPE_KEY),
                                                  "bolt")
                                              .hasAttribute(
                                                  AttributeKey.stringKey(
                                                      StormUtils.COMPONENT_NAME_KEY),
                                                  "split")
                                              .hasAttribute(
                                                  AttributeKey.stringKey(
                                                      StormUtils.SOURCE_COMPONENT_KEY),
                                                  "spout")
                                              .hasAttribute(
                                                  AttributeKey.stringArrayKey(
                                                      StormUtils.STORM_TUPLE_VALUES_KEY),
                                                  List.of("the cow jumped over the moon"))
                                              .hasAttributesSatisfying(
                                                  attributes ->
                                                      Objects.requireNonNull(
                                                          attributes.get(
                                                              SemanticAttributes
                                                                  .MESSAGING_MESSAGE_ID)));
                                        },
                                        span -> {
                                          assertExecutor(
                                              span, "split", "wordCount", List.of("the"));
                                        },
                                        span -> {},
                                        span -> {
                                          assertExecutor(
                                              span, "split", "wordCount", List.of("cow"));
                                        },
                                        span -> {},
                                        span -> {
                                          assertExecutor(
                                              span, "split", "wordCount", List.of("jumped"));
                                        },
                                        span -> {},
                                        span -> {
                                          assertExecutor(
                                              span, "split", "wordCount", List.of("over"));
                                        },
                                        span -> {},
                                        span -> {
                                          assertExecutor(
                                              span, "split", "wordCount", List.of("the"));
                                        },
                                        span -> {},
                                        span -> {
                                          assertExecutor(
                                              span, "split", "wordCount", List.of("moon"));
                                        },
                                        span -> {}));
                  })
              // verify the wordCount bolt spans
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans -> {
                              assertWordCount(spans, List.of("the"));
                            });
                  })
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans -> {
                              assertWordCount(spans, List.of("cow"));
                            });
                  })
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans -> {
                              assertWordCount(spans, List.of("jumped"));
                            });
                  })
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans -> {
                              assertWordCount(spans, List.of("over"));
                            });
                  })
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans -> {
                              assertWordCount(spans, List.of("the"));
                            });
                  })
              .anySatisfy(
                  trace -> {
                    TracesAssert.assertThat(Collections.singletonList(trace))
                        .hasTracesSatisfyingExactly(
                            spans -> {
                              assertWordCount(spans, List.of("moon"));
                            });
                  });
        });
  }
}
