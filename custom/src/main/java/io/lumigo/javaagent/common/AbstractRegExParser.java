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
import io.lumigo.javaagent.utils.Strings;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract class AbstractRegExParser {
  private static final Logger LOGGER = Logger.getLogger(AbstractRegExParser.class.getName());

  private static final String NO_MAGIC_VALUE = "NO_MAGIC_VALUE";

  protected static final JsonFactory JSON_FACTORY = new JsonFactory();

  protected abstract String getEnvVarName();

  protected abstract List<String> getDefaultRegexKeys();

  protected String getMagicValue() {
    return NO_MAGIC_VALUE;
  }

  private String getWarningMessage() {
    if (NO_MAGIC_VALUE.equals(getMagicValue())) {
      return "it must be a stringified JSON array of regular expressions, e.g.: '[\"a.*\",\"b.*\"]'";
    } else {
      return "it must either be "
          + getMagicValue()
          + " or a stringified JSON array of regular expressions, e.g.: '[\"a.*\",\"b.*\"]'";
    }
  }

  public ParseExpressionResult parseExpressions(ConfigProperties config, String overrideEnvName) {
    final String sourceEnvVar;
    if (!Strings.isBlank(overrideEnvName) && !Strings.isBlank(config.getString(overrideEnvName))) {
      sourceEnvVar = overrideEnvName;
    } else if (!Strings.isBlank(config.getString(getEnvVarName()))) {
      sourceEnvVar = getEnvVarName();
    } else {
      sourceEnvVar = "";
    }

    final String regExps = !sourceEnvVar.isEmpty() ? config.getString(sourceEnvVar) : "";
    assert regExps != null;
    if (regExps.equalsIgnoreCase(getMagicValue())) {
      return new ParseExpressionResult(sourceEnvVar, regExps);
    }

    final List<Pattern> patterns;
    if (!Strings.isBlank(regExps)) {
      patterns = new ArrayList<>();
      try (JsonParser parser = JSON_FACTORY.createParser(regExps)) {
        if (!JsonToken.START_ARRAY.equals(parser.nextToken())) {
          throw new IllegalArgumentException();
        }
        while (!JsonToken.END_ARRAY.equals(parser.nextToken())) {
          if (parser.currentToken().equals(JsonToken.VALUE_STRING)) {
            patterns.add(Pattern.compile(parser.getText(), Pattern.CASE_INSENSITIVE));
          } else {
            throw new IllegalArgumentException();
          }
        }
        return new ParseExpressionResult(sourceEnvVar, regExps, patterns);
      } catch (Exception e) {
        LOGGER.warning(
            "Failed to parse the regex: "
                + regExps
                + " set in the "
                + sourceEnvVar
                + " environment variable; "
                + getWarningMessage()
                + " Falling back to default: "
                + getDefaultRegexKeys());
      }
    }
    return new ParseExpressionResult(
        sourceEnvVar,
        regExps,
        getDefaultRegexKeys().stream()
            .map(exp -> Pattern.compile(exp, Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList()));
  }
}
