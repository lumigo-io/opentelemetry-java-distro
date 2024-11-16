package io.lumigo.javaagent.instrumentation.netty.v4_1.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TracingHandlerUtils {
  private static final Logger logger = Logger.getLogger(TracingHandlerUtils.class.getName());

  private static final String IO_OPENTELEMETRY_ATTRIBUTE_KEYS_CLASS_NAME = "io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys";
  private static final AttributeKey<Deque<Object>> SERVER_CONTEXT =
      AttributeKey.valueOf(IO_OPENTELEMETRY_ATTRIBUTE_KEYS_CLASS_NAME + "#server-context");

  public static Context extractContext(ChannelHandlerContext ctx) {
    try {
      // Get the server context attribute
      Attribute<Deque<Object>> serverContextAttr =
          ctx.channel().attr(SERVER_CONTEXT);

      // Get the server context
      Deque<Object> serverContexts = serverContextAttr.get();
      // Get the first server context
      Object serverContext = serverContexts != null ? serverContexts.peekFirst() : null;

      if (serverContext != null) {
        // Extract the context from the server context
        return extractContext(serverContext);
      }
    } catch (Exception ignored) {
      logger.log(
          Level.WARNING,
          "Failed to extract context from channel attribute.");
    }
    return null;
  }

  public static Context extractContext(Object obj) {
    try {
      // Ensure the object is not null
      if (obj == null) {
        throw new IllegalArgumentException("Provided object is null.");
      }

      // Use reflection to invoke the "context()" method
      Method contextMethod = obj.getClass().getMethod("context");

      // Bypass Java access checks
      contextMethod.setAccessible(true);

      Object contextObj = contextMethod.invoke(obj);

      // Cast the result to Context
      if (contextObj instanceof Context) {
        return (Context) contextObj;
      } else {
        throw new RuntimeException("Extracted object is not of type Context.");
      }
    } catch (NoSuchMethodException e) {
      logger.log(
          Level.WARNING,
          "The object does not have a 'context()' method.",
          e);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to extract context from object.",
          e);
    }
    return null;
  }
}
