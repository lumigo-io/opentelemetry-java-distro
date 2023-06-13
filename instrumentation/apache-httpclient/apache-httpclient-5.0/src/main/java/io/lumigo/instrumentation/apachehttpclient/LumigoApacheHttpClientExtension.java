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
package io.lumigo.instrumentation.apachehttpclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientInstrumenter;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import io.opentelemetry.javaagent.tooling.instrumentation.InstrumentationModuleInstaller;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(AgentExtension.class)
public final class LumigoApacheHttpClientExtension implements AgentExtension {

  @Override
  public AgentBuilder extend(AgentBuilder parentAgentBuilder, ConfigProperties config) {
    AgentBuilder.Transformer transformer =
        (builder, typeDescription, classLoader, module, protectionDomain) ->
            builder
                .method(named("instrumenter"))
                .intercept(MethodDelegation.to(ApacheHttpClientInstrumenter.class));

    return parentAgentBuilder
        .type(typeMatcher(), classLoaderOptimization())
        .and(InstrumentationModuleInstaller.NOT_DECORATOR_MATCHER)
        .transform(transformer);
  }

  @Override
  public String extensionName() {
    return "lumigo-apache-httpclient-instrumentation-loader";
  }

  private ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons");
  }

  private ElementMatcher<TypeDescription> typeMatcher() {
    return is(
        "io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons");
  }
}
