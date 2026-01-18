include .env
export

# Export UID/GID for container user mapping (bind mount permissions)
export UID := $(shell id -u)
export GID := $(shell id -g)

CORE_SERVICES := vault postgres
SONAR_COMPOSE_FILE := deploy/docker-compose.sonar.yml

.PHONY: all restart sonar sonar-start sonar-stop sonar-status sonar-logs sonar-restart reinit issues setup-dirs

all: restart

restart: stop build setup-dirs start-core vault-init start-rest

reinit: stop-rm-volumes build setup-dirs start-core vault-init start-rest

setup-dirs:
	@mkdir -p data/sftp-metadata data/sftp-errors data/sftp-failed temp/sftp-downloads

stop-rm-volumes:
	docker compose -p $(PROJECT_NAME) down -v --remove-orphans

stop:
	docker compose -p $(PROJECT_NAME) down --remove-orphans

build:
	docker compose -p $(PROJECT_NAME) build

start-core:
	docker compose -p $(PROJECT_NAME) up -d $(CORE_SERVICES)
	sleep 4

vault-init:
	bash scripts/vault-init.sh

start-rest:
	docker compose -p $(PROJECT_NAME) up -d


sonar:
	@echo "Checking if SonarQube is running..."
	@docker compose -p $(PROJECT_NAME) -f $(SONAR_COMPOSE_FILE) ps | grep -q sonarqube || (echo "ERROR: SonarQube is not running. Start it with: make sonar-start" && exit 1)
	@echo "Running SonarQube analysis..."
	mvn clean verify sonar:sonar -Dsonar.host.url=http://$(SONAR_HOST):$(SONAR_PORT) -Dsonar.token=$(SONAR_TOKEN)

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
