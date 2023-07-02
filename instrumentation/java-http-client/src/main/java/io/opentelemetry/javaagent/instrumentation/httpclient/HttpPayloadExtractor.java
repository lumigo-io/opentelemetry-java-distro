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
package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

public class HttpPayloadExtractor implements AttributesExtractor<HttpRequest, HttpResponse<?>> {
  private static final Logger LOGGER = Logger.getLogger(HttpPayloadExtractor.class.getName());

  private static final String HTTP_REQUEST_BODY_KEY = "http.request.body";
  static final String HTTP_RESPONSE_BODY_KEY = "http.response.body";

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, HttpRequest httpRequest) {

    if (httpRequest.bodyPublisher().isPresent()) {
      HttpRequest.BodyPublisher bodyPublisher = httpRequest.bodyPublisher().get();
      if (bodyPublisher.contentLength() == 0) {
        // HTTP Request BodyPublisher has no content
        attributes.put(HTTP_REQUEST_BODY_KEY, "null");
      } else {
        // TODO: TRYING SOMETHING HERE
        Flow.Subscriber subscriber = new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {

          }

          @Override
          public void onNext(Object item) {

          }

          @Override
          public void onError(Throwable throwable) {

          }

          @Override
          public void onComplete() {

          }
        };
      }
    } else {
      // No HTTP Request BodyPublisher present
      attributes.put(HTTP_REQUEST_BODY_KEY, "null");
    }

    // HTTP Request BodyPublisher has content, either fixed length or a stream.
    // Request body content is captured by instrumentation on
    // java.net.http.Http1Exchange$Http1BodySubscriber.onNext()
    System.out.println("METHOD: onStart()");
    System.out.println("CONTEXT: " + currentContext());
    System.out.println("SPAN ID: " + Span.current().getSpanContext().getSpanId());
    System.out.println("PASSED CONTEXT: " + parentContext);
    System.out.println("SPAN ID FROM PASSED CONTEXT: " + Span.fromContext(parentContext).getSpanContext().getSpanId());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HttpRequest httpRequest,
      HttpResponse httpResponse,
      Throwable error) {
    Span currentSpan = Span.current();
    System.out.println("METHOD: onEnd()");
    System.out.println("CONTEXT: " + currentContext());
    System.out.println("SPAN ID: " + Span.current().getSpanContext().getSpanId());
    System.out.println("PASSED CONTEXT: " + context);
    System.out.println("SPAN ID FROM PASSED CONTEXT: " + Span.fromContext(context).getSpanContext().getSpanId());

    // Set the Content-Encoding header attribute as it's removed from the Response
    // by the Apache Http Client when it decompresses the payload
    // if (ResponsePayloadBridge.isGzipped(context)) {
    //   currentSpan.setAttribute(AttributeKey.stringKey("http.response.header.content_encoding"), "gzip");
    // }

    // Set the captured response payload onto a Span attribute
    // currentSpan.setAttribute(HttpPayloadExtractor.HTTP_RESPONSE_BODY_KEY,
    //     ResponsePayloadBridge.getPayload(context));
  }
}
