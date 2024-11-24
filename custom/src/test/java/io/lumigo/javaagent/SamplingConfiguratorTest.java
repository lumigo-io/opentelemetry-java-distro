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

import io.lumigo.javaagent.common.HttpEndpointFilter;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SamplingConfiguratorTest extends AbstractSamplingConfiguratorTest {
  @Test
  public void testCustomize() {
    System.setProperty(
        HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER,
        "[\".*/health.*\", \".*/actuator.*\", \".*/version.*\"]");
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();

    new SamplingConfigurer().customize(builder);
    addPropertiesCustomizer(builder);

    AutoConfiguredOpenTelemetrySdk sdk = builder.build();

    SdkTracerProvider tracerProvider = sdk.getOpenTelemetrySdk().getSdkTracerProvider();
    Sampler sampler = tracerProvider.getSampler();

    Attributes attributes = Attributes.of(AttributeKey.stringKey("http.target"), "/version");
    SamplingResult result =
        sampler.shouldSample(
            Context.root(),
            IdGenerator.random().generateTraceId(),
            "name",
            SpanKind.SERVER,
            attributes,
            Collections.emptyList());
    Assertions.assertEquals(SamplingResult.drop(), result);
  }
}
