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
package io.lumigo.javaagent.instrumentation.storm;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class StormSingleton {
  private static final String INSTRUMENTATION_NAME = "io.lumigo.storm";

  private static final Instrumenter<Object, Object> STORM_INSTRUMENTER;

  static {
    STORM_INSTRUMENTER =
        Instrumenter.builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, (request) -> "storm span")
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<Object, Object> stormInstrumenter() {
    return STORM_INSTRUMENTER;
  }

  private StormSingleton() {}
}
