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

import io.opentelemetry.api.trace.Span;
import java.io.CharArrayWriter;

public final class CharBufferHolder extends AbstractBufferHolder {
  private final CharArrayWriter buffer;

  public CharBufferHolder(CharArrayWriter buffer, Span span, String charsetName) {
    super(span, charsetName);
    this.buffer = buffer;
  }

  @Override
  protected String getBufferAsString(String charsetName) {
    return buffer.toString();
  }

  public void append(int c) {
    resetCapture();
    buffer.write(c);
  }

  public void append(char[] chars) {
    resetCapture();
    buffer.write(chars, 0, chars.length);
  }

  public void append(char[] chars, int off, int len) {
    resetCapture();
    buffer.write(chars, off, len);
  }

  public void append(String str) {
    if (str == null) {
      str = "null";
    }

    resetCapture();
    buffer.write(str, 0, str.length());
  }

  public void append(String str, int off, int len) {
    resetCapture();
    buffer.write(str, off, len);
  }
}
