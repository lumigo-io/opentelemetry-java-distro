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
package io.lumigo.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lumigo.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class NettyChannelPipelineInstrumentation implements TypeInstrumentation {

  public static final String OPENTELEMETRY_RELOCATED_HANDLER_CLASS_NAME =
      "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler";
  public static final String LUMIGO_HANDLER_CLASS_NAME =
      "io.lumigo.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.channel.ChannelPipeline");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.netty.channel.ChannelPipeline"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add").or(named("replace")))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, named("io.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAddAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAddAdvice {

    private static final Logger logger = Logger.getLogger(ChannelPipelineAddAdvice.class.getName());

    @Advice.OnMethodEnter
    public static void trackCallDepth(
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      // With Java 21 we have error from Muzzle:
      // Missing method io.netty.channel.ChannelHandler#getClass()Ljava/lang/Class;
      // This is probably, because the class is not loaded by the same classloader as the agent.
      // so we can't use handler.getClass() method on it.
      // Similar to
      // io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyChannelPipelineInstrumentation.ChannelPipelineAddAdvice.trackCallDepth
      //      if (callDepth == null) {
      //        callDepth = CallDepth.forClass(ChannelPipeline.class);
      //        callDepth.getAndIncrement();
      //      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(1) String handlerName,
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {

      // Here we have to check if the call depth is greater than 1, because the handler is added
      // together with
      // io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyChannelPipelineInstrumentation.ChannelPipelineAddAdvice.addHandler
      //      if (callDepth.decrementAndGet() > 1) {
      //        return;
      //      }

      VirtualField<ChannelHandler, ChannelHandler> virtualField =
          VirtualField.find(ChannelHandler.class, ChannelHandler.class);

      try {
        // Server pipeline handlers
        if (Objects.equals(handlerName, OPENTELEMETRY_RELOCATED_HANDLER_CLASS_NAME)) {

          // if our handler is already attached, don't attach it again
          if (virtualField.get(handler) != null) {
            return;
          }

          ChannelHandler ourHandler = new HttpServerTracingHandler();
          // With Java 21 we have error from Muzzle:
          // Missing method io.netty.channel.ChannelHandler#getClass()Ljava/lang/Class;
          // This is probably, because the class is not loaded by the same classloader as the agent
          // so we can't use getClass() method on it.
          // This is a workaround to avoid the error, until we find a better solution.
          pipeline.addAfter(
              OPENTELEMETRY_RELOCATED_HANDLER_CLASS_NAME, LUMIGO_HANDLER_CLASS_NAME, ourHandler);

          // associate our handle with open telemetry handler so they could be removed together
          virtualField.set(handler, ourHandler);
        }

      } catch (IllegalArgumentException exception) {
        logger.log(Level.WARNING, "Failed to add tracing handler", exception);
      }
    }
  }
}
