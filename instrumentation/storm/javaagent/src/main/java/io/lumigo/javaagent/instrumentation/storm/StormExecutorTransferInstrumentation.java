/*
 * Copyright 2024 Lumigo LTD
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
package io.lumigo.javaagent.instrumentation.storm;

import static io.lumigo.javaagent.instrumentation.storm.StormExecutorSingleton.stormExecutorInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.storm.tuple.AddressedTuple;

public class StormExecutorTransferInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.storm.executor.ExecutorTransfer");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        named("tryTransfer"),
        io.lumigo.javaagent.instrumentation.storm.StormExecutorTransferInstrumentation.class
                .getName()
            + "$StormTryTransferAdvice");
  }

  @SuppressWarnings("unused")
  public static class StormTryTransferAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) AddressedTuple addressedTuple) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!stormExecutorInstrumenter().shouldStart(parentContext, addressedTuple)) {
        return;
      }
      System.out.println("StormExecutorAdvice.onEnter");

      // Make sure the parent span is active on the Context when you start the new span
      try (Scope scope = parentContext.makeCurrent()) {
        Context context = stormExecutorInstrumenter().start(parentContext, addressedTuple);
        final Span span = Java8BytecodeBridge.spanFromContext(context);
        span.setAttribute(
            "storm.executor.transferMessageId", addressedTuple.tuple.getMessageId().toString());
        stormExecutorInstrumenter().end(context, addressedTuple, null, null);
      }
    }
  }
}
