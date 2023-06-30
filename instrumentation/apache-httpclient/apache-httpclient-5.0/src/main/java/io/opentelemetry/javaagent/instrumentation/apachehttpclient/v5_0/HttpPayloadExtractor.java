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
package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;

public class HttpPayloadExtractor implements AttributesExtractor<HttpRequest, HttpResponse> {
  private static final Logger LOGGER = Logger.getLogger(HttpPayloadExtractor.class.getName());

  private static final String HTTP_REQUEST_BODY_KEY = "http.request.body";
  static final String HTTP_RESPONSE_BODY_KEY = "http.response.body";

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, HttpRequest httpRequest) {
    ClassicHttpRequest classicHttpRequest;
    if (httpRequest instanceof ClassicHttpRequest) {
      classicHttpRequest = (ClassicHttpRequest) httpRequest;
    } else {
      LOGGER.warning("Instance of `ClassicHttpRequest` not found, unable to capture request payload");
      return;
    }

    HttpEntity entity = classicHttpRequest.getEntity();
    if (entity != null) {
      BufferedHttpEntity bufferedHttpEntity;
      try {
        bufferedHttpEntity = new BufferedHttpEntity(entity);
        classicHttpRequest.setEntity(bufferedHttpEntity);

        String requestBody =
            new BufferedReader(new InputStreamReader(bufferedHttpEntity.getContent()))
                .lines()
                .collect(Collectors.joining("\n"));
        attributes.put(HTTP_REQUEST_BODY_KEY, requestBody);
      } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Failed to capture HTTP Request payload", e);
      }
    } else {
      attributes.put(HTTP_REQUEST_BODY_KEY, "null");
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HttpRequest httpRequest,
      HttpResponse httpResponse,
      Throwable error) {
    Span currentSpan = Span.current();

    // Set the Content-Encoding header attribute as it's removed from the Response
    // by the Apache Http Client when it decompresses the payload
    if (ResponsePayloadBridge.isGzipped(context)) {
      currentSpan.setAttribute(AttributeKey.stringKey("http.response.header.content_encoding"), "gzip");
    }

    // Set the captured response payload onto a Span attribute
    currentSpan.setAttribute(HttpPayloadExtractor.HTTP_RESPONSE_BODY_KEY,
        ResponsePayloadBridge.getPayload(context));
  }
}
