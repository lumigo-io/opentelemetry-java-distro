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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcTest extends AbstractGrpcTest {
  @RegisterExtension
  static final InstrumentationExtension instrumentation = AgentInstrumentationExtension.create();

  protected final Queue<ThrowingRunnable<?>> closer = new ConcurrentLinkedQueue<>();

  private static final String REQUEST_BODY_TEMPLATE = "{\"name\":\"%s\"}";
  private static final String RESPONSE_BODY_TEMPLATE = "{\"message\":\"Hello %s\"}";
  private static final String COMBO_BODY_TEMPLATE =
      "{\"name\":\"%s\",\"message\":\"Hello %s\",\"age\":%s}";

  @AfterEach
  void close() throws Throwable {
    while (!closer.isEmpty()) {
      closer.poll().run();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"some name", "another name"})
  void blocking(String paramName) throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request request, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response response =
                Helloworld.Response.newBuilder().setMessage("Hello " + request.getName()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
          }
        };

    Server server = ServerBuilder.forPort(0).addService(greeter).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

    Helloworld.Response response =
        instrumentation.runWithSpan(
            "parent",
            () -> stub.sayHello(Helloworld.Request.newBuilder().setName(paramName).build()));

    assertThat(response.getMessage()).isEqualTo("Hello " + paramName);

    instrumentation.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("example.Greeter/SayHello")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                      .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                      .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHello")
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.request.payload"),
                          String.format(REQUEST_BODY_TEMPLATE, paramName))
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.response.payload"),
                          String.format(RESPONSE_BODY_TEMPLATE, paramName)),
              span -> {
                span.hasName("example.Greeter/SayHello")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(1))
                    .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                    .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                    .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHello")
                    .hasAttribute(
                        AttributeKey.stringKey("rpc.grpc.request.payload"),
                        String.format(REQUEST_BODY_TEMPLATE, paramName))
                    .hasAttribute(
                        AttributeKey.stringKey("rpc.grpc.response.payload"),
                        String.format(RESPONSE_BODY_TEMPLATE, paramName));
              });
        });
  }

  @Test
  void listenableFuture() throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request request, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response response =
                Helloworld.Response.newBuilder().setMessage("Hello " + request.getName()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
          }
        };

    Server server = ServerBuilder.forPort(0).addService(greeter).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterFutureStub stub = GreeterGrpc.newFutureStub(channel);

    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    instrumentation.runWithSpan(
        "parent",
        () -> {
          ListenableFuture<Helloworld.Response> future =
              Futures.transform(
                  stub.sayHello(Helloworld.Request.newBuilder().setName("a name").build()),
                  resp -> {
                    instrumentation.runWithSpan("child", () -> {});
                    return resp;
                  },
                  MoreExecutors.directExecutor());
          try {
            response.set(Futures.getUnchecked(future));
          } catch (Throwable t) {
            error.set(t);
          }
        });

    assertThat(error).hasValue(null);
    assertThat(response.get().getMessage()).isEqualTo("Hello a name");

    instrumentation.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("example.Greeter/SayHello")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                      .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                      .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHello")
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.request.payload"),
                          String.format(REQUEST_BODY_TEMPLATE, "a name"))
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.response.payload"),
                          String.format(RESPONSE_BODY_TEMPLATE, "a name")),
              span ->
                  span.hasName("example.Greeter/SayHello")
                      .hasKind(SpanKind.SERVER)
                      .hasParent(trace.getSpan(1))
                      .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                      .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                      .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHello")
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.request.payload"),
                          String.format(REQUEST_BODY_TEMPLATE, "a name"))
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.response.payload"),
                          String.format(RESPONSE_BODY_TEMPLATE, "a name")),
              span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void streaming() throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request request, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response response =
                Helloworld.Response.newBuilder().setMessage("Hello " + request.getName()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
          }
        };

    Server server = ServerBuilder.forPort(0).addService(greeter).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    instrumentation.runWithSpan(
        "parent",
        () -> {
          stub.sayHello(
              Helloworld.Request.newBuilder().setName("test").build(),
              new StreamObserver<Helloworld.Response>() {
                @Override
                public void onNext(Helloworld.Response value) {
                  response.set(value);
                }

                @Override
                public void onError(Throwable t) {
                  error.set(t);
                }

                @Override
                public void onCompleted() {
                  instrumentation.runWithSpan("child", () -> {});
                  latch.countDown();
                }
              });
        });

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    assertThat(error).hasValue(null);
    assertThat(response.get().getMessage()).isEqualTo("Hello test");

    instrumentation.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("example.Greeter/SayHello")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                      .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                      .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHello")
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.request.payload"),
                          String.format(REQUEST_BODY_TEMPLATE, "test"))
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.response.payload"),
                          String.format(RESPONSE_BODY_TEMPLATE, "test")),
              span ->
                  span.hasName("example.Greeter/SayHello")
                      .hasKind(SpanKind.SERVER)
                      .hasParent(trace.getSpan(1))
                      .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                      .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                      .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHello")
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.request.payload"),
                          String.format(REQUEST_BODY_TEMPLATE, "test"))
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.response.payload"),
                          String.format(RESPONSE_BODY_TEMPLATE, "test")),
              span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"some name", "another name"})
  void blockingComplexObject(String paramName) throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHelloDetailed(
              Helloworld.Request request, StreamObserver<Helloworld.ComboObject> responseObserver) {
            Helloworld.ComboObject response =
                Helloworld.ComboObject.newBuilder()
                    .setName(request.getName())
                    .setMessage("Hello " + request.getName())
                    .setAge(request.getName().length())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
          }
        };

    Server server = ServerBuilder.forPort(0).addService(greeter).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

    Helloworld.ComboObject response =
        instrumentation.runWithSpan(
            "parent",
            () ->
                stub.sayHelloDetailed(Helloworld.Request.newBuilder().setName(paramName).build()));

    assertThat(response.getName()).isEqualTo(paramName);
    assertThat(response.getMessage()).isEqualTo("Hello " + paramName);
    assertThat(response.getAge()).isEqualTo(paramName.length());

    instrumentation.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("example.Greeter/SayHelloDetailed")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                      .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                      .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHelloDetailed")
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.request.payload"),
                          String.format(REQUEST_BODY_TEMPLATE, paramName))
                      .hasAttribute(
                          AttributeKey.stringKey("rpc.grpc.response.payload"),
                          String.format(
                              COMBO_BODY_TEMPLATE, paramName, paramName, paramName.length())),
              span -> {
                span.hasName("example.Greeter/SayHelloDetailed")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(1))
                    .hasAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
                    .hasAttribute(SemanticAttributes.RPC_SERVICE, "example.Greeter")
                    .hasAttribute(SemanticAttributes.RPC_METHOD, "SayHelloDetailed")
                    .hasAttribute(
                        AttributeKey.stringKey("rpc.grpc.request.payload"),
                        String.format(REQUEST_BODY_TEMPLATE, paramName))
                    .hasAttribute(
                        AttributeKey.stringKey("rpc.grpc.response.payload"),
                        String.format(
                            COMBO_BODY_TEMPLATE, paramName, paramName, paramName.length()));
              });
        });
  }
}
