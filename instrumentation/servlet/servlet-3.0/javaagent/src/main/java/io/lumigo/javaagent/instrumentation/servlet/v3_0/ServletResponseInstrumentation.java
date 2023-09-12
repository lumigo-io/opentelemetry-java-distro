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
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharBufferHolder;
import io.lumigo.instrumentation.core.SpanAndRelatedObjectHolder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.PrintWriter;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServletResponseInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.servlet.ServletResponse");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("javax.servlet.ServletResponse"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getOutputStream")
            .and(takesArguments(0))
            .and(returns(named("javax.servlet.ServletOutputStream")))
            .and(isPublic()),
        ServletResponseInstrumentation.class.getName() + "$OutputStreamAdvice");

    transformer.applyAdviceToMethod(
        named("getWriter")
            .and(takesArguments(0))
            .and(returns(named("java.io.PrintWriter")))
            .and(isPublic()),
        ServletResponseInstrumentation.class.getName() + "$WriterAdvice");
  }

  public static class OutputStreamAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ServletResponse servletResponse,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      if (servletResponse instanceof HttpServletResponseWrapper) {
        return;
      }

      callDepth = CallDepth.forClass(ServletResponse.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This ServletResponse thiz,
        @Advice.Thrown Throwable throwable,
        @Advice.Return ServletOutputStream servletOutputStream,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (throwable != null) {
        return;
      }

      VirtualField<ServletOutputStream, ByteBufferHolder> contextStore =
          VirtualField.find(ServletOutputStream.class, ByteBufferHolder.class);
      if (contextStore.get(servletOutputStream) != null) {
        // Prevent re-creation on multiple calls
        return;
      }

      contextStore.set(servletOutputStream, HolderFactory.createByteBufferHolder(thiz, null));

      // Though we're not using the span, create a holder to tie servlet response to the output
      // stream
      SpanAndRelatedObjectHolder holder = new SpanAndRelatedObjectHolder(null);
      holder.setRelatedObject(servletOutputStream);
      VirtualField.find(ServletResponse.class, SpanAndRelatedObjectHolder.class).set(thiz, holder);
    }
  }

  public static class WriterAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ServletResponse servletResponse,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      if (servletResponse instanceof HttpServletResponseWrapper) {
        return;
      }

      callDepth = CallDepth.forClass(ServletResponse.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This ServletResponse thiz,
        @Advice.Thrown Throwable throwable,
        @Advice.Return PrintWriter printWriter,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (throwable != null) {
        return;
      }

      VirtualField<PrintWriter, CharBufferHolder> contextStore =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class);
      if (contextStore.get(printWriter) != null) {
        // Prevent re-creation on multiple calls
        return;
      }

      contextStore.set(printWriter, HolderFactory.createCharBufferHolder(thiz, null));

      // Though we're not using the span, create a holder to tie servlet response to the print
      // writer
      SpanAndRelatedObjectHolder holder = new SpanAndRelatedObjectHolder(null);
      holder.setRelatedObject(printWriter);
      VirtualField.find(ServletResponse.class, SpanAndRelatedObjectHolder.class).set(thiz, holder);
    }
  }
}
