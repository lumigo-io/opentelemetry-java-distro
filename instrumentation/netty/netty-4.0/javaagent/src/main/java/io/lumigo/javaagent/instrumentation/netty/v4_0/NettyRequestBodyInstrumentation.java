package io.lumigo.javaagent.instrumentation.netty.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.*;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.SpanAndRelatedObjectHolder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.HttpServerRequestTracingHandler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.io.ByteArrayOutputStream;

public class NettyRequestBodyInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.channel.ChannelInboundHandlerAdapter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("io.netty.channel.ChannelInboundHandlerAdapter"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("channelRead")
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, Object.class))
            .and(isPublic()),
        NettyRequestBodyInstrumentation.class.getName() + "$ChannelReadAdvice");
  }

  public static class ChannelReadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ChannelInboundHandlerAdapter thiz,
        @Advice.Argument(0) ChannelHandlerContext ctx,
        @Advice.Argument(1) Object msg,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder

    ) {
      if (!(thiz instanceof HttpServerRequestTracingHandler)) {
        return;
      }

      System.out.println(thiz.getClass().getName() + " - OnMethodEnter - ");
      callDepth = CallDepth.forClass(ChannelInboundHandlerAdapter.class);

//      if (callDepth.getAndIncrement() > 0) {
//        System.out.println(thiz.getClass().getName() + " - OnMethodEnter - " + "callDepth.getAndIncrement() > 0");
//        return;
//      }

      if (msg instanceof HttpRequest) {
        System.out.println(thiz.getClass().getName() + " - OnMethodEnter - " + "msg instanceof HttpRequest");
        Span span = Java8BytecodeBridge.currentSpan();
        if (!span.getSpanContext().isValid()) {
          System.out.println(thiz.getClass().getName() + " - OnMethodEnter - " + "span.getSpanContext().isValid() is false");
          return;
        }

        bufferHolder = new ByteBufferHolder(new ByteArrayOutputStream(), span, "UTF-8"); // Adjust charset as needed
        VirtualField.find(ChannelHandlerContext.class, ByteBufferHolder.class).set(ctx, bufferHolder);
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.This ChannelInboundHandlerAdapter thiz,
        @Advice.Argument(0) ChannelHandlerContext ctx,
        @Advice.Argument(1) Object msg,
        @Advice.Local("lumigoCallDepth") CallDepth callDepth,
        @Advice.Local("lumigoBuffer") ByteBufferHolder bufferHolder
    ) {
      if (!(thiz instanceof HttpServerRequestTracingHandler)) {
        return;
      }


//      if (callDepth.decrementAndGet() > 0) {
//        System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "callDepth.decrementAndGet() > 0");
//        return;
//      }

      if (bufferHolder == null) {
        System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "bufferHolder == null");
        bufferHolder = VirtualField.find(ChannelHandlerContext.class, ByteBufferHolder.class).get(ctx);
        if (bufferHolder == null) {
          System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "bufferHolder == null 2");
          return;
        }
      }

      if (msg instanceof HttpContent) {
        System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "msg instanceof HttpContent");
        HttpContent httpContent = (HttpContent) msg;
        ByteBuf content = httpContent.content();

        if (content.isReadable()) {

          content.retain();
          try {
            int readableBytes = content.readableBytes();
            byte[] bytes = new byte[readableBytes];
            int readerIndex = content.readerIndex();
            content.getBytes(readerIndex, bytes);
//            bufferHolder.append(bytes);
          } finally {
            content.release(); // Release the copied buffer
          }


//          System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "content.isReadable()");
//          int readableBytes = content.readableBytes();
//          System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "readableBytes: " + readableBytes);
//          byte[] bytes = new byte[readableBytes];
//          int readerIndex = content.readerIndex();
//          System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "readerIndex: " + readerIndex);
//          content.readBytes(bytes);
//          bufferHolder.append(bytes);
        }

//        if (msg instanceof LastHttpContent) {
//          System.out.println(thiz.getClass().getName() + " - OnMethodExit - " + "msg instanceof LastHttpContent");
//          bufferHolder.captureRequestBody();
//          // Clean up the buffer holder to prevent memory leaks
//          VirtualField.find(ChannelHandlerContext.class, ByteBufferHolder.class).set(ctx, null);
//        }
      }
    }
  }
}
