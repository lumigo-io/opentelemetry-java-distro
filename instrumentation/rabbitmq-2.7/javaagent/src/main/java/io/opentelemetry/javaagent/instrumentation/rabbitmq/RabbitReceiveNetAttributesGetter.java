/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import java.net.Inet6Address;

public class RabbitReceiveNetAttributesGetter
    implements NetClientAttributesGetter<ReceiveRequest, GetResponse> {

  @Nullable
  @Override
  public String getServerAddress(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getServerSocketAddress(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getAddress().getHostAddress();
  }

  @Nullable
  @Override
  public Integer getServerSocketPort(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getPort();
  }

  @Nullable
  @Override
  public String getSockFamily(ReceiveRequest request, @Nullable GetResponse response) {
    if (request.getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }
}
