# GitLab CI/CD Pipeline Configuration

This guide documents the CI/CD pipeline configuration for building, testing, and deploying the IRD0 Insurance Platform.

## Pipeline Overview

```
┌─────────┐   ┌─────────┐   ┌──────────┐   ┌─────────┐   ┌─────────┐
│  Build  │ → │  Test   │ → │ Security │ → │ Package │ → │ Deploy  │
└─────────┘   └─────────┘   └──────────┘   └─────────┘   └─────────┘
    │             │              │              │             │
    ▼             ▼              ▼              ▼             ▼
 Compile      Unit tests      SAST         Docker        Coolify
 all Java     Integration   Dependency    images        webhook
 modules      tests         scanning      build/push    trigger
```

## Pipeline Stages

| Stage | Purpose | Duration |
|-------|---------|----------|
| `build` | Compile all microservices | ~3 min |
| `test` | Run unit and integration tests | ~5 min |
| `security` | SAST and dependency scanning | ~2 min |
| `package` | Build Docker images | ~4 min |
| `deploy` | Trigger Coolify deployment | ~1 min |

## Required CI/CD Variables

Configure these in GitLab: Project → Settings → CI/CD → Variables

### Database Credentials

| Variable | Description | Protected | Masked |
|----------|-------------|-----------|--------|
| `POSTGRES_USER` | PostgreSQL username | Yes | No |
| `POSTGRES_PASSWORD` | PostgreSQL password | Yes | Yes |

### Keycloak Configuration

| Variable | Description | Protected | Masked |
|----------|-------------|-----------|--------|
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin password | Yes | Yes |
| `KEYCLOAK_CLIENT_SECRET` | OAuth2 client secret | Yes | Yes |

### Vault Configuration

| Variable | Description | Protected | Masked |
|----------|-------------|-----------|--------|
| `VAULT_TOKEN` | Vault access token | Yes | Yes |
| `VAULT_ADDR` | Vault server address | Yes | No |

### Container Registry

| Variable | Description | Protected | Masked |
|----------|-------------|-----------|--------|
| `CI_REGISTRY` | Registry URL (auto-set if using GitLab registry) | - | - |
| `CI_REGISTRY_USER` | Registry username | No | No |
| `CI_REGISTRY_PASSWORD` | Registry password | Yes | Yes |

### Deployment

| Variable | Description | Protected | Masked |
|----------|-------------|-----------|--------|
| `COOLIFY_WEBHOOK_URL` | Coolify deployment webhook | Yes | No |
| `DEPLOY_DOMAIN` | Production domain | Yes | No |

## Complete .gitlab-ci.yml

