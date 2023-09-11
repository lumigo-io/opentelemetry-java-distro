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
package io.lumigo.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public final class Servlet3Singletons {
  private static final String INSTRUMENTATION_NAME = "io.lumigo.servlet";

  static final String INTERNAL_SPAN_NAME = "async response payload";

  private static final Instrumenter<ServletRequest, ServletResponse> ASYNC_RESPONSE_INSTRUMENTER;

  static {
    ASYNC_RESPONSE_INSTRUMENTER =
        Instrumenter.<ServletRequest, ServletResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, (request) -> INTERNAL_SPAN_NAME)
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<ServletRequest, ServletResponse> asyncResponseInstrumenter() {
    return ASYNC_RESPONSE_INSTRUMENTER;
  }

  private Servlet3Singletons() {}
}
