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

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/** A Span Exporter that logs every span at INFO level using java.util.logging. */
public final class FileLoggingSpanExporter implements SpanExporter {
  private FileHandler fileHandler = null;
  private final AtomicBoolean isShutdown = new AtomicBoolean();

  /** Returns a new {@link FileLoggingSpanExporter}. */
  public static FileLoggingSpanExporter create(String file) throws IOException {
    FileLoggingSpanExporter ret = new FileLoggingSpanExporter();

    ret.fileHandler = new FileHandler(file, true);
    ret.fileHandler.setFormatter(new SimpleFormatter());

    return ret;
  }

  /**
   * Class constructor.
   *
   * @deprecated Use {@link #create(String file)}.
   */
  @Deprecated
  public FileLoggingSpanExporter() {}

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    if (isShutdown.get()) {
      return CompletableResultCode.ofFailure();
    }

    // We always have 32 + 16 + name + several whitespace, 60 seems like an OK initial guess.
    StringBuilder sb = new StringBuilder(60);
    for (SpanData span : spans) {
      InstrumentationScopeInfo instrumentationScopeInfo = span.getInstrumentationScopeInfo();

      sb.setLength(0);
      sb.append("Span{");
      sb.append("traceId=").append(span.getTraceId());
      sb.append(", parentId=").append(span.getParentSpanId());
      sb.append(", name=").append(span.getName());
      sb.append(", id=").append(span.getSpanId());
      sb.append(", kind=").append(span.getKind());
      sb.append(", timestamp=").append(new Date(span.getStartEpochNanos()));
      sb.append(", duration=")
          .append(Duration.ofNanos(span.getEndEpochNanos() - span.getStartEpochNanos()));
      sb.append(", attributes=").append(span.getAttributes());
      sb.append(", status=").append(span.getStatus());
      sb.append(", events=").append(span.getEvents());
      sb.append(", tracer={");
      sb.append("name=").append(instrumentationScopeInfo.getName());
      sb.append(", version=").append(instrumentationScopeInfo.getVersion());
      sb.append('}');
      sb.append(", resource=").append(span.getResource());
      sb.append('}');

      log(sb.toString());
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
    CompletableResultCode resultCode = new CompletableResultCode();

    fileHandler.flush();

    return resultCode.succeed();
  }

  private void log(String msg) {
    LogRecord record = new LogRecord(Level.INFO, msg);
    record.setLoggerName(FileLoggingSpanExporter.class.getName());
    fileHandler.publish(record);
  }

  @Override
  public CompletableResultCode shutdown() {
    if (!isShutdown.compareAndSet(false, true)) {
      log("Calling shutdown() multiple times.");
      return CompletableResultCode.ofSuccess();
    }
    return flush();
  }
}
