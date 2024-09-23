/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientTracingFilter;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

/** Entrypoint for instrumenting Spring Webflux HTTP clients. */
public final class SpringWebfluxTelemetry {

  /**
   * Returns a new {@link SpringWebfluxTelemetry} configured with the given {@link OpenTelemetry}.
   */
  public static SpringWebfluxTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringWebfluxTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringWebfluxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxTelemetryBuilder(openTelemetry);
  }

  // We use ServerWebExchange (which holds both the request and response)
  // because we need it to get the HTTP route while instrumenting.
  private final Instrumenter<ServerWebExchange, ServerWebExchange> serverInstrumenter;
  private final Instrumenter<ClientRequest, ClientResponse> clientInstrumenter;
  private final ContextPropagators propagators;

  SpringWebfluxTelemetry(
      Instrumenter<ClientRequest, ClientResponse> clientInstrumenter,
      Instrumenter<ServerWebExchange, ServerWebExchange> serverInstrumenter,
      ContextPropagators propagators) {
    this.clientInstrumenter = clientInstrumenter;
    this.serverInstrumenter = serverInstrumenter;
    this.propagators = propagators;
  }

  public void addClientTracingFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    for (ExchangeFilterFunction filterFunction : exchangeFilterFunctions) {
      if (filterFunction instanceof WebClientTracingFilter) {
        return;
      }
    }
    exchangeFilterFunctions.add(new WebClientTracingFilter(clientInstrumenter, propagators));
  }

  public WebFilter createWebFilter() {
    return new TelemetryProducingWebFilter(serverInstrumenter);
  }

  public WebFilter createWebFilterAndRegisterReactorHook() {
    registerReactorHook();
    return this.createWebFilter();
  }

  private static void registerReactorHook() {
    ContextPropagationOperator.builder().build().registerOnEachOperator();
  }
}
