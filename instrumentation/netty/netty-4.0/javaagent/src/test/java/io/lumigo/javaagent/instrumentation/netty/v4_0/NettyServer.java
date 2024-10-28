package io.lumigo.javaagent.instrumentation.netty.v4_0;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class NettyServer {
  private final int port;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private ChannelFuture channelFuture;

  public NettyServer(int port) {
    this.port = port;
  }

  public void start() throws InterruptedException {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();

    ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          public void initChannel(SocketChannel ch) {
            System.out.println("Initializing channel pipeline");
            ch.pipeline().addLast(new HttpServerCodec());
            ch.pipeline().addLast(new HttpObjectAggregator(8192)); // Add this line
            ch.pipeline().addLast(new RequestsHandler());
          }
        });

    channelFuture = serverBootstrap.bind(port).sync(); // Start the server
    System.out.println("Server started on port: " + port);
  }

  public void stop() {
    if (channelFuture != null) {
      channelFuture.channel().close();
    }
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
  }
}

