package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.util;

import io.opentelemetry.context.ContextKey;

public class HttpContextKeys {
  public static final ContextKey<String> HTTP_REQUEST_BODY = ContextKey.named("http.request.body");
  public static final ContextKey<String> HTTP_RESPONSE_BODY = ContextKey.named("http.response.body");
}
