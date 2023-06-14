/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpResponse;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;

public class ApacheHttpClientHelper {

  public static void doMethodExit(
      Context context, ClassicHttpRequest request, Object result, Throwable throwable) {
    if (throwable != null) {
      instrumenter().end(context, request, null, throwable);
    } else if (result instanceof HttpResponse) {
      instrumenter().end(context, request, (HttpResponse) result, null);
    } else {
      // ended in WrappingStatusSettingResponseHandler
    }
  }

  private ApacheHttpClientHelper() {}
}
