/*
 * Copyright 2023 Lumigo LTD
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
package io.lumigo.javaagent;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring the {@link AutoConfigurationCustomizer}. See the {@link
 * #customize(AutoConfigurationCustomizer)} method below.
 *
 * <p>Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 *
 * @see AutoConfigurationCustomizerProvider
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class LumigoConfigurator implements AutoConfigurationCustomizerProvider {
  public static final String LUMIGO_TRACER_TOKEN = "lumigo.tracer.token";
  public static final String LUMIGO_DEBUG = "lumigo.debug";
  public static final String LUMIGO_DEBUG_SPANDUMP = "lumigo.debug.spandump";

  public static final String LUMIGO_ENDPOINT_URL =
      "https://ga-otlp.lumigo-tracer-edge.golumigo.com";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration
        .addPropertiesCustomizer(this::propertiesCustomizer)
        .addTracerProviderCustomizer(this::tracerProviderCustomizer)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private SdkTracerProviderBuilder tracerProviderCustomizer(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties cfg) {

    String debugSpanDump = cfg.getString(LUMIGO_DEBUG_SPANDUMP);
    if (!debugSpanDump.isEmpty()) {
      if (debugSpanDump.split("/").length > 1) {
        Logger l = Logger.getLogger(LoggingSpanExporter.class.getName());
        try {
          l.addHandler(new FileHandler(debugSpanDump));
        } catch (IOException e) {
          throw new RuntimeException("Failed to create file handler for " + debugSpanDump, e);
        }
      }

      tracerProvider =
          tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
    }

    return tracerProvider;
  }

  private Map<String, String> propertiesCustomizer(ConfigProperties cfg) {
    Map<String, String> customized = new HashMap<>();

    String accessToken = cfg.getString(LUMIGO_TRACER_TOKEN);
    if (!accessToken.isEmpty()) {
      String rawHeaders = cfg.getString("otel.exporter.otlp.headers", "");
      List<String> headers = new ArrayList<>(Arrays.asList(rawHeaders.split(",")));

      headers.add("Authorization=LumigoToken " + accessToken);

      customized.put("otel.exporter.otlp.headers", String.join(",", headers));
    }

    if (cfg.getBoolean(LUMIGO_DEBUG, false)) {
      customized.put("otel.javaagent.debug", "true");
      System.setProperty("io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel", "debug");
      customized.put("otel.log.level", "debug");
    }

    return customized;
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.exporter.otlp.endpoint", LUMIGO_ENDPOINT_URL);
    properties.put("otel.exporter.otlp.protocol", "http/protobuf");
    return properties;
  }
}
