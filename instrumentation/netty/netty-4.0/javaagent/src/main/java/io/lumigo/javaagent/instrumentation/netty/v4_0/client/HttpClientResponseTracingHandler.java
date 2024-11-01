package io.lumigo.javaagent.instrumentation.netty.v4_0.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {


  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

    System.out.println("HttpClientResponseTracingHandler.channelRead");

    if (msg instanceof HttpContent) {
      System.out.println("HttpClientResponseTracingHandler.channelRead - HttpContent");

    }

    super.channelRead(ctx, msg);
  }
}
