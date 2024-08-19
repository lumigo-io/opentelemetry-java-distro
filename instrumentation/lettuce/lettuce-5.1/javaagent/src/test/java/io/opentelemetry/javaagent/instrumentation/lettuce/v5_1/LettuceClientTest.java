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
package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

class LettuceClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  static int port;

  static RedisClient redisClient;
  static StatefulRedisConnection<String, String> connection;
  static RedisCommands<String, String> commands;

  @BeforeAll
  static void setupSpec() {
    redisServer.start();
    port = redisServer.getMappedPort(6379);
    redisClient = RedisClient.create("redis://localhost:"+port+"/0");
    connection = redisClient.connect();
    commands = connection.sync();
  }

  @AfterAll
  static void cleanupSpec() {
    redisServer.stop();
  }

  @BeforeEach
  void setup() {
    commands.flushall();
    connection.flushCommands();
    testing.waitForTraces(1);
    testing.clearData();
  }

  @Test
  // TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void setCommand() {
    commands.set("foo", "bar");

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
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                                equalTo(AttributeKey.stringKey("db.response.body"), "OK"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port))));
  }

  @Test
  // TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void getCommand() {
    commands.set("foo", "bar");
    String value = commands.get("foo");

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
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                                equalTo(AttributeKey.stringKey("db.response.body"), "OK"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "GET foo"),
                                equalTo(AttributeKey.stringKey("db.response.body"), "bar"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port))));
  }

  @Test
  // TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void commandWithNoArguments() {
    commands.set("foo", "bar");
    String value = commands.randomkey();

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
                                equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("RANDOMKEY")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(AttributeKey.stringKey("db.response.body"), "foo"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port))));
  }
}
