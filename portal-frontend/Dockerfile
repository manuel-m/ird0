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
RUN apk add --no-cache curl
COPY --from=build /app/dist/portal-frontend/browser /usr/share/nginx/html
COPY portal-frontend/nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
