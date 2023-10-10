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
package io.lumigo.javaagent.instrumentation.grpc.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ClientCall;
import io.lumigo.instrumentation.core.SemanticAttributes;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ClientCallListenerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.grpc.ClientCall");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(nameContainsIgnoreCase(
            "TracingClientInterceptor$TracingClientCall$TracingClientCallListener"))
        .and(extendsClass(named("io.grpc.ClientCall$Listener")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), ClientCallListenerInstrumentation.class.getName() + "$ConstructAdvice");
    transformer.applyAdviceToMethod(
        named("onMessage").and(isPublic()).and(not(isAbstract())).and(takesArguments(1)),
        ClientCallListenerInstrumentation.class.getName() + "$OnMessageAdvice");
    transformer.applyAdviceToMethod(
        named("onClose").and(isPublic()).and(not(isAbstract())),
        ClientCallListenerInstrumentation.class.getName() + "$OnCloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ClientCall.Listener.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.This ClientCall.Listener<?> listener,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() != 0) {
        return;
      }
      VirtualField<ClientCall.Listener<?>, StringListHolder> virtualField =
          VirtualField.find(ClientCall.Listener.class, StringListHolder.class);
      virtualField.set(listener, new StringListHolder(new ArrayList<>()));
    }
  }

  @SuppressWarnings("unused")
  public static class OnMessageAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ClientCall.Listener<?> listener,
        @Advice.Argument(0) Object msg,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ClientCall.Listener.class);
      if (callDepth.getAndIncrement() != 0) {
        return;
      }

      if (msg instanceof GeneratedMessageV3) {
        List<String> responseMsgs =
            VirtualField.find(ClientCall.Listener.class, StringListHolder.class)
                .get(listener)
                .getStringList();
        try {
          responseMsgs.add(
              JsonFormat.printer()
                  .omittingInsignificantWhitespace()
                  .print((GeneratedMessageV3) msg));
          Java8BytecodeBridge.currentSpan()
              .setAttribute(SemanticAttributes.GRPC_RESPONSE_BODY, JsonUtil.toJson(responseMsgs));
        } catch (InvalidProtocolBufferException e) {
          // At this point we know that msg is a GeneratedMessageV3, so this should never happen
          Java8BytecodeBridge.currentSpan()
              .setAttribute(SemanticAttributes.GRPC_RESPONSE_BODY, msg.toString());
        }
      } else {
        Java8BytecodeBridge.currentSpan()
            .setAttribute(SemanticAttributes.GRPC_RESPONSE_BODY, msg.toString());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("unused")
  public static class OnCloseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This ClientCall.Listener<?> listener) {
      VirtualField<ClientCall.Listener<?>, StringListHolder> virtualField =
          VirtualField.find(ClientCall.Listener.class, StringListHolder.class);
      StringListHolder holder = virtualField.get(listener);
      if (holder == null) {
        return;
      }
      holder.getStringList().clear();
      virtualField.set(listener, null);
    }
  }
}
