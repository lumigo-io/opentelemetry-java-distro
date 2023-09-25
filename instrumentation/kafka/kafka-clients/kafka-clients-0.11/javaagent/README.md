# Background on fork

This module is a fork of the upstream [Kafka client instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/kafka/kafka-clients/kafka-clients-0.11/javaagent).
This fork adds `ConsumerPayloadAttributeExtractor` into the list of extractors used in
`KafkaSingletons.CONSUMER_PROCESS_INSTRUMENTER` to facilitate collection of message payloads on a span.

This fork is necessary as the byte code advice directly accesses a static field on `KafkaSingletons`
containing the instrumenter. There is no mechanism to extend the `InstrumenterBuilder` to replace
aspects of what it provides, which is why this approach has been taken.

Every update to OpenTelemetry JAVA SDK base version will require:
- A re-copy of instrumentation changes from the [javaagent module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/kafka/kafka-clients/kafka-clients-0.11/javaagent)
- Remove `KafkaClientsInstrumentationModule` from the copied instrumentation
- Re-application of the following changes:

## `KafkaSingletons`

Update the `static` block to use the below for creating the `CONSUMER_PROCESS_INSTRUMENTER`:
  
```java
// Add Lumigo custom payload extractor
CONSUMER_PROCESS_INSTRUMENTER =
    instrumenterFactory.createConsumerOperationInstrumenter(
    MessageOperation.PROCESS, Collections.singletonList(new ConsumerPayloadAttributeExtractor()));
```

## `KafkaProducerInstrumentation`

Modify `SendAdvice.onMethodEnter()` to include the below before creating producer callback:

```java
if (null != record.value()) {
  Java8BytecodeBridge.currentSpan()
      .setAttribute(SemanticAttributes.MESSAGING_PAYLOAD, record.value().toString());
}
```

## `io.lumigo.javaagent.instrumentation.kafkaclients.v0_11.KafkaClientInstrumentationModule`

Ensure `typeInstrumentations()` includes all instrumentations from the upstream module.
