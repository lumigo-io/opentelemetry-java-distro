FROM ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk8-20211213.1570880324

COPY ./distro/lumigo-opentelemetry-distro.jar /opt/lumigo/lumigo-opentelemetry-distro.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/lumigo/lumigo-opentelemetry-distro.jar"
