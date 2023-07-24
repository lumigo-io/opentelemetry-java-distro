/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.httpclient.ResponsePayloadBridge;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaHttpClientInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lumigo-java-http-client";

  public static Instrumenter<HttpRequest, HttpResponse<?>> createInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders,
      List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>>
          additionalExtractors) {
    io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter httpAttributesGetter = io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>
        httpAttributesExtractorBuilder =
            HttpClientAttributesExtractor.builder(
                httpAttributesGetter, new JavaHttpClientNetAttributesGetter());
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(capturedRequestHeaders);
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(capturedResponseHeaders);

    return Instrumenter.<HttpRequest, HttpResponse<?>>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(httpAttributesExtractorBuilder.build())
        .addAttributesExtractors(additionalExtractors)
        .addOperationMetrics(HttpClientMetrics.get())
        // Custom Context customizer for holding response payload
        .addContextCustomizer((context, request, attributes) -> new ResponsePayloadBridge.Builder().init(context))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private JavaHttpClientInstrumenterFactory() {}
}
