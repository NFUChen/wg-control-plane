# Stage 1: build Angular app (production) — output is packed into the Spring Boot jar
FROM node:22-bookworm-slim AS frontend
WORKDIR /frontend
COPY static/package.json static/package-lock.json ./
RUN npm ci
COPY static/ ./
RUN npm run build

# Stage 2: JVM app + WireGuard base image
FROM ghcr.io/nfuchen/wg-control-plane-dev-base:latest

ENV APP_HOME=/app
WORKDIR $APP_HOME

COPY gradlew $APP_HOME/
COPY gradle/ $APP_HOME/gradle/
COPY build.gradle.kts $APP_HOME/
COPY settings.gradle.kts $APP_HOME/

COPY src/ $APP_HOME/src/

# Spring Boot serves from classpath:/static/ — Angular "application" builder emits browser bundle here
COPY --from=frontend /frontend/dist/static/browser/ $APP_HOME/src/main/resources/static/

RUN chmod +x gradlew

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
ENV PATH="$JAVA_HOME/bin:$PATH"

RUN ./gradlew bootJar

EXPOSE 8080

RUN chown -R appuser:appuser $APP_HOME

# For WireGuard operations, the container may need privileged mode or capabilities
# (NET_ADMIN, SYS_MODULE) and access to /dev/net/tun.

CMD ["java", "-jar", "build/libs/wg-control-plane-0.0.1-SNAPSHOT.jar"]
