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
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
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

public class ClientCallInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.grpc.ClientCall");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ClientCall"))
        .and(
            not(
                named(
                    "io.opentelemetry.instrumentation.grpc.v1_6.TracingClientInterceptor.TracingClientCall")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("start").and(isPublic()).and(not(isAbstract())).and(takesArguments(2)),
        ClientCallInstrumentation.class.getName() + "$StartAdvice");
    transformer.applyAdviceToMethod(
        named("sendMessage").and(isPublic()).and(not(isAbstract())).and(takesArguments(1)),
        ClientCallInstrumentation.class.getName() + "$SendMessageAdvice");
    transformer.applyAdviceToMethod(
        named("halfClose").or(named("cancel")).and(isPublic()).and(not(isAbstract())),
        ClientCallInstrumentation.class.getName() + "$CleanupAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ClientCall<?, ?> clientCall,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ClientCall.class);
      if (callDepth.getAndIncrement() != 0) {
        return;
      }

      VirtualField<ClientCall<?, ?>, StringListHolder> requestMsgs =
          VirtualField.find(ClientCall.class, StringListHolder.class);

      requestMsgs.set(clientCall, new StringListHolder(new ArrayList<>()));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("unused")
  public static class SendMessageAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ClientCall<?, ?> clientCall,
        @Advice.Argument(0) Object msg,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ClientCall.class);
      if (callDepth.getAndIncrement() != 0) {
        return;
      }

      if (msg instanceof GeneratedMessageV3) {
        List<String> requestMsgs =
            VirtualField.find(ClientCall.class, StringListHolder.class)
                .get(clientCall)
                .getStringList();

        try {
          requestMsgs.add(
              JsonFormat.printer()
                  .omittingInsignificantWhitespace()
                  .print((GeneratedMessageV3) msg));
          Java8BytecodeBridge.currentSpan()
              .setAttribute(SemanticAttributes.GRPC_REQUEST_BODY, JsonUtil.toJson(requestMsgs));
        } catch (InvalidProtocolBufferException e) {
          // At this point we know that msg is a GeneratedMessageV3, so this should never happen
          Java8BytecodeBridge.currentSpan()
              .setAttribute(SemanticAttributes.GRPC_REQUEST_BODY, msg.toString());
        }
      } else {
        Java8BytecodeBridge.currentSpan()
            .setAttribute(SemanticAttributes.GRPC_REQUEST_BODY, msg.toString());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("unused")
  public static class CleanupAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This ClientCall<?, ?> clientCall) {
      VirtualField<ClientCall<?, ?>, StringListHolder> virtualField =
          VirtualField.find(ClientCall.class, StringListHolder.class);
      StringListHolder holder = virtualField.get(clientCall);
      if (holder == null) {
        return;
      }
      holder.getStringList().clear();
      virtualField.set(clientCall, null);
    }
  }
}
