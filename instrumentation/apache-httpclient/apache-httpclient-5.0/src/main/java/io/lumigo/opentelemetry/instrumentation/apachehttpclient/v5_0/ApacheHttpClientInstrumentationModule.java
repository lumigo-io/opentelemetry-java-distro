/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lumigo.opentelemetry.instrumentation.apachehttpclient.v5_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheHttpClientInstrumentationModule extends InstrumentationModule {

  public ApacheHttpClientInstrumentationModule() {
    super("lumigo-apache-httpclient", "lumigo-apache-httpclient-5.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new ApacheHttpClientInstrumentation(), new ApacheHttpAsyncClientInstrumentation(),
        new SessionInputBufferInstrumentation(), new HttpMessageParserInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.lumigo.opentelemetry.instrumentation.apachehttpclient.");
  }
}
