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
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lumigo.instrumentation.core.CharBufferHolder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.PrintWriter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation may get moved to a separate module if it's needed for other instrumentation
 * beyond servlet.
 */
public class PrintWriterInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("java.io.PrintWriter"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, int.class)).and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$WriteIntAdvice");

    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, char[].class)).and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$WriteCharArrayAdvice");

    transformer.applyAdviceToMethod(
        named("write")
            .and(takesArguments(3))
            .and(takesArgument(0, char[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$WriteCharArraySliceAdvice");

    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, String.class)).and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$WriteStringAdvice");

    transformer.applyAdviceToMethod(
        named("write")
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$WriteStringSliceAdvice");

    transformer.applyAdviceToMethod(
        named("print").and(takesArguments(1)).and(takesArgument(0, String.class)).and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$WriteStringAdvice");

    transformer.applyAdviceToMethod(
        named("println").and(takesArguments(0)).and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$PrintLnAdvice");

    transformer.applyAdviceToMethod(
        named("println").and(takesArguments(1)).and(takesArgument(0, String.class)).and(isPublic()),
        PrintWriterInstrumentation.class.getName() + "$PrintLnStringAdvice");
  }

  public static class WriteIntAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz,
        @Advice.Argument(value = 0) int c,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(c);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class WriteCharArrayAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz,
        @Advice.Argument(value = 0) char[] chars,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(chars);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class WriteCharArraySliceAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz,
        @Advice.Argument(value = 0) char[] chars,
        @Advice.Argument(value = 1) int offset,
        @Advice.Argument(value = 2) int length,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(chars, offset, length);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class WriteStringAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz,
        @Advice.Argument(value = 0) String str,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(str);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class WriteStringSliceAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz,
        @Advice.Argument(value = 0) String str,
        @Advice.Argument(value = 1) int offset,
        @Advice.Argument(value = 2) int length,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(str, offset, length);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class PrintLnAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz, @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(System.lineSeparator());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  public static class PrintLnStringAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This PrintWriter thiz,
        @Advice.Argument(value = 0) String str,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(PrintWriter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      CharBufferHolder bufferHolder =
          VirtualField.find(PrintWriter.class, CharBufferHolder.class).get(thiz);
      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(str);
      bufferHolder.append(System.lineSeparator());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
