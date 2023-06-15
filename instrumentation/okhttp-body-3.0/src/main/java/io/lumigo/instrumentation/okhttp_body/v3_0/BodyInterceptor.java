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
package io.lumigo.instrumentation.okhttp_body.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import javax.annotation.Nullable;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public final class BodyInterceptor {
  // we're still using the interceptor on its own for now (as in the upstream interceptor)
  @SuppressWarnings("deprecation")
  public static final Interceptor TRACING_INTERCEPTOR =
      OkHttpTelemetry.builder(GlobalOpenTelemetry.get())
          .addAttributesExtractor(new BodyAttributesExtractor())
          .build()
          .newInterceptor();

  public static class BodyAttributesExtractor implements AttributesExtractor<Request, Response> {
    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, Request request) {
      attributes.put("http.request_body_content_type", request.body().contentType().toString());
      try {
        long length = request.body().contentLength();
        attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, length);
        if (length > 0) {
          final RequestBody body = request.newBuilder().build().body();
          final Buffer buffer = new Buffer();
          body.writeTo(buffer);
          attributes.put("http.request_body", buffer.readUtf8());
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Request request,
        @Nullable Response response,
        @Nullable Throwable error) {

      if (response == null || response.body() == null) {
        return;
      }

      attributes.put("http.response_body_content_type", response.body().contentType().toString());
      long length = response.body().contentLength();
      attributes.put(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, length);
      if (length > 0) {
        final ResponseBody body = response.newBuilder().build().body();
        try {
          attributes.put("http.response_body", body.string());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
