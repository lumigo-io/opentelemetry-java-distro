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
package io.lumigo.javaagent;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RedisSamplingConfiguratorTest extends AbstractSamplingConfiguratorTest {
  @AfterEach
  public void tearDown() {
    System.clearProperty(RedisSamplingConfigurer.LUMIGO_REDUCED_REDIS_INSTRUMENTATION);
  }

  @Test
  public void shouldDropByInfoCommand() {
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();

    new RedisSamplingConfigurer().customize(builder);
    addPropertiesCustomizer(builder);

    AutoConfiguredOpenTelemetrySdk sdk = builder.build();

    SdkTracerProvider tracerProvider = sdk.getOpenTelemetrySdk().getSdkTracerProvider();
    Sampler sampler = tracerProvider.getSampler();

    // Test that the default behavior is to drop "INFO" command in the span name
    Assertions.assertEquals(
        SamplingResult.drop(),
        sampler.shouldSample(
            Context.root(),
            IdGenerator.random().generateTraceId(),
            "INFO",
            SpanKind.CLIENT,
            Attributes.of(
                AttributeKey.stringKey("db.system"), "redis",
                AttributeKey.stringKey("db.statement"), "server"),
            Collections.emptyList()));

    // Test that the default behavior is to drop other INFO commands
    Assertions.assertEquals(
        SamplingResult.drop(),
        sampler.shouldSample(
            Context.root(),
            IdGenerator.random().generateTraceId(),
            "INFO",
            SpanKind.CLIENT,
            Attributes.of(
                AttributeKey.stringKey("db.system"), "redis",
                AttributeKey.stringKey("db.statement"), "other"),
            Collections.emptyList()));

    // Test that the default behavior is to drop other INFO commands
    Assertions.assertEquals(
        SamplingResult.drop(),
        sampler.shouldSample(
            Context.root(),
            IdGenerator.random().generateTraceId(),
            "redis",
            SpanKind.CLIENT,
            Attributes.of(
                AttributeKey.stringKey("db.system"), "redis",
                AttributeKey.stringKey("db.statement"), "INFO"),
            Collections.emptyList()));
  }

  @Test
  public void shouldNotDropIfFalse() {
    System.setProperty(RedisSamplingConfigurer.LUMIGO_REDUCED_REDIS_INSTRUMENTATION, "false");
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();

    new RedisSamplingConfigurer().customize(builder);
    addPropertiesCustomizer(builder);

    AutoConfiguredOpenTelemetrySdk sdk = builder.build();

    SdkTracerProvider tracerProvider = sdk.getOpenTelemetrySdk().getSdkTracerProvider();
    Sampler sampler = tracerProvider.getSampler();

    Attributes attributes =
        Attributes.of(
            AttributeKey.stringKey("db.system"), "redis",
            AttributeKey.stringKey("db.statement"), "server");
    SamplingResult result =
        sampler.shouldSample(
            Context.root(),
            IdGenerator.random().generateTraceId(),
            "INFO",
            SpanKind.CLIENT,
            attributes,
            Collections.emptyList());
    Assertions.assertEquals(SamplingResult.recordAndSample(), result);
  }

  @Test
  public void shouldNotDropIfNotInfo() {
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();

    new RedisSamplingConfigurer().customize(builder);
    addPropertiesCustomizer(builder);

    AutoConfiguredOpenTelemetrySdk sdk = builder.build();

    SdkTracerProvider tracerProvider = sdk.getOpenTelemetrySdk().getSdkTracerProvider();
    Sampler sampler = tracerProvider.getSampler();

    Attributes attributes =
        Attributes.of(
            AttributeKey.stringKey("db.system"), "redis",
            AttributeKey.stringKey("db.statement"), "other");
    SamplingResult result =
        sampler.shouldSample(
            Context.root(),
            IdGenerator.random().generateTraceId(),
            "Other",
            SpanKind.CLIENT,
            attributes,
            Collections.emptyList());
    Assertions.assertEquals(SamplingResult.recordAndSample(), result);
  }
}
