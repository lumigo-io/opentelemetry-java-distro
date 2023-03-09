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

It is also strongly suggested that you set the `OTEL_SERVICE_NAME` environment variable with, as value, the service name you have chosen for your application:

```console
OTEL_SERVICE_NAME=<service name>
```

Replace `<service name> with the desired name of the service`.

**Note:** While you are providing environment variables for configuration, consider also providing the one needed for [no-code tracer activation](#no-code-activation) :-)

### Tracer activation

#### Option 1: JAVA_TOOL_OPTIONS environment variables

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
| `LUMIGO_TRACER_TOKEN`   | `t_...` token                  | Yes       | The token of the account to report to.                                                   |
| `LUMIGO_DEBUG`          | `true\                         | false`    | No                                                                                       | Enable extensive debug logging.                                                          |
| `LUMIGO_SWITCH_OFF`     | `true\                         | false`    | No                                                                                       | Disable the agent.                                                                       |
| `LUMIGO_DEBUG_SPANDUMP` | path-like, e.g., `/dev/stdout` | No        | Enable debug span dump. If the value is a path-like, it'll export the spans to this path |

For more configuration options, see
the [Upstream Agent Configuration](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/).

## Baseline setup

The Lumigo OpenTelemetry Distro will automatically create the following OpenTelemetry constructs
provided to a `TraceProvider`.

### Resource attributes

#### SDK resource attributes

* The attributes from the default resource:
  * `telemetry.sdk.language`: `java`
  * `telemetry.sdk.name`: `opentelemetry`
  * `telemetry.sdk.version`: depends on the version of the `opentelemetry-sdk` included in
    the [dependencies](./setup.py)

* The `lumigo.distro.version` containing the version of the Lumigo OpenTelemetry Distro for Python
  as specified in the [VERSION file](./src/lumigo_opentelemetry/VERSION)

#### Process resource attributes

* The following `process.runtime.*` attributes as specified in
  the [Process Semantic Conventions](https://opentelemetry.io/docs/reference/specification/resource/semantic_conventions/process/#process-runtimes):
  * `process.runtime.description`
  * `process.runtime.name`
  * `process.runtime.version`

#### Amazon ECS resource attributes

If the instrumented Python application is running on the Amazon Elastic Container Service (ECS):

* `cloud.provider` attribute with value `aws`
* `cloud.platform` with value `aws_ecs`
* `container.name` with the hostname of the ECS Task container
* `container.id` with the ID of the Docker container (based on the cgroup id)

If the ECS task uses the ECS agent v1.4.0, and has therefore access to
the [Task metadata endpoint version 4](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v4.html),
the following experimental attributes as specified in
the [AWS ECS Resource Attributes](https://github.com/open-telemetry/opentelemetry-specification/blob/42081e023b3827d824c45031e3ccd19318ff3411/specification/resource/semantic_conventions/cloud_provider/aws/ecs.md)
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

* If the `LUMIGO_TRACER_TOKEN` environment variable is set:
  an [`OTLP Exporter`](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)
  is configured to push data to Lumigo
* If the `LUMIGO_DEBUG_SPANDUMP` environment variable is set to a path-like:
  a [`SimpleSpanProcessor`](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/export/SimpleSpanProcessor.java),
  which uses
  a [`FileLoggingSpanExporter`](https://github.com/lumigo-io/opentelemetry-java-distro/blob/main/custom/src/main/java/io/lumigo/javaagent/FileLoggingSpanExporter.java),
 to save to file the spans collected. Do not use this in production!

### SDK configuration

* The
  following [SDK environment variables](https://opentelemetry.io/docs/reference/specification/sdk-environment-variables/)
  are supported:
  * `OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT`
  * `OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT`

  ** If the `OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT` environment variable is not set, the span
  attribute size limit will be taken from `OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT` environment variable.
  The default size limit when both are not set is 2048.
