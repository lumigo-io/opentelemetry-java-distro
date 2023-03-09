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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LumigoAgent {
  public static void premain(final String agentArgs, final Instrumentation inst) {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst) {
    if (is_switch_off()) {
      System.err.println(
          "Lumigo OpenTelemetry JavaAgent distribution disabled via the 'LUMIGO_SWITCH_OFF' environment variable");
      return;
    }
    System.out.println(
        "Loading the Lumigo OpenTelemetry JavaAgent distribution (version "
            + parseVersion()
            + ") (injection mode: automatic injection)");
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

  private static String parseVersion() {
    String version = LumigoAgent.class.getPackage().getImplementationVersion();
    if (version != null) {
      try {
        Matcher pt = Pattern.compile("^lumigo\\-(.*)?\\-otel.*$").matcher(version);
        if (pt.find()) {
          return pt.group(1);
        }
      } catch (Exception e) {
      }
    }
    return "dev";
  }

  private static boolean is_switch_off() {
    String value = System.getProperty("lumigo.switch_off");
    if (value == null) {
      value = System.getenv("LUMIGO_SWITCH_OFF");
    }
    return Boolean.parseBoolean(value);
  }
}
