# ---------- Stage 1: Build with Maven ----------
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only POM files first (for dependency caching)
COPY pom.xml .
COPY microservices/directory/pom.xml ./microservices/directory/

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

# Copy the jar from the build stage
COPY --from=build /app/microservices/directory/target/directory-1.0.0.jar app.jar

# Copy the specific config file
COPY microservices/directory/configs/${APP_YML} application.yml

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml"]
