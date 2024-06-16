package io.lumigo.javaagent.instrumentation.lettuce.v5_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.HashMap;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class LettuceConfigCustomizer implements AutoConfigurationCustomizerProvider {
  private static final String LETTUCE_EXPERIMENTAL_ATTRIBUTE_KEY =
      "otel.instrumentation.lettuce.experimental-span-attributes";
  private static final String DB_STATEMENT_SANITIZER_KEY =
      "otel.instrumentation.common.db-statement-sanitizer.enabled";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesCustomizer(
        config -> {
          Map<String, String> overrides = new HashMap<>();

          // disable OTeL instrumentation for Lettuce
          overrides.put("otel.instrumentation.lettuce.enabled", "false");

          if (null == config.getBoolean(LETTUCE_EXPERIMENTAL_ATTRIBUTE_KEY)) {
            overrides.put(LETTUCE_EXPERIMENTAL_ATTRIBUTE_KEY, "true");
          }

          if (null == config.getBoolean(DB_STATEMENT_SANITIZER_KEY) || config.getBoolean(DB_STATEMENT_SANITIZER_KEY)) {
            overrides.put(DB_STATEMENT_SANITIZER_KEY, "false");
          }

          return overrides;
        });
  }
}
