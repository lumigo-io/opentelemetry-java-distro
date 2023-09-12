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
package io.lumigo.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.servlet.Servlet;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractServlet30Test {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static final String JSON_BODY =
      "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":42}";

  protected static URI serverUrl;

  protected static void setupServlets(
      BiConsumer<Class<? extends Servlet>, String> servletRegistry) {
    servletPaths()
        .forEach(
            args -> {
              servletRegistry.accept(
                  servlets().get((Integer) args.get()[0]), (String) args.get()[1]);
            });
  }

  protected static void buildUrl(int port) {
    serverUrl = URI.create("http://localhost:" + port);
  }

  private static void assertInstrumentation(String urlPath) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(serverUrl + urlPath))
            .POST(HttpRequest.BodyPublishers.ofString(JSON_BODY))
            .header("Content-Type", "application/json")
            .build();

    try {
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      Assertions.assertThat(response.body()).isEqualTo(JSON_BODY);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    boolean isAsync = urlPath.contains("async");

    TracesAssert.assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace -> {
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("POST " + urlPath)
                            .hasKind(SpanKind.SERVER)
                            .hasAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                            .hasAttribute(AttributeKey.longKey("http.status_code"), 200L)
                            .hasAttribute(AttributeKey.stringKey("http.request.body"), JSON_BODY)
                            .hasAttribute(AttributeKey.stringKey("http.response.body"), JSON_BODY);
                      });
            });
  }

  @ParameterizedTest(name = "{index} => {1}")
  @MethodSource("servletPaths")
  void testServletInstrumentation(ArgumentsAccessor arguments) {
    assertInstrumentation(arguments.getString(1));
  }

  static Stream<Arguments> servletPaths() {
    return Stream.of(
        Arguments.of(0, "/echo_stream_single_byte_print"),
        Arguments.of(1, "/echo_stream_byteArray_print"),
        Arguments.of(2, "/echo_stream_byteArrayOffset_print"),
        Arguments.of(3, "/echo_stream_readLines_print"),
        Arguments.of(4, "/echo_reader_read_write"),
        Arguments.of(5, "/echo_reader_readCharArray_write"),
        Arguments.of(6, "/echo_reader_readCharArrayOffset_write"),
        Arguments.of(7, "/echo_reader_readLine_write"),
        Arguments.of(8, "/echo_reader_readLines_write"),
        Arguments.of(9, "/echo_reader_readLine_print"),
        Arguments.of(10, "/echo_reader_readLine_printArray"),
        Arguments.of(11, "/echo_asyncResponse_stream"),
        Arguments.of(12, "/echo_asyncResponse_writer"));
  }

  static List<Class<? extends Servlet>> servlets() {
    return List.of(
        TestServlets.EchoStream_single_byte_print.class,
        TestServlets.EchoStream_byteArray_print.class,
        TestServlets.EchoStream_byteArrayOffset_print.class,
        TestServlets.EchoStream_readLine_print.class,
        TestServlets.EchoReader_read_write.class,
        TestServlets.EchoReader_readCharArray_write.class,
        TestServlets.EchoReader_readCharArrayOffset_write.class,
        TestServlets.EchoReader_readLine_write.class,
        TestServlets.EchoReader_readLines_write.class,
        TestServlets.EchoReader_readLine_print.class,
        TestServlets.EchoReader_readLine_printArray.class,
        TestServlets.EchoAsyncResponse_stream.class,
        TestServlets.EchoAsyncResponse_writer.class);
  }
}
