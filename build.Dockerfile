# Use the official maven/Java 17 image to create a build artifact.
FROM openjdk:17-jdk-slim as builder

# Set the working directory in the image to "/app"
WORKDIR /app

# Copy the Gradle executable to the Docker image
COPY gradlew .
COPY gradle gradle

# Copy the rest of your app's source code from your host to your image filesystem.
COPY build.gradle settings.gradle ./
COPY . /app

# Build the project and skip tests
RUN ./gradlew gradle clean build -x test  -x javadoc -x spotlessApply -x spotlessJavaCheck
