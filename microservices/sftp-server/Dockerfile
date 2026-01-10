# ---------- Stage 1: Build with Maven ----------
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only POM files first (for dependency caching)
COPY pom.xml .
COPY microservices/sftp-server/pom.xml ./microservices/sftp-server/

# Download all dependencies (this layer is cached)
RUN mvn -f microservices/sftp-server/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy source code
COPY microservices/sftp-server/src ./microservices/sftp-server/src

# Compile the project and create the jar
RUN mvn -f microservices/sftp-server/pom.xml package -DskipTests

# ---------- Stage 2: Final image ----------
FROM eclipse-temurin:21-jdk-alpine

ARG APP_YML=sftp.yml
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create directories for data and keys
RUN mkdir -p /app/data /app/keys

# Copy the jar from the build stage
COPY --from=build /app/microservices/sftp-server/target/sftp-server-1.0.0-exec.jar app.jar

# Copy common configuration
COPY microservices/sftp-server/configs/application.yml application.yml

# Copy instance-specific configuration
COPY microservices/sftp-server/configs/${APP_YML} instance.yml

# Set ownership of application directory to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose SFTP port and management port
EXPOSE 2222 9090

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
