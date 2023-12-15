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
package io.lumigo.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.OutputStream;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class OutputStreamInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.http.AmazonHttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("org.apache.http.impl.io.")
        .and(hasSuperType(named("java.io.OutputStream")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write").and(isPublic()).and(takesArguments(1)).and(takesArgument(0, int.class)),
        OutputStreamInstrumentation.class.getName() + "$OutputStream_WriteIntAdvice");
    transformer.applyAdviceToMethod(
        named("write").and(isPublic()).and(takesArguments(1)).and(takesArgument(0, byte[].class)),
        OutputStreamInstrumentation.class.getName() + "$OutputStream_WriteByteArrayAdvice");
    transformer.applyAdviceToMethod(
        named("write")
            .and(isPublic())
            .and(takesArguments(3))
            .and(takesArgument(0, byte[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class)),
        OutputStreamInstrumentation.class.getName() + "$OutputStream_WriteByteArrayOffsetAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(isPublic()).and(takesArguments(0)),
        OutputStreamInstrumentation.class.getName() + "$OutputStream_CloseAdvice");
  }

  public static class OutputStream_WriteIntAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This OutputStream thiz,
        @Advice.Argument(0) int b,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(OutputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      ByteBufferHolder bufferHolder =
          VirtualField.find(OutputStream.class, ByteBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(b);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class OutputStream_WriteByteArrayAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This OutputStream thiz,
        @Advice.Argument(0) byte[] b,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(OutputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      ByteBufferHolder bufferHolder =
          VirtualField.find(OutputStream.class, ByteBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(b);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class OutputStream_WriteByteArrayOffsetAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This OutputStream thiz,
        @Advice.Argument(0) byte[] b,
        @Advice.Argument(1) int off,
        @Advice.Argument(2) int len,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(OutputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      ByteBufferHolder bufferHolder =
          VirtualField.find(OutputStream.class, ByteBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(b, off, len);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class OutputStream_CloseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This OutputStream thiz) {
      VirtualField<OutputStream, ByteBufferHolder> store =
          VirtualField.find(OutputStream.class, ByteBufferHolder.class);
      ByteBufferHolder bufferHolder = store.get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.captureRequestBody();
      store.set(thiz, null);
    }
  }
}
