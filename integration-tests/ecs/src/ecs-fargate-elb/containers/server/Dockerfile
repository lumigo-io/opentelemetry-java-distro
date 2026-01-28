FROM ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk8-20211213.1570880324

COPY ./distro/lumigo-opentelemetry-distro.jar /opt/lumigo/lumigo-opentelemetry-distro.jar
COPY ./test-metadata.sh /usr/local/bin/test-metadata.sh

RUN chmod +x /usr/local/bin/test-metadata.sh

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/lumigo/lumigo-opentelemetry-distro.jar"

ENTRYPOINT ["/usr/local/bin/test-metadata.sh"]
CMD ["java", "-jar", "/app/springboot.jar"]
