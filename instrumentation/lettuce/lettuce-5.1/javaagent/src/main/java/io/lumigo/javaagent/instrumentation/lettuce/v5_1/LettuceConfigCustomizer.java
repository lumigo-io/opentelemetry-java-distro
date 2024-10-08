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

package io.lumigo.javaagent.instrumentation.lettuce.v5_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.HashMap;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class LettuceConfigCustomizer implements AutoConfigurationCustomizerProvider {
  private static final String LETTUCE_EXPERIMENTAL_ATTRIBUTE_KEY =
      "otel.instrumentation.lettuce.experimental-span-attributes";
  private static final String DB_STATEMENT_SANITIZER_KEY =
      "otel.instrumentation.common.db-statement-sanitizer.enabled";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesCustomizer(
        config -> {
          Map<String, String> overrides = new HashMap<>();

          // disable OTeL instrumentation for Lettuce
          overrides.put("otel.instrumentation.lettuce.enabled", "false");

          if (null == config.getBoolean(LETTUCE_EXPERIMENTAL_ATTRIBUTE_KEY)) {
            overrides.put(LETTUCE_EXPERIMENTAL_ATTRIBUTE_KEY, "true");
          }

          if (null == config.getBoolean(DB_STATEMENT_SANITIZER_KEY)) {
            overrides.put(DB_STATEMENT_SANITIZER_KEY, "false");
          }

          return overrides;
        });
  }
}
