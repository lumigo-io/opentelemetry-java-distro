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
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharBufferHolder;
import io.lumigo.instrumentation.core.SpanAndRelatedObjectHolder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Accessor;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons;
import java.io.BufferedReader;
import java.io.PrintWriter;
import javax.servlet.AsyncContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Servlet30AsyncInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.servlet.Servlet");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.servlet.ServletRequest"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("startAsync").and(returns(named("javax.servlet.AsyncContext"))).and(isPublic()),
        this.getClass().getName() + "$StartAsyncAdvice");
  }

  public static class StartAsyncAdvice {
    public static final String LUMIGO_SERVLET_3_ASYNC_LISTENER_PRESENCE =
        "io.lumigo.Servlet3AsyncListener";

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ServletRequest servletRequest,
        @Advice.Local("lumigoSpanHolder") SpanAndRelatedObjectHolder requestSpanHolder,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(AsyncContext.class);
      if (callDepth.getAndIncrement() == 1) {
        // We start at 1 because of the OTeL servlet instrumentation
        VirtualField<ServletRequest, SpanAndRelatedObjectHolder> requestVirtualField =
            VirtualField.find(ServletRequest.class, SpanAndRelatedObjectHolder.class);

        requestSpanHolder = requestVirtualField.get(servletRequest);
        requestVirtualField.set(servletRequest, null);
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This ServletRequest servletRequest,
        @Advice.Return AsyncContext asyncContext,
        @Advice.Local("lumigoSpanHolder") SpanAndRelatedObjectHolder requestSpanHolder,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      // We never reach zero as the OTeL Servlet instrumentation is also instrumenting this method
      // We want to run on our outermost layer, which is 1
      if (callDepth.decrementAndGet() != 1) {
        return;
      }

      if (asyncContext == null) {
        return;
      }

      if (!(servletRequest instanceof HttpServletRequest)) {
        return;
      }

      if (servletRequest.getAttribute(LUMIGO_SERVLET_3_ASYNC_LISTENER_PRESENCE) != null) {
        // We've already added our listener
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

      // Set the SpanHolder from methodEnter() of advice above
      // This is necessary to handle the case where the request class is a facade,
      // and the actual request object is not available in the async listener
      requestVirtualField.set(servletRequest, requestSpanHolder);

      Servlet3Accessor accessor = Servlet3Accessor.INSTANCE;
      accessor.addRequestAsyncListener(
          (HttpServletRequest) servletRequest,
          new AsyncResponseBodyListener(
              requestVirtualField,
              inputStreamVirtualField,
              readerVirtualField,
              responseVirtualField,
              outputStreamVirtualField,
              writerVirtualField,
              (HttpServletRequest) servletRequest),
          Servlet3Singletons.helper()
              .getAsyncListenerResponse((HttpServletRequest) servletRequest));
      accessor.setRequestAttribute(
          (HttpServletRequest) servletRequest, LUMIGO_SERVLET_3_ASYNC_LISTENER_PRESENCE, true);
    }
  }
}
