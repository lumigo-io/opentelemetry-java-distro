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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

public class RequestPayloadBridge {
  private static final ContextKey<RequestPayloadBridge> CONTEXT_KEY =
      ContextKey.named("lumigo-java-httpclient-request-payload-bridge");

  private byte[] payloadBuffer = new byte[0];
  private int payloadBufferLength = 0;
  private boolean payloadRetrieved = false;
  private boolean isFirstPayload = true;

  private RequestPayloadBridge() {}

  public static void appendPayload(Context context, ByteBuffer buffer) {
    System.out.println("METHOD: appendPayload()");
    System.out.println("CONTEXT: " + currentContext());
    System.out.println("SPAN ID: " + Span.current().getSpanContext().getSpanId());
    System.out.println("PASSED CONTEXT: " + context);
    System.out.println("SPAN ID FROM PASSED CONTEXT: " + Span.fromContext(context).getSpanContext().getSpanId());
    RequestPayloadBridge bridge = context.get(CONTEXT_KEY);
    // Don't append anymore if we've already added the payload at the end of the Span.
    if (bridge != null && !bridge.payloadRetrieved) {
      if (buffer.hasRemaining() && bridge.isFirstPayload) {
        // This is the first time we've been called
        bridge.payloadBuffer = asByteArray(buffer);
        bridge.payloadBufferLength = bridge.payloadBuffer.length;
        bridge.isFirstPayload = false;
      } else if (!bridge.isFirstPayload) {
        // There is existing buffer we've captured which must be appended to
        byte[] toAppend = asByteArray(buffer);
        byte[] result = new byte[bridge.payloadBufferLength + toAppend.length];
        System.arraycopy(bridge.payloadBuffer, 0, result, 0, bridge.payloadBufferLength);
        System.arraycopy(toAppend, 0, result, bridge.payloadBufferLength, toAppend.length);
        bridge.payloadBuffer = result;
        bridge.payloadBufferLength += toAppend.length;
      }
    }
  }

  private static byte[] asByteArray(ByteBuffer buf) {
    byte[] result;
    if (buf.hasArray() && buf.arrayOffset() == 0 && buf.capacity() == buf.remaining()) {
      result = buf.array();
    } else {
      result = new byte[buf.remaining()];
      if (buf.hasArray()) {
        System.arraycopy(buf.array(), buf.arrayOffset() + buf.position(), result, 0, result.length);
      } else {
        // It's a Direct buffer
        ByteBuffer duplicate = buf.duplicate();
        duplicate.mark();
        duplicate.get(result);
        duplicate.reset();
      }
    }
    return result;
  }

  public static void completePayload(Context context) {
    System.out.println("METHOD: completePayload()");
    System.out.println("CONTEXT: " + currentContext());
    System.out.println("SPAN ID: " + Span.current().getSpanContext().getSpanId());
    System.out.println("PASSED CONTEXT: " + context);
    System.out.println("SPAN ID FROM PASSED CONTEXT: " + Span.fromContext(context).getSpanContext().getSpanId());
    RequestPayloadBridge bridge = context.get(CONTEXT_KEY);
    if (bridge != null) {
      // Set the Request Body onto the Span as an Attribute
      final String requestPayload = new String(bridge.payloadBuffer, 0, bridge.payloadBufferLength, StandardCharsets.UTF_8);
      Span.current().setAttribute(HttpPayloadExtractor.HTTP_RESPONSE_BODY_KEY, requestPayload);

      bridge.payloadRetrieved = true;
    }
  }

  public static class Builder {
    public Context init(Context context) {
      return context.with(RequestPayloadBridge.CONTEXT_KEY, new RequestPayloadBridge());
    }
  }
}
