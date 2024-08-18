# Use a specific Gradle image
FROM gradle:8.8.0-jdk21 AS builder

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Pre-download dependencies
RUN gradle build -x test -x javadoc -x spotlessApply -x spotlessJavaCheck --no-daemon --info || true

# Copy the rest of the source code
COPY . .

# Final build step
RUN gradle build -x test -x javadoc -x spotlessApply -x spotlessJavaCheck --no-daemon --info
