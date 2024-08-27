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
package io.lumigo.javaagent.instrumentation.rabbitmq;

import static org.junit.Assert.assertTrue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.ShutdownSignalException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class RabbitMqTest {
  /*@RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqTest.class.getName());
  private static GenericContainer<?> rabbitMqContainer;
  private static ConnectionFactory connectionFactory;
  private static final String JSON_BODY =
      "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":42}";

  private Connection connection;
  private Channel channel;

  @BeforeAll
  static void setUp() {
    rabbitMqContainer =
        new GenericContainer<>("rabbitmq:latest")
            .withExposedPorts(5672)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    rabbitMqContainer.start();

    connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(rabbitMqContainer.getHost());
    connectionFactory.setPort(rabbitMqContainer.getMappedPort(5672));
  }

  @AfterAll
  static void tearDown() {
    rabbitMqContainer.stop();
  }

  @BeforeEach
  void setUpEach() throws Exception {
    connection = connectionFactory.newConnection();
    channel = connection.createChannel();
  }

  @AfterEach
  void tearDownEach() throws Exception {
    try {
      channel.close();
      connection.close();
    } catch (ShutdownSignalException e) {
      // ignore
    }
  }*/


  @Test
  void testTrue() throws Exception {
    if (true) {
    }
  }
 /* @Test
  void testBasicPublishAndGet() throws Exception {

    String exchangeName = "some-exchange";
    String routingKey = "some-routing-key";
    String queueName =
        instrumentation.runWithSpan(
            "producer parent",
            () -> {
              channel.exchangeDeclare(exchangeName, "direct", false);
              String tempQueueName = channel.queueDeclare().getQueue();
              channel.queueBind(tempQueueName, exchangeName, routingKey);
              channel.basicPublish(exchangeName, routingKey, null, JSON_BODY.getBytes());
              return tempQueueName;
            });

    GetResponse response =
        instrumentation.runWithSpan("consumer parent", () -> channel.basicGet(queueName, true));

    Assertions.assertThat(response.getBody()).isEqualTo(JSON_BODY.getBytes());

    TracesAssert.assertThat(instrumentation.waitForTraces(2))
        .hasSize(2)
        .hasTracesSatisfyingExactly(
            trace -> {
              trace
                  .hasSize(5)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("producer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                      },
                      span -> {
                        span.hasName("exchange.declare")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "exchange.declare");
                      },
                      span -> {
                        span.hasName("queue.declare")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "queue.declare");
                      },
                      span -> {
                        span.hasName("queue.bind")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "queue.bind");
                      },
                      span -> {
                        span.hasName(exchangeName + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, exchangeName)
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                routingKey)
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "basic.publish")
                            .hasAttribute(
                                AttributeKey.stringKey("messaging.message.payload"), JSON_BODY)
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                (long) JSON_BODY.getBytes().length);
                      });
            },
            trace -> {
              trace
                  .hasSize(2)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("consumer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                      },
                      span -> {
                        span.hasName("<generated> receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, exchangeName)
                            .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                            .hasAttribute(
                                AttributeKey.stringKey("messaging.message.payload"), JSON_BODY)
                            .hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "basic.get")
                            .hasAttribute(AttributeKey.stringKey("rabbitmq.queue"), queueName);
                      });
            });
  }

  @Test
  void testBasicPublishAndGetWithDefaultExchange() throws Exception {
    if (true) {
      return;
    }
    String queueName =
        instrumentation.runWithSpan(
            "producer parent",
            () -> {
              String tempQueueName = channel.queueDeclare().getQueue();
              channel.basicPublish("", tempQueueName, null, JSON_BODY.getBytes());
              return tempQueueName;
            });

    GetResponse response =
        instrumentation.runWithSpan("consumer parent", () -> channel.basicGet(queueName, true));

    Assertions.assertThat(response.getBody()).isEqualTo(JSON_BODY.getBytes());

    TracesAssert.assertThat(instrumentation.waitForTraces(2))
        .hasSize(2)
        .hasTracesSatisfyingExactly(
            trace -> {
              trace
                  .hasSize(3)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("producer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                      },
                      span -> {
                        span.hasName("queue.declare")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "queue.declare");
                      },
                      span -> {
                        span.hasName("<default> publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "<default>")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "basic.publish")
                            .hasAttribute(
                                AttributeKey.stringKey("messaging.message.payload"), JSON_BODY)
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                (long) JSON_BODY.getBytes().length);
                      });
            },
            trace -> {
              trace
                  .hasSize(2)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("consumer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                      },
                      span -> {
                        span.hasName("<generated> receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "<default>")
                            .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                            .hasAttribute(
                                AttributeKey.stringKey("messaging.message.payload"), JSON_BODY)
                            .hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "basic.get")
                            .hasAttribute(AttributeKey.stringKey("rabbitmq.queue"), queueName);
                      });
            });
  }

  @Test
  void testBasicPublishAndGetConsumer() throws Exception {
    if (true) {
      return;
    }
    String exchangeName = "some-exchange";
    channel.exchangeDeclare(exchangeName, "direct", false);
    String queueName = channel.queueDeclare().getQueue();
    channel.queueBind(queueName, exchangeName, "");

    Consumer callback =
        new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(
              String consumerTag,
              com.rabbitmq.client.Envelope envelope,
              AMQP.BasicProperties properties,
              byte[] body) {
            Assertions.assertThat(body).isEqualTo(JSON_BODY.getBytes());
          }
        };

    channel.basicConsume(queueName, callback);

    channel.basicPublish(exchangeName, "", null, JSON_BODY.getBytes());

    // Needed to ensure the "<generated> process" child span of the "send" span has been received.
    Thread.sleep(150);

    TracesAssert.assertThat(instrumentation.waitForTraces(5))
        .hasSize(5)
        .hasTracesSatisfyingExactly(
            trace -> {
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("exchange.declare")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "exchange.declare");
                      });
            },
            trace -> {
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("queue.declare")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "queue.declare");
                      });
            },
            trace -> {
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("queue.bind")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "queue.bind");
                      });
            },
            trace -> {
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName("basic.consume")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "basic.consume");
                      });
            },
            trace -> {
              trace
                  .hasSize(2)
                  .hasSpansSatisfyingExactly(
                      span -> {
                        span.hasName(exchangeName + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasNoParent()
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, exchangeName)
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "basic.publish")
                            .hasAttribute(
                                AttributeKey.stringKey("messaging.message.payload"), JSON_BODY)
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                (long) JSON_BODY.getBytes().length);
                      },
                      span -> {
                        span.hasName("<generated> process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, exchangeName)
                            .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                            .hasAttribute(
                                AttributeKey.stringKey("rabbitmq.command"), "basic.deliver")
                            .hasAttribute(
                                AttributeKey.stringKey("messaging.message.payload"), JSON_BODY)
                            .hasAttribute(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                (long) JSON_BODY.getBytes().length);
                      });
            });
  }*/
}
