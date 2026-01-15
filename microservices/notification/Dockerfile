# ---------- Stage 1: Build with Maven ----------
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only POM files first (for dependency caching)
COPY pom.xml .
COPY microservices/notification/pom.xml ./microservices/notification/

# Download all dependencies (this layer is cached)
RUN mvn -f microservices/notification/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy source code (changes here won't invalidate dependency cache)
COPY microservices/notification/src ./microservices/notification/src

# Compile the project and create the jar (dependencies already downloaded)
RUN mvn -f microservices/notification/pom.xml package -DskipTests

# ---------- Stage 2: Final image ----------
FROM eclipse-temurin:21-jdk-alpine

ARG APP_YML=notification.yml
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the executable jar from the build stage
COPY --from=build /app/microservices/notification/target/notification-1.0.0-exec.jar app.jar

# Copy common configuration
COPY microservices/notification/configs/application.yml application.yml

# Copy instance-specific configuration
COPY microservices/notification/configs/${APP_YML} instance.yml

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Document the port the service listens on
EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
