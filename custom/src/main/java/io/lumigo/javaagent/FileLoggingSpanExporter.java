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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** A Span Exporter that logs every span at INFO level using java.util.logging. */
public final class FileLoggingSpanExporter implements SpanExporter {

  public static final Logger logger = Logger.getLogger(FileLoggingSpanExporter.class.getName());

  /** Returns a new {@link FileLoggingSpanExporter}. */
  public static FileLoggingSpanExporter create(String file) throws IOException {
    return new FileLoggingSpanExporter(file);
  }

  private FileWriter out;
  private final AtomicBoolean isShutdown = new AtomicBoolean();

  private FileLoggingSpanExporter(String file) throws IOException {
    this.out = new FileWriter(file, true);
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    if (isShutdown.get()) {
      return CompletableResultCode.ofFailure();
    }

    TraceRequestMarshaler traceRequestMarshaler = TraceRequestMarshaler.create(spans);

    /*
     * The fact that we are printing one JSON object per line makes the overall document,
     * so we need to recreate the JsonGenerator every time.
     */
    try (JsonGenerator generator =
        new JsonFactory()
            .createGenerator(out)
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET) // Do not close the underpinning file
    ) {
      traceRequestMarshaler.writeJsonTo(generator);
      out.write(System.lineSeparator());
    } catch (IOException e) {
      logger.log(java.util.logging.Level.SEVERE, "Failed to export spans", e);
      return CompletableResultCode.ofFailure();
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
      this.out.flush();
    } catch (IOException e) {
      logger.log(java.util.logging.Level.SEVERE, "Failed to flush spans", e);
      return CompletableResultCode.ofFailure();
    }

    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    if (!isShutdown.compareAndSet(false, true)) {
      return CompletableResultCode.ofSuccess();
    }

    Boolean failed = false;
    try {
      this.out.flush();
    } catch (IOException e) {
      logger.log(java.util.logging.Level.SEVERE, "Failed to flush spans", e);
      failed = true;
    }
    try {
      this.out.close();
    } catch (IOException e) {
      logger.log(java.util.logging.Level.SEVERE, "Failed to close file", e);
      failed = true;
    }

    if (failed) {
      return CompletableResultCode.ofFailure();
    }
    return CompletableResultCode.ofSuccess();
  }
}
