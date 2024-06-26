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
import java.io.UnsupportedEncodingException;

public abstract class AbstractBufferHolder {
  public static final int MAX_BUFFER_LENGTH = 2048;

  protected final Span span;
  protected final String charsetName;

  private boolean bufferCaptured = false;

  public AbstractBufferHolder(Span span, String charsetName) {
    this.span = span;
    this.charsetName = charsetName;
  }

  protected void resetCapture() {
    bufferCaptured = false;
  }

  protected abstract String getBufferAsString(String charsetName)
      throws UnsupportedEncodingException;

  /**
   * This may be called multiple times, but the buffer will only be captured once. Append calls will
   * reset the bufferCaptured flag, enabling re-capture.
   */
  public void captureRequestBody() {
    if (bufferCaptured) {
      return;
    }
    bufferCaptured = true;

    try {
      final String requestBody = getBufferAsString(charsetName);
      if (requestBody != null && !requestBody.isEmpty()) {
        span.setAttribute(LumigoSemanticAttributes.HTTP_REQUEST_BODY, requestBody);
      }
    } catch (UnsupportedEncodingException ignored) {
      // Ignore error, it shouldn't occur as we've previously parsed the charset for validity
    }
  }

  public Span getSpan() {
    return span;
  }

  public void captureResponseBody(Span currentSpan) {
    if (bufferCaptured) {
      return;
    }
    bufferCaptured = true;

    try {
      final String responseBody = getBufferAsString(charsetName);
      if (responseBody != null && !responseBody.isEmpty()) {
        currentSpan.setAttribute(LumigoSemanticAttributes.HTTP_RESPONSE_BODY, responseBody);
      }
    } catch (UnsupportedEncodingException ignored) {
      // Ignore error, it shouldn't occur as we've previously parsed the charset for validity
    }
  }
}
