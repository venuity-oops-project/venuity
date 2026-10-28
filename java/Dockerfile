FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw \
    && ./mvnw -B -ntp -pl venuity-server -am -DskipTests package

FROM eclipse-temurin:25-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system venuity \
    && useradd --system --gid venuity --home-dir /app --shell /usr/sbin/nologin venuity

WORKDIR /app
COPY --from=build /workspace/venuity-server/target/venuity.jar /app/venuity.jar

USER venuity
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/venuity.jar"]
