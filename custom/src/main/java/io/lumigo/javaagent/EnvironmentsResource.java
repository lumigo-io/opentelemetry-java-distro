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
package io.lumigo.javaagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(ResourceProvider.class)
public class EnvironmentsResource implements ResourceProvider {
  private static final String ENV_ATTR_NAME = "process.environ";
  private static final String LUMIGO_SECRET_MASKING_REGEX = "lumigo.secret.masking.regex";
  private static final String LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT =
      "lumigo.secret.masking.regex.environment";

  private static final List<Pattern> DEFAULT_PATTERNS = DefaultPatterns();

  private static List<Pattern> DefaultPatterns() {
    return Stream.of(
            Pattern.compile(".*pass.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*key.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*secret.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*credential.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*passphrase.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("SessionToken", Pattern.CASE_INSENSITIVE),
            Pattern.compile("x-amz-security-token", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Signature", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Credential", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Authorization", Pattern.CASE_INSENSITIVE))
        .collect(Collectors.toList());
  }

  public static List<Pattern> patternsFromConfig(ConfigProperties config, String key) {
    String json = config.getString(key);

    if (json == null || json.isEmpty()) {
      return DEFAULT_PATTERNS;
    }

    try {
      return new ObjectMapper()
          .readValue(json, new TypeReference<List<String>>() {}).stream()
              .map(EnvironmentsResource::stringToPattern)
              .collect(Collectors.toList());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Pattern stringToPattern(String s) {
    return Pattern.compile(s, Pattern.CASE_INSENSITIVE);
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    Map<String, String> envs = new HashMap<>(System.getenv());

    List<Pattern> MaskingRegex = patternsFromConfig(config, LUMIGO_SECRET_MASKING_REGEX);
    List<Pattern> MaskingRegexEnv =
        patternsFromConfig(config, LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT);

    for (String key : envs.keySet()) {
      Stream.concat(MaskingRegex.stream(), MaskingRegexEnv.stream())
          .forEach(
              pattern -> {
                if (pattern.matcher(key).matches()) {
                  envs.put(key, "****");
                }
              });
    }

    try {
      AttributesBuilder ab = Attributes.builder();
      ab.put(ENV_ATTR_NAME, new ObjectMapper().writeValueAsString(envs));
      return Resource.create(ab.build());
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return null;
    }
  }
}
