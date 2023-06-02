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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProcessEnvironmentScrubberTest {
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  public static final String SPECIAL_KEY = "special";
  public static final String SPECIAL_VALUE = "SPECIAL_VALUE";
  private static final String MY_SECRET_KEY = "my.secret.key";
  private static final String MY_SECRET_VALUE = "SECRET_VALUE";

  private void verifyScrubbing(
      ProcessEnvironmentScrubber scrubber,
      String expectedSecretValue,
      String expectedSpecialValue) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(MY_SECRET_KEY, MY_SECRET_VALUE);
    envVars.put(SPECIAL_KEY, SPECIAL_VALUE);

    String result = scrubber.scrub(envVars);
    assertThat(result, notNullValue());

    String secretValue = null, specialValue = null;

    try (JsonParser parser = JSON_FACTORY.createParser(result)) {
      if (!JsonToken.START_OBJECT.equals(parser.nextToken())) {
        throw new IllegalArgumentException();
      }
      while (!JsonToken.END_OBJECT.equals(parser.nextToken())) {
        if (parser.currentToken().equals(JsonToken.VALUE_STRING)) {
          if (parser.getCurrentName().equals(MY_SECRET_KEY)) {
            secretValue = parser.getValueAsString();
          } else if (parser.getCurrentName().equals(SPECIAL_KEY)) {
            specialValue = parser.getValueAsString();
          }
        }
      }
    } catch (Exception e) {
      Assertions.fail();
    }

    assertThat(secretValue, equalTo(expectedSecretValue));
    assertThat(specialValue, equalTo(expectedSpecialValue));
  }

  @Test
  public void testDefaults() {
    ConfigProperties mockConfig = mock();
    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);
    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testEmptyEnvironmentMasking() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT))
        .thenReturn("[]");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);
    assertThat(scrubber.expressionPatterns.size(), equalTo(0));

    verifyScrubbing(scrubber, MY_SECRET_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testInvalidJsonEnvironmentMasking() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX))
        .thenReturn("['.*\"my.*']");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);
    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));

    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testInvalidRegExEnvironmentMasking() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX))
        .thenReturn("[\"(ad\"]");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);

    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));

    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testInvalidJsonNumberArray() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX))
        .thenReturn("[42]");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);

    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));

    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testInvalidJsonBooleanArray() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX))
        .thenReturn("[true]");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);

    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));

    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testInvalidJsonPlainString() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX))
        .thenReturn("foo");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);

    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));

    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }

  @Test
  public void testInvalidJsonObject() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX))
        .thenReturn("{\"foo\": \"bar\"}");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);

    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));

    verifyScrubbing(scrubber, ProcessEnvironmentScrubber.SCRUBBED_VALUE, SPECIAL_VALUE);
  }
}
