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
package io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0;

import static io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0.ApacheHttpClientConfigCustomizer.CAPTURE_CLIENT_REQUEST_HEADERS;
import static io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0.ApacheHttpClientConfigCustomizer.CAPTURE_CLIENT_RESPONSE_HEADERS;
import static io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0.ApacheHttpClientConfigCustomizer.CONTENT_ENCODING;
import static io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0.ApacheHttpClientConfigCustomizer.CONTENT_TYPE;
import static io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0.ApacheHttpClientConfigCustomizer.TRANSFER_ENCODING;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConfigurationOverrideTest {

  @Captor ArgumentCaptor<Function<ConfigProperties, Map<String, String>>> functionCaptor;

  @Test
  void testEmptyConfig(@Mock AutoConfigurationCustomizer mockCustomizer) {
    ApacheHttpClientConfigCustomizer configCustomizer = new ApacheHttpClientConfigCustomizer();
    configCustomizer.customize(mockCustomizer);

    verify(mockCustomizer).addPropertiesCustomizer(functionCaptor.capture());

    ConfigProperties mockConfig = mock();
    Map<String, String> props = functionCaptor.getValue().apply(mockConfig);

    assertThat(props.size(), equalTo(3));
    assertThat(props.get("otel.instrumentation.apache-httpclient-5.0.enabled"), equalTo("false"));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(TRANSFER_ENCODING));
  }

  @Test
  void testConfigWithExistingRequestHeader(@Mock AutoConfigurationCustomizer mockCustomizer) {
    ApacheHttpClientConfigCustomizer configCustomizer = new ApacheHttpClientConfigCustomizer();
    configCustomizer.customize(mockCustomizer);

    verify(mockCustomizer).addPropertiesCustomizer(functionCaptor.capture());

    ConfigProperties mockConfig = mock();
    doReturn("content-length").when(mockConfig).getString(CAPTURE_CLIENT_REQUEST_HEADERS);

    Map<String, String> props = functionCaptor.getValue().apply(mockConfig);

    assertThat(props.size(), equalTo(3));
    assertThat(props.get("otel.instrumentation.apache-httpclient-5.0.enabled"), equalTo("false"));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString("content-length"));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(TRANSFER_ENCODING));
  }

  @Test
  void testConfigWithExistingResponseHeader(@Mock AutoConfigurationCustomizer mockCustomizer) {
    ApacheHttpClientConfigCustomizer configCustomizer = new ApacheHttpClientConfigCustomizer();
    configCustomizer.customize(mockCustomizer);

    verify(mockCustomizer).addPropertiesCustomizer(functionCaptor.capture());

    ConfigProperties mockConfig = mock();
    doReturn("content-length").when(mockConfig).getString(CAPTURE_CLIENT_RESPONSE_HEADERS);

    Map<String, String> props = functionCaptor.getValue().apply(mockConfig);

    assertThat(props.size(), equalTo(3));
    assertThat(props.get("otel.instrumentation.apache-httpclient-5.0.enabled"), equalTo("false"));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString("content-length"));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(TRANSFER_ENCODING));
  }

  @Test
  void testConfigWithExistingContentTypeHeader(@Mock AutoConfigurationCustomizer mockCustomizer) {
    ApacheHttpClientConfigCustomizer configCustomizer = new ApacheHttpClientConfigCustomizer();
    configCustomizer.customize(mockCustomizer);

    verify(mockCustomizer).addPropertiesCustomizer(functionCaptor.capture());

    ConfigProperties mockConfig = mock();
    doReturn("content-type, content-encoding")
        .when(mockConfig)
        .getString(CAPTURE_CLIENT_RESPONSE_HEADERS);

    Map<String, String> props = functionCaptor.getValue().apply(mockConfig);

    assertThat(props.size(), equalTo(3));
    assertThat(props.get("otel.instrumentation.apache-httpclient-5.0.enabled"), equalTo("false"));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(TRANSFER_ENCODING));
  }

  @Test
  void testConfigWithExistingContentTypeAndMultipleHeaders(
      @Mock AutoConfigurationCustomizer mockCustomizer) {
    ApacheHttpClientConfigCustomizer configCustomizer = new ApacheHttpClientConfigCustomizer();
    configCustomizer.customize(mockCustomizer);

    verify(mockCustomizer).addPropertiesCustomizer(functionCaptor.capture());

    ConfigProperties mockConfig = mock();
    doReturn("content-type,content-length,content-encoding")
        .when(mockConfig)
        .getString(CAPTURE_CLIENT_RESPONSE_HEADERS);

    Map<String, String> props = functionCaptor.getValue().apply(mockConfig);

    assertThat(props.size(), equalTo(3));
    assertThat(props.get("otel.instrumentation.apache-httpclient-5.0.enabled"), equalTo("false"));

    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_TYPE));
    assertThat(props.get(CAPTURE_CLIENT_REQUEST_HEADERS), containsString(CONTENT_ENCODING));
    assertThat(props.get(CAPTURE_CLIENT_RESPONSE_HEADERS), containsString(TRANSFER_ENCODING));
  }
}
