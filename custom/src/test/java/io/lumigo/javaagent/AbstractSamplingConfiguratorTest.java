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

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSamplingConfiguratorTest {
  protected static void addPropertiesCustomizer(AutoConfiguredOpenTelemetrySdkBuilder builder) {
    builder.addPropertiesCustomizer(
        config -> {
          Map<String, String> overrides = new HashMap<>();
          overrides.put("otel.traces.exporter", "none");
          overrides.put("otel.metrics.exporter", "none");
          overrides.put("otel.logs.exporter", "none");
          return overrides;
        });
  }
}
