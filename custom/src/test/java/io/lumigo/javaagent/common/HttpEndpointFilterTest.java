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
package io.lumigo.javaagent.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.Test;

class HttpEndpointFilterTest {
  @Test
  void testDefaults() {
    ConfigProperties mockConfig = mock();

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo(""));
  }

  @Test
  void testServerOverride() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER))
        .thenReturn("[\".*/custom.*\"]");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result =
        filter.parseExpressions(
            mockConfig, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER);

    assertThat(result.getExpressionPatterns().size(), equalTo(1));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/custom.*"));
    assertThat(result.getRegularExpressions(), equalTo("[\".*/custom.*\"]"));

    result =
        filter.parseExpressions(
            mockConfig, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo(""));
  }

  @Test
  void testServerOverrideWithOriginals() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER))
        .thenReturn("[\".*/health.*\", \".*/actuator.*\", \".*/custom.*\"]");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result =
        filter.parseExpressions(
            mockConfig, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER);

    assertThat(result.getExpressionPatterns().size(), equalTo(3));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getExpressionPatterns().get(2).pattern(), equalTo(".*/custom.*"));
    assertThat(
        result.getRegularExpressions(),
        equalTo("[\".*/health.*\", \".*/actuator.*\", \".*/custom.*\"]"));

    result =
        filter.parseExpressions(
            mockConfig, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo(""));
  }

  @Test
  void testClientOverride() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT))
        .thenReturn("[\".*/custom.*\"]");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result =
        filter.parseExpressions(
            mockConfig, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT);

    assertThat(result.getExpressionPatterns().size(), equalTo(1));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/custom.*"));
    assertThat(result.getRegularExpressions(), equalTo("[\".*/custom.*\"]"));

    result =
        filter.parseExpressions(
            mockConfig, HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo(""));
  }

  @Test
  void testEmptyHttpFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo(""));
  }

  @Test
  void testEmptyArrayHttpFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("[]");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(0));
    assertThat(result.getRegularExpressions(), equalTo("[]"));
  }

  @Test
  void testCustomHttpFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("[\".*/custom.*\"]");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(1));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/custom.*"));
    assertThat(result.getRegularExpressions(), equalTo("[\".*/custom.*\"]"));
  }

  @Test
  void testInvalidJsonHttpFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("['.*\"my.*']");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo("['.*\"my.*']"));
  }

  @Test
  void testInvalidRegExHttpFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("[\"(ad\"]");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo("[\"(ad\"]"));
  }

  @Test
  void testInvalidJsonPlainStringFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("foo");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo("foo"));
  }

  @Test
  void testInvalidJsonObjectFilter() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(HttpEndpointFilter.LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX))
        .thenReturn("{\"foo\": \"bar\"}");

    HttpEndpointFilter filter = new HttpEndpointFilter();
    ParseExpressionResult result = filter.parseExpressions(mockConfig, null);

    assertThat(result.getExpressionPatterns().size(), equalTo(2));
    assertThat(result.getExpressionPatterns().get(0).pattern(), equalTo(".*/health.*"));
    assertThat(result.getExpressionPatterns().get(1).pattern(), equalTo(".*/actuator.*"));
    assertThat(result.getRegularExpressions(), equalTo("{\"foo\": \"bar\"}"));
  }
}
