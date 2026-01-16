include .env
export

CORE_SERVICES := vault postgres

.PHONY: all restart sonar reinit issues

all: restart

restart: stop build start-core vault-init start-rest

reinit: stop-rm-volumes build start-core vault-init start-rest

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
	mvn clean verify sonar:sonar -Dsonar.host.url=http://$(SONAR_HOST):$(SONAR_PORT) -Dsonar.token=$(SONAR_TOKEN)

issues: 
	curl -u $(SONAR_TOKEN): "http://$(SONAR_HOST):$(SONAR_PORT)/api/issues/search?componentKeys=$(SONAR_PROJECT)"
