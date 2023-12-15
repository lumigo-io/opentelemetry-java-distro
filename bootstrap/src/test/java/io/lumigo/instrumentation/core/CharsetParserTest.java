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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class CharsetParserTest {
  @Test
  void getDefaultWhenNull() {
    assertEquals(StandardCharsets.UTF_8, CharsetParser.extractCharset(null));
  }

  @Test
  void getDefaultWhenEmptyString() {
    assertEquals(StandardCharsets.UTF_8, CharsetParser.extractCharset(""));
  }

  @Test
  void getDefaultWhenInvalid() {
    assertEquals(
        StandardCharsets.UTF_8, CharsetParser.extractCharset("text/html; charset=invalid"));
  }

  @Test
  void getDefaultWhenNoCharset() {
    assertEquals(StandardCharsets.UTF_8, CharsetParser.extractCharset("text/html"));
  }

  @Test
  void getCharset() {
    assertEquals(
        StandardCharsets.UTF_16, CharsetParser.extractCharset("text/html; charset=UTF-16"));
  }
}
