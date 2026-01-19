# ---------- Stage 1: Build with Maven ----------
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only POM files first (for dependency caching)
COPY pom.xml .
COPY utilities/commons/pom.xml ./utilities/commons/
COPY microservices/portal-bff/pom.xml ./microservices/portal-bff/

# Install parent POM and download commons dependencies (this layer is cached)
RUN mvn -N install -B && \
    mvn -f utilities/commons/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy commons source and install it
COPY utilities/commons/src ./utilities/commons/src
RUN mvn -f utilities/commons/pom.xml install -DskipTests -B

# Now resolve portal-bff dependencies (parent and commons are now in local repo)
RUN mvn -f microservices/portal-bff/pom.xml dependency:resolve dependency:resolve-plugins -B

# Copy portal-bff source code (changes here won't invalidate dependency cache)
COPY microservices/portal-bff/src ./microservices/portal-bff/src

# Compile the project and create the jar (dependencies already downloaded)
RUN mvn -f microservices/portal-bff/pom.xml package -DskipTests -B

# ---------- Stage 2: Final image ----------
FROM eclipse-temurin:21-jdk-alpine

ARG APP_YML=portal-bff.yml
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the executable jar from the build stage
COPY --from=build /app/microservices/portal-bff/target/portal-bff-1.0.0-exec.jar app.jar

# Copy common configuration
COPY microservices/portal-bff/configs/application.yml application.yml

# Copy instance-specific configuration
COPY microservices/portal-bff/configs/${APP_YML} instance.yml

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Document the port the service listens on
EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
