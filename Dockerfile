# =============================================================================
# Stage 1: Build
# =============================================================================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Copy Maven wrapper and POM first for dependency caching
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache git bash curl jq

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy fat JAR from build stage
COPY --from=build /build/target/agent-library-*.jar app.jar

# Copy utility scripts
COPY scripts/gen-password-hash.sh /app/scripts/
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/scripts/gen-password-hash.sh /app/docker-entrypoint.sh

# Create data directory and set ownership.
# Named volumes (docker-compose) inherit these permissions on first mount.
# For bind mounts, the host directory must be writable by UID 100 (appuser).
RUN mkdir -p /data && chown -R appuser:appgroup /data

# Environment defaults (overridable by docker-compose or docker run -e)
ENV LIBRARY_DATA_DIR=/data
ENV LIBRARY_REPO_PATH=/data/library.git
ENV LIBRARY_WORK_PATH=/data/library-work
ENV LIBRARY_USERS_FILE=/data/users.yaml

VOLUME /data
EXPOSE 8080

USER appuser

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
