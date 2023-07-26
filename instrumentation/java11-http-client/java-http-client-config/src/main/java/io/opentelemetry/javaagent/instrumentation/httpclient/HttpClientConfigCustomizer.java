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
package io.opentelemetry.javaagent.instrumentation.httpclient;

import com.google.auto.service.AutoService;
import io.lumigo.javaagent.utils.Strings;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is in a separate module from the instrumentation as it needs to be compiled with Java
 * 8. The class is loaded by the JDK ServiceLoader instead of the {@link SafeServiceLoader} from
 * OTeL which doesn't fail with {@link UnsupportedClassVersionError}
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class HttpClientConfigCustomizer implements AutoConfigurationCustomizerProvider {
  static final String CAPTURE_CLIENT_REQUEST_HEADERS =
      "otel.instrumentation.http.capture-headers.client.request";
  static final String CAPTURE_CLIENT_RESPONSE_HEADERS =
      "otel.instrumentation.http.capture-headers.client.response";
  static final String CONTENT_TYPE = "content-type";
  static final String CONTENT_ENCODING = "content-encoding";
  static final String TRANSFER_ENCODING = "transfer-encoding";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    final String javaVersion = System.getProperty("java.version");
    if (javaVersion.startsWith("1.8")
        || javaVersion.startsWith("9")
        || javaVersion.startsWith("10")) {
      // Disable the java-http-client instrumentation for Java 8, 9 and 10
      autoConfiguration.addPropertiesCustomizer(
          config -> {
            Map<String, String> overrides = new HashMap<>();
            overrides.put("otel.instrumentation.lumigo-java-http-client.enabled", "false");
            return overrides;
          });

      return;
    }

    autoConfiguration.addPropertiesCustomizer(
        config -> {
          Map<String, String> overrides = new HashMap<>();

          // disable OTeL instrumentation for Java HTTP Client
          overrides.put("otel.instrumentation.java-http-client.enabled", "false");

          // capture request content-type header
          overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_REQUEST_HEADERS, CONTENT_TYPE);

          // capture response content-type header
          overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_RESPONSE_HEADERS, CONTENT_TYPE);

          // capture request content-encoding header
          overrideHeaderProperty(
              config, overrides, CAPTURE_CLIENT_REQUEST_HEADERS, CONTENT_ENCODING);

          // capture response content-encoding header
          overrideHeaderProperty(
              config, overrides, CAPTURE_CLIENT_RESPONSE_HEADERS, CONTENT_ENCODING);

          // capture response transfer-encoding header
          overrideHeaderProperty(
              config, overrides, CAPTURE_CLIENT_RESPONSE_HEADERS, TRANSFER_ENCODING);

          return overrides;
        });
  }

  private void overrideHeaderProperty(
      ConfigProperties config,
      Map<String, String> overrides,
      String headerName,
      String desiredValue) {
    String originalHeaderValues = config.getString(headerName);
    String overriddenHeaderValues = overrides.get(headerName);

    // Check if there's already a value for the header we should append onto
    if (!Strings.isBlank(overriddenHeaderValues)) {
      // Check if the overridden value doesn't contain the value we need to set
      if (!overriddenHeaderValues.contains(desiredValue)) {
        overriddenHeaderValues += "," + desiredValue;
        overrides.put(headerName, overriddenHeaderValues);
      }
      // Check if there's already a value from config for the header we should append to
    } else if (!Strings.isBlank(originalHeaderValues)) {
      // Check if the config value doesn't contain the value we need to set
      if (!originalHeaderValues.contains(desiredValue)) {
        originalHeaderValues += "," + desiredValue;
        overrides.put(headerName, originalHeaderValues);
      }
      // No existing values for the header found
    } else {
      overrides.put(headerName, desiredValue);
    }
  }
}
