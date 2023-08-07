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
package io.lumigo.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PayloadBridge {
  private static final ContextKey<PayloadBridge> CONTEXT_KEY =
      ContextKey.named("lumigo-aws-sdk-payload-bridge");

  private byte[] responsePayloadBuffer = new byte[0];
  private int responsePayloadBufferLength = 0;
  private boolean responsePayloadRetrieved = false;
  private boolean isFirstResponsePayload = true;
  private byte[] requestPayloadBuffer = new byte[0];
  private int requestPayloadBufferLength = 0;
  private boolean requestPayloadRetrieved = false;
  private boolean isFirstRequestPayload = true;

  private PayloadBridge() {}

  public static void appendResponsePayload(
      Context context, byte[] buffer, int bodyStartPos, int length) {
    PayloadBridge bridge = context.get(CONTEXT_KEY);
    // Don't append anymore if we've already added the payload at the end of the Span.
    if (bridge != null && !bridge.responsePayloadRetrieved) {
      if (bodyStartPos > -1 && bodyStartPos < length && bridge.isFirstResponsePayload) {
        // This is the first time we've been called
        bridge.responsePayloadBuffer = Arrays.copyOfRange(buffer, bodyStartPos, length);
        bridge.responsePayloadBufferLength = length - bodyStartPos;
        bridge.isFirstResponsePayload = false;
      } else if (!bridge.isFirstResponsePayload) {
        // There is existing buffer we've captured which must be appended to
        byte[] result = new byte[bridge.responsePayloadBufferLength + length];
        System.arraycopy(
            bridge.responsePayloadBuffer, 0, result, 0, bridge.responsePayloadBufferLength);
        System.arraycopy(buffer, 0, result, bridge.responsePayloadBufferLength, length);
        bridge.responsePayloadBuffer = result;
        bridge.responsePayloadBufferLength += length;
      }
    }
  }

  public static String getResponsePayload(Context context) {
    PayloadBridge bridge = context.get(CONTEXT_KEY);
    if (bridge != null && !bridge.responsePayloadRetrieved) {
      bridge.responsePayloadRetrieved = true;

      return new String(
          bridge.responsePayloadBuffer,
          0,
          bridge.responsePayloadBufferLength,
          StandardCharsets.UTF_8);
    }

    return null;
  }

  public static void appendRequestPayload(
      Context context, byte[] buffer, int bodyStartPos, int length) {
    PayloadBridge bridge = context.get(CONTEXT_KEY);
    // Don't append anymore if we've already retrieved the payload.
    if (bridge != null && !bridge.requestPayloadRetrieved) {
      if (bodyStartPos > -1 && bodyStartPos < length && bridge.isFirstRequestPayload) {
        // This is the first time we've been called
        bridge.requestPayloadBuffer = Arrays.copyOfRange(buffer, bodyStartPos, length);
        bridge.requestPayloadBufferLength = length - bodyStartPos;
        bridge.isFirstRequestPayload = false;
      } else if (!bridge.isFirstRequestPayload) {
        // There is existing buffer we've captured which must be appended to
        byte[] result = new byte[bridge.requestPayloadBufferLength + length];
        System.arraycopy(
            bridge.requestPayloadBuffer, 0, result, 0, bridge.requestPayloadBufferLength);
        System.arraycopy(buffer, 0, result, bridge.requestPayloadBufferLength, length);
        bridge.requestPayloadBuffer = result;
        bridge.requestPayloadBufferLength += length;
      }
    }
  }

  public static String getRequestPayload(Context context) {
    PayloadBridge bridge = context.get(CONTEXT_KEY);
    if (bridge != null && !bridge.requestPayloadRetrieved) {
      bridge.requestPayloadRetrieved = true;

      return new String(
          bridge.requestPayloadBuffer,
          0,
          bridge.requestPayloadBufferLength,
          StandardCharsets.UTF_8);
    }

    return null;
  }

  public static class Builder {
    public Context init(Context context) {
      return context.with(PayloadBridge.CONTEXT_KEY, new PayloadBridge());
    }
  }
}
