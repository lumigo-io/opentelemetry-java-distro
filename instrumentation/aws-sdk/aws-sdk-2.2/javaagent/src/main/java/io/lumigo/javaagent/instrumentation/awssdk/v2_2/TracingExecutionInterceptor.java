/*
 * Copyright 2023 Lumigo LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.lumigo.javaagent.instrumentation.awssdk.v2_2;

import static io.lumigo.javaagent.instrumentation.awssdk.v2_2.ContextAttributeBridge.CONTEXT_ATTRIBUTE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * A {@link ExecutionInterceptor} for use as an SPI by the AWS SDK to automatically trace all
 * requests. This is a copy of the original class from the OpenTelemetry Java SDK, with the
 * customization for payload collection
 */
public class TracingExecutionInterceptor implements ExecutionInterceptor {
  static final String HTTP_REQUEST_BODY_KEY = "http.request.body";
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-span-attributes", false);

  private static final boolean USE_MESSAGING_PROPAGATOR =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);

  static final String HTTP_RESPONSE_BODY_KEY = "http.response.body";

  private final ExecutionInterceptor delegate =
      AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES)
          .setUseConfiguredPropagatorForMessaging(USE_MESSAGING_PROPAGATOR)
//          .setContextCustomizer(context -> new PayloadBridge.Builder().init(context))
          .build()
          .newExecutionInterceptor();

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    delegate.beforeExecution(context, executionAttributes);
  }

  @Override
  public SdkRequest modifyRequest(
      Context.ModifyRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyRequest(context, executionAttributes);
  }

  @Override
  public void beforeMarshalling(
      Context.BeforeMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeMarshalling(context, executionAttributes);
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterMarshalling(context, executionAttributes);
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpRequest(context, executionAttributes);
  }

  @Override
  public Optional<RequestBody> modifyHttpContent(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpContent(context, executionAttributes);
  }

  @Override
  public Optional<AsyncRequestBody> modifyAsyncHttpContent(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpContent(context, executionAttributes);
  }

  @Override
  public void beforeTransmission(
      Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
    delegate.beforeTransmission(context, executionAttributes);
  }

  @Override
  public void afterTransmission(
      Context.AfterTransmission context, ExecutionAttributes executionAttributes) {
    delegate.afterTransmission(context, executionAttributes);
  }

  @Override
  public SdkHttpResponse modifyHttpResponse(
      Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponse(context, executionAttributes);
  }

  @Override
  public Optional<Publisher<ByteBuffer>> modifyAsyncHttpResponseContent(
      Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpResponseContent(context, executionAttributes);
  }

  @Override
  public Optional<InputStream> modifyHttpResponseContent(
      Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponseContent(context, executionAttributes);
  }

  @Override
  public void beforeUnmarshalling(
      Context.BeforeUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeUnmarshalling(context, executionAttributes);
  }

  @Override
  public void afterUnmarshalling(
      Context.AfterUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterUnmarshalling(context, executionAttributes);
  }

  @Override
  public SdkResponse modifyResponse(
      Context.ModifyResponse context, ExecutionAttributes executionAttributes) {

    io.opentelemetry.context.Context otelContext =
        executionAttributes.getAttribute(CONTEXT_ATTRIBUTE);

    if (null != otelContext) {
      Span span = Span.fromContext(otelContext);

      final String requestBody = PayloadBridge.getRequestPayload(otelContext);
      if (null != requestBody) {
        span.setAttribute(HTTP_REQUEST_BODY_KEY, requestBody);
      }

      String responseBody = PayloadBridge.getResponsePayload(otelContext);
      if (null != responseBody) {
        span.setAttribute(HTTP_RESPONSE_BODY_KEY, responseBody);
      }
    }

    return delegate.modifyResponse(context, executionAttributes);
  }

  @Override
  public void afterExecution(
      Context.AfterExecution context, ExecutionAttributes executionAttributes) {
    delegate.afterExecution(context, executionAttributes);
  }

  @Override
  public Throwable modifyException(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    return delegate.modifyException(context, executionAttributes);
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    delegate.onExecutionFailure(context, executionAttributes);
  }
}
