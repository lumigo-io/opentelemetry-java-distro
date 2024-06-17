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
import io.grpc.ServerCall;
import io.lumigo.instrumentation.core.LumigoSemanticAttributes;
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

public class ServerCallInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.grpc.ServerCall");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ServerCall"))
        .and(
            not(
                named(
                    "io.opentelemetry.instrumentation.grpc.v1_6.TracingServerInterceptor.TracingServerCall")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendMessage").and(isPublic()).and(not(isAbstract())).and(takesArguments(1)),
        ServerCallInstrumentation.class.getName() + "$SendMessageAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(isPublic()).and(not(isAbstract())).and(takesArguments(2)),
        ServerCallInstrumentation.class.getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendMessageAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ServerCall<?, ?> serverCall,
        @Advice.Argument(0) Object msg,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ServerCall.class);
      if (callDepth.getAndIncrement() != 0) {
        return;
      }

      if (msg instanceof GeneratedMessageV3) {
        VirtualField<ServerCall<?, ?>, StringListHolder> responseMsgsVirtual =
            VirtualField.find(ServerCall.class, StringListHolder.class);
        if (responseMsgsVirtual.get(serverCall) == null) {
          responseMsgsVirtual.set(serverCall, new StringListHolder(new ArrayList<>()));
        }

        List<String> responseMsgs = responseMsgsVirtual.get(serverCall).getStringList();
        try {
          responseMsgs.add(
              JsonFormat.printer()
                  .omittingInsignificantWhitespace()
                  .print((GeneratedMessageV3) msg));
          Java8BytecodeBridge.currentSpan()
              .setAttribute(LumigoSemanticAttributes.GRPC_RESPONSE_BODY, JsonUtil.toJson(responseMsgs));
        } catch (InvalidProtocolBufferException e) {
          // At this point we know that msg is a GeneratedMessageV3, so this should never happen
          Java8BytecodeBridge.currentSpan()
              .setAttribute(LumigoSemanticAttributes.GRPC_RESPONSE_BODY, msg.toString());
        }
      } else {
        Java8BytecodeBridge.currentSpan()
            .setAttribute(LumigoSemanticAttributes.GRPC_RESPONSE_BODY, msg.toString());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This ServerCall<?, ?> serverCall) {
      VirtualField<ServerCall<?, ?>, StringListHolder> virtualField =
          VirtualField.find(ServerCall.class, StringListHolder.class);
      StringListHolder holder = virtualField.get(serverCall);
      if (holder == null) {
        return;
      }
      holder.getStringList().clear();
      virtualField.set(serverCall, null);
    }
  }
}
