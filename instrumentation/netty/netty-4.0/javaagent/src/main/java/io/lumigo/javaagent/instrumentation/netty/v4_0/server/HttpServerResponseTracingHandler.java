package io.lumigo.javaagent.instrumentation.netty.v4_0.server;


import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import java.io.ByteArrayOutputStream;


public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {


  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    System.out.println("HttpServerResponseTracingHandler.write");

    if (msg instanceof HttpResponse) {
      System.out.println("HttpServerResponseTracingHandler.write - HttpResponse");
    }

    if (msg instanceof HttpContent) {
      System.out.println("HttpServerResponseTracingHandler.write - HttpContent");

      HttpContent httpContent = (HttpContent) msg;
      ByteBuf content = httpContent.content();

      if (content.isReadable()) {
        System.out.println(
            "HttpServerResponseTracingHandler.write - HttpContent - isReadable");

        ByteBuf copiedContent = content.copy();
        Span span = Java8BytecodeBridge.currentSpan();
        ByteBufferHolder bufferHolder = new ByteBufferHolder(new ByteArrayOutputStream(), span,
            "UTF-8");

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
