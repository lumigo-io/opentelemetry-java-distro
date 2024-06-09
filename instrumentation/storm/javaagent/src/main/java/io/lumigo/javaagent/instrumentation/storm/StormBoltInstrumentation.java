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
import io.opentelemetry.semconv.SemanticAttributes;
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
      span.setAttribute(StormUtils.STORM_TYPE_KEY, "bolt");
      span.setAttribute(StormUtils.COMPONENT_NAME_KEY, StormUtils.getComponentName());
      span.setAttribute(SemanticAttributes.THREAD_NAME, StormUtils.getThreadName());
      span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "storm");
      span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, StormUtils.getMessageId(tuple));
      span.setAttribute(
          AttributeKey.stringArrayKey(StormUtils.STORM_TUPLE_VALUES_KEY),
          StormUtils.getValues(tuple));
      span.setAttribute(StormUtils.SOURCE_COMPONENT_KEY, StormUtils.getSourceComponent(tuple));
      span.setAttribute(StormUtils.STORM_ID_KEY, StormUtils.getStormId(tuple));
      span.setAttribute(StormUtils.STORM_VERSION_KEY, StormUtils.getStormVersion(tuple));
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
