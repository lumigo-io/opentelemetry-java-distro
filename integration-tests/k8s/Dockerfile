FROM alpine

# context is the root of the repo
COPY agent/build/libs/agent-dev-SNAPSHOT-all.jar /work/javaagent/opentelemetry-javaagent.jar

ENTRYPOINT ["cp", "/work/javaagent/opentelemetry-javaagent.jar", "/opt/javaagent/opentelemetry-javaagent.jar"]
