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

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSampler;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSamplerBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.logging.Logger;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class RedisSamplingConfigurer implements AutoConfigurationCustomizerProvider {
  private static final Logger LOGGER = Logger.getLogger(RedisSamplingConfigurer.class.getName());

  public static final String LUMIGO_REDUCED_REDIS_INSTRUMENTATION =
      "lumigo.reduced.redis.instrumentation";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addSamplerCustomizer(RedisSamplingConfigurer::customizeRedisSpans);
  }

  private static Sampler customizeRedisSpans(
      Sampler defaultSampler, ConfigProperties configProperties) {

    RuleBasedRoutingSamplerBuilder samplerBuilder =
        RuleBasedRoutingSampler.builder(SpanKind.CLIENT, defaultSampler);

    String reduceRedisInstrumentation =
        configProperties.getString(LUMIGO_REDUCED_REDIS_INSTRUMENTATION);
    boolean isReducedRedisInstrumentationEnabled;

    if (reduceRedisInstrumentation == null || reduceRedisInstrumentation.isEmpty()) {
      isReducedRedisInstrumentationEnabled = true; // Default to true
    } else if (reduceRedisInstrumentation.equalsIgnoreCase("true")) {
      isReducedRedisInstrumentationEnabled = true;
    } else if (reduceRedisInstrumentation.equalsIgnoreCase("false")) {
      isReducedRedisInstrumentationEnabled = false;
    } else {
      LOGGER.warning(
          "Invalid value for LUMIGO_REDUCED_REDIS_INSTRUMENTATION: "
              + reduceRedisInstrumentation
              + ". Defaulting to true.");
      isReducedRedisInstrumentationEnabled = true;
    }

    if (isReducedRedisInstrumentationEnabled) {

      // Setting the environment variable `LUMIGO_REDUCED_REDIS_INSTRUMENTATION=false` will disable
      // this optimization.
      LOGGER.finest(
          "Lumigo reduces Redis instrumentation. The `db.statement` attribute (e.g., `INFO server`) is excluded by default. Set `LUMIGO_REDUCED_REDIS_INSTRUMENTATION=false` to disable this behavior.");

      // Define attribute keys
      AttributeKey<String> dbSystemKey = AttributeKey.stringKey("db.system");
      AttributeKey<String> dbStatementKey = AttributeKey.stringKey("db.statement");

      samplerBuilder.customize(
          dbSystemKey,
          "redis",
          RuleBasedRoutingSampler.builder(SpanKind.CLIENT, defaultSampler)
              // have to use regex to match the db.statement attribute, that can be "INFO server" or
              // "server", depending on the Redis configuration
              .drop(dbStatementKey, "(INFO\\s+)?server")
              .build());
    }

    return samplerBuilder.build();
  }
}
