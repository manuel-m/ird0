I need your help to **refactor an existing microservices-based insurance portal** to improve configuration consistency and infrastructure decoupling.
---
## Context
The system consists of multiple Spring Boot microservices (Java 21) running locally via **Docker Compose**.
Current problems:
* Service ports are defined in multiple places:
  >
  >   * Java code
  * `application.yml`
  * Docker Compose files
  * Documentation
* Some services still reference each other via `localhost` and explicit ports
---
## Target Architecture & Principles
Please refactor the project following these principles:
1. **Uniform internal ports**
   >
   >    * All Spring Boot services must listen on the same internal port (e.g. `8080`)
   * No hardcoded ports in Java code
2. **Docker DNS-based service discovery**
   >
   >    * Services must communicate using Docker DNS names (service names), not `localhost`
   * Example: `http://insurers:8080` instead of `http://localhost:8084`
3. **Single source of configuration**
   >
   >    * Use a `.env` file as the single source of truth
   * Configuration must be consumable by:
     >
     >      * Spring Boot applications
     * Docker Compose
     * Test execution
4. **Environment-driven configuration**
   >
   >    * Spring Boot must rely on environment variables and profiles
   * Use placeholders with defaults (e.g. `${VAR:default}`)
---
## Refactoring Tasks
Please:
### 1. Spring Boot Configuration
* Refactor `application.yml` files to remove hardcoded ports
* Ensure `server.port` is configurable via environment variables
* Externalize all inter-service URLs
### 2. Docker Compose
* Refactor `docker-compose.yml` to:
  >
  >   * Use a single internal port for all services
  * Remove service-specific internal port definitions
  * Rely on `.env` for configuration
  * Use Docker service names for networking
### 3. Inter-service Communication
* Refactor HTTP clients (RestTemplate, WebClient, Feign, etc.)
* Replace `localhost` and fixed ports with service DNS names
### 4. Tests
* Refactor tests to avoid fixed ports
* Use random ports or injected configuration
* Ensure consistency with `.env`
---
## Expected Output
Please provide:
* A **step-by-step refactoring plan**
* Example snippets for:
  >
  >   * `application.yml`
  * `.env`
  * `docker-compose.yml`
  * Java configuration classes
* Clear explanations of:
  >
  >   * Why each change is needed
  * How it improves maintainability and portability
The goal is to produce a **clean, cloud-native, Docker-friendly microservices setup** that is easy to run locally and easy to deploy later to orchestration platforms.
Write the answer as if you were guiding a team of experienced backend developers.
Update related documentation.

