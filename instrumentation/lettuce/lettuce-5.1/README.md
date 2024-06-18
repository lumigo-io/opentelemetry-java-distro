# Background on fork


This module is a fork of the upstream [Lettuce 5.1 client instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/lettuce/lettuce-5.1).
This fork enhances the original instrumentation by adding payload attributes to spans.


## Maintenance

For every update to the OpenTelemetry JAVA SDK base version, follow these steps:
- A re-copy of instrumentation javaagent and library from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/lettuce/lettuce-5.1)
- From the javaagent remove the `LettuceInstrumentationModule` from the package `io.opentelemetry.javaagent.instrumentation.lettuce.v5_1`
- From the library, apply the modifications detailed below.

## Modifications in `OpenTelemetryTracing` Class

### `OpenTelemetrySpan` Inner Class Changes

#### Method: start
#### Signature: public synchronized Tracer.Span start(RedisCommand<?, ?, ?> command)
#### Changes: Added the response payload to the span attributes.
#### Before:
```java
if (output != null) {
  String error = output.getError();
  if (error != null) {
    span.setStatus(StatusCode.ERROR, error);
  }
}
```
#### After
```java
if (output != null) {
    String error = output.getError();
    if (error != null) {
        span.setStatus(StatusCode.ERROR, error);
    }
    Object result = output.get();
    if (result != null) {
        span.setAttribute("db.response.body", result.toString());
    }
}
```

#### Method: finish
#### Signature: private void finish(Span span)
#### Changes: Removed the sanitizer code because it truncates the request payload.
#### Before:
```java      
if (name != null) {
  String statement =
      sanitizer.sanitize(name, argsList != null ? argsList : splitArgs(argsString));
  span.setAttribute(DB_STATEMENT, statement);
}
span.end();
```
#### After:
```java
if (name != null) {
  String statement = argsList != null ?  String.join(" ", argsList) : argsString;
  if (statement != null) {
    span.setAttribute(SemanticAttributes.DB_STATEMENT, statement);
  }
}
span.end();
```

NOTE: By removing the sanitizer part, we also delete the dependency on `instrumentation:lettuce:lettuce-common:library` from the library.

# Reason for Forking the Lettuce 5.1 Instrumentation

Attempts to add payloads without forking the instrumentation were unsuccessful. Although theoretically possible by:
1. enabling the `db-statement-sanitizer` flag
2. Instrumenting the `start` & `finish` methods in the `OpenTelemetrySpan` class from the package `io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing`

Issues Encountered:
1. The `db-statement-sanitizer` flag is ignored by this instrumentation.
2. The instrumentation failed for unknown reasons.

Therefore, the decision was made to fork the instrumentation and directly add the payloads, consistent with modifications made in other instrumentations.
