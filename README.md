# Lumigo OpenTelemetry Distro for Java

The Lumigo OpenTelemetry Distro for Java is made of several upstream OpenTelemetry packages, with additional automated quality-assurance and customizations that optimize for no-code injection, meaning that you should need to update exactly zero lines of code in your application in order to make use of the Lumigo OpenTelemetry Distribution, see the [Tracer activation](#tracer-activation) section.

## Setup

The setup of Lumigo OpenTelemetry Distro for Java is made of the three following steps:
1. [Download](#download) the Lumigo OpenTelemetry Distro for Java
2. [Configure](#environment-based-configuration) the Lumigo tracer token
3. [Activate](#tracer-activation) the Lumigo OpenTelemetry Distro for Java

### Download

Download the latest version from the [Releases](https://github.com/lumigo-io/opentelemetry-java-distro/releases) page.
To instruct your Java Virtual Machine to load the Lumigo OpenTelemetry Distro for Java on startup, there are a few options:

### Environment-based configuration

Configure the `LUMIGO_TRACER_TOKEN` environment variable with the token value generated for you by the Lumigo platform, under `Settings --> Tracing --> Manual tracing` (see the [Lumigo Tokens](https://docs.lumigo.io/docs/lumigo-tokens) documentation for more details on how to retrieve your Lumigo token):

```console
LUMIGO_TRACER_TOKEN=<token>
```

Replace `<token>` below with the token generated for you by the Lumigo platform.

It is also advised that you set the `OTEL_SERVICE_NAME` environment variable with, as value, the service name you have chosen for your application:

```console
OTEL_SERVICE_NAME=<service name>
```

Replace `<service name>` with the desired name of the service.

**Note:** While you setting environment variables for configuration, consider also providing the one needed for [no-code tracer activation](#option-1-java_tool_options) :-)

### Tracer activation

The Lumigo OpenTelemetry Distro for Java must be loaded by the Java Virtual Machine before your application is loaded.
The two supported ways to achieve this are:

* **preferred:** Setting the [`JAVA_TOOL_OPTIONS` environment variable](#option-1-java_tool_options)
* Setting the [`-javaagent` command-line parameter](#option-2-command-line-parameters)

The `JAVA_TOOL_OPTIONS` method is preferred because setting environment variables is almost always easier than modifying the entrypoint of a container image (which is usually where you would set the `-javaagent` property).

#### Option 1: JAVA_TOOL_OPTIONS

Set the `JAVA_TOOL_OPTIONS` environment variable on your Java Virtual Machine as follows:

```console
export JAVA_TOOL_OPTIONS="-javaagent:<path-to-lumigo-otel-javaagent>"
```

This is the preferred option in containerized applications or when running your Java application as a SystemD unit, as in such environments is easier to set environment variables than changing the startup arguments of the Java Virtual Machine like the following option explains.

#### Option 2: Command-line parameters

Pass the `-javaagent` property to the startup command as follows:

```console
java -javaagent:<path-to-lumigo-otel-javaagent> -jar app.jar
```

## Configuration

### OpenTelemetry configurations

The Lumigo OpenTelemetry Distro for Java is made of upstream OpenTelemetry packages as well as some additional logic and, as such, the environment variables that work with "vanilla" OpenTelemetry work also with the Lumigo OpenTelemetry Distro for Java.
Specifically supported are:

* [General configurations](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/configuration/sdk-environment-variables.md#general-sdk-configuration)

The system properties are lower case versions of the environment variables, and with dots replaced by underscores.
For example, the environment variable `LUMIGO_TRACER_TOKEN` can be set using the system property `lumigo.tracer.token`.

### Lumigo-specific configurations

The Lumigo OpenTelemetry Distro for Java additionally supports the following configuration options as environment variables:

* `LUMIGO_TRACER_TOKEN=<token>`: Configure the Lumigo token to enable to upload of telemetry to Lumigo; without this environment variable, your Java process will not send telemetry to Lumigo.
* `LUMIGO_DEBUG=TRUE`: Enables debug logging
* `LUMIGO_DEBUG_SPANDUMP=<path>`: Log all spans collected to the `<path>` file; this is an option intended only for debugging purposes and should *not* be used in production. In containers, `/dev/stdout` and `/dev/stderr` are often a good choice.
  This setting is independent from `LUMIGO_DEBUG`, that is, `LUMIGO_DEBUG` does not need to additionally be set for `LUMIGO_DEBUG_SPANDUMP` to work.
* `LUMIGO_SWITCH_OFF=TRUE`: This option disables the Lumigo OpenTelemetry Distro entirely; no instrumentation will be injected, no tracing data will be collected.
* To disable specific instrumentation otel java agent have generic way to do it by setting `OTEL_INSTRUMENTATION_<INSTRUMENTATION-NAME>_ENABLED=false`. 
  * For example, to disable the `storm` instrumentation, set `OTEL_INSTRUMENTATION_STORM_ENABLED=false` you can read about it [here](https://opentelemetry.io/docs/zero-code/java/agent/configuration/#suppressing-specific-agent-instrumentation).
* `LUMIGO_SECRET_MASKING_REGEX='["regex1", "regex2"]'`: Prevents Lumigo from sending keys that match the supplied regular expressions in process environment data. All regular expressions are case-insensitive. The "magic" value `all` will redact everything. By default, Lumigo applies the following regular expressions: `[".*pass.*", ".*key.*", ".*secret.*", ".*credential.*", ".*passphrase.*"]`. More fine-grained settings can be applied via the following environment variable, which will override `LUMIGO_SECRET_MASKING_REGEX` for a specific type of data:
  * `LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT` applies secret redaction to process environment variables (that is, the content of `System.getenv()`)
* `LUMIGO_TAG=<value>`: Adds the tag value as an attribute to all spans; this is useful to identify the source of the telemetry in Lumigo. See [here](https://docs.lumigo.io/docs/tags) for details.
* `LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX='["regex1", "regex2"]'`: This option enables the filtering of client and server endpoints that match the supplied regular expressions. By default, this distribution applies the following regular expressions: `[".*/health.*", ".*/actuator.*"]`. More fine-grained settings can be applied via the following environment variables, which will override `LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX` for a specific span type:
  * `LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_SERVER` applies the filter to server spans only. Matching is performed against the following attributes on a span: `url.path`, and `http.target`[^1].
  * `LUMIGO_FILTER_HTTP_ENDPOINTS_REGEX_CLIENT` applies the filter to client spans only. Matching is performed against the following attributes on a span: `url.full`, and `http.url`[^2].
* `LUMIGO_ENABLE_LOGS=true`: Turns on the logging instrumentation to capture log-records, for logging libraries that support open-telemetry (e.g. Logback). By default, the logging instrumentation is disabled.
* `LUMIGO_ENABLE_TRACES=true`: Turns on the tracing instrumentation. By default, the tracing instrumentation is enabled.
* `LUMIGO_REDUCED_MONGO_INSTRUMENTATION=true`: Reduces the amount of data collected by the MongoDB [instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/mongo), such as not collecting the `db.operation` attribute `isMaster`. By default, the MongoDB instrumentation reduces the amount of data collected.
* `LUMIGO_REDUCED_REDIS_INSTRUMENTATION=true`: Reduces the amount of data collected by the Redis [instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/jedis), such as not collecting the `db.statement` attribute `INFO server`. By default, the Redis instrumentation reduces the amount of data collected.

For more configuration options, see the [Upstream Agent Configuration](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/).

[^1] The `http.target` attribute in the Trace HTTP Semantic Conventions is deprecated and replaced by `url.path`.
[^2] The `http.url` attribute in the Trace HTTP Semantic Conventions is deprecated and replaced by `url.full`.

### Execution Tags

[Execution Tags](https://docs.lumigo.io/docs/execution-tags) allow you to dynamically add dimensions to your invocations so that they can be identified, searched for, and filtered in Lumigo.
For example: in multi-tenanted systems, execution tags are often used to mark with the identifiers of the end-users that trigger them for analysis (e.g., [Explore view](https://docs.lumigo.io/docs/explore)) and alerting purposes.

#### Creating Execution Tags

In the Lumigo OpenTelemetry Distro for Java, execution tags are represented as [span attributes](https://opentelemetry.io/docs/reference/specification/common/#attribute) and, specifically, as span attributes with the `lumigo.execution_tags.` prefix.
For example, you could add an execution tag as follows:

```java
import io.opentelemetry.api.trace.Span;

Span.current().setAttribute("lumigo.execution_tags.foo","bar");
```

Notice that, using OpenTelemetry's [`Span.current()` API](https://opentelemetry.io/docs/instrumentation/java/manual/#get-the-current-span), you do not need to keep track of the current span, you can get it at any point of your program execution.

In OpenTelemetry, span attributes can be `strings`, `numbers` (double precision floating point or signed 64 bit integer), `booleans` (a.k.a. "primitive types"), and arrays of one primitive type (e.g., an array of string, and array of numbers or an array of booleans).
In Lumigo, booleans and numbers are transformed to strings.

**IMPORTANT:** If you use the `Span.setAttribute` API multiple times _on the same span_ to set values for the same key multiple values, you may override previous values rather than adding to them:

```java
import io.opentelemetry.api.trace.Span;

Span.current().setAttribute("lumigo.execution_tags.foo","bar");
Span.current().setAttribute("lumigo.execution_tags.foo","baz");
```

In the snippets above, the `foo` execution tag will have in Lumigo only the `baz` value!
Multiple values for an execution tag are supported as follows:

```java
import io.opentelemetry.api.trace.Span;

Span.current().setAttribute(AttributeKey.stringArrayKey("lumigo.execution_tags.foo"), Arrays.asList("bar", "baz"));
```

The snippets above will produce in Lumigo the `foo` tag having both `bar` and `baz` values.
Another option to set multiple values is setting [execution Tags in different spans of an invocation](#execution-tags-in-different-spans-of-an-invocation).

#### Execution Tags in different spans of an invocation

In Lumigo, multiple spans may be merged together into one invocation, which is the entry that you see, for example, in the [Explore view](https://docs.lumigo.io/docs/explore).
The invocation will include all execution tags on all its spans, and merge their values:

```java
import io.opentelemetry.api.trace.Span;

Span.current().setAttribute("lumigo.execution_tags.foo", "bar");
Tracer tracer = GlobalOpenTelemetry.getTracer("my-app");
Span nestedSpan = tracer.spanBuilder("child_span").startSpan();

try (Scope scope = nestedSpan.makeCurrent()) {
    // Do something interesting
    nestedSpan.setAttribute("lumigo.execution_tags.foo", "baz");
} finally {
    nestedSpan.end();
}
```

In the example above, the invocation in Lumigo resulting from executing the code will have both `bar` and `baz` values associated with the `foo` execution tag.
Which spans are merged in the same invocation depends on the parent-child relations among those spans.
Explaining this topic is outside the scope of this documentation; a good first read to get deeper into the topic is the [Traces](https://opentelemetry.io/docs/concepts/signals/traces/) documentation of OpenTelemetry.
In case your execution tags on different spans appear on different invocations than what you would expect, get in touch with [Lumigo support](https://docs.lumigo.io/docs/support).

#### Execution Tag Limitations

* Up to 50 execution tag keys per invocation in Lumigo, irrespective of how many spans are part of the invocation or how many values each execution tag has.
* The `key` of an execution tag cannot contain the `.` character; for example: `lumigo.execution_tags.my.tag` is not a valid tag. The OpenTelemetry `Span.setAttribute()` API will not fail or log warnings, but that will be displayed as `my` in Lumigo.
* Each execution tag key can be at most 50 characters long; the `lumigo.execution_tags.` prefix does _not_ count against the 50 characters limit.
* Each execution tag value can be at most 70 characters long.


### Programmatic Errors

[Programmatic Errors](https://docs.lumigo.io/docs/programmatic-errors) allow you to customize errors, monitor and troubleshoot issues that should not necessarily interfere with the service.
For example, an application tries to remove a user who doesn't exist. These custom errors can be captured by adding just a few lines of additional code to your application.

Programmatic Errors indicating that a non-fatal error occurred, such as an application error. You can log programmatic errors, track custom error issues, and trigger [Alerts](https://docs.lumigo.io/docs/event-alert).


#### Creating a Programmatic Error

Programmatic errors are created by adding [span events](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events) with a custom attribute being set with the key name `lumigo.type`.

For example, you could add a programmatic error as follows:

```java
Attributes eventAttributes = Attributes.of(
    AttributeKey.stringKey("lumigo.type"), "<error-type>"
);

Span.current().addEvent("<error-message>", eventAttributes);
```

## Supported runtimes

* JDK: 11.x, 17.x, 21.x

## Supported packages

| Instrumentation | Package | Supported Versions |
| --- | --- | --- |
| apache-httpclient-5.0 | [apache-httpclient-5.0](https://central.sonatype.com/artifact/org.apache.httpcomponents.client5/httpclient5) | 5.0.1~5.4.1 |
| | | 5.0 |
| | | 5.0-alpha1 |
| | | 5.0-alpha2 |
| | | 5.0-alpha3 |
| | | 5.0-beta1 |
| | | 5.0-beta2 |
| | | 5.0-beta3 |
| | | 5.0-beta4 |
| | | 5.0-beta5 |
| | | 5.0-beta6 |
| | | 5.0-beta7 |
| | | 5.1 |
| | | 5.1-beta1 |
| | | 5.2 |
| | | 5.2-alpha1 |
| | | 5.2-beta1 |
| | | 5.3-alpha1 |
| | | 5.4-alpha1 |
| | | 5.4-alpha2 |
| | | 5.4-beta1 |
| aws-sdk-1.11 | [aws-sdk-1.11](https://central.sonatype.com/artifact/com.amazonaws/aws-java-sdk-core) | 1.11.106~1.12.780 |
| aws-sdk-2.2 | [aws-sdk-2.2](https://central.sonatype.com/artifact/software.amazon.awssdk/aws-core) | 2.2.0~2.29.43 |
| grpc-1.6 | [grpc-1.6](https://central.sonatype.com/artifact/io.grpc/grpc-core) | 1.6.0~1.69.0 |
| java-http-client | [java-http-client](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/package-summary.html) | 11 |
| | | 17 |
| jdbc | [jdbc](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/package-summary.html) | 11 |
| | | 17 |
| jedis-1.4 | [jedis-1.4](https://central.sonatype.com/artifact/redis.clients/jedis) | 1.4.0~1.5.2 |
| | | 2.0.0~2.10.2 |
| | | 3.0.0~3.10.0 |
| | | 4.0.0~4.4.5 |
| | | 5.0.0~5.3.0-beta1 |
| kafka-clients-0.11 | [kafka-clients-0.11](https://central.sonatype.com/artifact/org.apache.kafka/kafka-clients) | 0.8.2-beta~0.11.0.3 |
| | | 1.0.0~1.1.1 |
| | | 2.0.0~2.8.2 |
| | | 3.0.0~3.9.0 |
| lettuce-5.1 | [lettuce-5.1](https://central.sonatype.com/artifact/io.lettuce/lettuce-core) | 5.1.0.RELEASE~5.3.7.RELEASE |
| | | 6.0.0.M1~6.6.0.BETA2 |
| netty-4.1 | [netty-4.1](https://central.sonatype.com/artifact/io.netty/netty-codec-http) | 4.1.0.Final~4.1.1.Final |
| | | 4.1.3.Final~4.1.7.Final |
| | | 4.1.9.Final |
| | | 4.1.11.Final~4.1.14.Final |
| | | 4.1.16.Final~4.1.21.Final |
| | | 4.1.23.Final~4.1.32.Final |
| | | 4.1.34.Final~4.1.71.Final |
| | | 4.1.73.Final~4.1.78.Final |
| | | 4.1.80.Final~4.1.90.Final |
| | | 4.1.92.Final~4.1.101.Final |
| | | 4.1.103.Final~4.2.0.Alpha2 |
| | | 4.2.0.Alpha4~4.2.0.RC1 |
| rabbitmq-2.7 | [rabbitmq-2.7](https://central.sonatype.com/artifact/com.rabbitmq/amqp-client) | 2.7.0 |
| | | 3.1.2~3.6.6 |
| | | 4.0.0~4.12.0 |
| | | 5.0.0~5.24.0 |
| servlet-3.0 | [servlet-3.0](https://central.sonatype.com/artifact/javax.servlet/javax.servlet-api) | 3.0.1~3.1.0 |
| | | 4.0.0~4.0.1 |
| | | 3.1-b01 |
| | | 3.1-b02 |
| | | 3.1-b03 |
| | | 3.1-b04 |
| | | 3.1-b05 |
| | | 3.1-b06 |
| | | 3.1-b07 |
| | | 3.1-b08 |
| | | 3.1-b09 |
| spring-webflux-5.0 | [spring-webflux-5.0](UNKNOWN) | 5.0.0.RELEASE |
| spring-webmvc-3.1 | [spring-webmvc-3.1](https://central.sonatype.com/artifact/org.springframework/spring-webmvc) | 3.1.0.RELEASE~3.2.18.RELEASE |
| | | 4.0.0.RELEASE~4.3.30.RELEASE |
| | | 5.0.0.RELEASE~5.3.31 |
| | | 6.0.0~6.2.1 |
| storm | [storm](https://central.sonatype.com/artifact/org.apache.storm/storm-client) | 2.0.0~2.7.1 |

## Baseline setup

The Lumigo OpenTelemetry Distro will automatically create the following OpenTelemetry constructs provided to a `TraceProvider`.

### Resource attributes

#### SDK resource attributes

* The attributes from the default resource:
  * `telemetry.sdk.language`: `java`
  * `telemetry.sdk.name`: `opentelemetry`
  * `telemetry.sdk.version`: depends on the version of the `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom` included in the [dependencies](./build.gradle)

* The `lumigo.distro.version` containing the version of the Lumigo OpenTelemetry Distro for Java

#### Process resource attributes

* The following `process.runtime.*` attributes as specified in the [Process Semantic Conventions](https://opentelemetry.io/docs/reference/specification/resource/semantic_conventions/process/#process-runtimes):
  * `process.runtime.description`
  * `process.runtime.name`
  * `process.runtime.version`

#### Amazon ECS resource attributes

If the instrumented application is running on the Amazon Elastic Container Service (ECS):

* `cloud.provider` attribute with value `aws`
* `cloud.platform` with value `aws_ecs`
* `container.name` with the hostname of the ECS Task container
* `container.id` with the ID of the Docker container (based on the cgroup id)

If the ECS task uses the ECS agent v1.4.0, and has therefore access to the [Task metadata endpoint version 4](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v4.html), the following experimental attributes as specified in the [AWS ECS Resource Attributes](https://github.com/open-telemetry/opentelemetry-specification/blob/42081e023b3827d824c45031e3ccd19318ff3411/specification/resource/semantic_conventions/cloud_provider/aws/ecs.md)
specification:

* `aws.ecs.container.arn`
* `aws.ecs.cluster.arn`
* `aws.ecs.launchtype`
* `aws.ecs.task.arn`
* `aws.ecs.task.family`
* `aws.ecs.task.revision`

#### Kubernetes resource attributes

* `k8s.pod.uid` with the Pod identifier, supported for both cgroups v1 and v2
* `k8s.container.name` with the name of the container.

### Span exporters

* If the `LUMIGO_TRACER_TOKEN` environment variable is set: an [`OTLP Exporter`](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)
  is configured to push data to Lumigo
* If the `LUMIGO_DEBUG_SPANDUMP` environment variable is set to a path-like value, we define the [`FileLoggingSpanExporter`](https://github.com/lumigo-io/opentelemetry-java-distro/blob/main/custom/src/main/java/io/lumigo/javaagent/FileLoggingSpanExporter.java), to save to file the spans collected.
  Do not use this flag in production unless you need to troubleshoot trace data!

### SDK configuration

The default maximum span attribute length is 1024.
This can be overwritten with the `OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT` and `OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT` environment variables, but when sending data to Lumigo, the span attributes will be truncated to 2048 maximum length.

The Lumigo OpenTelemetry Java distro automatically configures a [`BatchSpanProcessor`](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/export/BatchSpanProcessor.java) to export tracing data to Lumigo, with the following settings:

* `otel.bsp.schedule.delay`: `10ms` (at least how often the `BatchSpanProcessor` will flush data to Lumigo)
* `otel.bsp.max.export.batch.size`: `100` (maximum amount of spans queued before flushing; when the limit is passed, a flush will occur) 
* `otel.bsp.export.timeout`: `1s` (timeout for flushing data to Lumigo)

The metrics and logs exporters are disabled (`otel.logs.exporter` and `otel.metrics.exporter` are set to `none`) as [Lumigo OpenTelemetry endpoint](https://docs.lumigo.io/docs/lumigo-opentelemetry-endpoint) currently does not provide support for the `/v1/metrics` and `/v1/logs` endpoints.
