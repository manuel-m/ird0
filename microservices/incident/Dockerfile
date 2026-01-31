# ---------- Stage 1: Build with Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only POM files first (for dependency caching)
COPY pom.xml .
COPY utilities/commons/pom.xml ./utilities/commons/
COPY microservices/incident/pom.xml ./microservices/incident/

# Install parent POM and download commons dependencies (this layer is cached)
RUN mvn -N install -B && \
    mvn -f utilities/commons/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy commons source and install it
COPY utilities/commons/src ./utilities/commons/src
RUN mvn -f utilities/commons/pom.xml install -DskipTests -B

# Now resolve incident dependencies (parent and commons are now in local repo)
RUN mvn -f microservices/incident/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy incident source code (changes here won't invalidate dependency cache)
COPY microservices/incident/src ./microservices/incident/src

# Compile the project and create the jar (dependencies already downloaded)
RUN mvn -f microservices/incident/pom.xml package -DskipTests -B

# ---------- Stage 2: Final image ----------
FROM eclipse-temurin:21-jdk-alpine

ARG APP_YML=incident.yml
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the executable jar from the build stage
COPY --from=build /app/microservices/incident/target/incident-1.0.0-exec.jar app.jar

# Copy common configuration
COPY microservices/incident/configs/application.yml application.yml

# Copy production profile configuration
COPY microservices/incident/configs/application-production.yml application-production.yml

# Copy instance-specific configuration
COPY microservices/incident/configs/${APP_YML} instance.yml

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Document the port the service listens on
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
