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
package io.lumigo.javaagent.instrumentation.netty.v4_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Netty41InstrumentationModule extends InstrumentationModule {
  public Netty41InstrumentationModule() {
    super("lumigo-netty", "lumigo-netty-4.1");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.lumigo.javaagent.instrumentation.netty.v4_1.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new NettyChannelPipelineInstrumentation());
  }

  @Override
  public int order() {
    // This instrumentation should run after the OpenTelemetry instrumentation
    return 1;
  }
}
