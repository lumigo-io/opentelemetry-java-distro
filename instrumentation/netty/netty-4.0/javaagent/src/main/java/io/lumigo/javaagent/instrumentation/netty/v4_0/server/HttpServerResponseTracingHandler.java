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
package io.lumigo.javaagent.instrumentation.netty.v4_0.server;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import java.io.ByteArrayOutputStream;
import java.util.Deque;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context context = null;
//    Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
    System.out.println("HttpServerResponseTracingHandler.write");

//    if (context == null || !(msg instanceof HttpResponse)) {
//      ctx.write(msg, prm);
//      return;
//    }

    if (msg instanceof HttpResponse) {
      System.out.println("HttpServerResponseTracingHandler.write - HttpResponse");
    }

    if (msg instanceof HttpContent) {
      System.out.println("HttpServerResponseTracingHandler.write - HttpContent");

      HttpContent httpContent = (HttpContent) msg;
      ByteBuf content = httpContent.content();

      if (content.isReadable()) {
        System.out.println("HttpServerResponseTracingHandler.write - HttpContent - isReadable");

        ByteBuf copiedContent = content.copy();
        Span span;
        if (context != null) {
          System.out.println("HttpServerResponseTracingHandler.write - HttpContent - isReadable - context: " + context);
          span = Java8BytecodeBridge.spanFromContext(context);
        } else {
          span = Java8BytecodeBridge.currentSpan();
        }

        ByteBufferHolder bufferHolder =
            new ByteBufferHolder(new ByteArrayOutputStream(), span, "UTF-8");

        try {
          byte[] bytes = new byte[copiedContent.readableBytes()];
          copiedContent.readBytes(bytes);
          System.out.println(
              "HttpServerResponseTracingHandler.write - HttpContent - isReadable - bytes: "
                  + new String(bytes));
          bufferHolder.append(bytes);
          bufferHolder.captureResponseBody(span);
        } finally {
          copiedContent.release(); // Release the copied buffer
        }
      }
    }

    ctx.write(msg, prm);
  }
}
