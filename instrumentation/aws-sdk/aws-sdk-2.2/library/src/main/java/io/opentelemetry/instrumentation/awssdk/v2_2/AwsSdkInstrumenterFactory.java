/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lumigo-aws-sdk-2.2";

  static final AttributesExtractor<ExecutionAttributes, SdkHttpResponse> rpcAttributesExtractor =
      RpcClientAttributesExtractor.create(AwsSdkRpcAttributesGetter.INSTANCE);
  private static final AwsSdkExperimentalAttributesExtractor experimentalAttributesExtractor =
      new AwsSdkExperimentalAttributesExtractor();

  static final AwsSdkHttpAttributesGetter httpAttributesGetter = new AwsSdkHttpAttributesGetter();
  private static final AwsSdkNetAttributesGetter netAttributesGetter =
      new AwsSdkNetAttributesGetter();
  static final AttributesExtractor<ExecutionAttributes, SdkHttpResponse> httpAttributesExtractor =
      HttpClientAttributesExtractor.create(httpAttributesGetter, netAttributesGetter);

  private static final AwsSdkSpanKindExtractor spanKindExtractor = new AwsSdkSpanKindExtractor();

  private static final List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>>
      defaultAttributesExtractors = Arrays.asList(rpcAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>>
      extendedAttributesExtractors =
          Arrays.asList(rpcAttributesExtractor, experimentalAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>>
      defaultConsumerAttributesExtractors =
          Arrays.asList(rpcAttributesExtractor, httpAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>>
      extendedConsumerAttributesExtractors =
          Arrays.asList(
              rpcAttributesExtractor, httpAttributesExtractor, experimentalAttributesExtractor);

  static Instrumenter<ExecutionAttributes, SdkHttpResponse> requestInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes,
      Function<Context, Context> contextCustomizer) {

    return createInstrumenter(
        openTelemetry,
        captureExperimentalSpanAttributes
            ? extendedAttributesExtractors
            : defaultAttributesExtractors,
        contextCustomizer,
        AwsSdkInstrumenterFactory.spanKindExtractor);
  }

  static Instrumenter<ExecutionAttributes, SdkHttpResponse> consumerInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {

    return createInstrumenter(
        openTelemetry,
        captureExperimentalSpanAttributes
            ? extendedConsumerAttributesExtractors
            : defaultConsumerAttributesExtractors,
        (context) -> context,
        SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<ExecutionAttributes, SdkHttpResponse> createInstrumenter(
      OpenTelemetry openTelemetry,
      List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>> extractors,
      Function<Context, Context> contextCustomizer,
      SpanKindExtractor<ExecutionAttributes> spanKindExtractor) {

    return Instrumenter.<ExecutionAttributes, SdkHttpResponse>builder(
            openTelemetry, INSTRUMENTATION_NAME, AwsSdkInstrumenterFactory::spanName)
        .addAttributesExtractors(extractors)
        .addContextCustomizer((context, request, attributes) -> contextCustomizer.apply(context))
        .buildInstrumenter(spanKindExtractor);
  }

  private static String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return awsServiceName + "." + awsOperation;
  }

  private AwsSdkInstrumenterFactory() {}
}
