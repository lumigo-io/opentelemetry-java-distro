/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.URIAuthority;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {
  private static final Logger logger =
      Logger.getLogger(ApacheHttpClientHttpAttributesGetter.class.getName());

  @Override
  public String getMethod(HttpRequest request) {
    return request.getMethod();
  }

  @Override
  public String getUrl(HttpRequest request) {
    // similar to org.apache.hc.core5.http.message.BasicHttpRequest.getUri()
    // not calling getUri() to avoid unnecessary conversion
    StringBuilder url = new StringBuilder();
    URIAuthority authority = request.getAuthority();
    if (authority != null) {
      String scheme = request.getScheme();
      if (scheme != null) {
        url.append(scheme);
        url.append("://");
      } else {
        url.append("http://");
      }
      url.append(authority.getHostName());
      int port = authority.getPort();
      if (port >= 0) {
        url.append(":");
        url.append(port);
      }
    }
    String path = request.getPath();
    if (path != null) {
      if (url.length() > 0 && !path.startsWith("/")) {
        url.append("/");
      }
      url.append(path);
    } else {
      url.append("/");
    }
    return url.toString();
  }

  @Override
  public List<String> getRequestHeader(HttpRequest request, String name) {
    return getHeader(request, name);
  }

  @Override
  public Integer getStatusCode(
      HttpRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.getCode();
  }

  @Override
  @Nullable
  public String getFlavor(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = getVersion(request, response);
    if (protocolVersion == null) {
      return null;
    }
    String protocol = protocolVersion.getProtocol();
    if (!protocol.equals("HTTP")) {
      return null;
    }
    int major = protocolVersion.getMajor();
    int minor = protocolVersion.getMinor();
    if (major == 1 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    if (major == 1 && minor == 1) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
    if (major == 2 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    logger.log(Level.FINE, "unexpected http protocol version: {0}", protocolVersion);
    return null;
  }

  @Override
  public List<String> getResponseHeader(HttpRequest request, HttpResponse response, String name) {
    return getHeader(response, name);
  }

  private static ProtocolVersion getVersion(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null && response != null) {
      protocolVersion = response.getVersion();
    }
    return protocolVersion;
  }

  private static List<String> getHeader(MessageHeaders messageHeaders, String name) {
    return headersToList(messageHeaders.getHeaders(name));
  }

  // minimize memory overhead by not using streams
  private static List<String> headersToList(Header[] headers) {
    if (headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (Header header : headers) {
      headersList.add(header.getValue());
    }
    return headersList;
  }
}
