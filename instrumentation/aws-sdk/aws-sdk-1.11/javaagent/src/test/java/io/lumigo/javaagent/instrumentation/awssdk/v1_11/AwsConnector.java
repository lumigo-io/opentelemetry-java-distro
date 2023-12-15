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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sns.AmazonSNSAsyncClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sns.model.SetTopicAttributesResult;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.*;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

public class AwsConnector {
  private static final String SNS_POLICY =
      "{"
          + "  \"Statement\": ["
          + "    {"
          + "      \"Effect\": \"Allow\","
          + "      \"Principal\": \"*\","
          + "      \"Action\": \"sns:Publish\","
          + "      \"Resource\": \"%s\""
          + "    }]"
          + "}";
  private static final String SQS_POLICY =
      "{"
          + "  \"Statement\": ["
          + "    {"
          + "      \"Effect\": \"Allow\","
          + "      \"Principal\": \"*\","
          + "      \"Action\": \"sqs:SendMessage\","
          + "      \"Resource\": \"%s\""
          + "    }]"
          + "}";

  private LocalStackContainer localstack;
  private AmazonSQSAsyncClient sqsClient;
  private AmazonS3Client s3Client;
  private AmazonSNSAsyncClient snsClient;

  public static AwsConnector localstack() {
    AwsConnector awsConnector = new AwsConnector();

    awsConnector.localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.2"))
            .withServices(
                LocalStackContainer.Service.SQS,
                LocalStackContainer.Service.SNS,
                LocalStackContainer.Service.S3)
            .withEnv("DEBUG", "1")
            .withEnv("SQS_PROVIDER", "elasticmq")
            .withStartupTimeout(Duration.ofMinutes(2));
    awsConnector.localstack.start();
    awsConnector.localstack.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("test")));

    AWSCredentialsProvider credentialsProvider =
        new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(
                awsConnector.localstack.getAccessKey(), awsConnector.localstack.getSecretKey()));

    awsConnector.sqsClient =
        ((AmazonSQSAsyncClient)
            (AmazonSQSAsyncClient.asyncBuilder()
                .withEndpointConfiguration(
                    getEndpointConfiguration(
                        awsConnector.localstack, LocalStackContainer.Service.SQS))
                .withCredentials(credentialsProvider)
                .build()));

    awsConnector.s3Client =
        ((AmazonS3Client)
            (AmazonS3Client.builder()
                .withEndpointConfiguration(
                    getEndpointConfiguration(
                        awsConnector.localstack, LocalStackContainer.Service.S3))
                .withCredentials(credentialsProvider)
                .build()));

    awsConnector.snsClient =
        ((AmazonSNSAsyncClient)
            (AmazonSNSAsyncClient.asyncBuilder()
                .withEndpointConfiguration(
                    getEndpointConfiguration(
                        awsConnector.localstack, LocalStackContainer.Service.SNS))
                .withCredentials(credentialsProvider)
                .build()));

    return awsConnector;
  }

  public static AwsClientBuilder.EndpointConfiguration getEndpointConfiguration(
      LocalStackContainer localstack, LocalStackContainer.Service service) {
    return new AwsClientBuilder.EndpointConfiguration(
        localstack.getEndpointOverride(service).toString(), localstack.getRegion());
  }

  public static AwsConnector liveAws() {
    AwsConnector awsConnector = new AwsConnector();

    awsConnector.sqsClient =
        ((AmazonSQSAsyncClient)
            (AmazonSQSAsyncClient.asyncBuilder().withRegion(Regions.US_EAST_1).build()));

    awsConnector.s3Client =
        ((AmazonS3Client) (AmazonS3Client.builder().withRegion(Regions.US_EAST_1).build()));

    awsConnector.snsClient =
        ((AmazonSNSAsyncClient)
            (AmazonSNSAsyncClient.asyncBuilder().withRegion(Regions.US_EAST_1).build()));

    return awsConnector;
  }

  public String createQueue(final String queueName) {
    DefaultGroovyMethods.println(this, "Create queue " + queueName);
    return sqsClient.createQueue(queueName).getQueueUrl();
  }

  public String getQueueArn(final String queueUrl) {
    DefaultGroovyMethods.println(this, "Get ARN for queue " + queueUrl);
    return sqsClient
        .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("QueueArn"))
        .getAttributes()
        .get("QueueArn");
  }

  public SetTopicAttributesResult setTopicPublishingPolicy(final String topicArn) {
    DefaultGroovyMethods.println(this, "Set policy for topic " + topicArn);
    return snsClient.setTopicAttributes(
        new SetTopicAttributesRequest(topicArn, "Policy", String.format(SNS_POLICY, topicArn)));
  }

  public SetQueueAttributesResult setQueuePublishingPolicy(String queueUrl, final String queueArn) {
    DefaultGroovyMethods.println(this, "Set policy for queue " + queueArn);
    return sqsClient.setQueueAttributes(
        queueUrl, Collections.singletonMap("Policy", String.format(SQS_POLICY, queueArn)));
  }

  public Bucket createBucket(final String bucketName) {
    DefaultGroovyMethods.println(this, "Create bucket " + bucketName);
    return s3Client.createBucket(bucketName);
  }

  public void deleteBucket(final String bucketName) {
    DefaultGroovyMethods.println(this, "Delete bucket " + bucketName);
    ObjectListing objectListing = s3Client.listObjects(bucketName);
    Iterator<S3ObjectSummary> objIter = objectListing.getObjectSummaries().iterator();
    while (objIter.hasNext()) {
      s3Client.deleteObject(bucketName, objIter.next().getKey());
    }

    s3Client.deleteBucket(bucketName);
  }

  public void enableS3ToSqsNotifications(final String bucketName, final String sqsQueueArn) {
    DefaultGroovyMethods.println(
        this, "Enable notification for bucket " + bucketName + " to queue " + sqsQueueArn);
    BucketNotificationConfiguration notificationConfiguration =
        new BucketNotificationConfiguration();
    notificationConfiguration.addConfiguration(
        "sqsQueueConfig",
        new QueueConfiguration(sqsQueueArn, EnumSet.of(S3Event.ObjectCreatedByPut)));
    s3Client.setBucketNotificationConfiguration(
        new SetBucketNotificationConfigurationRequest(bucketName, notificationConfiguration));
  }

  public void enableS3ToSnsNotifications(final String bucketName, final String snsTopicArn) {
    DefaultGroovyMethods.println(
        this, "Enable notification for bucket " + bucketName + " to topic " + snsTopicArn);
    BucketNotificationConfiguration notificationConfiguration =
        new BucketNotificationConfiguration();
    notificationConfiguration.addConfiguration(
        "snsTopicConfig",
        new TopicConfiguration(snsTopicArn, EnumSet.of(S3Event.ObjectCreatedByPut)));
    s3Client.setBucketNotificationConfiguration(
        new SetBucketNotificationConfigurationRequest(bucketName, notificationConfiguration));
  }

  public String createTopicAndSubscribeQueue(final String topicName, final String queueArn) {
    DefaultGroovyMethods.println(
        this, "Create topic " + topicName + " and subscribe to queue " + queueArn);
    CreateTopicResult ctr = snsClient.createTopic(topicName);
    snsClient.subscribe(ctr.getTopicArn(), "sqs", queueArn);
    return ctr.getTopicArn();
  }

  public ReceiveMessageResult receiveMessage(final String queueUrl) {
    DefaultGroovyMethods.println(this, "Receive message from queue " + queueUrl);
    return sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl).withWaitTimeSeconds(20));
  }

  public PurgeQueueResult purgeQueue(final String queueUrl) {
    DefaultGroovyMethods.println(this, "Purge queue " + queueUrl);
    return sqsClient.purgeQueue(new PurgeQueueRequest(queueUrl));
  }

  public PutObjectResult putSampleData(final String bucketName) {
    DefaultGroovyMethods.println(this, "Put sample data to bucket " + bucketName);
    return s3Client.putObject(bucketName, "otelTestKey", "otelTestData");
  }

  public PublishResult publishSampleNotification(String topicArn) {
    return snsClient.publish(topicArn, "Hello There");
  }

  public void disconnect() {
    if (localstack != null) {
      localstack.stop();
    }
  }
}
