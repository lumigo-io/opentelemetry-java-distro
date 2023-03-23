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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LumigoVersion {
  public static final String VERSION = parseVersion();
  private static final String VERSION_PATTERN = "^lumigo\\-(.*)?\\-otel.*$";

  private static String parseVersion() {
    String version = LumigoVersion.class.getPackage().getImplementationVersion();
    final Pattern pattern = Pattern.compile(VERSION_PATTERN);
    try {
      Matcher pt = pattern.matcher(version);
      if (pt.find()) {
        return pt.group(1);
      }
    } catch (Exception e) {
      // ignore
    }
    return "unknown";
  }
}
