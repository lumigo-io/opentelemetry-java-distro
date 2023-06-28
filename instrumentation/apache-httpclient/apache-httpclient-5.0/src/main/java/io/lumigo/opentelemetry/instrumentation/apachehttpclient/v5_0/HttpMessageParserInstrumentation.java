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
package io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class HttpMessageParserInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.hc.core5.http.io.HttpMessageParser");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.hc.core5.http.io.HttpMessageParser"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("parse"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.hc.core5.http.io.SessionInputBuffer")))
            .and(takesArgument(1, named("java.io.InputStream"))),
        this.getClass().getName() + "$ParseHeaderAdvice");

  }

  public static class ParseHeaderAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result) {
      Context context = currentContext();
      // Add the Response Headers to the object on the Context.
      // We can't rely on using the Response Headers in an AttributeExtractor,
      // as some headers are removed. Such as `Content-Encoding: gzip`.
      ResponsePayloadBridge.responseHeaders(context, (HttpMessage) result);
    }
  }
}
