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
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
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
import java.io.IOException;
import java.io.InputStream;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class InputStreamInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.http.AmazonHttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    //    return extendsClass(named("java.io.InputStream"))
    //        .and(not(hasSuperType(named("javax.servlet.ServletInputStream"))));
    //    return
    // nameStartsWith("org.apache.http.impl.io.").and(hasSuperType(named("java.io.InputStream")));
    //    return namedOneOf(
    //        "org.apache.http.impl.io.ContentLengthInputStream",
    //        "org.apache.http.impl.io.ChunkedInputStream",
    //        "org.apache.http.impl.io.IdentityInputStream");
    return nameStartsWith("org.apache.http.impl.io.")
        .and(hasSuperType(named("java.io.InputStream")))
        .and(declaresField(named("in")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("read").and(takesArguments(0)).and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadAdvice");

    transformer.applyAdviceToMethod(
        named("read").and(takesArguments(1)).and(takesArgument(0, byte[].class)).and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadByteArrayAdvice");

    transformer.applyAdviceToMethod(
        named("read")
            .and(takesArguments(3))
            .and(takesArgument(0, byte[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadByteArrayWithOffsetAdvice");

    transformer.applyAdviceToMethod(
        named("readAllBytes").and(takesArguments(0)).and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadAllBytesAdvice");

    transformer.applyAdviceToMethod(
        named("readNBytes").and(takesArguments(1)).and(takesArgument(0, int.class)).and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadNBytesAdvice");

    transformer.applyAdviceToMethod(
        named("readNBytes")
            .and(takesArguments(3))
            .and(takesArgument(0, byte[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadByteArrayWithOffsetAdvice");

    transformer.applyAdviceToMethod(
        named("readLine")
            .and(takesArguments(3))
            .and(takesArgument(0, byte[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        InputStreamInstrumentation.class.getName() + "$ReadByteArrayWithOffsetAdvice");
  }

  public static class ReadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This InputStream thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(InputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(InputStream.class, ByteBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This InputStream thiz,
        @Advice.Return int read,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (read == -1) {
        // Buffer is complete
        HelperUtil.handleResponseSpan(bufferHolder);
        VirtualField.find(InputStream.class, ByteBufferHolder.class).set(thiz, null);
      } else {
        bufferHolder.append(read);
      }
    }
  }

  public static class ReadByteArrayAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This InputStream thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(InputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(InputStream.class, ByteBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This InputStream thiz,
        @Advice.Return int read,
        @Advice.Argument(0) byte[] b,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (read == -1) {
        // Buffer is complete
        HelperUtil.handleResponseSpan(bufferHolder);
      } else {
        bufferHolder.append(b, 0, read);
        try {
          if (thiz.available() == 0) {
            // Buffer is now complete
            bufferHolder.captureRequestBody();
            VirtualField.find(InputStream.class, ByteBufferHolder.class).set(thiz, null);
          }
        } catch (IOException e) {
          // Do nothing
        }
      }
    }
  }

  public static class ReadByteArrayWithOffsetAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This InputStream thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(InputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(InputStream.class, ByteBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This InputStream thiz,
        @Advice.Return int read,
        @Advice.Argument(0) byte[] b,
        @Advice.Argument(1) int off,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      if (read == -1) {
        // Buffer is complete
        HelperUtil.handleResponseSpan(bufferHolder);
        VirtualField.find(InputStream.class, ByteBufferHolder.class).set(thiz, null);
      } else {
        bufferHolder.append(b, off, read);
        try {
          if (thiz.available() == 0) {
            // Buffer is now complete
            HelperUtil.handleResponseSpan(bufferHolder);
            VirtualField.find(InputStream.class, ByteBufferHolder.class).set(thiz, null);
          }
        } catch (IOException e) {
          // Do nothing
        }
      }
    }
  }

  public static class ReadAllBytesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This InputStream thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(InputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(InputStream.class, ByteBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This InputStream thiz,
        @Advice.Return byte[] b,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(b);
      HelperUtil.handleResponseSpan(bufferHolder);
      VirtualField.find(InputStream.class, ByteBufferHolder.class).set(thiz, null);
    }
  }

  public static class ReadNBytesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This InputStream thiz,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      callDepth = CallDepth.forClass(InputStream.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      bufferHolder = VirtualField.find(InputStream.class, ByteBufferHolder.class).get(thiz);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This InputStream thiz,
        @Advice.Return byte[] b,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (bufferHolder == null) {
        return;
      }

      bufferHolder.append(b, 0, b.length);
      try {
        if (thiz.available() == 0) {
          // Buffer is now complete
          HelperUtil.handleResponseSpan(bufferHolder);
          VirtualField.find(InputStream.class, ByteBufferHolder.class).set(thiz, null);
        }
      } catch (IOException e) {
        // Do nothing
      }
    }
  }
}
