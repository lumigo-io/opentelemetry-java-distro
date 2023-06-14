# Background on fork

This module is a fork of the upstream [Apache HttpClient v5 instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/apache-httpclient/apache-httpclient-5.0/javaagent).
This fork adds a HTTP Payload `AttributeExtractor` into the `InstrumenterBuilder`
to facilitate collection of HTTP payloads of the request/response as an attribute on a span.

This fork is necessary as the byte code advice in `ApacheHttpClientInstrumentation` directly accesses
the static field on `ApacheHttpClientSingletons` containing the instrumenter.
There is no mechanism to extend the `InstrumenterBuilder` to replace aspects of what it provides,
which is why this approach has been taken.

Every update to OpenTelemetry JAVA SDK base version will require a re-copy of instrumentation changes
and re-application of below in `ApacheHttpClientSingletons`:

```java
// Custom HTTP payload extractor
.addAttributesExtractor(new HttpPayloadExtractor())
```
