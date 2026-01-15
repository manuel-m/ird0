include .env
export

CORE_SERVICES := vault postgres

.PHONY: all restart

all: restart

restart: stop build start-core init start-rest

stop:
	docker compose -p $(PROJECT_NAME) down -v --remove-orphans

build:
	docker compose -p $(PROJECT_NAME) build

start-core:
	docker compose -p $(PROJECT_NAME) up -d $(CORE_SERVICES)
	sleep 4

init:
	bash scripts/vault-init.sh

start-rest:
	docker compose -p $(PROJECT_NAME) up -d

