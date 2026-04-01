# Stage 1: build Angular app (production) — output is packed into the Spring Boot jar
FROM node:22-bookworm-slim AS frontend
WORKDIR /frontend
COPY static/package.json static/package-lock.json ./
RUN npm ci
COPY static/ ./
RUN npm run build

# Stage 2: compile Spring Boot fat JAR (sources + Gradle cache remain in this stage only)
FROM ghcr.io/nfuchen/wg-control-plane-dev-base:latest AS builder

ENV APP_HOME=/app
WORKDIR $APP_HOME

COPY gradlew $APP_HOME/
COPY gradle/ $APP_HOME/gradle/
COPY build.gradle.kts $APP_HOME/
COPY settings.gradle.kts $APP_HOME/

COPY src/ $APP_HOME/src/

# Spring Boot serves from classpath:/static/ — must match Angular baseHref (/app/) and SpaController forward:/app/index.html
COPY --from=frontend /frontend/dist/static/browser/ $APP_HOME/src/main/resources/static/app/

RUN chmod +x gradlew

ARG TARGETARCH
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-${TARGETARCH}
ENV PATH="$JAVA_HOME/bin:$PATH"

RUN ./gradlew bootJar --no-daemon

# Stage 3: slim runtime — Temurin JRE + WireGuard CLI stack (not the dev-base image).
# wg-quick shells out to ip(8) and often iptables (PostUp/PostDown); keep those packages.
FROM eclipse-temurin:17-jre-jammy

ENV APP_HOME=/app \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wireguard-tools \
        iproute2 \
        iptables \
        # Python and Ansible for remote management
        python3 \
        python3-pip \
        openssh-client \
        curl \
    && pip3 install --no-cache-dir \
        ansible \
        ansible-core \
        paramiko \
        sshpass \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf ~/.cache/pip

WORKDIR $APP_HOME

COPY --from=builder /app/build/libs/wg-control-plane-0.0.1-SNAPSHOT.jar $APP_HOME/app.jar

EXPOSE 8080

RUN groupadd -r appuser && useradd -r -g appuser appuser && \
    chown -R appuser:appuser $APP_HOME

# Process runs as root so wg-quick can manage interfaces; app files owned by appuser.
# Containers still typically need NET_ADMIN (and often privileged or /dev/net/tun) for WireGuard.

CMD ["java", "-jar", "app.jar"]
