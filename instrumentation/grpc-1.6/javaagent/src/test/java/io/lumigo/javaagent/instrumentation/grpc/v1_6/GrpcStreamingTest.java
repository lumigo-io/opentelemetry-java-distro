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
package io.lumigo.javaagent.instrumentation.grpc.v1_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GrpcStreamingTest extends AbstractGrpcTest {
  @RegisterExtension
  static final InstrumentationExtension instrumentation = AgentInstrumentationExtension.create();

  protected final Queue<ThrowingRunnable<?>> closer = new ConcurrentLinkedQueue<>();

  private static final String MESSAGE_TEMPLATE = "{\"message\":\"call %s\"}";

  @AfterEach
  void close() throws Throwable {
    while (!closer.isEmpty()) {
      closer.poll().run();
    }
  }

  @ParameterizedTest
  @MethodSource("provideMessageCounts")
  void conversation(int clientMessageCount, int serverMessageCount) throws Exception {
    Queue<String> clientMessages = new ConcurrentLinkedQueue<>();
    Queue<String> serverMessages = new ConcurrentLinkedQueue<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);

    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public StreamObserver<Helloworld.Message> conversation(
              StreamObserver<Helloworld.Message> responseObserver) {
            return new StreamObserver<Helloworld.Message>() {
              @Override
              public void onNext(Helloworld.Message value) {
                serverMessages.add(value.getMessage());

                for (int i = 1; i <= serverMessageCount; i++) {
                  responseObserver.onNext(value);
                }
              }

              @Override
              public void onError(Throwable t) {
                error.set(t);
                responseObserver.onError(t);
              }

              @Override
              public void onCompleted() {
                responseObserver.onCompleted();
                latch.countDown();
              }
            };
          }
        };

    Server server = ServerBuilder.forPort(0).addService(greeter).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel).withWaitForReady();

    StreamObserver<Helloworld.Message> observer2 =
        stub.conversation(
            new StreamObserver<Helloworld.Message>() {
              @Override
              public void onNext(Helloworld.Message value) {
                clientMessages.add(value.getMessage());
              }

              @Override
              public void onError(Throwable t) {
                error.set(t);
              }

              @Override
              public void onCompleted() {
                latch.countDown();
              }
            });

    for (int i = 1; i <= clientMessageCount; i++) {
      Helloworld.Message msg = Helloworld.Message.newBuilder().setMessage("call " + i).build();
      observer2.onNext(msg);
    }
    observer2.onCompleted();

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    assertThat(error).hasValue(null);
    assertThat(serverMessages)
        .containsExactlyElementsOf(
            IntStream.rangeClosed(1, clientMessageCount)
                .mapToObj(i -> "call " + i)
                .collect(Collectors.toList()));
    List<String> clientMessagesList =
        IntStream.rangeClosed(1, serverMessageCount)
          .boxed()
          .flatMap(
              unused ->
                IntStream.rangeClosed(1, clientMessageCount).mapToObj(i -> "call " + i))
          .sorted()
          .collect(Collectors.toList());
    assertThat(clientMessages)
        .containsExactlyElementsOf(clientMessagesList);
    assertThat(clientMessagesList.size()).isEqualTo(clientMessageCount * serverMessageCount);

    instrumentation.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("example.Greeter/Conversation")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                        .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                        .hasAttribute(SemanticAttributes.RPC_METHOD, "Conversation")
                        .hasAttribute(
                            AttributeKey.stringKey("rpc.grpc.request.payload"),
                            constructRequestBody(clientMessageCount))
                        .hasAttribute(
                            AttributeKey.stringKey("rpc.grpc.response.payload"),
                            constructResponseBody(serverMessageCount)),
                span ->
                    span.hasName("example.Greeter/Conversation")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                        .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                        .hasAttribute(SemanticAttributes.RPC_METHOD, "Conversation")
                        .hasAttribute(
                            AttributeKey.stringKey("rpc.grpc.request.payload"),
                            constructRequestBody(clientMessageCount))
                        .hasAttribute(
                            AttributeKey.stringKey("rpc.grpc.response.payload"),
                            constructResponseBody(serverMessageCount))));
  }

  private static String constructRequestBody(int messageCount) {
    if (messageCount == 1) {
      return String.format(MESSAGE_TEMPLATE, 1);
    }

    return IntStream.rangeClosed(1, messageCount)
        .boxed()
        .map(i -> String.format(MESSAGE_TEMPLATE, i))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String constructResponseBody(int messageCount) {
    if (messageCount == 1) {
      return String.format(MESSAGE_TEMPLATE, 1);
    }

    return IntStream.rangeClosed(1, messageCount)
        .boxed()
        .flatMap(
            unused ->
                IntStream.rangeClosed(1, messageCount).mapToObj(i -> String.format(MESSAGE_TEMPLATE, i)))
        .sorted()
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static Stream<Arguments> provideMessageCounts() {
    return Stream.of(Arguments.of(1, 1), Arguments.of(2, 2), Arguments.of(3, 3));
  }
}
