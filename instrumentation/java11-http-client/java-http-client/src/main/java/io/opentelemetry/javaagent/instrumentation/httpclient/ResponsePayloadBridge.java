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
package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ResponsePayloadBridge {
  private static final ContextKey<ResponsePayloadBridge> CONTEXT_KEY =
      ContextKey.named("lumigo-java-http-client-response-payload-bridge");

  private byte[] payloadBuffer = new byte[0];
  private int payloadBufferLength = 0;
  private boolean payloadRetrieved = false;
  private boolean isFirstPayload = true;

  private ResponsePayloadBridge() {}

  public static void appendPayload(Context context, byte[] buffer, int length) {
    ResponsePayloadBridge bridge = context.get(CONTEXT_KEY);
    // Don't append anymore if we've already added the payload at the end of the Span.
    if (bridge != null && !bridge.payloadRetrieved) {
      if (bridge.isFirstPayload) {
        // This is the first time we've been called
        bridge.payloadBuffer = Arrays.copyOfRange(buffer, 0, length);
        bridge.payloadBufferLength = length;
        bridge.isFirstPayload = false;
      } else {
        // There is existing buffer we've captured which must be appended to
        byte[] result = new byte[bridge.payloadBufferLength + length];
        System.arraycopy(bridge.payloadBuffer, 0, result, 0, bridge.payloadBufferLength);
        System.arraycopy(buffer, 0, result, bridge.payloadBufferLength, length);
        bridge.payloadBuffer = result;
        bridge.payloadBufferLength += length;
      }
    }
  }

  public static String getPayload(Context context) {
    ResponsePayloadBridge bridge = context.get(CONTEXT_KEY);
    if (bridge != null && !bridge.payloadRetrieved) {
      bridge.payloadRetrieved = true;

      if (bridge.payloadBufferLength == 0) {
        // No payload captured
        return null;
      }

      // Return the payload
      return new String(bridge.payloadBuffer, 0, bridge.payloadBufferLength, StandardCharsets.UTF_8);
    }

    return null;
  }

  public static class Builder {
    public Context init(Context context) {
      return context.with(ResponsePayloadBridge.CONTEXT_KEY, new ResponsePayloadBridge());
    }
  }
}
