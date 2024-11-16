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
package io.lumigo.javaagent.instrumentation.netty.v4_1.server;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import java.io.ByteArrayOutputStream;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    // Extract the context from the channel
    Context context = TracingHandlerUtils.extractContext(ctx);

    // Capture the response body
    if (msg instanceof HttpContent) {

      HttpContent httpContent = (HttpContent) msg;
      // Copy the content to avoid releasing the buffer
      ByteBuf content = httpContent.content();

      // Capture the response body
      if (content.isReadable()) {
        ByteBuf copiedContent = content.copy();
        Span span;
        if (context != null) {
          // Get the span from the context
          span = Java8BytecodeBridge.spanFromContext(context);
        } else {
          // otherwise, get the current span
          span = Java8BytecodeBridge.currentSpan();
        }
        ByteBufferHolder bufferHolder =
            new ByteBufferHolder(new ByteArrayOutputStream(), span, "UTF-8");

        try {
          byte[] bytes = new byte[copiedContent.readableBytes()];
          copiedContent.readBytes(bytes);

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