```yaml
# .gitlab-ci.yml

stages:
  - build
  - test
  - security
  - package
  - deploy

variables:
  MAVEN_OPTS: >-
    -Dhttps.protocols=TLSv1.2
    -Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository
    -Dorg.slf4j.simpleLogger.showDateTime=true
  MAVEN_CLI_OPTS: >-
    --batch-mode
    --errors
    --fail-at-end
    --show-version
  DOCKER_DRIVER: overlay2
  DOCKER_TLS_CERTDIR: "/certs"

# Cache Maven dependencies between jobs
cache:
  key: ${CI_COMMIT_REF_SLUG}
  paths:
    - .m2/repository/

# Base image for Java jobs
.java-base:
  image: eclipse-temurin:21-jdk
  before_script:
    - chmod +x ./mvnw

# Build stage - compile all modules
build:
  extends: .java-base
  stage: build
  script:
    - ./mvnw $MAVEN_CLI_OPTS clean compile
  artifacts:
    paths:
      - "**/target/classes/"
    expire_in: 1 hour
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
    - if: $CI_COMMIT_TAG

# Test stage - run tests in parallel per service
.test-base:
  extends: .java-base
  stage: test
  needs: ["build"]
  script:
    - ./mvnw $MAVEN_CLI_OPTS test -pl $MODULE_PATH -am
  artifacts:
    when: always
    reports:
      junit: "**/target/surefire-reports/TEST-*.xml"
    expire_in: 1 week

test:directory:
  extends: .test-base
  variables:
    MODULE_PATH: microservices/directory

test:incident:
  extends: .test-base
  variables:
    MODULE_PATH: microservices/incident

test:notification:
  extends: .test-base
  variables:
    MODULE_PATH: microservices/notification

test:portal-bff:
  extends: .test-base
  variables:
    MODULE_PATH: microservices/portal-bff

test:sftp-server:
  extends: .test-base
  variables:
    MODULE_PATH: microservices/sftp-server

# Integration tests
test:integration:
  extends: .java-base
  stage: test
  needs: ["build"]
  services:
    - name: postgres:16-alpine
      alias: postgres
  variables:
    POSTGRES_USER: test_user
    POSTGRES_PASSWORD: test_pass
    POSTGRES_DB: test_db
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/test_db
    SPRING_DATASOURCE_USERNAME: test_user
    SPRING_DATASOURCE_PASSWORD: test_pass
  script:
    - ./mvnw $MAVEN_CLI_OPTS verify -Pintegration-tests
  artifacts:
    when: always
    reports:
      junit: "**/target/failsafe-reports/TEST-*.xml"
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
    - if: $CI_COMMIT_TAG

# Security scanning
sast:
  stage: security
  needs: []
  allow_failure: true

dependency_scanning:
  stage: security
  needs: []
  allow_failure: true

# Include GitLab's security scanning templates
include:
  - template: Security/SAST.gitlab-ci.yml
  - template: Security/Dependency-Scanning.gitlab-ci.yml

# Package stage - build Docker images
.docker-base:
  stage: package
  image: docker:24.0
  services:
    - docker:24.0-dind
  before_script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
  needs:
    - job: test:directory
      optional: true
    - job: test:incident
      optional: true
    - job: test:notification
      optional: true
    - job: test:portal-bff
      optional: true
    - job: test:sftp-server
      optional: true
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
    - if: $CI_COMMIT_TAG

package:directory:
  extends: .docker-base
  script:
    - docker build -t $CI_REGISTRY_IMAGE/directory:$CI_COMMIT_SHA -f microservices/directory/Dockerfile .
    - docker push $CI_REGISTRY_IMAGE/directory:$CI_COMMIT_SHA
    - |
      if [ -n "$CI_COMMIT_TAG" ]; then
        docker tag $CI_REGISTRY_IMAGE/directory:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/directory:$CI_COMMIT_TAG
        docker push $CI_REGISTRY_IMAGE/directory:$CI_COMMIT_TAG
      fi
    - |
      if [ "$CI_COMMIT_BRANCH" == "$CI_DEFAULT_BRANCH" ]; then
        docker tag $CI_REGISTRY_IMAGE/directory:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/directory:latest
        docker push $CI_REGISTRY_IMAGE/directory:latest
      fi

package:incident:
  extends: .docker-base
  script:
    - docker build -t $CI_REGISTRY_IMAGE/incident:$CI_COMMIT_SHA -f microservices/incident/Dockerfile .
    - docker push $CI_REGISTRY_IMAGE/incident:$CI_COMMIT_SHA
    - |
      if [ -n "$CI_COMMIT_TAG" ]; then
        docker tag $CI_REGISTRY_IMAGE/incident:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/incident:$CI_COMMIT_TAG
        docker push $CI_REGISTRY_IMAGE/incident:$CI_COMMIT_TAG
      fi
    - |
      if [ "$CI_COMMIT_BRANCH" == "$CI_DEFAULT_BRANCH" ]; then
        docker tag $CI_REGISTRY_IMAGE/incident:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/incident:latest
        docker push $CI_REGISTRY_IMAGE/incident:latest
      fi

package:notification:
  extends: .docker-base
  script:
    - docker build -t $CI_REGISTRY_IMAGE/notification:$CI_COMMIT_SHA -f microservices/notification/Dockerfile .
    - docker push $CI_REGISTRY_IMAGE/notification:$CI_COMMIT_SHA
    - |
      if [ -n "$CI_COMMIT_TAG" ]; then
        docker tag $CI_REGISTRY_IMAGE/notification:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/notification:$CI_COMMIT_TAG
        docker push $CI_REGISTRY_IMAGE/notification:$CI_COMMIT_TAG
      fi
    - |
      if [ "$CI_COMMIT_BRANCH" == "$CI_DEFAULT_BRANCH" ]; then
        docker tag $CI_REGISTRY_IMAGE/notification:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/notification:latest
        docker push $CI_REGISTRY_IMAGE/notification:latest
      fi

package:portal-bff:
  extends: .docker-base
  script:
    - docker build -t $CI_REGISTRY_IMAGE/portal-bff:$CI_COMMIT_SHA -f microservices/portal-bff/Dockerfile .
    - docker push $CI_REGISTRY_IMAGE/portal-bff:$CI_COMMIT_SHA
    - |
      if [ -n "$CI_COMMIT_TAG" ]; then
        docker tag $CI_REGISTRY_IMAGE/portal-bff:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/portal-bff:$CI_COMMIT_TAG
        docker push $CI_REGISTRY_IMAGE/portal-bff:$CI_COMMIT_TAG
      fi
    - |
      if [ "$CI_COMMIT_BRANCH" == "$CI_DEFAULT_BRANCH" ]; then
        docker tag $CI_REGISTRY_IMAGE/portal-bff:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/portal-bff:latest
        docker push $CI_REGISTRY_IMAGE/portal-bff:latest
      fi

package:sftp-server:
  extends: .docker-base
  script:
    - docker build -t $CI_REGISTRY_IMAGE/sftp-server:$CI_COMMIT_SHA -f microservices/sftp-server/Dockerfile .
    - docker push $CI_REGISTRY_IMAGE/sftp-server:$CI_COMMIT_SHA
    - |
      if [ -n "$CI_COMMIT_TAG" ]; then
        docker tag $CI_REGISTRY_IMAGE/sftp-server:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/sftp-server:$CI_COMMIT_TAG
        docker push $CI_REGISTRY_IMAGE/sftp-server:$CI_COMMIT_TAG
      fi
    - |
      if [ "$CI_COMMIT_BRANCH" == "$CI_DEFAULT_BRANCH" ]; then
        docker tag $CI_REGISTRY_IMAGE/sftp-server:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/sftp-server:latest
        docker push $CI_REGISTRY_IMAGE/sftp-server:latest
      fi

package:portal-frontend:
  extends: .docker-base
  script:
    - docker build -t $CI_REGISTRY_IMAGE/portal-frontend:$CI_COMMIT_SHA -f frontend/portal/Dockerfile .
    - docker push $CI_REGISTRY_IMAGE/portal-frontend:$CI_COMMIT_SHA
    - |
      if [ -n "$CI_COMMIT_TAG" ]; then
        docker tag $CI_REGISTRY_IMAGE/portal-frontend:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/portal-frontend:$CI_COMMIT_TAG
        docker push $CI_REGISTRY_IMAGE/portal-frontend:$CI_COMMIT_TAG
      fi
    - |
      if [ "$CI_COMMIT_BRANCH" == "$CI_DEFAULT_BRANCH" ]; then
        docker tag $CI_REGISTRY_IMAGE/portal-frontend:$CI_COMMIT_SHA $CI_REGISTRY_IMAGE/portal-frontend:latest
        docker push $CI_REGISTRY_IMAGE/portal-frontend:latest
      fi

# Deploy stage - trigger Coolify
deploy:staging:
  stage: deploy
  image: curlimages/curl:latest
  needs:
    - package:directory
    - package:incident
    - package:notification
    - package:portal-bff
    - package:sftp-server
    - package:portal-frontend
  script:
    - |
      curl -X POST "$COOLIFY_WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d "{\"ref\": \"$CI_COMMIT_SHA\", \"environment\": \"staging\"}"
  environment:
    name: staging
    url: https://staging.$DEPLOY_DOMAIN
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
      when: manual

deploy:production:
  stage: deploy
  image: curlimages/curl:latest
  needs:
    - deploy:staging
  script:
    - |
      curl -X POST "$COOLIFY_WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d "{\"ref\": \"$CI_COMMIT_SHA\", \"environment\": \"production\"}"
  environment:
    name: production
    url: https://$DEPLOY_DOMAIN
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
      when: manual
    - if: $CI_COMMIT_TAG
      when: manual
```

