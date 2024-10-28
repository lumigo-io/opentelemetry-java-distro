/*
 * Copyright 2024 Lumigo LTD
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
package io.lumigo.javaagent.instrumentation.netty.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class NettyTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static NettyServer server;
  private static int serverPort;

  @BeforeAll
  public static void setup() throws InterruptedException {
    serverPort = PortUtils.findOpenPort();
    server = new NettyServer(serverPort);
    server.start();
    Thread.sleep(1000); // Wait briefly to ensure the server is fully initialized
  }

  @Test
  public void testHelloWorldGetResponse() throws InterruptedException {
    NettyHttpClient client = new NettyHttpClient("localhost", serverPort);
    var response = client.sendGetRequest(); // New GET request method

    // Validate the HTTP response
    assertEquals(HttpResponseStatus.OK, response.getStatus());
    String responseBody = response.content().toString(StandardCharsets.UTF_8);
    assertEquals("Hello, World!", responseBody);
  }

  @Test
  public void testEchoPostResponse() throws InterruptedException {
    NettyHttpClient client = new NettyHttpClient("localhost", serverPort);
    String testContent = "This is a test message";
    var response = client.sendPostRequest(testContent); // New POST request method

    // Validate the HTTP response
    assertEquals(HttpResponseStatus.OK, response.getStatus());
    String responseBody = response.content().toString(StandardCharsets.UTF_8);
    assertEquals(testContent, responseBody); // Should echo back the posted content
  }

  @AfterAll
  public static void teardown() {
    server.stop();
  }
}
