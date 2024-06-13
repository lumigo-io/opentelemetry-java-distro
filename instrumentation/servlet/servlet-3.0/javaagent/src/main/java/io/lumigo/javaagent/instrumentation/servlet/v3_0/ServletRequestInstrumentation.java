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
import java.io.BufferedReader;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServletRequestInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.servlet.ServletRequest");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("javax.servlet.ServletRequest"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getInputStream")
            .and(takesArguments(0))
            .and(returns(named("javax.servlet.ServletInputStream")))
            .and(isPublic()),
        ServletRequestInstrumentation.class.getName() + "$GetInputStreamAdvice");

    transformer.applyAdviceToMethod(
        named("getReader")
            .and(takesArguments(0))
            .and(returns(named("java.io.BufferedReader")))
            .and(isPublic()),
        ServletRequestInstrumentation.class.getName() + "$GetReaderAdvice");
  }

  public static class GetInputStreamAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ServletRequest thiz,
        @Advice.Local("lumigoSpanHolder") SpanAndRelatedObjectHolder spanHolder,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ServletRequest.class);
      callDepth.getAndIncrement();

      spanHolder =
          VirtualField.find(ServletRequest.class, SpanAndRelatedObjectHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This ServletRequest thiz,
        @Advice.Return ServletInputStream servletInputStream,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("lumigoSpanHolder") SpanAndRelatedObjectHolder spanHolder,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      // Retrieve Jetty check
      VirtualField<ServletRequest, Boolean> jettyCheckVirtual =
          VirtualField.find(ServletRequest.class, Boolean.class);
      Boolean jettyCheck = jettyCheckVirtual.get(thiz);
      jettyCheckVirtual.set(thiz, null);

      callDepth = CallDepth.forClass(ServletRequest.class);
      if (callDepth.decrementAndGet() > 0 && Boolean.FALSE.equals(jettyCheck)) {
        return;
      }

      if (spanHolder == null) {
        return;
      }

      if (throwable != null) {
        return;
      }

      VirtualField<ServletInputStream, ByteBufferHolder> contextStore =
          VirtualField.find(ServletInputStream.class, ByteBufferHolder.class);
      if (contextStore.get(servletInputStream) != null) {
        // Prevent re-creation on multiple calls
        return;
      }

      contextStore.set(
          servletInputStream, HolderFactory.createByteBufferHolder(thiz, spanHolder.getSpan()));
      // Set the ServletInputStream for retrieval through the ServletRequest later
      spanHolder.setRelatedObject(servletInputStream);
    }
  }

  public static class GetReaderAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.This ServletRequest thiz,
        @Advice.Local("lumigoSpanHolder") SpanAndRelatedObjectHolder spanHolder,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ServletRequest.class);
      callDepth.getAndIncrement();

      spanHolder =
          VirtualField.find(ServletRequest.class, SpanAndRelatedObjectHolder.class).get(thiz);

      // Jetty implements getReader() by wrapping getInputStream() in an InputStreamReader
      // To enable instrumentation of the InputStream, we need to set a flag indicating to
      // not skip it due to the call depth
      // changed from thiz.getClass() to declaringClass because muzzle check plugin didn't allow it.
      // and won't inject the instrumentation.
      VirtualField.find(ServletRequest.class, Boolean.class)
          .set(thiz, declaringClass.getName().contains("jetty"));
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This ServletRequest thiz,
        @Advice.Return BufferedReader reader,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("lumigoSpanHolder") SpanAndRelatedObjectHolder spanHolder,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ServletRequest.class);
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (spanHolder == null) {
        return;
      }

      if (throwable != null) {
        return;
      }

      VirtualField<BufferedReader, CharBufferHolder> contextStore =
          VirtualField.find(BufferedReader.class, CharBufferHolder.class);
      if (contextStore.get(reader) != null) {
        // Prevent re-creation on multiple calls
        return;
      }

      contextStore.set(reader, HolderFactory.createCharBufferHolder(thiz, spanHolder.getSpan()));
      // Set the BufferedReader for retrieval through the ServletRequest later
      spanHolder.setRelatedObject(reader);
    }
  }
}
