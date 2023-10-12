# Background on fork

This module is a fork of the upstream [AWS SDK v2 library instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/aws-sdk/aws-sdk-2.2/library).

Every update to OpenTelemetry JAVA SDK base version will require a re-copy of instrumentation changes
and re-application of the following:

## `AwsSdkInstrumenterFactory`

Update `INSTRUMENTATION_NAME` to be `io.opentelemetry.lumigo-aws-sdk-2.2`.

Update `createInstrumenter()` with new parameter for `contextCustomizer` and pass to Instrumenter Builder:

```java
private static Instrumenter<ExecutionAttributes, SdkHttpResponse> createInstrumenter(
    OpenTelemetry openTelemetry,
    List<AttributesExtractor<ExecutionAttributes, SdkHttpResponse>> extractors,
    Function<Context, Context> contextCustomizer,
    SpanKindExtractor<ExecutionAttributes> spanKindExtractor) {

  return Instrumenter.<ExecutionAttributes, SdkHttpResponse>builder(
          openTelemetry, INSTRUMENTATION_NAME, AwsSdkInstrumenterFactory::spanName)
      .addAttributesExtractors(extractors)
      .addContextCustomizer((context, request, attributes) -> contextCustomizer.apply(context))
      .buildInstrumenter(spanKindExtractor);
}
```

Update `consumerInstrumenter()` with to pass no-op `contextCustomizer`:

```java
static Instrumenter<ExecutionAttributes, SdkHttpResponse> consumerInstrumenter(
    OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {

  return createInstrumenter(
      openTelemetry,
      captureExperimentalSpanAttributes
          ? extendedConsumerAttributesExtractors
          : defaultConsumerAttributesExtractors,
      (context) -> context,
      SpanKindExtractor.alwaysConsumer());
}
```

Update `requestInstrumenter()` with new parameter for `contextCustomizer` and pass to `createInstrumenter()`:

```java
static Instrumenter<ExecutionAttributes, SdkHttpResponse> requestInstrumenter(
    OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes,
    Function<Context, Context> contextCustomizer) {

  return createInstrumenter(
      openTelemetry,
      captureExperimentalSpanAttributes
          ? extendedAttributesExtractors
          : defaultAttributesExtractors,
      contextCustomizer,
      AwsSdkInstrumenterFactory.spanKindExtractor);
}
```

## `AwsSdkTelemetryBuilder`

Add `ContextCustomizer` to builder:

```java
private Function<Context, Context> contextCustomizer;

@CanIgnoreReturnValue
public AwsSdkTelemetryBuilder setContextCustomizer(Function<Context, Context> contextCustomizer) {
  this.contextCustomizer = contextCustomizer;
  return this;
}
```

Modify `build()` to pass `contextCustomizer` to `AwsSdkTelemetry` constructor:

```java
public AwsSdkTelemetry build() {
  return new AwsSdkTelemetry(
      openTelemetry,
      captureExperimentalSpanAttributes,
      useMessagingPropagator,
      useXrayPropagator,
      contextCustomizer);
}
```

## `AwsSdkTelemetry`

Modify constructor to accept `contextCustomizer` and pass to `AwsSdkInstrumenterFactory.requestInstrumenter()`:

```java
AwsSdkTelemetry(
    OpenTelemetry openTelemetry,
    boolean captureExperimentalSpanAttributes,
    boolean useMessagingPropagator,
    boolean useXrayPropagator,
    Function<Context, Context> contextCustomizer) {
  this.useXrayPropagator = useXrayPropagator;
  this.requestInstrumenter =
      AwsSdkInstrumenterFactory.requestInstrumenter(
          openTelemetry, captureExperimentalSpanAttributes, contextCustomizer);
  this.consumerInstrumenter =
      AwsSdkInstrumenterFactory.consumerInstrumenter(
          openTelemetry, captureExperimentalSpanAttributes);
  this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  this.messagingPropagator =
      useMessagingPropagator ? openTelemetry.getPropagators().getTextMapPropagator() : null;
}
```

## `SqsImpl`

Modify `createConsumerSpan()` to set message id and payload onto the span:

```java
if (consumerInstrumenter.shouldStart(parentContext, executionAttributes)) {
  io.opentelemetry.context.Context context =
    consumerInstrumenter.start(parentContext, executionAttributes);

  if (message != null) {
    final Span span = Span.fromContext(context);
    span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, message.messageId());
    span.setAttribute("messaging.message.payload", message.toString());
  }

  // TODO: Even if we keep HTTP attributes (see afterMarshalling), does it make sense here
  //  per-message?
  // TODO: Should we really create root spans if we can't extract anything, or should we attach
  //  to the current context?
  consumerInstrumenter.end(context, executionAttributes, httpResponse, null);
}
```
