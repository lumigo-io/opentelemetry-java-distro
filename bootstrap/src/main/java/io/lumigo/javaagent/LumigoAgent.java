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
package io.lumigo.javaagent;

import io.opentelemetry.javaagent.OpenTelemetryAgent;
import java.lang.instrument.Instrumentation;

public class LumigoAgent {
  public static void premain(final String agentArgs, final Instrumentation inst) {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst) {
    if (is_switch_off()) {
      return;
    }
    OpenTelemetryAgent.agentmain(agentArgs, inst);
  }

  private static boolean isDebugMode() {
    String value = System.getProperty("otel.javaagent.debug");
    if (value == null) {
      value = System.getenv("OTEL_JAVAAGENT_DEBUG");
    }
    if (value == null) {
      value = System.getProperty("lumigo.debug");
    }
    if (value == null) {
      value = System.getenv("LUMIGO_DEBUG");
    }
    return Boolean.parseBoolean(value);
  }

  private static boolean is_switch_off() {
    String value = System.getProperty("lumigo.switch_off");
    if (value == null) {
      value = System.getenv("LUMIGO_SWITCH_OFF");
    }
    return Boolean.parseBoolean(value);
  }
}
