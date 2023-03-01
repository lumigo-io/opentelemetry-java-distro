# Lumigo OpenTelemetry JavaAgent Distro

This repository contains the Lumigo OpenTelemetry JavaAgent Distro.

The Lumigo OpenTelemetry JavaAgent Distro made of serval upstream OpenTelemetry components packaged,
with additional automated quality-assurance and customizations that **optimize for no-code injection
**. Meaning, you can use it without changing your code.

## Setup

To install the Lumigo OpenTelemetry JavaAgent Distro, you can download the latest version from
the [releases](https://github.com/lumigo-io/opentelemetry-java-distro/releases) page, and instruct
java to load the provided JAR file. as a JavaAgent.

For example, if you are using the `java` command, you can run the application with the agent by:

```console
java -javaagent:/opt/lumigo-otel-javaagent.jar -jar myapp.jar
```

Another option is to set the `JAVA_TOOL_OPTIONS` environment variable:

```console
export JAVA_TOOL_OPTIONS="-javaagent:/opt/lumigo-otel-javaagent.jar"
```

Now, the only thing left to do is to set the `LUMIGO_TRACER_TOKEN` environment variable with
your token.

```console
export LUMIGO_TRACER_TOKEN="your-token-here"
```

## Configuration

Configuring the Lumigo OpenTelemetry JavaAgent Distro is done using environment variables or system
properties.

The system properties are lower case versions of the environment variables, and with dots replaced
by underscores. For example, the environment variable `LUMIGO_TRACER_TOKEN` can be set using the
system property `lumigo.tracer.token`.

### Environment variables

| Name                    | Description                                                                              |
|-------------------------|------------------------------------------------------------------------------------------|
| `LUMIGO_TRACER_TOKEN`   | The token of the account to report to.                                                   |
| `LUMIGO_DEBUG`          | Enable extensive debug logging.                                                          |
| `LUMIGO_SWITCH_OFF`     | Disable the agent.                                                                       |
| `LUMIGO_DEBUG_SPANDUMP` | Enable debug span dump. If the value is a path-like, it'll export the spans to this path |


For more configuration options, see
the [Upstream Agent Configuration](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/).
