# Multi-stage Dockerfile for Venuity (Java 25 Single-JAR)
# Stage 1: Build the standalone executable JAR using Eclipse Temurin 25 JDK
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY java/ .
RUN chmod +x mvnw \
    && ./mvnw -B -ntp -pl venuity-server -am -DskipTests package

# Stage 2: Hardened, lightweight runtime using Eclipse Temurin 25 JRE
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 venuity \
    && useradd --system --uid 10001 --gid venuity --home-dir /app --shell /usr/sbin/nologin venuity

WORKDIR /app
COPY --from=build --chown=venuity:venuity /workspace/venuity-server/target/venuity.jar /app/venuity.jar

USER venuity

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/venuity.jar"]
