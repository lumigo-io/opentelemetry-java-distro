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
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.*;
import java.util.logging.Logger;

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring the {@link AutoConfigurationCustomizer}. See the {@link
 * #customize(AutoConfigurationCustomizer)} method below.
 *
 * <p>Also see {@link <a
 * href="https://github.com/open-telemetry/opentelemetry-java/issues/2022">OpenTelemetry Java
 * 'Confusing configuration story' Issue #2022</a>}
 *
 * @see AutoConfigurationCustomizerProvider
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class LumigoConfigurator implements AutoConfigurationCustomizerProvider {
  public static final String LUMIGO_TRACER_TOKEN = "lumigo.tracer.token";
  public static final String LUMIGO_DEBUG_SPANDUMP = "lumigo.debug.spandump";

  public static final Logger LOGGER = Logger.getLogger(LumigoConfigurator.class.getName());

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
    if (!Strings.isBlank(debugSpanDump)) {
      if (!(debugSpanDump.split("/").length > 1)) {
        LOGGER.warning("Spandump path '" + debugSpanDump + "' is not valid; spandump is disabled.");
      } else {
        try {
          tracerProvider.addSpanProcessor(
              SimpleSpanProcessor.create(FileSpanExporter.create(debugSpanDump)));

          LOGGER.finest("Dumping spans to '" + debugSpanDump + "' file");
        } catch (Exception e) {
          LOGGER.severe("Cannot create spandump exporter to '" + debugSpanDump + "' file: " + e);
        }
      }
    }

    return tracerProvider;
  }

  private Map<String, String> propertiesCustomizer(ConfigProperties originalCfg) {
    String accessToken = originalCfg.getString(LUMIGO_TRACER_TOKEN);
    if (Strings.isBlank(accessToken)) {
      LOGGER.warning(
          "Lumigo token not provided (env var 'LUMIGO_TRACER_TOKEN' not set); no data will be sent to Lumigo.");
      return Collections.emptyMap();
    }

    Map<String, String> customizedCfg = new HashMap<>();

    List<String> headers = new ArrayList<>();

    String rawHeaders = originalCfg.getString("otel.exporter.otlp.headers");
    if (!Strings.isBlank(rawHeaders)) {
      headers.addAll(Arrays.asList(rawHeaders.split(",")));
    }

    headers.add("Authorization=LumigoToken " + accessToken);

    customizedCfg.put("otel.exporter.otlp.headers", String.join(",", headers));

    // Upsert only if not set by the user, this allows the user to override the endpoint (i.e. for
    // testing)
    setIfNotSet(originalCfg, customizedCfg, "otel.exporter.otlp.endpoint", LUMIGO_ENDPOINT_URL);
    setIfNotSet(originalCfg, customizedCfg, "otel.exporter.otlp.protocol", "http/protobuf");

    /*
     * Disable the metrics exporter, as Lumigo does not currently offer a /v1/metrics endpoint
     */
    setIfNotSet(originalCfg, customizedCfg, "otel.metrics.exporter", "none");

    /*
     * Disable the metrics exporter, as Lumigo does not currently offer a /v1/logs endpoint
     * for general use.
     */
    setIfNotSet(originalCfg, customizedCfg, "otel.logs.exporter", "none");

    return customizedCfg;
  }

  private static void setIfNotSet(
      ConfigProperties originalCfg, Map<String, String> customizedCfg, String key, String value) {

    if (Strings.isBlank(originalCfg.getString(key))) {
      customizedCfg.put(key, value);
    }
  }

  private Map<String, String> getDefaultProperties() {
    return Collections.singletonMap("otel.traces.sampler", "always_on");
  }
}
