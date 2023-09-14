# Background on fork

This module is a fork of the upstream [RabbitMQ client instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/rabbitmq-2.7/javaagent).
This fork adds `GetResponseBodyAttributeExtractor` into the list of extractors used in
`RabbitSingletons.createReceiveInstrumenter()` to facilitate collection of message payloads on a span.

This fork is necessary as the byte code advice directly accesses a static field on `RabbitSingletons`
containing the instrumenter. There is no mechanism to extend the `InstrumenterBuilder` to replace
aspects of what it provides, which is why this approach has been taken.

Every update to OpenTelemetry JAVA SDK base version will require:
- A re-copy of instrumentation changes from the [javaagent module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/rabbitmq-2.7/javaagent)
- Remove `RabbitMqInstrumentationModule` from the copied instrumentation
- Re-application of the following changes:

## `RabbitSingletons`

Update the `createReceiveInstrumenter()` call to include:
  
```java
// Custom Lumigo extractor
extractors.add(new GetResponseBodyAttributeExtractor());
```

## `io.lumigo.javaagent.instrumentation.rabbitmq.RabbitMqInstrumentationModule`

Ensure `typeInstrumentation()` includes all instrumentations from the upstream module.

## `RabbitChannelInstrumentation`

Modify `ChannelGetAdvice.extractAndStartSpan()` to pass `response`, an instance of `GetResponse`
into the `InstrumenterUtil.startAndEnd()` method call.
