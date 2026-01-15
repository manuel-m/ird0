CORE_SERVICES := vault postgres

.PHONY: all restart

all: restart

restart: stop build start-core init start-rest

stop:
	docker compose down -v --remove-orphans

build:
	docker compose build

start-core:
	docker compose up -d $(CORE_SERVICES)
	sleep 4

init:
	bash scripts/vault-init.sh

start-rest:
	docker compose up -d

