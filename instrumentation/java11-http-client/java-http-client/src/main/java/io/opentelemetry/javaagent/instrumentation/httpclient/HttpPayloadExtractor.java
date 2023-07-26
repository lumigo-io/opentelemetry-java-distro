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
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

public class HttpPayloadExtractor implements AttributesExtractor<HttpRequest, HttpResponse<?>> {
  private static final Logger LOGGER = Logger.getLogger(HttpPayloadExtractor.class.getName());

  private static final String HTTP_REQUEST_BODY_KEY = "http.request.body";
  static final String HTTP_RESPONSE_BODY_KEY = "http.response.body";

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, HttpRequest httpRequest) {
    if (httpRequest.bodyPublisher().isEmpty()) {
      // No HTTP Request BodyPublisher present
      return;
    }

    HttpRequest.BodyPublisher bodyPublisher = httpRequest.bodyPublisher().get();
    if (bodyPublisher.contentLength() != 0) {
      // Subscribe to the HttpRequest.BodyPublisher and capture the content
      RequestPayloadSubscriber requestPayloadSubscriber = new RequestPayloadSubscriber(attributes);
      bodyPublisher.subscribe(requestPayloadSubscriber);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HttpRequest httpRequest,
      HttpResponse httpResponse,
      Throwable error) {

    // Set the captured response payload onto a Span attribute
    if (null != httpResponse) {
      final String content = ResponsePayloadBridge.getPayload(context);
      if (null != content) {
        attributes.put(HTTP_RESPONSE_BODY_KEY, content);
      }
    }
  }

  static class RequestPayloadSubscriber implements Flow.Subscriber<ByteBuffer> {
    private Flow.Subscription subscription;
    private ByteArrayOutputStream outputStream;
    private final AttributesBuilder attributes;

    public RequestPayloadSubscriber(AttributesBuilder attributes) {
      this.attributes = attributes;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      if (this.subscription == null) {
        this.subscription = subscription;
        this.outputStream = new ByteArrayOutputStream();
        subscription.request(Long.MAX_VALUE);
      } else {
        subscription.cancel();
      }
    }

    @Override
    public void onNext(ByteBuffer item) {
      byte[] bytes = new byte[item.remaining()];
      item.get(bytes);
      try {
        outputStream.write(bytes);
      } catch (IOException e) {
        LOGGER.severe("Failed to capture HTTP Request Body: " + e.getMessage());
      }
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
      if (outputStream.size() > 0) {
        attributes.put(HTTP_REQUEST_BODY_KEY, outputStream.toString(StandardCharsets.UTF_8));
      }

      this.subscription.cancel();
    }
  }
}
