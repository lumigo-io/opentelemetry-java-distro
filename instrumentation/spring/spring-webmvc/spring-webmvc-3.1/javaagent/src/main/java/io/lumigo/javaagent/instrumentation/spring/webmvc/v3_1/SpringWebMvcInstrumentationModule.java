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
package io.lumigo.javaagent.instrumentation.spring.webmvc.v3_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.DispatcherServletInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.HandlerAdapterInstrumentation;
import net.bytebuddy.matcher.ElementMatcher;
import java.util.Collections;
import java.util.List;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

@AutoService(InstrumentationModule.class)
public class SpringWebMvcInstrumentationModule extends InstrumentationModule {
  public SpringWebMvcInstrumentationModule() {
    super("lumigo-spring-webmvc", "lumigo-spring-webmvc-3.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("javax.servlet.Filter");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith(
        "org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter");
  }

  @Override
  public boolean isIndyModule() {
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new DispatcherServletInstrumentation(), new HandlerAdapterInstrumentation());
  }
}
