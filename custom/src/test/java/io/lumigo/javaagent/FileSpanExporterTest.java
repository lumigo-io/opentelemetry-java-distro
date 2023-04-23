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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasLength;

import io.opentelemetry.sdk.common.CompletableResultCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * {@link FileSpanExporter} is tested indirectly in the smoke tests.
 */
public class FileSpanExporterTest {

  @Test
  void testEmptySpanDumpSerialization(@TempDir Path tempDir) throws Exception {
    var spanDumpFile = Paths.get(tempDir.toAbsolutePath().toString(), "spandump.json");
    try (final var it = new FileSpanExporter(spanDumpFile.toString())) {
      assertThat(it.export(Collections.emptyList()), equalTo(CompletableResultCode.ofSuccess()));
      assertThat(Files.readString(spanDumpFile), hasLength(0));
    }
  }
}
