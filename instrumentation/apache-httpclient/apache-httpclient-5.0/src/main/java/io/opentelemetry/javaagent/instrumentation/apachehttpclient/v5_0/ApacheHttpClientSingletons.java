/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lumigo-apache-httpclient-5.0";

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    ApacheHttpClientHttpAttributesGetter httpAttributesGetter =
        new ApacheHttpClientHttpAttributesGetter();

    InstrumenterBuilder<HttpRequest, HttpResponse> builder =
        Instrumenter.<HttpRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    httpAttributesGetter, CommonConfig.get().getPeerServiceMapping()))
            // Custom HTTP payload extractor
            .addAttributesExtractor(new HttpPayloadExtractor())
            // Custom Context customizer for holding response payload
            .addContextCustomizer((context, request, attributes) -> new ResponsePayloadBridge.Builder().init(context))
            .addOperationMetrics(HttpClientMetrics.get());
    if (CommonConfig.get().shouldEmitExperimentalHttpClientMetrics()) {
      builder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpClientSingletons() {}
}
