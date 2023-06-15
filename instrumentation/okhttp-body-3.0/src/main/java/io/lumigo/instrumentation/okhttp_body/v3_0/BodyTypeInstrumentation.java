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
package io.lumigo.instrumentation.okhttp_body.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.OkHttpClient;

public class BodyTypeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("okhttp3.OkHttpClient$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(OkHttpClient.Builder.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This OkHttpClient.Builder builder,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // No-args constructor is automatically called by constructors with args, but we only want to
      // run once from the constructor with args because that is where the dedupe needs to happen.
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (builder.interceptors().contains(BodyInterceptor.TRACING_INTERCEPTOR)) {
        return;
      }
      builder.addInterceptor(BodyInterceptor.TRACING_INTERCEPTOR);
    }
  }
}
