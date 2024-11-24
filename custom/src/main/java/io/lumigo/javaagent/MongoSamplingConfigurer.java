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
public class MongoSamplingConfigurer implements AutoConfigurationCustomizerProvider {
  private static final Logger LOGGER = Logger.getLogger(MongoSamplingConfigurer.class.getName());

  public static final String LUMIGO_REDUCED_MONGO_INSTRUMENTATION =
      "lumigo.reduced.mongo.instrumentation";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addSamplerCustomizer(MongoSamplingConfigurer::customizeMongoSpans);
  }

  private static Sampler customizeMongoSpans(
      Sampler defaultSampler, ConfigProperties configProperties) {

    RuleBasedRoutingSamplerBuilder samplerBuilder =
        RuleBasedRoutingSampler.builder(SpanKind.CLIENT, defaultSampler);

    String reduceMongoInstrumentation =
        configProperties.getString(LUMIGO_REDUCED_MONGO_INSTRUMENTATION);
    boolean isReducedMongoInstrumentationEnabled =
        reduceMongoInstrumentation == null || !reduceMongoInstrumentation.equalsIgnoreCase("false");

    if (isReducedMongoInstrumentationEnabled) {

      LOGGER.finest("Filtering Mongo spans");

      // Define attribute keys
      AttributeKey<String> dbSystemKey = AttributeKey.stringKey("db.system");
      AttributeKey<String> dbOperationKey = AttributeKey.stringKey("db.operation");

      samplerBuilder.customize(
          dbSystemKey,
          "mongodb",
          RuleBasedRoutingSampler.builder(SpanKind.CLIENT, defaultSampler)
              .drop(dbOperationKey, "isMaster")
              .build());
    }

    return samplerBuilder.build();
  }
}
