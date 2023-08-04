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
      int bodyStartPos = -1;
      // The HTTP body begins after the first two consecutive set of '\r\n' characters in the
      // request
      // We scan then two-by-two to reduce iterations. The body may start on an odd or
      // even character count, but this will be compensated by advancing i "just enough"
      // to go to the next candidate pair of characters.
      for (int i = 0;
          // We check only if we have at least two characters left, otherwise there is not
          // enough data in the buffer to contain an HTTP body anyhow.
          i < len - 1;
      // We manually increment the position in the loop, as increments could be by
      // one or by two depending on what we find.
      ) {

        if ((char) buffer[i] != '\r') {
          if ((char) buffer[i + 1] == '\r') {
            // The first character is not '\r', but the second is, and we will check
            // in the next iteration from there.
            i += 1;
          } else {
            // Neither the next nor the following character are '\r', we can skip both
            i += 2;
          }
        } else if ((char) buffer[i + 2] != '\r') {
          // The first character is a '\r', but the character two positions further is not,
          // so we likely found the beginning of the next header
          i += 3;
        } else if ((char) buffer[i + 1] == '\n' && (char) buffer[i + 3] == '\n') {
          // We found the end of the headers segment :-)
          bodyStartPos = i + 4;
          break;
        }
      }

      Context context = currentContext();
      PayloadBridge.appendRequestPayload(context, buffer, bodyStartPos, len);
    }
  }
}
