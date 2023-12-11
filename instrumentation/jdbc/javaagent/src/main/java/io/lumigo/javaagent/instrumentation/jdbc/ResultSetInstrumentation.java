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
package io.lumigo.javaagent.instrumentation.jdbc;

import static io.lumigo.javaagent.instrumentation.jdbc.JdbcSingletons.resultInstrumenter;
import static io.lumigo.javaagent.instrumentation.jdbc.SqlUtility.ATTRIBUTE_VALUE_MAX_LENGTH;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentSpan;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.spanFromContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ResultSetInstrumentation implements TypeInstrumentation {
  public static final String SQL_PAYLOAD_ATTRIBUTE_KEY = "db.results";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.sql.ResultSet");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.ResultSet"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), ResultSetInstrumentation.class.getName() + "$ResultSetConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("next").and(takesArguments(0)).and(isPublic()),
        ResultSetInstrumentation.class.getName() + "$ResultSetNextAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResultSetConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This ResultSet thiz) {
      VirtualField<ResultSet, Context> virtualField =
          VirtualField.find(ResultSet.class, Context.class);
      if (null == virtualField.get(thiz)) {
        virtualField.set(thiz, currentContext());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResultSetNextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ResultSet.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ResultSet thiz,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Return boolean result) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      VirtualField<ResultSet, Context> virtualField =
          VirtualField.find(ResultSet.class, Context.class);
      final Context parentContext = virtualField.get(thiz);
      if (null == parentContext) {
        return;
      }

      if (!resultInstrumenter().shouldStart(parentContext, null)) {
        // If we're not starting, remove virtual field and return
        virtualField.set(thiz, null);
        return;
      }

      Context context = resultInstrumenter().start(parentContext, null);
      try (Scope ignored = context.makeCurrent()) {
        final Span span = currentSpan();

        if (result) {
          final Span parentSpan = spanFromContext(parentContext);

          final ResultSetMetaData metaData = thiz.getMetaData();
          String jsonRow = SqlUtility.getRowObject(thiz, metaData, ATTRIBUTE_VALUE_MAX_LENGTH);
          span.setAttribute(
              SQL_PAYLOAD_ATTRIBUTE_KEY,
              SqlUtility.constructJsonArray(Collections.singletonList(jsonRow)));
        } else {
          // No rows to process in ResultSet
          span.setAttribute(SQL_PAYLOAD_ATTRIBUTE_KEY, "[]");
        }
        resultInstrumenter().end(context, null, null, null);
      } catch (SQLException sqle) {
        resultInstrumenter().end(context, null, null, sqle);
      } finally {
        // Remove the Context from the ResultSet to clear memory
        virtualField.set(thiz, null);
      }
    }
  }
}
