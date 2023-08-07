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

import io.opentelemetry.context.Context;
import java.lang.reflect.Field;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;

/**
 * This class is only necessary to access the package access {@link
 * software.amazon.awssdk.core.interceptor.ExecutionAttribute} from {@link
 * io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor}. It's needed to
 * retrieve the {@link Context} from the {@link
 * software.amazon.awssdk.core.interceptor.ExecutionAttributes}.
 */
public class ContextAttributeBridge {
  public static final ExecutionAttribute<Context> CONTEXT_ATTRIBUTE = getContextAttribute();

  @SuppressWarnings("unchecked")
  private static ExecutionAttribute<Context> getContextAttribute() {
    ExecutionAttribute<Context> context = null;
    try {
      Class<?> clazz =
          Class.forName("io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor");
      Field field = clazz.getDeclaredField("CONTEXT_ATTRIBUTE");
      field.setAccessible(true);
      context = (ExecutionAttribute<Context>) field.get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      // TODO Auto-generated catch block
    }
    return context;
  }
}
