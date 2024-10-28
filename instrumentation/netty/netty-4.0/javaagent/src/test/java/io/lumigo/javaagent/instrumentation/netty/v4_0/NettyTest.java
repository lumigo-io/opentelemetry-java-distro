package io.lumigo.javaagent.instrumentation.netty.v4_0;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
