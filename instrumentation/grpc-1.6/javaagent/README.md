# Background on partial fork

This module is a partial fork of the upstream [gRPC instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/grpc-1.6/javaagent).
This fork replaces `TracingServerInterceptor` to work around the problem of the Context not being
active during `onMessage()` calls. This problem was raised [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/9605).

Every update to OpenTelemetry JAVA SDK base version will require:
- A re-copy of instrumentation changes from the [javaagent module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/grpc-1.6/javaagent)
- Remove `GrpcInstrumentationModule` from the copied instrumentation
- A re-copy of instrumentation changes from the [library module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/grpc-1.6/library)
- Re-application of the following changes:

## `TracingServerInterceptor`

Update the `TracingServerCallListener.onMessage()` call to replace `delegate().onMessage()` with:
  
```java
// Lumigo custom changes to work around issue: https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/9605
try (Scope ignored = context.makeCurrent()) {
    delegate().onMessage(message);
}
```

## `io.lumigo.javaagent.instrumentation.grpc.v1_6.GrpcInstrumentationModule`

Ensure `typeInstrumentation()` includes all instrumentations from the upstream module.
