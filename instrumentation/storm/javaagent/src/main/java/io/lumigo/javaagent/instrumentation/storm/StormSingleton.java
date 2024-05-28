package io.lumigo.javaagent.instrumentation.storm;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class StormSingleton {
  private static final String INSTRUMENTATION_NAME = "io.lumigo.storm";

  private static final Instrumenter<Object, Object> STORM_INSTRUMENTER;

  static {
    STORM_INSTRUMENTER =
        Instrumenter.builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, (request) -> "storm span")
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<Object, Object> stormInstrumenter() {
    return STORM_INSTRUMENTER;
  }

  private StormSingleton() {}
}
