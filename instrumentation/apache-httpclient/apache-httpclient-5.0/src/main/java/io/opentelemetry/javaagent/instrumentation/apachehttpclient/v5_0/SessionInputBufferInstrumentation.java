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
package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.InputStream;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SessionInputBufferInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.hc.core5.http.impl.io.SessionInputBufferImpl");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hc.core5.http.impl.io.SessionInputBufferImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("fillBuffer"))
            .and(not(isAbstract()))
            .and(takesArguments(1))
            .and(takesArgument(0, named("java.io.InputStream"))),
        this.getClass().getName() + "$ResponsePayloadAdvice");

  }

  public static class ResponsePayloadAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) InputStream inputStream,
        @Advice.FieldValue(value = "buffer") byte[] buffer,
        @Advice.FieldValue(value = "bufferPos") int bufferPos,
        @Advice.FieldValue(value = "bufferLen") int bufferLen) {

      int bodyStartPos = -1;
      // The HTTP body begins after the first two consecutive set of '\r\n' characters in the request
      // We scan then two-by-two to reduce iterations. The body may start on an odd or
      // even character count, but this will be compensated by advancing i "just enough"
      // to go to the next candidate pair of characters.
      for (
          int i = bufferPos;
          // We check only if we have at least two characters left, otherwise there is not
          // enough data in the buffer to contain an HTTP body anyhow.
          i < bufferLen - 1;
          // We manually increment the position in the loop, as increments could be by
          // one or by two depending on what we find.
        ) {

        if ((char)buffer[i] != '\r') {
          if ((char)buffer[i+1] == '\r') {
            // The first character is not '\r', but the second is, and we will check
            // in the next iteration from there.
            i+=1;
          } else {
            // Neither the next nor the following character are '\r', we can skip both
            i+=2;
          }
        } else if ((char)buffer[i+2] != '\r') {
          // The first character is a '\r', but the character two positions further is not,
          // so we likely found the beginning of the next header
          i+=3;
        } else if ((char)buffer[i+1] == '\n' && (char)buffer[i+3] == '\n') {
          // We found the end of the headers segment :-)
          bodyStartPos = i + 4;
          break;
        }
      }

      Context context = currentContext();
      // Append the payload to the object on the Context which can be accessed by HttpPayloadExtractor
      // We always call this and delegate to appendPayload whether the body start was found,
      // because the second part of a chunked response will not have a body start to be found.
      ResponsePayloadBridge.appendPayload(context, buffer, bodyStartPos, bufferLen);
    }
  }
}