## Pipeline Features

### Maven Caching

The pipeline caches Maven dependencies between jobs:

```yaml
cache:
  key: ${CI_COMMIT_REF_SLUG}
  paths:
    - .m2/repository/
```

This significantly speeds up builds after the first run.

### Parallel Test Execution

Tests run in parallel for each microservice:

- `test:directory`
- `test:incident`
- `test:notification`
- `test:portal-bff`
- `test:sftp-server`

Each job only tests its specific module, reducing overall pipeline duration.

### Docker-in-Docker (DinD)

The package stage uses Docker-in-Docker for building images:

```yaml
services:
  - docker:24.0-dind
variables:
  DOCKER_DRIVER: overlay2
  DOCKER_TLS_CERTDIR: "/certs"
```

### Image Tagging Strategy

Images are tagged with:
- Commit SHA (always): `$CI_COMMIT_SHA`
- Git tag (if present): `$CI_COMMIT_TAG`
- `latest` (on main branch)

This allows both precise deployments and easy testing.

### Manual Deployment Gates

Production deployments require manual approval:

```yaml
rules:
  - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
    when: manual
```

## Security Scanning

The pipeline includes GitLab's built-in security scanning:

### SAST (Static Application Security Testing)

Scans source code for vulnerabilities:
- SQL injection patterns
- XSS vulnerabilities
- Hardcoded secrets

