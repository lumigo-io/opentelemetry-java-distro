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

import com.fasterxml.jackson.core.JsonGenerator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ProcessEnvironmentScrubber extends SecretScrubber {
  public static final String LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT =
      "lumigo.secret.masking.regex.environment";

  final String regExps;
  final List<Pattern> expressionPatterns;

  public ProcessEnvironmentScrubber(ConfigProperties config) {
    ParseExpressionResult result =
        parseExpressions(config, LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT);
    this.regExps = result.getRegularExpressions();
    this.expressionPatterns = result.getExpressionPatterns();
  }

  public String scrub(Map<String, String> content) {
    if (!this.regExps.isEmpty() && this.regExps.equals(LUMIGO_SECRET_MASKING_ALL_MAGIC)) {
      return SCRUBBED_VALUE;
    }

    StringWriter writer = new StringWriter();
    try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
      generator.writeStartObject();

      for (Map.Entry<String, String> entry : content.entrySet()) {
        boolean shouldMask =
            expressionPatterns.stream().anyMatch(pattern -> pattern.matcher(entry.getKey()).find());
        if (shouldMask) {
          generator.writeStringField(entry.getKey(), SCRUBBED_VALUE);
        } else {
          generator.writeStringField(entry.getKey(), entry.getValue());
        }
      }

      generator.writeEndObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Writer returns the JSON as a string, but we need the JSON to be escaped for storing in a
    // single String.
    return writer.toString().replaceAll("\"", "\\\\\"");
  }
}
