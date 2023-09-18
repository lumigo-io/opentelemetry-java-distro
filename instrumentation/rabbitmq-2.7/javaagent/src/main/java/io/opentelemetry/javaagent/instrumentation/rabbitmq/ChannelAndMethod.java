/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.Channel;
import java.util.Map;

@AutoValue
public abstract class ChannelAndMethod {

  public static ChannelAndMethod create(Channel channel, String method) {
    return new AutoValue_ChannelAndMethod(channel, method);
  }

  abstract Channel getChannel();

  abstract String getMethod();

  private Map<String, Object> headers;

  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, Object> headers) {
    this.headers = headers;
  }
}
