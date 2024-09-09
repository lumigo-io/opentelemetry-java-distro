/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter.splitArgs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CompleteableCommand;
import io.lettuce.core.protocol.OtelCommandArgsUtil;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class OpenTelemetryTracing implements Tracing {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  // copied from DbIncubatingAttributes.DbSystemValues
  private static final String REDIS = "redis";

  private static final AttributesExtractor<OpenTelemetryEndpoint, Void> serverAttributesExtractor =
      ServerAttributesExtractor.create(new LettuceServerAttributesGetter());
  private static final AttributesExtractor<OpenTelemetryEndpoint, Void> networkAttributesExtractor =
      NetworkAttributesExtractor.create(new LettuceServerAttributesGetter());
  private final TracerProvider tracerProvider;

  OpenTelemetryTracing(io.opentelemetry.api.trace.Tracer tracer, RedisCommandSanitizer sanitizer) {
    this.tracerProvider = new OpenTelemetryTracerProvider(tracer, sanitizer);
  }

  @Override
  public TracerProvider getTracerProvider() {
    return tracerProvider;
  }

  @Override
  public TraceContextProvider initialTraceContextProvider() {
    return new OpenTelemetryTraceContextProvider();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  // Added in lettuce 5.2, ignored in 6.0+
  // @Override
  public boolean includeCommandArgsInSpanTags() {
    return true;
  }

  @Override
  @Nullable
  public Endpoint createEndpoint(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      return new OpenTelemetryEndpoint((InetSocketAddress) socketAddress);
    }
    return null;
  }

  private static class OpenTelemetryTracerProvider implements TracerProvider {

    private final Tracer openTelemetryTracer;

    OpenTelemetryTracerProvider(
        io.opentelemetry.api.trace.Tracer tracer, RedisCommandSanitizer sanitizer) {
      openTelemetryTracer = new OpenTelemetryTracer(tracer, sanitizer);
    }

    @Override
    public Tracer getTracer() {
      return openTelemetryTracer;
    }
  }

  private static class OpenTelemetryTraceContextProvider implements TraceContextProvider {

    @Override
    public TraceContext getTraceContext() {
      return new OpenTelemetryTraceContext();
    }
  }

  private static class OpenTelemetryTraceContext implements TraceContext {
    private final Context context;

    OpenTelemetryTraceContext() {
      this.context = Context.current();
    }

    public Context getSpanContext() {
      return context;
    }
  }

  static class OpenTelemetryEndpoint implements Endpoint {
    @Nullable final InetSocketAddress address;

    OpenTelemetryEndpoint(@Nullable InetSocketAddress address) {
      this.address = address;
    }
  }

  private static class OpenTelemetryTracer extends Tracer {

    private final io.opentelemetry.api.trace.Tracer tracer;
    private final RedisCommandSanitizer sanitizer;

    OpenTelemetryTracer(io.opentelemetry.api.trace.Tracer tracer, RedisCommandSanitizer sanitizer) {
      this.tracer = tracer;
      this.sanitizer = sanitizer;
    }

    @Override
    public OpenTelemetrySpan nextSpan() {
      return nextSpan(Context.current());
    }

    @Override
    public OpenTelemetrySpan nextSpan(TraceContext traceContext) {
      if (!(traceContext instanceof OpenTelemetryTraceContext)) {
        return nextSpan();
      }

      Context context = ((OpenTelemetryTraceContext) traceContext).getSpanContext();
      return nextSpan(context);
    }

    private OpenTelemetrySpan nextSpan(Context context) {
      // Name will be updated later, we create with an arbitrary one here to store other data before
      // the span starts.
      SpanBuilder spanBuilder =
          tracer
              .spanBuilder("redis")
              .setSpanKind(SpanKind.CLIENT)
              .setParent(context)
              .setAttribute(DB_SYSTEM, REDIS);
      return new OpenTelemetrySpan(context, spanBuilder, sanitizer);
    }
  }

  // The order that callbacks will be called in or which thread they are called from is not well
  // defined. We go ahead and buffer all data until we know we have a span. This implementation is
  // particularly safe, synchronizing all accesses. Relying on implementation details would allow
  // reducing synchronization but the impact should be minimal.
  private static class OpenTelemetrySpan extends Tracer.Span {

    private final Context context;
    private final SpanBuilder spanBuilder;
    private final RedisCommandSanitizer sanitizer;

    @Nullable private String name;
    @Nullable private List<Object> events;
    @Nullable private Throwable error;
    @Nullable private Span span;
    @Nullable private List<String> argsList;
    @Nullable private String argsString;

    OpenTelemetrySpan(Context context, SpanBuilder spanBuilder, RedisCommandSanitizer sanitizer) {
      this.context = context;
      this.spanBuilder = spanBuilder;
      this.sanitizer = sanitizer;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span name(String name) {
      if (span != null) {
        span.updateName(name);
      }

      this.name = name;

      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span remoteEndpoint(Endpoint endpoint) {
      if (endpoint instanceof OpenTelemetryEndpoint) {
        fillEndpoint((OpenTelemetryEndpoint) endpoint);
      }
      return this;
    }

    private void fillEndpoint(OpenTelemetryEndpoint endpoint) {
      AttributesBuilder attributesBuilder = Attributes.builder();
      Context currentContext = span == null ? context : context.with(span);
      serverAttributesExtractor.onStart(attributesBuilder, currentContext, endpoint);
      networkAttributesExtractor.onEnd(attributesBuilder, currentContext, endpoint, null, null);
      if (span != null) {
        span.setAllAttributes(attributesBuilder.build());
      } else {
        spanBuilder.setAllAttributes(attributesBuilder.build());
      }
    }

    // Added and called in 6.0+
    // @Override
    @CanIgnoreReturnValue
    @SuppressWarnings("UnusedMethod")
    public synchronized Tracer.Span start(RedisCommand<?, ?, ?> command) {
      start();

      Span span = this.span;
      if (span == null) {
        throw new IllegalStateException("Span started but null, this is a programming error.");
      }
      span.updateName(command.getType().name());

      if (command.getArgs() != null) {
        argsList = OtelCommandArgsUtil.getCommandArgs(command.getArgs());
      }

      if (command instanceof CompleteableCommand) {
        CompleteableCommand<?> completeableCommand = (CompleteableCommand<?>) command;
        completeableCommand.onComplete(
            (o, throwable) -> {
              if (throwable != null) {
                span.recordException(throwable);
              }

              CommandOutput<?, ?, ?> output = command.getOutput();
              if (output != null) {
                String error = output.getError();
                if (error != null) {
                  span.setStatus(StatusCode.ERROR, error);
                }
              }

              finish(span);
            });
      }

      return this;
    }

    // Not called by Lettuce in 6.0+ (though we call it ourselves above).
    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span start() {
      span = spanBuilder.startSpan();
      if (name != null) {
        span.updateName(name);
      }

      if (events != null) {
        for (int i = 0; i < events.size(); i += 2) {
          span.addEvent((String) events.get(i), (Instant) events.get(i + 1));
        }
        events = null;
      }

      if (error != null) {
        span.setStatus(StatusCode.ERROR);
        span.recordException(error);
        error = null;
      }

      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span annotate(String value) {
      if (span != null) {
        span.addEvent(value);
      } else {
        if (events == null) {
          events = new ArrayList<>();
        }
        events.add(value);
        events.add(Instant.now());
      }
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span tag(String key, String value) {
      if (key.equals("redis.args")) {
        argsString = value;
        return this;
      }
      if (span != null) {
        span.setAttribute(key, value);
      } else {
        spanBuilder.setAttribute(key, value);
      }
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span error(Throwable throwable) {
      if (span != null) {
        span.recordException(throwable);
      } else {
        this.error = throwable;
      }
      return this;
    }

    @Override
    public synchronized void finish() {
      if (span != null) {
        finish(span);
      }
    }

    private void finish(Span span) {
      if (name != null) {
        String statement =
            sanitizer.sanitize(name, argsList != null ? argsList : splitArgs(argsString));
        span.setAttribute(DB_STATEMENT, statement);
      }
      span.end();
    }
  }
}
