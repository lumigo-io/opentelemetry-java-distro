package io.lumigo.javaagent.instrumentation.netty.v4_0.server;

import io.netty.channel.CombinedChannelDuplexHandler;

public class HttpServerTracingHandler
    extends CombinedChannelDuplexHandler<
    HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler() {
    super(new HttpServerRequestTracingHandler(), new HttpServerResponseTracingHandler());
  }
}
