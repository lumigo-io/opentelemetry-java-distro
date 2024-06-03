/*
 * Copyright 2024 Lumigo LTD
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
package io.lumigo.javaagent.instrumentation.storm;

import static io.lumigo.javaagent.instrumentation.storm.StormSingleton.stormInstrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentSpan;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.storm.tuple.Tuple;

public class StormBoltInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.storm.topology.IBasicBolt"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        named("execute"), StormBoltInstrumentation.class.getName() + "$StormExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class StormExecuteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Tuple tuple,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!stormInstrumenter().shouldStart(parentContext, tuple)) {
        return;
      }

      context = stormInstrumenter().start(parentContext, tuple);
      scope = context.makeCurrent();
      final Span span = currentSpan();
      span.setAttribute("storm.type", "bolt");
      span.setAttribute("messaging.message.id", StormUtils.getMessageId(tuple));
      span.setAttribute(AttributeKey.stringArrayKey("storm.tuple.values"), StormUtils.getValues(tuple));
      span.setAttribute("storm.sourceComponent", StormUtils.getSourceComponent(tuple));
      span.setAttribute("storm.stormId", StormUtils.getStormId(tuple));
      span.setAttribute("storm.version", StormUtils.getStormVersion(tuple));
      span.setAttribute("service.name", StormUtils.getServiceName());
      span.setAttribute("thread.name", StormUtils.getThreadName());
    }

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Tuple tuple,
        @Advice.Thrown Throwable exception,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      stormInstrumenter().end(context, tuple, null, exception);
    }
  }
}
