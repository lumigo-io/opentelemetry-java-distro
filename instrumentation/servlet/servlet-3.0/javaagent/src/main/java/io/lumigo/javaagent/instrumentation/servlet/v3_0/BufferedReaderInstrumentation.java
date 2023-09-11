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

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.*;

import io.lumigo.instrumentation.core.CharBufferHolder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.BufferedReader;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation may get moved to a separate module if it's needed for other instrumentation
 * beyond servlet.
 */
public class BufferedReaderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("java.io.BufferedReader"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("read").and(takesArguments(0)).and(isPublic()),
        BufferedReaderInstrumentation.class.getName() + "$Reader_ReadAdvice");

    transformer.applyAdviceToMethod(
        named("read").and(takesArguments(1)).and(takesArgument(0, char[].class)).and(isPublic()),
        BufferedReaderInstrumentation.class.getName() + "$Reader_ReadCharArrayAdvice");

    transformer.applyAdviceToMethod(
        named("read")
            .and(takesArguments(3))
            .and(takesArgument(0, char[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        BufferedReaderInstrumentation.class.getName() + "$Reader_ReadByteArrayOffsetAdvice");

    transformer.applyAdviceToMethod(
        named("readLine").and(takesArguments(2)),
        BufferedReaderInstrumentation.class.getName() + "$BufferedReader_ReadLineAdvice");
  }

  public static class Reader_ReadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.This BufferedReader thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(BufferedReader.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(BufferedReader.class, CharBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exit(
        @Advice.This BufferedReader thiz,
        @Advice.Return int read,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (read == -1) {
        System.out.println("read() BEFORE captureRequestBody()");
        bufferHolder.captureRequestBody();
        System.out.println("read() AFTER captureRequestBody()");
      } else {
        bufferHolder.append(read);
      }
    }
  }

  public static class Reader_ReadCharArrayAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.This BufferedReader thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(BufferedReader.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(BufferedReader.class, CharBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exit(
        @Advice.This BufferedReader thiz,
        @Advice.Return int read,
        @Advice.Argument(0) char[] c,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (read == -1) {
        bufferHolder.captureRequestBody();
      } else {
        bufferHolder.append(c, 0, read);
      }
    }
  }

  public static class Reader_ReadByteArrayOffsetAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.This BufferedReader thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(BufferedReader.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(BufferedReader.class, CharBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exit(
        @Advice.This BufferedReader thiz,
        @Advice.Return int read,
        @Advice.Argument(0) char[] c,
        @Advice.Argument(1) int offset,
        @Advice.Argument(2) int length,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (read == -1) {
        bufferHolder.captureRequestBody();
      } else {
        bufferHolder.append(c, offset, read);
      }
    }
  }

  public static class BufferedReader_ReadLineAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.This BufferedReader thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(BufferedReader.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(BufferedReader.class, CharBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exit(
        @Advice.This BufferedReader thiz,
        @Advice.Return String line,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") CharBufferHolder bufferHolder) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (line == null) {
        bufferHolder.captureRequestBody();
      } else {
        bufferHolder.append(line);
      }
    }
  }
}
