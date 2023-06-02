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

import java.util.List;
import java.util.regex.Pattern;

public class ParseExpressionResult {
  private final String sourceEnvVar;
  private final String regularExpressions;
  private final List<Pattern> expressionPatterns;

  public ParseExpressionResult(List<Pattern> expressionPatterns) {
    this(null, null, expressionPatterns);
  }

  public ParseExpressionResult(String sourceEnvVar, String regularExpressions) {
    this(sourceEnvVar, regularExpressions, null);
  }

  public ParseExpressionResult(
      String sourceEnvVar, String regularExpressions, List<Pattern> expressionPatterns) {
    this.sourceEnvVar = sourceEnvVar;
    this.regularExpressions = regularExpressions;
    this.expressionPatterns = expressionPatterns;
  }

  public String getSourceEnvVar() {
    return sourceEnvVar;
  }

  public String getRegularExpressions() {
    return regularExpressions;
  }

  public List<Pattern> getExpressionPatterns() {
    return expressionPatterns;
  }
}
