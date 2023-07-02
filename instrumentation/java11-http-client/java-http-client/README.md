# Background on fork

This module is a fork of the upstream [Java HTTP client instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/java-http-client/javaagent).
This fork adds an HTTP Payload `AttributeExtractor` into the `InstrumenterBuilder`
to facilitate collection of HTTP payloads of the request/response as an attribute on a span.
It also adds a `ContextCustomizer` to add an instance of `ResponsePayloadBridge` into the
OTeL context for passing HTTP response body content between instrumentation and the
`AttributeExtractor`.

This fork is necessary as the byte code advice in `HttpClientInstrumentation` directly accesses
the static field on `JavaHttpClientSingletons` containing the instrumenter.
There is no mechanism to extend the `InstrumenterBuilder` to replace aspects of what it provides,
which is why this approach has been taken.

Every update to OpenTelemetry JAVA SDK base version will require:
- A re-copy of instrumentation changes from the [javaagent module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/java-http-client/javaagent)
- A re-copy of instrumentation changes from the [library module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/java-http-client/library)
- Re-application of the following changes:

## `JavaHttpClientInstrumenterFactory`

Change the `INSTRUMENTATION_NAME` static field to:

```java
public static final String INSTRUMENTATION_NAME = "io.opentelemetry.lumigo-java-http-client";
```

Update the `Instrumenter.builder()` call to include:
  
```java
// Custom Context customizer for holding response payload
.addContextCustomizer((context, request, attributes) -> new ResponsePayloadBridge.Builder().init(context))
```

## `JavaHttpClientSingletons`

For the `INSTRUMENTER` field, add the below to the `Instrumenter.builder()` chain:

```java
// Custom HTTP payload extractor
.addAttributesExtractor(new HttpPayloadExtractor())
```

## `HttpClientInstrumentationModule`

Update the list returned from `typeInstrumentations()` to include:

```java
new TrustedSubscriberInstrumentation()
```

Update the constructor to call:

```java
super("lumigo-java-http-client");
```

## `HttpClientInstrumentation`

Modify `transform()` for both `applyAdviceToMethod()` calls to include:

```java
.and(takesArgument(1, named("java.net.http.HttpResponse$BodyHandler"))),
```

Modify `methodEnter()` for `SendAdvice` and `SendAsyncAdvice` to have the following method parameter:

```java
@Advice.Argument(value = 1, readOnly = false) HttpResponse.BodyHandler<?> bodyHandler,
```

and the method body:

```java
      // This is necessary to ensure TrustedSubscriberInstrumentation has access to the correct context
      if (bodyHandler != null) {
        bodyHandler = new BodyHandlerWrapper<>(bodyHandler, context);
      }
```
right before `scope = context.makeCurrent()`.
