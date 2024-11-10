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

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lumigo.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.AbstractNettyChannelPipelineInstrumentation;
import net.bytebuddy.asm.Advice;

public class NettyChannelPipelineInstrumentation
    extends AbstractNettyChannelPipelineInstrumentation {

  public static final String OPENTELEMETRY_HANDLER_CLASS_NAME =
      "io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler";
  public static final String LUMIGO_HANDLER_CLASS_NAME =
      "io.lumigo.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler";

  @Override
  public void transform(TypeTransformer transformer) {
    super.transform(transformer);

    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add").or(named("replace")))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, named("io.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAddAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAddAdvice {

    @Advice.OnMethodEnter
    public static void trackCallDepth(
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {}

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(1) String handlerName,
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {

      try {
        // Server pipeline handlers
        if (handler
            instanceof
            io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler) {

          ChannelHandler ourHandler = new HttpServerTracingHandler();
          // With Java 21 we have error from Muzzle:
          // Missing method io.netty.channel.ChannelHandler#getClass()Ljava/lang/Class;
          // This is probably, because the class is not loaded by the same classloader as the agent
          // so we can't use getClass() method on it.
          // This is a workaround to avoid the error, until we find a better solution.
          pipeline.addAfter(
              OPENTELEMETRY_HANDLER_CLASS_NAME, LUMIGO_HANDLER_CLASS_NAME, ourHandler);
        }

      } catch (IllegalArgumentException ignored) {

      }
    }
  }
}
