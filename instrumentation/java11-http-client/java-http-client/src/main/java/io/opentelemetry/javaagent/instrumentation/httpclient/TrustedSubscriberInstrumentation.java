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
package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.nio.ByteBuffer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TrustedSubscriberInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(nameStartsWith("jdk.internal."))
        .and(implementsInterface(nameEndsWith("net.http.ResponseSubscribers$TrustedSubscriber")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("onNext"))
            .and(takesArguments(1)),
      TrustedSubscriberInstrumentation.class.getName() + "$OnNextAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnNextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) List<ByteBuffer> items) {

      // Ensure we didn't receive an empty list
      if (null != items && !items.isEmpty()) {
        // Calculate the total size of the buffers
        int totalSize = 0;
        for (ByteBuffer item : items) {
          totalSize += item.remaining();
        }

        byte[] result = new byte[totalSize];
        int offset = 0;

        // Copy the buffers into the result, using a read only buffer to prevent modification
        // of the real underlying buffer.
        // This approach is taken as opposed to using position() to reset the underlying buffer
        for (ByteBuffer item : items) {
          int length = item.remaining();
          item.asReadOnlyBuffer().get(result, offset, length);
          offset += length;
        }

        final Context context = currentContext();
        ResponsePayloadBridge.appendPayload(context, result, totalSize);
      }
    }
  }
}
