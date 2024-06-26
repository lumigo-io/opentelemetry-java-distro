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
package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.HashMap;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class KafkaClientConfigCustomizer implements AutoConfigurationCustomizerProvider {
  private static final String KAFKA_EXPERIMENTAL_ATTRIBUTES_KEY =
      "otel.instrumentation.kafka.experimental-span-attributes";

  private static final String KAFKA_PRODUCER_PROPAGATION_ATTRIBUTE_KEY =
      "otel.instrumentation.kafka.producer-propagation.enabled";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesCustomizer(
        config -> {
          Map<String, String> overrides = new HashMap<>();

          // If not set by user, set to true
          if (null == config.getBoolean(KAFKA_EXPERIMENTAL_ATTRIBUTES_KEY)) {
            overrides.put(KAFKA_EXPERIMENTAL_ATTRIBUTES_KEY, "true");
          }
          if (null == config.getBoolean(KAFKA_PRODUCER_PROPAGATION_ATTRIBUTE_KEY)) {
            overrides.put(KAFKA_PRODUCER_PROPAGATION_ATTRIBUTE_KEY, "true");
          }

          return overrides;
        });
  }
}
