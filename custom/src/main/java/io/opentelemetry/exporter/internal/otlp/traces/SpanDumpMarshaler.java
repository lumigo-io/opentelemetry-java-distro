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
package io.opentelemetry.exporter.internal.otlp.traces;

import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.ProtoFieldInfo;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.exporter.internal.otlp.ResourceMarshaler;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;

public class SpanDumpMarshaler extends MarshalerWithSize {

  private static final ProtoFieldInfo SPAN = ProtoFieldInfo.create(1, 10, "span");
  private static final ProtoFieldInfo RESOURCE = ProtoFieldInfo.create(2, 18, "resource");

  public static SpanDumpMarshaler create(SpanData spanData) {
    return new SpanDumpMarshaler(
        SpanMarshaler.create(spanData), ResourceMarshaler.create(spanData.getResource()));
  }

  private final SpanMarshaler spanMarshaler;
  private final ResourceMarshaler resourceMarshaler;

  SpanDumpMarshaler(SpanMarshaler spanMarshaler, ResourceMarshaler resourceMarshaler) {
    super(calculateSize(spanMarshaler, resourceMarshaler));
    this.spanMarshaler = spanMarshaler;
    this.resourceMarshaler = resourceMarshaler;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeMessage(SPAN, spanMarshaler);
    output.serializeMessage(RESOURCE, resourceMarshaler);
  }

  private static int calculateSize(
      SpanMarshaler spanMarshaler, ResourceMarshaler resourceMarshaler) {
    int size = 0;
    size += MarshalerUtil.sizeMessage(SPAN, spanMarshaler);
    size += MarshalerUtil.sizeMessage(RESOURCE, resourceMarshaler);
    return size;
  }
}
