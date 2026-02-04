/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    CommonConfig config = new CommonConfig(AgentInstrumentationConfig.get());

    List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>> additionalExtractors =
        new ArrayList<>();
    additionalExtractors.add(
        HttpClientPeerServiceAttributesExtractor.create(
            JavaHttpClientAttributesGetter.INSTANCE, config.getPeerServiceResolver()));

    if (config.shouldEmitExperimentalHttpClientTelemetry()) {
      additionalExtractors.add(
          HttpExperimentalAttributesExtractor.create(JavaHttpClientAttributesGetter.INSTANCE));
    }

    INSTRUMENTER =
        JavaHttpClientInstrumenterFactory.createInstrumenter(
            GlobalOpenTelemetry.get(),
            builder -> builder
                .setCapturedRequestHeaders(config.getClientRequestHeaders())
                .setCapturedResponseHeaders(config.getClientResponseHeaders())
                .setKnownMethods(config.getKnownHttpRequestMethods()),
            builder -> builder.setKnownMethods(config.getKnownHttpRequestMethods()),
            additionalExtractors,
            config.shouldEmitExperimentalHttpClientTelemetry());
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersSetter setter() {
    return SETTER;
  }

  private JavaHttpClientSingletons() {}
}