### Dependency Scanning

Checks dependencies for known vulnerabilities using:
- OWASP Dependency Check
- Retire.js (for JavaScript)

Results appear in:
- Merge request security tab
- Project → Security & Compliance → Vulnerability Report

## Merge Request Pipelines

For merge requests, only build and test stages run:

```yaml
rules:
  - if: $CI_PIPELINE_SOURCE == "merge_request_event"
```

This provides fast feedback without consuming resources on packaging.

## Customization

### Adding Environment-Specific Variables

For different environments, use variable scoping:

1. Go to Settings → CI/CD → Variables
2. When adding a variable, specify Environment scope:
   - `staging` for staging-only variables
   - `production` for production-only variables

### Adding Notifications

Add Slack/Teams notifications on pipeline completion:

```yaml
notify:success:
  stage: .post
  script:
    - |
      curl -X POST "$SLACK_WEBHOOK" \
        -H "Content-Type: application/json" \
        -d "{\"text\": \"Pipeline succeeded for $CI_PROJECT_NAME\"}"
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
      when: on_success

notify:failure:
  stage: .post
  script:
    - |
      curl -X POST "$SLACK_WEBHOOK" \
        -H "Content-Type: application/json" \
        -d "{\"text\": \"Pipeline FAILED for $CI_PROJECT_NAME\"}"
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
      when: on_failure
```

### Skip CI for Documentation Changes

```yaml
workflow:
  rules:
    - if: $CI_COMMIT_MESSAGE =~ /\[skip ci\]/
      when: never
    - if: $CI_COMMIT_BRANCH
```

## Troubleshooting

### Build Fails with Out of Memory

Increase Maven heap size:

```yaml
variables:
  MAVEN_OPTS: "-Xmx2048m -XX:MaxMetaspaceSize=512m"
```

### Docker Build Fails

Check DinD service is running:
```yaml
services:
  - name: docker:24.0-dind
    command: ["--tls=false"]
```

### Tests Fail to Connect to PostgreSQL

Ensure service alias matches connection string:
```yaml
services:
  - name: postgres:16-alpine
    alias: postgres  # Must match SPRING_DATASOURCE_URL
```

### Cache Not Being Used

Verify cache key matches between jobs. Use specific keys for different branches:
```yaml
cache:
  key: "${CI_COMMIT_REF_SLUG}-${CI_JOB_NAME}"
```

## Next Steps

1. Create `.gitlab-ci.yml` in repository root
2. Configure CI/CD variables in GitLab
3. Run first pipeline on main branch
4. Verify all stages pass
5. Set up Coolify webhook - see [coolify-app-deployment.md](coolify-app-deployment.md)
