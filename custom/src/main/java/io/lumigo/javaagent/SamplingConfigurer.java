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
package io.lumigo.javaagent;

import io.lumigo.javaagent.common.HttpEndpointFilter;
import io.lumigo.javaagent.common.ParseExpressionResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSampler;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSamplerBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class SamplingConfigurer {
  private static final Logger LOGGER = Logger.getLogger(SamplingConfigurer.class.getName());

  static void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addSamplerCustomizer(SamplingConfigurer::customizeServerSpans);
    autoConfiguration.addSamplerCustomizer(SamplingConfigurer::customizeClientSpans);
  }

  private static Sampler customizeServerSpans(Sampler sampler, ConfigProperties configProperties) {
    RuleBasedRoutingSamplerBuilder samplerBuilder =
        RuleBasedRoutingSampler.builder(SpanKind.SERVER, sampler);

    HttpEndpointFilter httpEndpointFilter = new HttpEndpointFilter();
    ParseExpressionResult parseResult =
        httpEndpointFilter.parseExpressions(
            configProperties, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER);

    LOGGER.finest(
        "Filtering HTTP server endpoint spans matching '"
            + parseResult.getExpressionPatterns()
            + "' regex");

    for (Pattern pattern : parseResult.getExpressionPatterns()) {
      samplerBuilder.drop(AttributeKey.stringKey("url.path"), pattern.pattern());

      // Deprecated in favor of url.path
      samplerBuilder.drop(SemanticAttributes.HTTP_TARGET, pattern.pattern());
    }

    return samplerBuilder.build();
  }

  private static Sampler customizeClientSpans(Sampler sampler, ConfigProperties configProperties) {
    RuleBasedRoutingSamplerBuilder samplerBuilder =
        RuleBasedRoutingSampler.builder(SpanKind.CLIENT, sampler);

    HttpEndpointFilter httpEndpointFilter = new HttpEndpointFilter();
    ParseExpressionResult parseResult =
        httpEndpointFilter.parseExpressions(
            configProperties, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT);

    LOGGER.finest(
        "Filtering HTTP client endpoint spans matching '"
            + parseResult.getExpressionPatterns()
            + "' regex");

    for (Pattern pattern : parseResult.getExpressionPatterns()) {
      samplerBuilder.drop(AttributeKey.stringKey("url.full"), pattern.pattern());

      // Deprecated in favor of url.path
      samplerBuilder.drop(SemanticAttributes.HTTP_URL, pattern.pattern());
    }

    return samplerBuilder.build();
  }
}
