/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;

import com.rabbitmq.client.GetResponse;
import io.lumigo.javaagent.instrumentation.rabbitmq.GetResponseBodyAttributeExtractor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.ArrayList;
import java.util.List;

public final class RabbitSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.rabbitmq.experimental-span-attributes", false);
  private static final String instrumentationName = "io.opentelemetry.rabbitmq-2.7";
  private static final Instrumenter<ChannelAndMethod, Void> channelInstrumenter =
      createChannelInstrumenter();
  private static final Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter =
      createReceiveInstrumenter();
  private static final Instrumenter<DeliveryRequest, Void> deliverInstrumenter =
      createDeliverInstrumenter();
  static final ContextKey<RabbitChannelAndMethodHolder> CHANNEL_AND_METHOD_CONTEXT_KEY =
      ContextKey.named("opentelemetry-rabbitmq-channel-and-method-context-key");

  public static Instrumenter<ChannelAndMethod, Void> channelInstrumenter() {
    return channelInstrumenter;
  }

  public static Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter() {
    return receiveInstrumenter;
  }

  static Instrumenter<DeliveryRequest, Void> deliverInstrumenter() {
    return deliverInstrumenter;
  }

  private static Instrumenter<ChannelAndMethod, Void> createChannelInstrumenter() {
    return Instrumenter.<ChannelAndMethod, Void>builder(
            GlobalOpenTelemetry.get(), instrumentationName, ChannelAndMethod::getMethod)
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(
                RabbitChannelAttributesGetter.INSTANCE, MessageOperation.PUBLISH))
        .addAttributesExtractor(
            NetClientAttributesExtractor.create(new RabbitChannelNetAttributesGetter()))
        .addContextCustomizer(
            (context, request, startAttributes) ->
                context.with(CHANNEL_AND_METHOD_CONTEXT_KEY, new RabbitChannelAndMethodHolder()))
        .buildInstrumenter(
            channelAndMethod ->
                channelAndMethod.getMethod().equals("Channel.basicPublish") ? PRODUCER : CLIENT);
  }

  private static Instrumenter<ReceiveRequest, GetResponse> createReceiveInstrumenter() {
    List<AttributesExtractor<ReceiveRequest, GetResponse>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            RabbitReceiveAttributesGetter.INSTANCE, MessageOperation.RECEIVE));
    extractors.add(NetClientAttributesExtractor.create(new RabbitReceiveNetAttributesGetter()));
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitReceiveExperimentalAttributesExtractor());
    }
    // Custom Lumigo extractor
    extractors.add(new GetResponseBodyAttributeExtractor());

    return Instrumenter.<ReceiveRequest, GetResponse>builder(
            GlobalOpenTelemetry.get(), instrumentationName, ReceiveRequest::spanName)
        .addAttributesExtractors(extractors)
        .setEnabled(ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
        .addSpanLinksExtractor(
            new PropagatorBasedSpanLinksExtractor<>(
                GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
                ReceiveRequestTextMapGetter.INSTANCE))
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<DeliveryRequest, Void> createDeliverInstrumenter() {
    List<AttributesExtractor<DeliveryRequest, Void>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            RabbitDeliveryAttributesGetter.INSTANCE, MessageOperation.PROCESS));
    extractors.add(new RabbitDeliveryExtraAttributesExtractor());
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitDeliveryExperimentalAttributesExtractor());
    }

    return Instrumenter.<DeliveryRequest, Void>builder(
            GlobalOpenTelemetry.get(), instrumentationName, DeliveryRequest::spanName)
        .addAttributesExtractors(extractors)
        .buildConsumerInstrumenter(DeliveryRequestGetter.INSTANCE);
  }

  private static <T, V> AttributesExtractor<T, V> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, V> getter, MessageOperation operation) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
        .build();
  }

  private RabbitSingletons() {}
}
