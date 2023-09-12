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
package io.lumigo.javaagent.instrumentation.servlet.v3_0;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharBufferHolder;
import io.lumigo.instrumentation.core.SpanAndRelatedObjectHolder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.io.BufferedReader;
import java.io.PrintWriter;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public final class VirtualFieldUtil {
  private VirtualFieldUtil() {}

  public static void handleRequestBody(
      ServletRequest servletRequest,
      VirtualField<ServletRequest, SpanAndRelatedObjectHolder> requestVirtualField,
      VirtualField<ServletInputStream, ByteBufferHolder> streamVirtualField,
      VirtualField<BufferedReader, CharBufferHolder> readerVirtualField) {

    SpanAndRelatedObjectHolder requestHolder = requestVirtualField.get(servletRequest);
    requestVirtualField.set(servletRequest, null);
    if (requestHolder == null) {
      return;
    }

    // Capture request body
    if (requestHolder.getRelatedObject() instanceof ServletInputStream) {
      ServletInputStream inputStream = (ServletInputStream) requestHolder.getRelatedObject();
      ByteBufferHolder requestBodyBuffer = streamVirtualField.get(inputStream);
      streamVirtualField.set(inputStream, null);
      if (requestBodyBuffer != null) {
        requestBodyBuffer.captureRequestBody();
      }
    } else if (requestHolder.getRelatedObject() instanceof BufferedReader) {
      BufferedReader reader = (BufferedReader) requestHolder.getRelatedObject();
      CharBufferHolder requestBuffer = readerVirtualField.get(reader);
      readerVirtualField.set(reader, null);
      if (requestBuffer != null) {
        requestBuffer.captureRequestBody();
      }
    }
  }

  public static void handleResponseBody(
      Span currentSpan,
      ServletResponse response,
      VirtualField<ServletResponse, SpanAndRelatedObjectHolder> responseVirtualField,
      VirtualField<ServletOutputStream, ByteBufferHolder> streamVirtualField,
      VirtualField<PrintWriter, CharBufferHolder> writerVirtualField) {
    SpanAndRelatedObjectHolder responseHolder = responseVirtualField.get(response);
    responseVirtualField.set(response, null);
    if (responseHolder == null) {
      return;
    }

    // Capture response body
    if (responseHolder.getRelatedObject() instanceof ServletOutputStream) {
      ServletOutputStream outputStream = (ServletOutputStream) responseHolder.getRelatedObject();
      ByteBufferHolder responseBodyBuffer = streamVirtualField.get(outputStream);
      streamVirtualField.set(outputStream, null);
      if (responseBodyBuffer != null) {
        responseBodyBuffer.captureResponseBody(currentSpan);
      }
    } else if (responseHolder.getRelatedObject() instanceof PrintWriter) {
      PrintWriter writer = (PrintWriter) responseHolder.getRelatedObject();
      CharBufferHolder responseBuffer = writerVirtualField.get(writer);
      writerVirtualField.set(writer, null);
      if (responseBuffer != null) {
        responseBuffer.captureResponseBody(currentSpan);
      }
    }
  }
}
