/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.httpclient.HttpPayloadExtractor;
import io.opentelemetry.javaagent.instrumentation.httpclient.ResponsePayloadBridge;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaHttpClientInstrumenterFactory {
  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.lumigo-java-http-client";

  public static Instrumenter<HttpRequest, HttpResponse<?>> createInstrumenter(
      OpenTelemetry openTelemetry,
      Consumer<HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>>
          extractorConfigurer,
      Consumer<HttpSpanNameExtractorBuilder<HttpRequest>> spanNameExtractorConfigurer,
      List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>> additionalExtractors,
      boolean emitExperimentalHttpClientMetrics) {

    JavaHttpClientAttributesGetter httpAttributesGetter = JavaHttpClientAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>
        httpAttributesExtractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(httpAttributesExtractorBuilder);

    HttpSpanNameExtractorBuilder<HttpRequest> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);
    spanNameExtractorConfigurer.accept(httpSpanNameExtractorBuilder);

    InstrumenterBuilder<HttpRequest, HttpResponse<?>> builder =
        Instrumenter.<HttpRequest, HttpResponse<?>>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addAttributesExtractor(new HttpPayloadExtractor())
            .addContextCustomizer((context, request, attributes) -> new ResponsePayloadBridge.Builder().init(context))
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private JavaHttpClientInstrumenterFactory() {}
}
