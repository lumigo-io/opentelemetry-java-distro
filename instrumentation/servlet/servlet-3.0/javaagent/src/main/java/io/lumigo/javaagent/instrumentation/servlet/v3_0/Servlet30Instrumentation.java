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

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharBufferHolder;
import io.lumigo.instrumentation.core.SpanAndRelatedObjectHolder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.BufferedReader;
import java.io.PrintWriter;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Servlet30Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.servlet.Servlet");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(namedOneOf("javax.servlet.Filter", "javax.servlet.Servlet"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("doFilter", "service")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        Servlet30Instrumentation.class.getName() + "$ServletAdvice");
  }

  public static class ServletAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) ServletRequest request,
        @Advice.Argument(value = 1) ServletResponse response,
        @Advice.Local("currentSpan") Span currentSpan,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {

      if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
        return;
      }

      callDepth = CallDepth.forClass(HolderFactory.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      currentSpan = Java8BytecodeBridge.currentSpan();

      // The Span is accessed within the ServletRequestInstrumentation advice
      VirtualField.find(ServletRequest.class, SpanAndRelatedObjectHolder.class)
          .set(request, new SpanAndRelatedObjectHolder(currentSpan));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(value = 0) ServletRequest request,
        @Advice.Argument(value = 1) ServletResponse response,
        @Advice.Local("currentSpan") Span currentSpan,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {

      if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
        return;
      }

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      // Gather request virtual fields
      VirtualField<ServletRequest, SpanAndRelatedObjectHolder> requestVirtualField =
          VirtualField.find(ServletRequest.class, SpanAndRelatedObjectHolder.class);
      VirtualField<ServletInputStream, ByteBufferHolder> inputStreamVirtualField =
          VirtualField.find(ServletInputStream.class, ByteBufferHolder.class);
      VirtualField<BufferedReader, CharBufferHolder> readerVirtualField =
          VirtualField.find(BufferedReader.class, CharBufferHolder.class);

      // Gather response virtual fields
      VirtualField<ServletResponse, SpanAndRelatedObjectHolder> responseVirtualField =
          VirtualField.find(ServletResponse.class, SpanAndRelatedObjectHolder.class);
      VirtualField<ServletOutputStream, ByteBufferHolder> outputStreamVirtualField =
          VirtualField.find(ServletOutputStream.class, ByteBufferHolder.class);
      VirtualField<PrintWriter, CharBufferHolder> writerVirtualField =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class);

      if (!request.isAsyncStarted()) {
        // Instrumenting async execution is handled by servlet listener

        // Capture response body
        VirtualFieldUtil.handleResponseBody(
            currentSpan,
            response,
            responseVirtualField,
            outputStreamVirtualField,
            writerVirtualField);

        // Capture request body
        VirtualFieldUtil.handleRequestBody(
            request, requestVirtualField, inputStreamVirtualField, readerVirtualField);
      }
    }
  }
}
