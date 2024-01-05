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
package io.lumigo.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SnsTracingTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static final String CREATE_QUEUE_RESPONSE =
      "<?xml version='1.0' encoding='utf-8'?>\n"
          + "<CreateQueueResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\"><CreateQueueResult><QueueUrl>http://127.0.0.1:";
  private static final String PUBLISH_REQUEST =
      "Action=Publish&Version=2010-03-31&TopicArn=arn%3Aaws%3Asns%3Aus-east-1%3A000000000000%3AsnsToSqsTestTopic&Message=Hello+There";
  private static final String PUBLISH_RESPONSE =
      "<?xml version='1.0' encoding='utf-8'?>\n"
          + "<PublishResponse xmlns=\"http://sns.amazonaws.com/doc/2010-03-31/\"><PublishResult>";
  private static final String RECEIVE_MESSAGE_REQUEST =
      "Action=ReceiveMessage&Version=2012-11-05&AttributeName.1=AWSTraceHeader&WaitTimeSeconds=20";

  private static final AwsConnector awsConnector = AwsConnector.localstack();

  @AfterAll
  static void afterAll() {
    awsConnector.disconnect();
  }

  @Test
  // TODO Update to use new http semantic conventions in 2.0
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void snsNotificationTriggersSqsMessage() {
    String queueName = "snsToSqsTestQueue";
    String topicName = "snsToSqsTestTopic";

    String queueUrl = awsConnector.createQueue(queueName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn);

    PublishResult publishResult = awsConnector.publishSampleNotification(topicArn);
    ReceiveMessageResult receivedMessage = awsConnector.receiveMessage(queueUrl);

    instrumentation.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.CreateQueue")
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
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSQS");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_METHOD, "CreateQueue");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey("http.response.body"),
                                          value -> {
                                            Assertions.assertThat(value)
                                                .contains(CREATE_QUEUE_RESPONSE);
                                            Assertions.assertThat(value).contains("<RequestId>");
                                          });
                                })),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.GetQueueAttributes")
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
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSQS");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.RPC_METHOD, "GetQueueAttributes");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                })),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.SetQueueAttributes")
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
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSQS");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.RPC_METHOD, "SetQueueAttributes");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                })),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SNS.CreateTopic")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSNS");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_METHOD, "CreateTopic");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                })),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SNS.Subscribe")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSNS");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_METHOD, "Subscribe");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                })),
        trace ->
            trace
                .hasSize(2)
                .hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SNS.Publish")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSNS");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_METHOD, "Publish");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey("http.request.body"),
                                          value -> {
                                            Assertions.assertThat(value).contains(PUBLISH_REQUEST);
                                            Assertions.assertThat(value)
                                                .contains(
                                                    "Message="
                                                        + URLEncoder.encode(
                                                            "Hello There", StandardCharsets.UTF_8));
                                          });
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey("http.response.body"),
                                          value -> {
                                            Assertions.assertThat(value).contains(PUBLISH_RESPONSE);
                                            Assertions.assertThat(value).contains("<MessageId>");
                                          });
                                }),
                    span ->
                        span.hasName("SQS.ReceiveMessage")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                attrs -> {
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.agent"), "java-aws-sdk");
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("aws.queue.url"), queueUrl);
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSQS");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.RPC_METHOD, "ReceiveMessage");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  // TODO: Add message id check
                                  //                                  assertThat(attrs)
                                  //
                                  // .containsKey(SemanticAttributes.MESSAGING_MESSAGE_ID);
                                  // TODO: Add message payload check
                                  //                                  assertThat(attrs)
                                  //                                      .hasEntrySatisfying(
                                  //
                                  // AttributeKey.stringKey("messaging.message.payload"),
                                  //                                          value -> {
                                  //
                                  // Assertions.assertThat(value).contains("MessageId=");
                                  //
                                  // Assertions.assertThat(value).contains("ReceiptHandle=");
                                  //
                                  // Assertions.assertThat(value).contains(messageBody);
                                  //
                                  // Assertions.assertThat(value).contains("AWSTraceHeader=Root");
                                  //                                          });
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
                        span.hasName("SQS.ReceiveMessage")
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
                                      .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.RPC_SERVICE, "AmazonSQS");
                                  assertThat(attrs)
                                      .containsEntry(
                                          SemanticAttributes.RPC_METHOD, "ReceiveMessage");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "POST");
                                  assertThat(attrs)
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200L);
                                  assertThat(attrs)
                                      .containsEntry(
                                          AttributeKey.stringKey("http.request.body"),
                                          RECEIVE_MESSAGE_REQUEST);
                                  assertThat(attrs)
                                      .hasEntrySatisfying(
                                          AttributeKey.stringKey("http.response.body"),
                                          value -> {
                                            Assertions.assertThat(value)
                                                .contains(
                                                    "<ReceiveMessageResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\">");
                                            Assertions.assertThat(value)
                                                .contains(
                                                    "&quot;Message&quot;: &quot;Hello There&quot;");
                                            Assertions.assertThat(value).contains("<MessageId>");
                                          });
                                })));
  }
}
