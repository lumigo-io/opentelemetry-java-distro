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
package io.lumigo.javaagent.instrumentation.awssdk.v2_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AwsSdkInstrumentationModule extends InstrumentationModule {
  public AwsSdkInstrumentationModule() {
    super("lumigo-aws-sdk", "lumigo-aws-sdk-2.2");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.lumigo.javaagent.instrumentation.awssdk.v2_2.")
        || className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  /**
   * Injects resource file with reference to our {@link TracingExecutionInterceptor} to allow SDK's
   * service loading mechanism to pick it up.
   */
  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register("software/amazon/awssdk/global/handlers/execution.interceptors");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // We don't actually transform it but want to make sure we only apply the instrumentation when
    // our key dependency is present.
    return hasClassesNamed("software.amazon.awssdk.core.interceptor.ExecutionInterceptor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new ResourceInjectingTypeInstrumentation(),
        new SessionInputBufferInstrumentation(),
        new SessionOutputBufferInstrumentation());
  }

  // A type instrumentation is needed to trigger resource injection.
  public static class ResourceInjectingTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // This is essentially the entry point of the AWS SDK, all clients implement it. We can ensure
      // our interceptor service definition is injected as early as possible if we typematch against
      // it.
      return named("software.amazon.awssdk.core.SdkClient");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      // Nothing to transform, this type instrumentation is only used for injecting resources.
    }
  }
}
