# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew :queryecho-app:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="QueryEcho" \
      org.opencontainers.image.description="Self-hosted SQL and transaction monitoring server" \
      org.opencontainers.image.source="https://github.com/QueryEcho/QueryEcho-app" \
      org.opencontainers.image.licenses="Apache-2.0"

RUN addgroup -S queryecho && adduser -S -G queryecho queryecho
WORKDIR /app
COPY --from=builder /workspace/queryecho-app/build/libs/*.jar /app/queryecho.jar

USER queryecho
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/queryecho.jar"]

