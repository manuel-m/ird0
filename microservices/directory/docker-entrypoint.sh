#!/bin/sh
set -e

# Create required directories (running as host UID via docker-compose user: directive)
mkdir -p /app/temp/sftp-downloads \
         /app/data/sftp-metadata \
         /app/data/sftp-errors \
         /app/data/sftp-failed

# Run the application
exec java -jar app.jar --spring.config.location=file:/app/application.yml,file:/app/instance.yml "$@"
