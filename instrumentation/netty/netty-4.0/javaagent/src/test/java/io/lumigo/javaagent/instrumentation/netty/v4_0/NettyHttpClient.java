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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class NettyHttpClient {

  private final String host;
  private final int port;
  private final NioEventLoopGroup clientGroup;
  private FullHttpResponse response;

  public NettyHttpClient(String host, int port) {
    this.host = host;
    this.port = port;
    this.clientGroup = new NioEventLoopGroup();
  }

  public FullHttpResponse sendGetRequest() throws InterruptedException {
    return sendRequest(HttpMethod.GET, null);
  }

  public FullHttpResponse sendPostRequest(String content) throws InterruptedException {
    return sendRequest(HttpMethod.POST, content);
  }

  private FullHttpResponse sendRequest(HttpMethod method, String content)
      throws InterruptedException {
    ChannelFuture future = null;
    try {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap
          .group(clientGroup)
          .channel(NioSocketChannel.class)
          .handler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                  ch.pipeline().addLast(new HttpClientCodec());
                  ch.pipeline().addLast(new HttpObjectAggregator(8192));
                  ch.pipeline()
                      .addLast(
                          new SimpleChannelInboundHandler<FullHttpResponse>() {
                            @Override
                            protected void channelRead0(
                                ChannelHandlerContext ctx, FullHttpResponse msg) {
                              response = msg.retain(); // Retain a copy of the response
                              ctx.close();
                            }

                            @Override
                            public void exceptionCaught(
                                ChannelHandlerContext ctx, Throwable cause) {
                              cause.printStackTrace(); // Log the exception for debugging
                              ctx.close(); // Close the context on error to free resources
                            }
                          });
                }
              });

      future = bootstrap.connect(host, port).sync();
      System.out.println("Client connected to server at " + host + ":" + port);

      HttpRequest request;
      if (method == HttpMethod.POST && content != null) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        request =
            new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, method, "/", Unpooled.copiedBuffer(contentBytes));
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentBytes.length);
      } else {
        request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, "/");
      }
      request.headers().set(HttpHeaderNames.HOST, host);

      // Send the request and wait for the response
      future.channel().writeAndFlush(request).sync();

      // Set a timeout for awaiting channel closure
      if (!future.channel().closeFuture().await(30, TimeUnit.SECONDS)) {
        System.err.println("Channel did not close in 30 seconds, timing out.");
      }

      return response;
    } finally {
      if (future != null && future.channel().isOpen()) {
        future.channel().close();
      }
      clientGroup.shutdownGracefully();
    }
  }
}
