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
package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public class ResponsePayloadBridge {
  private static final ContextKey<ResponsePayloadBridge> CONTEXT_KEY =
      ContextKey.named("lumigo-apache-httpclient-response-payload-bridge");

  private byte[] payloadBuffer = new byte[0];
  private int payloadBufferLength = 0;
  private boolean payloadRetrieved = false;
  private boolean isFirstPayload = true;
  private boolean isGzipped = false;
  private boolean isChunked = false;
  private Header[] headers;

  private ResponsePayloadBridge() {}

  public static void appendPayload(Context context, byte[] buffer, int bodyStartPos, int length) {
    ResponsePayloadBridge bridge = context.get(CONTEXT_KEY);
    // Don't append anymore if we've already added the payload at the end of the Span.
    if (bridge != null && !bridge.payloadRetrieved) {
      if (bodyStartPos > -1 && bodyStartPos < length && bridge.isFirstPayload) {
        // This is the first time we've been called
        bridge.payloadBuffer = Arrays.copyOfRange(buffer, bodyStartPos, length);
        bridge.payloadBufferLength = length - bodyStartPos;
        bridge.isFirstPayload = false;
      } else if (!bridge.isFirstPayload) {
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

      // If chunked response, ensure we strip the chunk header/trailer from the payload
      if (bridge.isChunked) {
        // Capture chunk header indicating size of chunk
        byte[] chunkHeaderBytes = new byte[15];
        int payloadStart = -1;
        int chunkSize = 0;
        for (int i = 0; i < bridge.payloadBuffer.length; i++) {
          if ((char)bridge.payloadBuffer[i] == '\r') {
            if ((char)bridge.payloadBuffer[i+1] == '\n') {
              // Found CRLF at end of chunk header
              final String chunkHeaderString = new String(chunkHeaderBytes, 0, i, StandardCharsets.UTF_8);
              payloadStart = i+2;
              // Convert the Chunk header from HEX into the number of bytes the chunk contains
              chunkSize = Integer.parseInt(chunkHeaderString, 16);
              break;
            }
          } else {
            // Haven't found the end of the chunk header, capture the byte we found
            chunkHeaderBytes[i] = bridge.payloadBuffer[i];
          }
        }

        if (payloadStart > -1) {
          if (bridge.payloadBufferLength < chunkSize) {
            // We haven't appended enough data to return anything
            return null;
          }

          if (bridge.isGzipped) {
            // Return the payload chunk only, encoded as Base64
            return Base64.getEncoder().encodeToString(
                new String(bridge.payloadBuffer, payloadStart, chunkSize,
                    StandardCharsets.UTF_8).getBytes(
                    StandardCharsets.UTF_8));
          } else {
            // Return the payload chunk only
            return new String(bridge.payloadBuffer, payloadStart, chunkSize,
                StandardCharsets.UTF_8);
          }
        }
      }

      return new String(bridge.payloadBuffer, 0, bridge.payloadBufferLength, StandardCharsets.UTF_8);
    }

    return null;
  }

  public static void responseHeaders(Context context, HttpMessage httpMessage) {
    ResponsePayloadBridge bridge = context.get(CONTEXT_KEY);
    if (bridge != null && httpMessage.getHeaders() != null) {
      bridge.headers = httpMessage.getHeaders();
      checkForGzip(bridge);
      checkForChunked(bridge);
    }
  }

  public static boolean isGzipped(Context context) {
    ResponsePayloadBridge bridge = context.get(CONTEXT_KEY);
    if (bridge != null) {
      return bridge.isGzipped;
    }

    return false;
  }

  private static void checkForGzip(ResponsePayloadBridge bridge) {
    Optional<Header> contentEncodingHeader = Arrays.stream(bridge.headers)
        .filter(header -> header.getName().equalsIgnoreCase("content-encoding"))
        .findFirst();
    if (contentEncodingHeader.isPresent() && "gzip".equalsIgnoreCase(contentEncodingHeader.get().getValue())) {
      bridge.isGzipped = true;
    }
  }

  private static void checkForChunked(ResponsePayloadBridge bridge) {
    Optional<Header> transferEncodingHeader = Arrays.stream(bridge.headers)
        .filter(header -> header.getName().equalsIgnoreCase("transfer-encoding"))
        .findFirst();
    if (transferEncodingHeader.isPresent() && "chunked".equalsIgnoreCase(transferEncodingHeader.get().getValue())) {
      bridge.isChunked = true;
    }
  }

  public static class Builder {
    public Context init(Context context) {
      return context.with(ResponsePayloadBridge.CONTEXT_KEY, new ResponsePayloadBridge());
    }
  }
}
