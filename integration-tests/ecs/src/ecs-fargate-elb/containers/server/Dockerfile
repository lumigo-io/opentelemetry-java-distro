FROM ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk17-20260126.21350382958

COPY ./distro/lumigo-opentelemetry-distro.jar /opt/lumigo/lumigo-opentelemetry-distro.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/lumigo/lumigo-opentelemetry-distro.jar"
