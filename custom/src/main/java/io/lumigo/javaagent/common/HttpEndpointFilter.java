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

import java.util.Arrays;
import java.util.List;

public class HttpEndpointFilter extends AbstractRegExParser {
  public static final String LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX =
      "lumigo.filter.http.endpoints.regex";
  public static final String LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT =
      "lumigo.filter.http.endpoints.regex.client";
  public static final String LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER =
      "lumigo.filter.http.endpoints.regex.server";

  public static final List<String> DEFAULT_REGEX_KEYS =
      Arrays.asList(".*/health.*", ".*/actuator.*");

  @Override
  protected String getEnvVarName() {
    return LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX;
  }

  @Override
  protected List<String> getDefaultRegexKeys() {
    return DEFAULT_REGEX_KEYS;
  }
}
