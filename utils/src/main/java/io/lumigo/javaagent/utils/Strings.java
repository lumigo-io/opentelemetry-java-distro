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
package io.lumigo.javaagent.utils;

public class Strings {

  public static boolean isBlank(String s) {
    int strLen;
    if (s == null || (strLen = s.length()) == 0) {
      return true;
    }
    for (int i = 0; i < strLen; i++) {
      if ((!Character.isWhitespace(s.charAt(i)))) {
        return false;
      }
    }
    return true;
  }
}
