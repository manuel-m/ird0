include .env
export

# Export UID/GID for container user mapping (bind mount permissions)
export UID := $(shell id -u)
export GID := $(shell id -g)

APP_SERVICES := \
policyholders-svc \
experts-svc \
incident-svc \
notification-svc \
providers-svc \
insurers-svc \
portal-bff

CORE_SERVICES := vault postgres
SONAR_COMPOSE_FILE := deploy/docker-compose.sonar.yml

.PHONY: \
all \
apps-stop \
apps-build \
apps-start \
apps-restart \
service-restart \
front-build \
restart \
sonar \
sonar-start \
sonar-stop \
sonar-status \
sonar-logs \
sonar-restart \
reinit issues \
setup-dirs \
start-db \
java-verify \
test-data-build \
test-data-inject \
smoke-test-build \
smoke-test

all: restart

restart: stop docker-build setup-dirs core-start vault-init start-rest front-build

reinit: stop-rm-volumes docker-build setup-dirs core-start vault-init start-rest

apps-restart: apps-stop java-verify apps-build apps-start

service-restart:
	docker compose -p $(PROJECT_NAME) down $(SVC)
	docker compose -p $(PROJECT_NAME) build $(SVC)
	docker compose -p $(PROJECT_NAME) up -d $(SVC)

apps-start:
	docker compose -p $(PROJECT_NAME) up -d $(APP_SERVICES)

apps-build:
	docker compose -p $(PROJECT_NAME) build $(APP_SERVICES)

apps-stop:
	docker compose -p $(PROJECT_NAME) down $(APP_SERVICES)

core-start:
	docker compose -p $(PROJECT_NAME) up -d $(CORE_SERVICES)
	sleep 4

front-dev:
	cd portal-frontend && pnpm generate-api && pnpm start

front-build:
	cd portal-frontend && pnpm generate-api && pnpm build

setup-dirs:
	@mkdir -p data/sftp-metadata data/sftp-errors data/sftp-failed temp/sftp-downloads

stop-rm-volumes:
	docker compose -p $(PROJECT_NAME) down -v --remove-orphans

stop:
	docker compose -p $(PROJECT_NAME) down

docker-build: java-verify
	docker compose -p $(PROJECT_NAME) build

start-db:
	docker compose -p $(PROJECT_NAME) up -d postgres

vault-init:
	bash scripts/vault-init.sh

start-rest:
	docker compose -p $(PROJECT_NAME) up -d

java-prettier:
	./mvnw spotless:apply -f microservices/incident/pom.xm

java-verify:
	./mvnw clean verify


test-data-build:
	./mvnw -f utilities/directory-data-generator/pom.xml clean package

test-data-inject:
	java -jar utilities/directory-data-generator/target/directory-data-generator.jar 100 -e POLICYHOLDER -o /tmp/policyholders.csv
	curl -X POST http://localhost:$(POLICYHOLDERS_HOST_PORT)/api/policyholders/import -F "file=@/tmp/policyholders.csv"
	java -jar utilities/directory-data-generator/target/directory-data-generator.jar 5 -e INSURER -o /tmp/insurers.csv
	curl -X POST http://localhost:$(INSURERS_HOST_PORT)/api/insurers/import -F "file=@/tmp/insurers.csv"


sonar:
	@echo "Checking if SonarQube is running..."
	@docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) ps | grep -q sonarqube || (echo "ERROR: SonarQube is not running. Start it with: make sonar-start" && exit 1)
	@echo "Running SonarQube analysis..."
	./mvnw clean java-verify sonar:sonar -Dsonar.host.url=http://$(SONAR_HOST):$(SONAR_PORT) -Dsonar.token=$(SONAR_TOKEN)

issues:
	curl -u $(SONAR_TOKEN): "http://$(SONAR_HOST):$(SONAR_PORT)/api/issues/search?componentKeys=$(SONAR_PROJECT)"

# SonarQube on-demand targets
sonar-start:
	@echo "Starting SonarQube (this may take 1-2 minutes)..."
	docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) up -d
	@echo "SonarQube starting... Wait for health check before running analysis."
	@echo "Check status: make sonar-status"
	@echo "Access UI: http://$(SONAR_HOST):$(SONAR_PORT)"

sonar-stop:
	@echo "Stopping SonarQube..."
	docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) down
	@echo "SonarQube stopped (volumes preserved)"

sonar-status:
	@echo "Checking SonarQube status..."
	@docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) ps
	@echo ""
	@echo "Health status:"
	@docker inspect --format='{{.State.Health.Status}}' $$(docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) ps -q sonarqube 2>/dev/null) 2>/dev/null || echo "SonarQube is not running"

sonar-logs:
	docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) logs -f sonarqube

sonar-restart: sonar-stop sonar-start

smoke-test-build:
	cd utilities/smoke-test && pnpm install && pnpm build

smoke-test:
	node utilities/smoke-test/dist/smoke-test.js
