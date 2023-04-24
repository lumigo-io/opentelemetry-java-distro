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

Configuring the Lumigo OpenTelemetry Distro for Java is done using environment variables or system properties.

The system properties are lower case versions of the environment variables, and with dots replaced by underscores.
For example, the environment variable `LUMIGO_TRACER_TOKEN` can be set using the system property `lumigo.tracer.token`.

### Environment variables

| Name                    | Valid values                   | Required? | Description                                                                              |
|-------------------------|--------------------------------|-----------|------------------------------------------------------------------------------------------|
| `LUMIGO_TRACER_TOKEN`   | `t_...` token                  | Yes       | The token of the account to report to. see the [Lumigo Tokens](https://docs.lumigo.io/docs/lumigo-tokens) documentation for how to retrieve your Lumigo token. |
| `LUMIGO_DEBUG`          | `true` or `false`              | No        | Enable debug logging. |
| `LUMIGO_SWITCH_OFF`     | `true` or `false`              | No        | If set to `true`, disable the Lumigo OpenTelemetry Distro for Java. Default: `false`. |
| `LUMIGO_DEBUG_SPANDUMP` | path-like, e.g., `/dev/stdout` | No        | Print a copy of the spans to to the file path specified as value. In containers, `/dev/stdout` and `/dev/stderr` are often a good choice. |

For more configuration options, see the [Upstream Agent Configuration](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/).

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
