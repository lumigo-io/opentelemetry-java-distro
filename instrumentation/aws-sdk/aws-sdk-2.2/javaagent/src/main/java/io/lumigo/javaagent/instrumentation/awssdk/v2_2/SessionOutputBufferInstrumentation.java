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
package io.lumigo.javaagent.instrumentation.awssdk.v2_2;

import static io.lumigo.instrumentation.core.AbstractBufferHolder.MAX_BUFFER_LENGTH;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SessionOutputBufferInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.http.impl.io.SessionOutputBufferImpl")
        .and(hasClassesNamed("software.amazon.awssdk.http.apache.ApacheHttpClient"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.http.impl.io.SessionOutputBufferImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("streamWrite")).and(not(isAbstract())).and(takesArguments(3)),
        this.getClass().getName() + "$StreamWriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class StreamWriteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) byte[] buffer,
        @Advice.Argument(1) int off,
        @Advice.Argument(2) int len) {
      Context context = currentContext();
      int capturedBuffer = PayloadBridge.getRequestPayloadBufferSize(context);
      if (capturedBuffer == -1 || capturedBuffer >= MAX_BUFFER_LENGTH) {
        // Skip capturing more than 2KB of data
        return;
      }

      if (!PayloadBridge.isFirstRequestPayload(context)) {
        // Skip checking for headers when we've already captured body content
        PayloadBridge.appendRequestPayload(context, buffer, -1, len);
        return;
      }

      int bodyStartPos = -1;
      int capturedLength = -1;
      // The HTTP body begins after the first two consecutive set of '\r\n' characters in the
      // request
      for (int i = 0;
          // We check only if we have at least four characters left, otherwise there is not
          // enough data in the buffer to contain an HTTP body anyhow.
          i < len - 3;
          i++
      ) {

        if (i - bodyStartPos >= MAX_BUFFER_LENGTH) {
          capturedLength = i - bodyStartPos;
          // Skip capturing more than 2KB of data
          break;
        }

        if ((char) buffer[i] == '\r' && (char) buffer[i + 1] == '\n' && (char) buffer[i + 2] == '\r' && (char) buffer[i + 3] == '\n') {
          // We found the end of the headers segment :-)
          bodyStartPos = i + 4;
          break;
        }
      }

      PayloadBridge.appendRequestPayload(context, buffer, bodyStartPos, capturedLength != -1 ? capturedLength : len);
    }
  }
}
