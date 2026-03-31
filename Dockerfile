# Use our custom base image with Java 17, WireGuard, and system tools
FROM ghcr.io/nfuchen/wg-control-plane-dev-base:latest

# Ensure we're in the correct working directory
WORKDIR $APP_HOME

# Copy Gradle wrapper and build files
COPY gradlew $APP_HOME/
COPY gradle/ $APP_HOME/gradle/
COPY build.gradle.kts $APP_HOME/
COPY settings.gradle.kts $APP_HOME/

# Copy source code
COPY src/ $APP_HOME/src/

# Make gradlew executable
RUN chmod +x gradlew

# Build the application
RUN ./gradlew build -x test

# Expose the default Spring Boot port
EXPOSE 8080

# Update ownership for the app directory (appuser already exists in base image)
RUN chown -R appuser:appuser $APP_HOME

# Note: For WireGuard operations, the container will need to run with privileged mode
# or have specific capabilities (NET_ADMIN, SYS_MODULE) and access to /dev/net/tun

# Switch to non-root user for running the app (WireGuard operations may require root)
USER appuser

# Run the application
CMD ["java", "-jar", "build/libs/wg-control-plane-0.0.1-SNAPSHOT.jar"]