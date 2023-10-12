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
package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

class JedisClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  static int port;

  static Jedis jedis;

  @BeforeAll
  static void setupSpec() {
    redisServer.start();
    port = redisServer.getMappedPort(6379);
    jedis = new Jedis("localhost", port);
  }

  @AfterAll
  static void cleanupSpec() {
    redisServer.stop();
  }

  @BeforeEach
  void setup() {
    jedis.flushAll();
    testing.clearData();
  }

  @Test
  void setCommand() {
    jedis.set("foo", "bar");

    TracesAssert.assertThat(testing.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo bar"),
                                equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))));
  }

  @Test
  void getCommand() {
    jedis.set("foo", "bar");
    String value = jedis.get("foo");

    assertThat(value).isEqualTo("bar");

    TracesAssert.assertThat(testing.waitForTraces(2))
        .hasSize(2)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo bar"),
                                equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "GET foo"),
                                equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))));
  }

  @Test
  void setSetTransactionCommand() {
    Transaction transaction = jedis.multi();
    transaction.set("foo", "bar");
    transaction.set("foo2", "bar2");
    transaction.exec();

    TracesAssert.assertThat(testing.waitForTraces(4))
        .hasSize(4)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("MULTI")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo bar"),
                                equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo2 bar2"),
                                equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("EXEC")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))));
  }

  @Test
  void commandWithNoArguments() {
    jedis.set("foo", "bar");
    String value = jedis.randomKey();

    assertThat(value).isEqualTo("foo");

    TracesAssert.assertThat(testing.waitForTraces(2))
        .hasSize(2)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo bar"),
                                equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("RANDOMKEY")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "RANDOMKEY"),
                                equalTo(SemanticAttributes.DB_OPERATION, "RANDOMKEY"),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, port))));
  }
}
