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
package io.lumigo.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import io.opentelemetry.javaagent.instrumentation.jedis.v1_4.JedisConnectionInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jedis.v1_4.JedisInstrumentation;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JedisInstrumentationModule extends InstrumentationModule {

  public JedisInstrumentationModule() {
    super("jedis", "jedis-1.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Avoid matching 3.x
    return not(hasClassesNamed("redis.clients.jedis.commands.ProtocolCommand"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new JedisConnectionInstrumentation(), new JedisInstrumentation());
  }
}
