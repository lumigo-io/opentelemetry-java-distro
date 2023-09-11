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

import static io.lumigo.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.asyncResponseInstrumenter;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharBufferHolder;
import io.lumigo.instrumentation.core.SpanAndRelatedObjectHolder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAsyncListener;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AsyncResponseBodyListener implements ServletAsyncListener<HttpServletResponse> {
  private final Span span;
  private final VirtualField<ServletRequest, SpanAndRelatedObjectHolder> requestVirtualField;
  private final VirtualField<ServletInputStream, ByteBufferHolder> inputStreamVirtualField;
  private final VirtualField<BufferedReader, CharBufferHolder> readerVirtualField;
  private final VirtualField<ServletResponse, SpanAndRelatedObjectHolder> responseVirtualField;
  private final VirtualField<ServletOutputStream, ByteBufferHolder> outputStreamVirtualField;
  private final VirtualField<PrintWriter, CharBufferHolder> writerVirtualField;
  private final HttpServletRequest servletRequest;
  private final AtomicBoolean responseHandled = new AtomicBoolean(false);

  public AsyncResponseBodyListener(
      VirtualField<ServletRequest, SpanAndRelatedObjectHolder> requestVirtualField,
      VirtualField<ServletInputStream, ByteBufferHolder> inputStreamVirtualField,
      VirtualField<BufferedReader, CharBufferHolder> readerVirtualField,
      VirtualField<ServletResponse, SpanAndRelatedObjectHolder> responseVirtualField,
      VirtualField<ServletOutputStream, ByteBufferHolder> outputStreamVirtualField,
      VirtualField<PrintWriter, CharBufferHolder> writerVirtualField,
      HttpServletRequest servletRequest) {
    this.requestVirtualField = requestVirtualField;
    this.inputStreamVirtualField = inputStreamVirtualField;
    this.readerVirtualField = readerVirtualField;
    this.responseVirtualField = responseVirtualField;
    this.outputStreamVirtualField = outputStreamVirtualField;
    this.writerVirtualField = writerVirtualField;
    this.servletRequest = servletRequest;
    this.span = Span.fromContext(Servlet3Singletons.helper().getServerContext(servletRequest));
  }

  @Override
  public void onComplete(HttpServletResponse httpServletResponse) {
    if (responseHandled.compareAndSet(false, true)) {
      setResponse(servletRequest, httpServletResponse);
    }
  }

  @Override
  public void onTimeout(long timeout) {
    // Do nothing
  }

  @Override
  public void onError(Throwable throwable, HttpServletResponse httpServletResponse) {
    if (responseHandled.compareAndSet(false, true)) {
      setResponse(servletRequest, httpServletResponse);
    }
  }

  private void setResponse(HttpServletRequest request, HttpServletResponse response) {
    if (response == null) {
      return;
    }

    Context childContext = null;

    if (!span.isRecording()) {
      // Start a child span to record request/response body
      childContext =
          asyncResponseInstrumenter()
              .start(Servlet3Singletons.helper().getServerContext(request), request);
    }

    // Capture response body
    VirtualFieldUtil.handleResponseBody(
        childContext != null ? Span.fromContext(childContext) : span,
        response,
        responseVirtualField,
        outputStreamVirtualField,
        writerVirtualField);

    // Capture request body
    VirtualFieldUtil.handleRequestBody(
        request, requestVirtualField, inputStreamVirtualField, readerVirtualField);

    if (childContext != null) {
      asyncResponseInstrumenter().end(childContext, request, response, null);
    }
  }
}
