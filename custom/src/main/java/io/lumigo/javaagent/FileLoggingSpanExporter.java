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
package io.lumigo.javaagent;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/** A Span Exporter that logs every span at INFO level using java.util.logging. */
public final class FileLoggingSpanExporter implements SpanExporter {
  private FileOutputStream outputStream = null;
  private final AtomicBoolean isShutdown = new AtomicBoolean();

  /** Returns a new {@link FileLoggingSpanExporter}. */
  public static FileLoggingSpanExporter create(String file) throws IOException {
    FileLoggingSpanExporter ret = new FileLoggingSpanExporter();

    ret.outputStream = new FileOutputStream(file, true);

    return ret;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    if (isShutdown.get()) {
      return CompletableResultCode.ofFailure();
    }

    TraceRequestMarshaler traceRequestMarshaler = TraceRequestMarshaler.create(spans);

    try {
      outputStream.write("\r\n\r\n".getBytes());
      JsonGenerator generator =
          new JsonFactory()
              .createGenerator(outputStream)
              .disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT)
              .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
              .useDefaultPrettyPrinter();
      traceRequestMarshaler.writeJsonTo(generator);
      generator.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return CompletableResultCode.ofSuccess();
  }

  /**
   * Flushes the data.
   *
   * @return the result of the operation
   */
  @Override
  public CompletableResultCode flush() {
    try {
      outputStream.flush();
    } catch (IOException e) {
      e.printStackTrace();
      return CompletableResultCode.ofFailure();
    }

    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    if (!isShutdown.compareAndSet(false, true)) {
      return CompletableResultCode.ofSuccess();
    }

    try {
      outputStream.flush();
      outputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
      return CompletableResultCode.ofFailure();
    }
    return CompletableResultCode.ofSuccess();
  }
}
