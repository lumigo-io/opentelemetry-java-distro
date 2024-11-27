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
    boolean isReducedMongoInstrumentationEnabled;

    if (reduceMongoInstrumentation == null || reduceMongoInstrumentation.isEmpty()) {
      isReducedMongoInstrumentationEnabled = true; // Default to true
    } else if (reduceMongoInstrumentation.equalsIgnoreCase("true")) {
      isReducedMongoInstrumentationEnabled = true;
    } else if (reduceMongoInstrumentation.equalsIgnoreCase("false")) {
      isReducedMongoInstrumentationEnabled = false;
    } else {
      LOGGER.warning(
          "Invalid value for LUMIGO_REDUCED_MONGO_INSTRUMENTATION: "
              + reduceMongoInstrumentation
              + ". Defaulting to true.");
      isReducedMongoInstrumentationEnabled = true;
    }

    if (isReducedMongoInstrumentationEnabled) {

      // Log that Lumigo reduces Mongo instrumentation by omitting certain attributes, like
      // `db.operation`
      // For example, the `isMaster` operation is not collected by default to optimize performance.
      // Setting the environment variable `LUMIGO_REDUCED_MONGO_INSTRUMENTATION=false` will disable
      // this optimization.
      LOGGER.finest(
          "Lumigo reduces Mongo instrumentation. The `db.operation` attribute (e.g., `isMaster`) is excluded by default. Set `LUMIGO_REDUCED_MONGO_INSTRUMENTATION=false` to disable this behavior.");

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
