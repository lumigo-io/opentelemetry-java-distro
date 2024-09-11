/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import java.net.http.*;

import io.opentelemetry.api.*;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.*;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JavaHttpClientInstrumenterBuilderFactory {
  private JavaHttpClientInstrumenterBuilderFactory() {}

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lumigo-java-http-client";

  public static DefaultHttpClientInstrumenterBuilder<HttpRequest, HttpResponse<?>> create(
      OpenTelemetry openTelemetry) {
    return new DefaultHttpClientInstrumenterBuilder<>(
        INSTRUMENTATION_NAME, openTelemetry, JavaHttpClientAttributesGetter.INSTANCE);
  }
}
