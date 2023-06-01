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

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SecretScrubberTest {
  @Test
  public void testEmptyEnvironmentMasking() {
    ConfigProperties config =
        new ConfigProperties() {
          @Override
          public String getString(String name) {
            if (name.equals(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT)) {
              return "[]";
            }
            return null;
          }

          @Override
          public Boolean getBoolean(String name) {
            return null;
          }

          @Override
          public Integer getInt(String name) {
            return null;
          }

          @Override
          public Long getLong(String name) {
            return null;
          }

          @Override
          public Double getDouble(String name) {
            return null;
          }

          @Override
          public Duration getDuration(String name) {
            return null;
          }

          @Override
          public List<String> getList(String name) {
            return null;
          }

          @Override
          public Map<String, String> getMap(String name) {
            return null;
          }
        };
    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(config);

    assertThat(scrubber.regExps, equalTo("[]"));
    assertThat(scrubber.expressionPatterns.size(), equalTo(0));
  }

  @Test
  public void testInvalidJsonEnvironmentMasking() {
    ConfigProperties config =
        new ConfigProperties() {
          @Override
          public String getString(String name) {
            if (name.equals(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX)) {
              return "['.*\"my.*']";
            }
            return null;
          }

          @Override
          public Boolean getBoolean(String name) {
            return null;
          }

          @Override
          public Integer getInt(String name) {
            return null;
          }

          @Override
          public Long getLong(String name) {
            return null;
          }

          @Override
          public Double getDouble(String name) {
            return null;
          }

          @Override
          public Duration getDuration(String name) {
            return null;
          }

          @Override
          public List<String> getList(String name) {
            return null;
          }

          @Override
          public Map<String, String> getMap(String name) {
            return null;
          }
        };
    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(config);

    assertThat(scrubber.regExps, equalTo("['.*\"my.*']"));
    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));
  }

  @Test
  public void testInvalidRegExEnvironmentMasking() {
    ConfigProperties config =
        new ConfigProperties() {
          @Override
          public String getString(String name) {
            if (name.equals(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX)) {
              return "[\"(ad\"]";
            }
            return null;
          }

          @Override
          public Boolean getBoolean(String name) {
            return null;
          }

          @Override
          public Integer getInt(String name) {
            return null;
          }

          @Override
          public Long getLong(String name) {
            return null;
          }

          @Override
          public Double getDouble(String name) {
            return null;
          }

          @Override
          public Duration getDuration(String name) {
            return null;
          }

          @Override
          public List<String> getList(String name) {
            return null;
          }

          @Override
          public Map<String, String> getMap(String name) {
            return null;
          }
        };
    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(config);

    assertThat(scrubber.regExps, equalTo("[\"(ad\"]"));
    assertThat(
        scrubber.expressionPatterns.size(),
        equalTo(ProcessEnvironmentScrubber.DEFAULT_REGEX_KEYS.size()));
  }
}
