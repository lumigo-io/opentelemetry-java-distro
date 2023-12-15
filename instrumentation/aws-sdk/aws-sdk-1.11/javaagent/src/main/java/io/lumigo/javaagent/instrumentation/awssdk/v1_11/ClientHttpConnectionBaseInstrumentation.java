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

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lumigo.instrumentation.core.ByteBufferHolder;
import io.lumigo.instrumentation.core.CharsetParser;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.impl.BHttpConnectionBase;
import org.apache.http.protocol.HTTP;

public class ClientHttpConnectionBaseInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.http.AmazonHttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.http.impl.BHttpConnectionBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("prepareOutput").and(isProtected()).and(takesArguments(1)),
        ClientHttpConnectionBaseInstrumentation.class.getName() + "$PrepareOutputAdvice");

    transformer.applyAdviceToMethod(
        named("prepareInput").and(isProtected()).and(takesArguments(1)),
        ClientHttpConnectionBaseInstrumentation.class.getName() + "$PrepareInputAdvice");
    transformer.applyAdviceToMethod(
        named("createInputStream").and(isProtected()).and(takesArguments(2)),
        ClientHttpConnectionBaseInstrumentation.class.getName() + "$CreateInputStreamAdvice");
  }

  public static class PrepareOutputAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return OutputStream outputStream) {
      if (outputStream != null) {
        VirtualField.find(OutputStream.class, ByteBufferHolder.class)
            .set(
                outputStream,
                new ByteBufferHolder(
                    new ByteArrayOutputStream(),
                    Java8BytecodeBridge.currentSpan(),
                    StandardCharsets.UTF_8.name()));
      }
    }
  }

  public static class PrepareInputAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This BHttpConnectionBase thiz, @Advice.Argument(0) HttpMessage msg) {
      VirtualField.find(BHttpConnectionBase.class, HttpMessage.class).set(thiz, msg);
    }
  }

  public static class CreateInputStreamAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This BHttpConnectionBase thiz, @Advice.Return InputStream inputStream) {
      if (inputStream != null) {
        String charset = StandardCharsets.UTF_8.name();

        VirtualField<BHttpConnectionBase, HttpMessage> store =
            VirtualField.find(BHttpConnectionBase.class, HttpMessage.class);
        HttpMessage msg = store.get(thiz);
        store.set(thiz, null);

        if (msg != null) {
          final Header contentTypeHeader = msg.getFirstHeader(HTTP.CONTENT_TYPE);
          if (contentTypeHeader != null) {
            charset = CharsetParser.extractCharsetName(contentTypeHeader.getValue());
          }
        }

        VirtualField.find(InputStream.class, ByteBufferHolder.class)
            .set(
                inputStream,
                new ByteBufferHolder(
                    new ByteArrayOutputStream(), Java8BytecodeBridge.currentSpan(), charset));
      }
    }
  }
}
