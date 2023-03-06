# Lumigo OpenTelemetry Distro for Java

The Lumigo OpenTelemetry Distro for Java is made of several upstream OpenTelemetry packages, with additional automated quality-assurance and customizations that optimize for no-code injection, meaning that you should need to update exactly zero lines of code in your application in order to make use of the Lumigo OpenTelemetry Distribution, see the [Tracer activation](#tracer-activation) section.

## Setup

The set up of Lumigo OpenTelemetry Distro for Java is made of the three following steps:
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

| Name                    | Valid values  | Required? | Description                                                                              |
|-------------------------|---------------|-----------|------------------------------------------------------------------------------------------|
| `LUMIGO_TRACER_TOKEN`   | `t_...` token | Yes       | The token of the account to report to.                                                   |
| `LUMIGO_DEBUG`          | `true\|false`  | No        | Enable extensive debug logging.                                                          |
| `LUMIGO_SWITCH_OFF`     | `true\|false`  | No        | Disable the agent.                                                                       |
| `LUMIGO_DEBUG_SPANDUMP` | path-like, e.g., `/dev/stdout` | No        | Enable debug span dump. If the value is a path-like, it'll export the spans to this path |

For more configuration options, see the [Upstream Agent Configuration](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/).
