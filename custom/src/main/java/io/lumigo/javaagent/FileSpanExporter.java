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
import io.opentelemetry.exporter.internal.otlp.traces.SpanDumpMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** A Span Exporter that logs every span at INFO level using java.util.logging. */
public final class FileSpanExporter implements SpanExporter {

  static final JsonFactory JSON_FACTORY =
      new JsonFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

  private static final Logger LOGGER = Logger.getLogger(FileSpanExporter.class.getName());

  /** Returns a new {@link FileSpanExporter}. */
  public static FileSpanExporter create(String file) throws IOException {
    return new FileSpanExporter(file);
  }

  private final FileWriter out;
  private final AtomicBoolean isShutdown = new AtomicBoolean();

  FileSpanExporter(@Nonnull String file) throws IOException {
    this.out = new FileWriter(file, true);
  }

  @Override
  public CompletableResultCode export(@Nonnull Collection<SpanData> spans) {
    if (isShutdown.get()) {
      return CompletableResultCode.ofFailure();
    }

    try (JsonGenerator gen = JSON_FACTORY.createGenerator(out)) {
      for (final SpanData span : spans) {
        final SpanDumpMarshaler spanDumpMarshaler = SpanDumpMarshaler.create(span);
        spanDumpMarshaler.writeJsonTo(gen);
      }
    } catch (Exception e) {
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
      LOGGER.log(java.util.logging.Level.SEVERE, "Failed to flush spans", e);
      return CompletableResultCode.ofFailure();
    }

    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    if (!isShutdown.compareAndSet(false, true)) {
      return CompletableResultCode.ofSuccess();
    }

    boolean success = true;
    try {
      this.out.flush();
    } catch (IOException e) {
      LOGGER.log(java.util.logging.Level.SEVERE, "Failed to flush spans", e);
      success = false;
    }
    try {
      this.out.close();
    } catch (IOException e) {
      LOGGER.log(java.util.logging.Level.SEVERE, "Failed to close file", e);
      success = false;
    }

    return success ? CompletableResultCode.ofSuccess() : CompletableResultCode.ofFailure();
  }
}
