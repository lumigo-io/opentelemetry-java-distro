package io.lumigo.javaagent.instrumentation.netty.v4_0.server;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import java.io.ByteArrayOutputStream;


public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {


  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

    System.out.println("HttpServerRequestTracingHandler.channelRead");

    if (msg instanceof HttpContent) {
      System.out.println("HttpServerRequestTracingHandler.channelRead - HttpContent");

      HttpContent httpContent = (HttpContent) msg;
      ByteBuf content = httpContent.content();

      if (content.isReadable()) {
        System.out.println(
            "HttpServerRequestTracingHandler.channelRead - HttpContent - isReadable");

        ByteBuf copiedContent = content.copy();
        Span span = Java8BytecodeBridge.currentSpan();
        ByteBufferHolder bufferHolder = new ByteBufferHolder(new ByteArrayOutputStream(), span,
            "UTF-8");
        try {

          byte[] bytes = new byte[copiedContent.readableBytes()];
          copiedContent.readBytes(bytes);
          System.out.println(
              "HttpServerRequestTracingHandler.channelRead - HttpContent - isReadable - bytes: "
                  + new String(bytes));

          bufferHolder.append(bytes);

          bufferHolder.captureRequestBody();

        } finally {
//          bufferHolder.release(); // Release the buffer
          copiedContent.release(); // Release the copied buffer
        }
      }
    }

    super.channelRead(ctx, msg);


  }
}
