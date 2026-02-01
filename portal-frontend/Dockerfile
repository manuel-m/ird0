# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app

# Enable corepack for pnpm
RUN corepack enable

# Install dependencies first (layer caching)
COPY portal-frontend/package.json portal-frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Copy source and build
COPY portal-frontend/ .
RUN pnpm build

# Stage 2: Serve
FROM nginx:alpine
RUN apk add --no-cache curl openssl

COPY --from=build /app/dist/portal-frontend/browser /usr/share/nginx/html

# Copy both nginx configs (entrypoint selects based on ENV_MODE)
COPY portal-frontend/nginx.conf /etc/nginx/nginx.conf
COPY portal-frontend/nginx-production.conf /etc/nginx/nginx-production.conf

# Copy entrypoint script
COPY portal-frontend/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80 443

ENTRYPOINT ["/docker-entrypoint.sh"]
