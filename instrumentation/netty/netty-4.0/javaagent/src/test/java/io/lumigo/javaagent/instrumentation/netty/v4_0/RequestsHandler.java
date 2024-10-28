package io.lumigo.javaagent.instrumentation.netty.v4_0;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

public class RequestsHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
    System.out.println("Received request: " + request.getMethod() + " " + request.getUri());
    FullHttpResponse response;

    if (request.getMethod() == HttpMethod.GET) {
      System.out.println("Handling GET request");
      response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
          Unpooled.copiedBuffer("Hello, World!", StandardCharsets.UTF_8)
      );
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

    } else if (request.getMethod() == HttpMethod.POST) {
      System.out.println("Handling POST request");
      String requestBody = request.content().toString(StandardCharsets.UTF_8);
      response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
          Unpooled.copiedBuffer(requestBody, StandardCharsets.UTF_8)
      );
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

    } else {
      System.out.println("Unsupported method: " + request.getMethod());
      response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED,
          Unpooled.copiedBuffer("Method Not Allowed", StandardCharsets.UTF_8)
      );
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
    }

    // Write the response and close the connection
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    System.out.println("Connection opened: " + ctx.channel().remoteAddress());
    super.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    System.out.println("Connection closed: " + ctx.channel().remoteAddress());
    super.channelInactive(ctx);
  }

}

