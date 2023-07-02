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
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.nio.ByteBuffer;

public class Http1BodySubscriberInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(nameStartsWith("jdk.internal."))
        .and(extendsClass(nameEndsWith("net.http.Http1Exchange$Http1BodySubscriber")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("onNext")),
        Http1BodySubscriberInstrumentation.class.getName() + "$RequestPayloadAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("onComplete")),
        Http1BodySubscriberInstrumentation.class.getName() + "$RequestPayloadCompleteAdvice");
  }

  public static class RequestPayloadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Argument(0) ByteBuffer item) {
      // Capture ByteBuffer and store in bridge
      RequestPayloadBridge.appendPayload(currentContext(), item);
    }

  }

  public static class RequestPayloadCompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter() {
      // Tell bridge the request body is complete
      RequestPayloadBridge.completePayload(currentContext());
    }
  }
}
