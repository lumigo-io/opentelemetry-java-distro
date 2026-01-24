/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientInstrumenterFactory;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    INSTRUMENTER =
        JavaHttpClientInstrumenterFactory.createInstrumenter(
            GlobalOpenTelemetry.get(),
            builder -> {},  // Use default configuration
            builder -> {},  // Use default span name configuration
            Collections.emptyList(),  // No additional extractors
            false);  // No experimental telemetry
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersSetter setter() {
    return SETTER;
  }

  private JavaHttpClientSingletons() {}
}
