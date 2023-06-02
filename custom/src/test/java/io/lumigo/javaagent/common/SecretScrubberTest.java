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

public class SecretScrubberTest {
  @Test
  public void testEmptyEnvironmentMasking() {
    ConfigProperties mockConfig = mock();
    when(mockConfig.getString(ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT))
        .thenReturn("[]");

    ProcessEnvironmentScrubber scrubber = new ProcessEnvironmentScrubber(mockConfig);

    assertThat(scrubber.expressionPatterns.size(), equalTo(0));
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
  }
}
