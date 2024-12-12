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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
      LOGGER.finest(
          "Lumigo reduces Redis instrumentation. Redis spans are sampled based on span name using regex. Set `LUMIGO_REDUCED_REDIS_INSTRUMENTATION=false` to disable this behavior.");
      return new RedisReduceInfoSpanSampler(defaultSampler);
    }

    // Use the default sampler if reduced instrumentation is disabled
    return defaultSampler;
  }

  // Custom Sampler Implementation with Regex
  static class RedisReduceInfoSpanSampler implements Sampler {
    private static final AttributeKey<String> DB_SYSTEM_KEY = AttributeKey.stringKey("db.system");
    private final Sampler delegateSampler;

    // Regex pattern to match span names containing "INFO," (case insensitive)
    private final Pattern spanNamePattern = Pattern.compile("INFO.*", Pattern.CASE_INSENSITIVE);

    public RedisReduceInfoSpanSampler(Sampler delegateSampler) {
      this.delegateSampler = delegateSampler;
    }

    @Override
    public SamplingResult shouldSample(
        Context parentContext,
        String traceId,
        String spanName,
        SpanKind spanKind,
        Attributes attributes,
        List<LinkData> parentLinks) {
      // Check if the db.system attribute is "redis"
      String dbSystem = attributes.get(DB_SYSTEM_KEY);
      if ("redis".equalsIgnoreCase(dbSystem)) {
        // Match the span name against the regex
        if (spanNamePattern.matcher(spanName).matches()) {
          return SamplingResult.drop();
        }
      }
      // Fallback to the delegate sampler
      return delegateSampler.shouldSample(
          parentContext, traceId, spanName, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
      return "RedisReduceInfoSpanSampler";
    }
  }
}
