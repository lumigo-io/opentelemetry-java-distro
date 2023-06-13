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
package io.lumigo.instrumentation.apachehttpclient;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public class HttpPayloadExtractor implements AttributesExtractor<HttpRequest, HttpResponse> {
  private static final String HTTP_REQUEST_BODY_KEY = "http.request.body";
  private static final String HTTP_RESPONSE_BODY_KEY = "http.response.body";

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, HttpRequest httpRequest) {
    ClassicHttpRequest classicHttpRequest;
    if (httpRequest instanceof ClassicHttpRequest) {
      classicHttpRequest = (ClassicHttpRequest) httpRequest;
    } else {
      // TODO Log error
      return;
    }

    HttpEntity entity = classicHttpRequest.getEntity();
    if (entity.isRepeatable()) {
      try {
        String requestBody =
            new BufferedReader(new InputStreamReader(entity.getContent()))
                .lines()
                .collect(Collectors.joining("\n"));
        attributes.put(HTTP_REQUEST_BODY_KEY, requestBody);
      } catch (IOException e) {
        // TODO Log error
      }
    } else {
      // TODO Handle non repeatable entity
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HttpRequest httpRequest,
      HttpResponse httpResponse,
      Throwable error) {
    ClassicHttpResponse classicHttpResponse;
    if (httpResponse instanceof ClassicHttpResponse) {
      classicHttpResponse = (ClassicHttpResponse) httpResponse;
    } else {
      // TODO Log error
      return;
    }

    HttpEntity entity = classicHttpResponse.getEntity();
    if (entity.isRepeatable()) {
      try {
        String responseBody =
            new BufferedReader(new InputStreamReader(entity.getContent()))
                .lines()
                .collect(Collectors.joining("\n"));
        attributes.put(HTTP_RESPONSE_BODY_KEY, responseBody);
      } catch (IOException e) {
        // TODO Log error
      }
    } else {
      // TODO Handle non repeatable entity
    }
  }
}
