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
package io.lumigo.javaagent.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test provider to diagnose ECS resource detection issues. Logs extensively to help understand if:
 * 1. ResourceProviders are being loaded at all 2. ECS metadata endpoint is reachable from Java 3.
 * What response we get from the endpoint
 */
@AutoService(ResourceProvider.class)
public class TestEcsLoadingResource implements ResourceProvider {
  private static final Logger LOGGER = Logger.getLogger(TestEcsLoadingResource.class.getName());

  @Override
  public Resource createResource(ConfigProperties config) {
    LOGGER.warning("=== TestEcsLoadingResource.createResource() CALLED ===");

    String metadataUri = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
    LOGGER.warning("ECS_CONTAINER_METADATA_URI_V4 = " + metadataUri);

    if (metadataUri == null || metadataUri.isEmpty()) {
      LOGGER.warning("ECS_CONTAINER_METADATA_URI_V4 is not set - not in ECS environment");
      return Resource.empty();
    }

    // Test container endpoint
    testEndpoint(metadataUri, "container");

    // Test task endpoint
    testEndpoint(metadataUri + "/task", "task");

    LOGGER.warning("=== TestEcsLoadingResource.createResource() COMPLETE ===");

    // Don't add any attributes, just for testing
    return Resource.empty();
  }

  private void testEndpoint(String url, String name) {
    LOGGER.warning("Testing " + name + " endpoint: " + url);

    try {
      HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setConnectTimeout(2000);
      conn.setReadTimeout(2000);
      conn.setRequestMethod("GET");

      int responseCode = conn.getResponseCode();
      LOGGER.warning(name + " endpoint response code: " + responseCode);

      if (responseCode == 200) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        int lineCount = 0;
        while ((line = reader.readLine()) != null && lineCount < 10) {
          response.append(line).append("\n");
          lineCount++;
        }
        reader.close();

        String preview = response.substring(0, Math.min(500, response.length()));
        LOGGER.warning(name + " endpoint response preview: " + preview);
      } else {
        LOGGER.warning(name + " endpoint returned non-200 status: " + responseCode);
      }

    } catch (Exception e) {
      LOGGER.log(
          Level.WARNING,
          name + " endpoint test FAILED: " + e.getClass().getName() + ": " + e.getMessage(),
          e);
    }
  }
}
