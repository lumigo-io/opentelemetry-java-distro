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
package io.lumigo.javaagent.instrumentation.servlet.v3_0;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharBufferHolder;
import io.opentelemetry.api.trace.Span;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public final class HolderFactory {
  private HolderFactory() {}

  public static ByteBufferHolder createByteBufferHolder(ServletRequest request, Span span) {
    return new ByteBufferHolder(
        new ByteArrayOutputStream(), span, getCharset(request.getCharacterEncoding()));
  }

  public static ByteBufferHolder createByteBufferHolder(ServletResponse response, Span span) {
    return new ByteBufferHolder(
        new ByteArrayOutputStream(), span, getCharset(response.getCharacterEncoding()));
  }

  public static CharBufferHolder createCharBufferHolder(ServletRequest request, Span span) {
    return new CharBufferHolder(
        new CharArrayWriter(), span, getCharset(request.getCharacterEncoding()));
  }

  public static CharBufferHolder createCharBufferHolder(ServletResponse response, Span span) {
    return new CharBufferHolder(
        new CharArrayWriter(), span, getCharset(response.getCharacterEncoding()));
  }

  private static String getCharset(String charsetName) {
    try {
      Charset.forName(charsetName);
    } catch (Exception ignored) {
      charsetName = StandardCharsets.UTF_8.name();
    }
    return charsetName;
  }
}
