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
package io.lumigo.instrumentation.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

public final class CharsetParser {
  private CharsetParser() {}

  public static Charset extractCharset(String contentTypeHeader) {
    if (contentTypeHeader != null) {
      String[] elements = contentTypeHeader.split(";");
      for (int i = 1; i < elements.length; i++) {
        String element = elements[i].trim();
        if (element.toLowerCase().startsWith("charset")) {
          String charsetStr = element.substring("charset".length() + 1);
          if (charsetStr.startsWith("\"") && charsetStr.endsWith("\"")) {
            charsetStr = charsetStr.substring(1, charsetStr.length() - 1);
          }
          try {
            return Charset.forName(charsetStr);
          } catch (UnsupportedCharsetException e) {
            return StandardCharsets.UTF_8;
          }
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  public static String extractCharsetName(String contentTypeHeader) {
    return extractCharset(contentTypeHeader).name();
  }
}
