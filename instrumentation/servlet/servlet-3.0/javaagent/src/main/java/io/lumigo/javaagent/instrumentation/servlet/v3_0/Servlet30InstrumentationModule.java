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
package io.lumigo.javaagent.instrumentation.servlet.v3_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Servlet30InstrumentationModule extends InstrumentationModule {
  public Servlet30InstrumentationModule() {
    super("lumigo-servlet", "lumigo-servlet-3.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.lumigo.javaagent.instrumentation.servlet.v3_0.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new Servlet30Instrumentation(),
        new Servlet30AsyncInstrumentation(),
        new ServletRequestInstrumentation(),
        new ServletResponseInstrumentation(),
        new ServletInputStreamInstrumentation(),
        new ServletOutputStreamInstrumentation(),
        new BufferedReaderInstrumentation(),
        new PrintWriterInstrumentation());
  }

  @Override
  public int order() {
    // This instrumentation should run after the OpenTelemetry instrumentation
    return 1;
  }
}
