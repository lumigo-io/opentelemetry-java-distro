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
package io.lumigo.javaagent.instrumentation.awssdk.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Condition;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class Aws2SqsTracingTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static SQSRestServer sqsServer;

  private static int sqsServerPort;

  private static SqsClient client;

  private static final String createQueueResponse =
      "<CreateQueueResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\">\n"
          + "                  <CreateQueueResult>\n"
          + "                    <QueueUrl>http://localhost:11001/queue/testQueue</QueueUrl>\n"
          + "                  </CreateQueueResult>\n"
          + "                  <ResponseMetadata>\n"
          + "                    <RequestId>00000000-0000-0000-0000-000000000000</RequestId>\n"
          + "                  </ResponseMetadata>\n"
          + "                </CreateQueueResponse>";

  private static final String sendMessageRequest =
      "Action=SendMessage&Version=2012-11-05&QueueUrl=http%3A%2F%2Flocalhost%3A11001%2F000000000000%2FtestQueue&MessageBody=%7B%22type%22%3A+%22hello%22%7D";

  private static final String receiveMessageRequest =
      "Action=ReceiveMessage&Version=2012-11-05&QueueUrl=http%3A%2F%2Flocalhost%3A11001%2F000000000000%2FtestQueue&AttributeName.1=AWSTraceHeader&MessageAttributeName.1=traceparent&MessageAttributeName.2=tracestate&MessageAttributeName.3=baggage";

  @BeforeAll
  static void setUp() throws URISyntaxException {
    sqsServerPort = PortUtils.findOpenPort();
    sqsServer = SQSRestServerBuilder.withPort(sqsServerPort).withInterface("localhost").start();
    client =
        SqsClient.builder()
            .endpointOverride(new URI("http://localhost:" + sqsServerPort))
            .region(Region.US_EAST_1)
            .build();
  }

  @AfterAll
  static void tearDown() {
    if (null != sqsServer) {
      sqsServer.stopAndWait();
    }
  }

  @Test
  void testSqsProducerConsumerService() {
    String queueName = "testQueue";
    String messageBody = "{\"type\": \"hello\"}";

    // Create the queue in SQS
    client.createQueue(builder -> builder.queueName(queueName).build());
    String queueUrl = "http://localhost:" + sqsServerPort + "/000000000000/" + queueName;

    // Send and receive a message
    client.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(messageBody).build());
    ReceiveMessageResponse response =
        client.receiveMessage(builder -> builder.queueUrl(queueUrl).build());
    assertThat(response.messages()).hasSize(1);
    Condition<Message> messageCondition =
        new Condition<>(
            message -> message.body().equals(messageBody), "message body equals " + messageBody);
    assertThat(response.messages().get(0)).has(messageCondition);

    instrumentation.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("Sqs.CreateQueue")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.queue.name"), queueName);
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.requestId"),
                                          "00000000-0000-0000-0000-000000000000");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "Sqs");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_METHOD, "CreateQueue");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.NET_PEER_NAME, "localhost");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.NET_PEER_PORT, sqsServerPort);
                                  assertThat(attrs)
                                      .containsEntry(
                                          TracingExecutionInterceptor.HTTP_RESPONSE_BODY_KEY,
                                          createQueueResponse);
                                })),
        trace ->
            trace
                .hasSize(2)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("Sqs.SendMessage")
                            .hasKind(SpanKind.PRODUCER)
                            .hasNoParent()
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.queue.url"), queueUrl);
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.requestId"),
                                          "00000000-0000-0000-0000-000000000000");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "Sqs");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_METHOD, "SendMessage");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.NET_PEER_NAME, "localhost");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.NET_PEER_PORT, sqsServerPort);
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey(
                                              TracingExecutionInterceptor.HTTP_REQUEST_BODY_KEY),
                                          value -> {
                                            assertThat(value).contains(sendMessageRequest);
                                            assertThat(value)
                                                .contains(
                                                    "MessageBody="
                                                        + URLEncoder.encode(
                                                            messageBody, StandardCharsets.UTF_8));
                                          });
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey(
                                              TracingExecutionInterceptor.HTTP_RESPONSE_BODY_KEY),
                                          value -> {
                                            assertThat(value)
                                                .contains(
                                                    "<SendMessageResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\">");
                                            assertThat(value)
                                                .contains(
                                                    "<RequestId>00000000-0000-0000-0000-000000000000</RequestId>");
                                            assertThat(value).contains("<MessageId>");
                                          });
                                }),
                    span ->
                        span.hasName("Sqs.ReceiveMessage")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "Sqs");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.RPC_METHOD, "ReceiveMessage");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.NET_PEER_NAME, "localhost");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.NET_PEER_PORT, sqsServerPort);
                                  assertThat(attrs)
                                      .containsKey(SemanticAttributes.MESSAGING_MESSAGE_ID);
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey("messaging.message.payload"),
                                          value -> {
                                            assertThat(value).contains("MessageId=");
                                            assertThat(value).contains("ReceiptHandle=");
                                            assertThat(value).contains(messageBody);
                                            assertThat(value).contains("AWSTraceHeader=Root");
                                          });
                                })),
        /*
         This span represents HTTP "sending of receive message" operation. It's always single,
         while there can be multiple CONSUMER spans (one per consumed message). This one could be
         suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation
         span would appear
        */
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("Sqs.ReceiveMessage")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.queue.url"), queueUrl);
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.requestId"),
                                          "00000000-0000-0000-0000-000000000000");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "Sqs");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.RPC_METHOD, "ReceiveMessage");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.NET_PEER_NAME, "localhost");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.NET_PEER_PORT, sqsServerPort);
                                  assertThat(attrs)
                                      .containsEntry(
                                          TracingExecutionInterceptor.HTTP_REQUEST_BODY_KEY,
                                          receiveMessageRequest);
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey(
                                              TracingExecutionInterceptor.HTTP_RESPONSE_BODY_KEY),
                                          value -> {
                                            assertThat(value)
                                                .contains(
                                                    "<ReceiveMessageResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\">");
                                            assertThat(value)
                                                .contains(
                                                    "<RequestId>00000000-0000-0000-0000-000000000000</RequestId>");
                                            assertThat(value)
                                                .contains(
                                                    "<Body>{&quot;type&quot;: &quot;hello&quot;}</Body>");
                                            assertThat(value).contains("<MessageId>");
                                          });
                                })));
  }
}
