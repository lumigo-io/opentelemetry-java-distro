# Background on fork

This module is a fork of the upstream [Apache HttpClient v5 instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/apache-httpclient/apache-httpclient-5.0/javaagent).
This fork adds a HTTP Payload `AttributeExtractor` into the `InstrumenterBuilder`
to facilitate collection of HTTP payloads of the request/response as an attribute on a span.
It also adds a `ContextCustomizer` to add an instance of `ResponsePayloadBridge` into the
OTeL context for passing HTTP response body content between instrumentation and the
`AttributeExtractor`.

This fork is necessary as the byte code advice in `ApacheHttpClientInstrumentation` directly accesses
the static field on `ApacheHttpClientSingletons` containing the instrumenter.
There is no mechanism to extend the `InstrumenterBuilder` to replace aspects of what it provides,
which is why this approach has been taken.

Every update to OpenTelemetry JAVA SDK base version will require a re-copy of instrumentation changes
and re-application of the following:

## `ApacheHttpClientSingletons`

Update `INSTRUMENTATION_NAME` to be `io.opentelemetry.lumigo-apache-httpclient-5.0`.

For the `INSTRUMENTER` field, add the below to the `Instrumenter.builder()` chain:

```java
// Custom HTTP payload extractor
.addAttributesExtractor(new HttpPayloadExtractor())
// Custom Context customizer for holding response payload
.addContextCustomizer((context, request, attributes) -> new ResponsePayloadBridge.Builder().init(context))
```

## `ApacheHttpClientInstrumentationModule`

Update the list returned from `typeInstrumentations()` to include:

```java
new SessionInputBufferInstrumentation(), new HttpMessageParserInstrumentation()
```

Update the constructor to call:

```java
super("lumigo-apache-httpclient", "lumigo-apache-httpclient-5.0");
```
