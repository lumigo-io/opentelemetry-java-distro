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
package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import com.google.auto.service.AutoService;
import io.lumigo.javaagent.utils.Strings;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class ApacheHttpClientConfigCustomizer implements AutoConfigurationCustomizerProvider {
  final static String CAPTURE_CLIENT_REQUEST_HEADERS = "otel.instrumentation.http.client.capture-request-headers";
  final static String CAPTURE_CLIENT_RESPONSE_HEADERS = "otel.instrumentation.http.client.capture-response-headers";
  final static String CONTENT_TYPE = "content-type";
  final static String CONTENT_ENCODING = "content-encoding";
  final static String TRANSFER_ENCODING = "transfer-encoding";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesCustomizer(config -> {
      Map<String, String> overrides = new HashMap<>();

      // disable OTeL instrumentation for ApacheHttpClient v5
      overrides.put("otel.instrumentation.apache-httpclient-5.0.enabled", "false");

      // capture request content-type header
      overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_REQUEST_HEADERS, CONTENT_TYPE);

      // capture response content-type header
      overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_RESPONSE_HEADERS, CONTENT_TYPE);

      // capture request content-encoding header
      overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_REQUEST_HEADERS, CONTENT_ENCODING);

      // capture response content-encoding header
      overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_RESPONSE_HEADERS, CONTENT_ENCODING);

      // capture response transfer-encoding header
      overrideHeaderProperty(config, overrides, CAPTURE_CLIENT_RESPONSE_HEADERS, TRANSFER_ENCODING);

      return overrides;
    });
  }

  private void overrideHeaderProperty(ConfigProperties config, Map<String, String> overrides, String headerName, String desiredValue) {
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
