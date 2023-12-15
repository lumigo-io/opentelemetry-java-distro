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
package io.lumigo.javaagent.instrumentation.awssdk.v1_11;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class LumigoIgnoredTypesConfigurer implements IgnoredTypesConfigurer {
  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    // TODO Investigate why these classes are being transformed by {@link
    // InputStreamInstrumentation} when they shouldn't be
    builder.allowClass("com.google.protobuf.AbstractMessageLite$Builder$LimitedInputStream");
    builder.allowClass("com.google.protobuf.IterableByteBufferInputStream");
  }

  @Override
  public int order() {
    // Run after upstream configurers
    return 1;
  }
}
