# ---------- Stage 1: Build with Maven ----------
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only POM files first (for dependency caching)
COPY pom.xml .
COPY utilities/commons/pom.xml ./utilities/commons/
COPY microservices/directory/pom.xml ./microservices/directory/

# Install parent POM and download commons dependencies (this layer is cached)
RUN mvn -N install -B && \
    mvn -f utilities/commons/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy commons source and install it
COPY utilities/commons/src ./utilities/commons/src
RUN mvn -f utilities/commons/pom.xml install -DskipTests -B

# Download all dependencies (this layer is cached and reused across all 3 services)
# This creates a separate Docker layer that only invalidates when POM files change
RUN mvn -f microservices/directory/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy source code (changes here won't invalidate dependency cache)
COPY microservices/directory/src ./microservices/directory/src

# Compile the project and create the jar (dependencies already downloaded)
RUN mvn -f microservices/directory/pom.xml package -DskipTests

# ---------- Stage 2: Final image ----------
FROM eclipse-temurin:21-jdk-alpine

ARG APP_YML=application.yml
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the executable jar from the build stage
COPY --from=build /app/microservices/directory/target/directory-1.0.0-exec.jar app.jar

# Copy common configuration
COPY microservices/directory/configs/application.yml application.yml

# Copy instance-specific configuration
COPY microservices/directory/configs/${APP_YML} instance.yml

# Create directories needed by the application and set ownership
RUN mkdir -p /app/temp/sftp-downloads \
             /app/data/sftp-metadata \
             /app/data/sftp-errors \
             /app/data/sftp-failed && \
    chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Document the port the service listens on
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
