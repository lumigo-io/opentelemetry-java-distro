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
package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaClientTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClientTest.class.getName());
  private static KafkaContainer kafka;
  private static Producer<Integer, String> producer;
  private static Consumer<Integer, String> consumer;
  private static final CountDownLatch consumerReady = new CountDownLatch(1);
  private static final String TOPIC = "my.topic";

  private static final String JSON_BODY =
      "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":42}";

  @BeforeAll
  static void setUp() throws InterruptedException, IOException {
    kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256M")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();

    // Create test topic
    kafka.execInContainer(
        "kafka-topics",
        "--create",
        "--topic",
        TOPIC,
        "--partitions",
        "1",
        "--replication-factor",
        "1",
        "--bootstrap-server",
        kafka.getBootstrapServers());

    producer = new KafkaProducer<>(producerProps());
    consumer = new KafkaConsumer<>(consumerProps());

    consumer.subscribe(
        Collections.singletonList(TOPIC),
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            consumerReady.countDown();
          }
        });
  }

  private static HashMap<String, Object> consumerProps() {
    HashMap<String, Object> props = new HashMap<>();
    props.put("bootstrap.servers", kafka.getBootstrapServers());
    props.put("group.id", "test");
    props.put("enable.auto.commit", "true");
    props.put("auto.commit.interval.ms", 10);
    props.put("session.timeout.ms", "30000");
    props.put("key.deserializer", IntegerDeserializer.class.getName());
    props.put("value.deserializer", StringDeserializer.class.getName());
    return props;
  }

  private static HashMap<String, Object> producerProps() {
    HashMap<String, Object> props = new HashMap<>();
    props.put("bootstrap.servers", kafka.getBootstrapServers());
    props.put("retries", 0);
    props.put("batch.size", "16384");
    props.put("linger.ms", 1);
    props.put("buffer.memory", "33554432");
    props.put("key.serializer", IntegerSerializer.class.getName());
    props.put("value.serializer", StringSerializer.class.getName());
    return props;
  }

  @AfterAll
  static void tearDown() {
    if (null != producer) {
      producer.close();
    }
    if (null != consumer) {
      consumer.close();
    }

    kafka.stop();
  }

  private void awaitUntilConsumerIsReady() throws InterruptedException {
    if (consumerReady.await(0, TimeUnit.SECONDS)) {
      return;
    }
    for (int i = 0; i < 10; i++) {
      consumer.poll(0);
      if (consumerReady.await(1, TimeUnit.SECONDS)) {
        break;
      }
    }
    if (consumerReady.getCount() != 0) {
      throw new IllegalStateException("Consumer was not ready");
    }
    consumer.seekToBeginning(Collections.emptyList());
  }

  private static List<AttributeAssertion> commonAttributes(String clientPrefix) {
    return Arrays.asList(
        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
        equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, TOPIC),
        satisfies(
            SemanticAttributes.MESSAGING_CLIENT_ID, (value) -> value.startsWith(clientPrefix)));
  }

  private static List<AttributeAssertion> sendAttributes(String messageKey, String messageValue) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                    AbstractLongAssert::isNotNegative),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative)));

    assertions.addAll(commonAttributes("producer"));

    if (null != messageKey) {
      assertions.add(equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    if (null != messageValue) {
      assertions.add(
          satisfies(
              AttributeKey.stringArrayKey("messaging.message.headers"),
              AbstractListAssert::isNotEmpty));
      assertions.add(equalTo(AttributeKey.stringKey("messaging.message.payload"), JSON_BODY));
    }

    return assertions;
  }

  private static List<AttributeAssertion> processAttributes(
      String messageKey, String messageValue) {
    List<AttributeAssertion> assertions = new ArrayList<>(commonAttributes("consumer"));

    assertions.add(equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"));
    assertions.add(
        satisfies(
            SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
            AbstractLongAssert::isNotNegative));
    assertions.add(
        satisfies(
            SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative));

    if (null != messageKey) {
      assertions.add(equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    if (null != messageValue) {
      assertions.add(
          satisfies(
              AttributeKey.stringArrayKey("messaging.message.headers"),
              AbstractListAssert::isNotEmpty));
      assertions.add(equalTo(AttributeKey.stringKey("messaging.message.payload"), JSON_BODY));
      assertions.add(
          equalTo(
              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
              messageValue.getBytes(StandardCharsets.UTF_8).length));
    }

    return assertions;
  }

  @Test
  void testProducerAndConsumerSpan() throws Exception {
    instrumentation.runWithSpan(
        "parent",
        () -> {
          producer
              .send(
                  new ProducerRecord<>(TOPIC, 1, JSON_BODY),
                  (meta, ex) -> {
                    if (ex == null) {
                      instrumentation.runWithSpan("producer callback", () -> {});
                    } else {
                      instrumentation.runWithSpan("producer exception: " + ex, () -> {});
                    }
                  })
              .get();
        });

    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(5).toMillis());
    Assertions.assertThat(records.count()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : records) {
      instrumentation.runWithSpan(
          "processing",
          () -> {
            Assertions.assertThat(record.key()).isEqualTo(1);
            Assertions.assertThat(record.value()).isEqualTo(JSON_BODY);
          });
    }

    List<List<SpanData>> traces = instrumentation.waitForTraces(1);
    String parentSpanId = "";
    String producerSpanId = "";
    String consumerSpanId = "";
    for (List<SpanData> trace : traces) {
      for (SpanData span : trace) {
        if (span.getName().equals("parent")) {
          parentSpanId = span.getSpanId();
        } else if (span.getName().equals(TOPIC + " publish")) {
          producerSpanId = span.getSpanId();
        } else if (span.getName().equals(TOPIC + " process")) {
          consumerSpanId = span.getSpanId();
        }
      }
    }

    String finalParentSpanId = parentSpanId;
    String finalProducerSpanId = producerSpanId;
    String finalConsumerSpanId = consumerSpanId;
    TracesAssert.assertThat(traces)
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace -> {
              trace.hasSpansSatisfyingExactlyInAnyOrder(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      span.hasName(TOPIC + " publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParentSpanId(finalParentSpanId)
                          .hasAttributesSatisfying(sendAttributes("1", JSON_BODY)),
                  span ->
                      span.hasName("producer callback")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParentSpanId(finalParentSpanId),
                  span ->
                      span.hasName(TOPIC + " process")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParentSpanId(finalProducerSpanId)
                          .hasAttributesSatisfying(processAttributes("1", JSON_BODY)),
                  span -> span.hasName("processing").hasParentSpanId(finalConsumerSpanId));
            });
  }

  @Test
  void testRecordsWithTopicPartitionKafkaConsume()
      throws ExecutionException, InterruptedException, TimeoutException {
    producer.send(new ProducerRecord<>(TOPIC, 0, null, JSON_BODY)).get(5, TimeUnit.SECONDS);

    instrumentation.waitForTraces(1);

    awaitUntilConsumerIsReady();
    ConsumerRecords<Integer, String> records = consumer.poll(Duration.ofSeconds(5).toMillis());
    List<? extends ConsumerRecord<?, ?>> recordsInPartition =
        records.records(new TopicPartition(TOPIC, 0));
    Assertions.assertThat(recordsInPartition.size()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : recordsInPartition) {
      Assertions.assertThat(record.key()).isNull();
      Assertions.assertThat(record.value()).isEqualTo(JSON_BODY);
    }

    List<List<SpanData>> traces = instrumentation.waitForTraces(1);
    String producerSpanId = "";
    for (List<SpanData> trace : traces) {
      for (SpanData span : trace) {
        if (span.getName().equals(TOPIC + " publish")) {
          producerSpanId = span.getSpanId();
        }
      }
    }
    String finalProducerSpanId = producerSpanId;

    TracesAssert.assertThat(traces)
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(TOPIC + " publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasNoParent()
                          .hasAttributesSatisfying(sendAttributes(null, JSON_BODY)),
                  span ->
                      span.hasName(TOPIC + " process")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParentSpanId(finalProducerSpanId)
                          .hasAttributesSatisfying(processAttributes(null, null)));
            });
  }
}
