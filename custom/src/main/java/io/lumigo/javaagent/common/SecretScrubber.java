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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.lumigo.javaagent.Strings;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class SecretScrubber {
  Logger LOGGER = Logger.getLogger(SecretScrubber.class.getName());

  JsonFactory JSON_FACTORY = new JsonFactory();

  public static final int DEFAULT_ATTRIBUTE_VALUE_LENGTH_LIMIT = 2048;
  public static final String LUMIGO_SECRET_MASKING_REGEX = "lumigo.secret.masking.regex";
  public static final String LUMIGO_SECRET_MASKING_ALL_MAGIC = "all";
  public static final String SCRUBBED_VALUE = "****";

  public static final List<String> DEFAULT_REGEX_KEYS =
      Arrays.asList(
          ".*pass.*",
          ".*key.*",
          ".*secret.*",
          ".*credential.*",
          ".*passphrase.*",
          ".*token.*",
          "SessionToken",
          "x-amz-security-token",
          "Signature",
          "Credential",
          "Authorization");

  ParseExpressionResult parseExpressions(ConfigProperties config, String overrideEnvName) {
    final String sourceEnvVar;
    if (!Strings.isBlank(overrideEnvName) && !Strings.isBlank(config.getString(overrideEnvName))) {
      sourceEnvVar = overrideEnvName;
    } else if (config.getString(LUMIGO_SECRET_MASKING_REGEX) != null) {
      sourceEnvVar = LUMIGO_SECRET_MASKING_REGEX;
    } else {
      sourceEnvVar = "";
    }

    final String regExps = !sourceEnvVar.isEmpty() ? config.getString(sourceEnvVar) : "";
    assert regExps != null;
    if (regExps.equalsIgnoreCase(LUMIGO_SECRET_MASKING_ALL_MAGIC)) {
      return new ParseExpressionResult(sourceEnvVar, regExps);
    }

    final List<Pattern> patterns;
    if (!Strings.isBlank(regExps)) {
      patterns = new ArrayList<>();
      try (JsonParser parser = JSON_FACTORY.createParser(regExps)) {
        if (!parser.nextToken().isStructStart()
            || !JsonToken.START_ARRAY.equals(parser.currentToken())) {
          throw new IllegalArgumentException();
        }
        while (!JsonToken.END_ARRAY.equals(parser.nextToken())) {
          if (parser.currentToken().equals(JsonToken.VALUE_STRING)) {
            patterns.add(Pattern.compile(parser.getText(), Pattern.CASE_INSENSITIVE));
          }
        }
        return new ParseExpressionResult(sourceEnvVar, regExps, patterns);
      } catch (Exception e) {
        LOGGER.warning(
            "Failed to parse the masking regex: "
                + regExps
                + " set in the "
                + sourceEnvVar
                + " environment variable; it must either be \"all\" or a stringified JSON array of regular expressions, e.g.: \\'[\"a.*\",\"b.*\"]\\'. Falling back to default: "
                + DEFAULT_REGEX_KEYS);
      }
    }
    return new ParseExpressionResult(
        sourceEnvVar,
        regExps,
        DEFAULT_REGEX_KEYS.stream()
            .map(exp -> Pattern.compile(exp, Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList()));
  }
}
