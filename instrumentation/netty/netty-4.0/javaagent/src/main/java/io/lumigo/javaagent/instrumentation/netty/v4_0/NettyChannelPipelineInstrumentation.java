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
package io.lumigo.javaagent.instrumentation.netty.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lumigo.javaagent.instrumentation.netty.v4_0.client.HttpClientTracingHandler;
import io.lumigo.javaagent.instrumentation.netty.v4_0.server.HttpServerTracingHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpServerCodec;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.AbstractNettyChannelPipelineInstrumentation;
import net.bytebuddy.asm.Advice;

public class NettyChannelPipelineInstrumentation
    extends AbstractNettyChannelPipelineInstrumentation {

  public static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent.";
  public static final String INSTRUMENTATION_NETTY = "instrumentation.netty.";

  @Override
  public void transform(TypeTransformer transformer) {
    super.transform(transformer);

    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add").or(named("replace")))
            .and(takesArgument(2, named("io.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAddAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAddAdvice {

    @Advice.OnMethodEnter
    public static void trackCallDepth(
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {
      System.out.println("ChannelPipelineAddAdvice - OnMethodEnter - trackCallDepth");
//      callDepth = CallDepth.forClass(handler.getClass());
//      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth) {

      System.out.println(
          "ChannelPipelineAddAdvice - OnMethodExit - addHandler - " + handler.getClass().getName());

//      if (callDepth.decrementAndGet() > 0) {
//        System.out.println(
//            "ChannelPipelineAddAdvice - OnMethodExit - addHandler - callDepth.decrementAndGet() > 0");
//        return;
//      }

//      VirtualField<ChannelHandler, ChannelHandler> instrumentationHandlerField =
//          VirtualField.find(ChannelHandler.class, ChannelHandler.class);

//      // don't add another instrumentation handler if there already is one attached
//      if (instrumentationHandlerField.get(handler) != null) {
//        System.out.println(
//            "ChannelPipelineAddAdvice - OnMethodExit - addHandler - instrumentationHandlerField.get(handler) != null");
//        return;
//      }
      try {
        ChannelHandler ourHandler = null;
        // Server pipeline handlers
        if (handler != null
            && handler.getClass().getName()
            .startsWith(IO_OPENTELEMETRY_JAVAAGENT)
            && handler.getClass().getName()
            .contains(INSTRUMENTATION_NETTY)
            && handler.getClass().getSimpleName().equals("HttpServerTracingHandler")) {
          ourHandler = new HttpServerTracingHandler();
          System.out.println(
              "ChannelPipelineAddAdvice - OnMethodExit - addHandler - HttpServerTracingHandler - "
                  + handler.getClass().getName());

          pipeline.addAfter(handler.getClass().getName(), ourHandler.getClass().getName(),
              ourHandler);
          // Client pipeline handlers
        } else if (handler != null
            && handler.getClass().getName()
            .startsWith(IO_OPENTELEMETRY_JAVAAGENT)
            && handler.getClass().getName()
            .contains(INSTRUMENTATION_NETTY)
            && handler.getClass().getSimpleName().equals("HttpClientTracingHandler")) {
          System.out.println(
              "ChannelPipelineAddAdvice - OnMethodExit - addHandler - HttpClientTracingHandler - "
                  + handler.getClass().getName());
          ourHandler = new HttpClientTracingHandler();

          pipeline.addAfter(handler.getClass().getName(), ourHandler.getClass().getName(),
              ourHandler);
        }

//          pipeline.addLast(ourHandler.getClass().getName(), ourHandler);

        // associate our handle with original handler so they could be removed together
//          instrumentationHandlerField.set(handler, ourHandler);
      } catch (IllegalArgumentException e) {
        System.out.println(
            "ChannelPipelineAddAdvice - OnMethodExit - addHandler - IllegalArgumentException - "
                + e.getMessage());
        // Prevented adding duplicate handlers.
      }
    }
  }
}
