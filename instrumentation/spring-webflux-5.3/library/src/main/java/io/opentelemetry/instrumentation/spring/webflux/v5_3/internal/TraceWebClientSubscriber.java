/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;

/**
 * Based on Spring Sleuth's Reactor instrumentation.
 * https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientBeanPostProcessor.java
 */
final class TraceWebClientSubscriber implements CoreSubscriber<ClientResponse> {

  private final Instrumenter<ClientRequest, ClientResponse> instrumenter;
  private final ClientRequest request;
  private final CoreSubscriber<? super ClientResponse> actual;
  private final reactor.util.context.Context reactorContext;
  private final io.opentelemetry.context.Context otelClientContext;
  private final io.opentelemetry.context.Context otelParentContext;

  TraceWebClientSubscriber(
      Instrumenter<ClientRequest, ClientResponse> instrumenter,
      ClientRequest request,
      CoreSubscriber<? super ClientResponse> actual,
      Context otelClientContext,
      Context otelParentContext) {
    this.instrumenter = instrumenter;
    this.request = request;
    this.actual = actual;
    this.reactorContext = actual.currentContext();
    this.otelClientContext = otelClientContext;
    this.otelParentContext = otelParentContext;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    this.actual.onSubscribe(subscription);
  }

  @Override
  public void onNext(ClientResponse response) {
    instrumenter.end(otelClientContext, request, response, null);
    try (Scope ignored = otelParentContext.makeCurrent()) {
      this.actual.onNext(response);
    }
  }

  @Override
  public void onError(Throwable t) {
    instrumenter.end(otelClientContext, request, null, t);
    try (Scope ignored = otelParentContext.makeCurrent()) {
      this.actual.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = otelParentContext.makeCurrent()) {
      this.actual.onComplete();
    }
  }

  @Override
  public reactor.util.context.Context currentContext() {
    return this.reactorContext;
  }
}
