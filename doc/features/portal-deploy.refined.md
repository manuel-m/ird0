# Portal Frontend Deployment

## Overview

The Portal Frontend Deployment feature packages the Angular SPA as a production-ready nginx container that serves static assets and reverse-proxies API requests to the Portal BFF and Keycloak identity provider.

### Business Value

- **Production Ready**: Self-contained deployment unit requiring no external web server configuration
- **Simplified Operations**: Single container handles static serving and API routing
- **Consistent Environment**: Same nginx configuration across development, staging, and production
- **Optimized Delivery**: Gzip compression, cache headers, and content hashing for efficient asset delivery

---

## User Stories

### US-DEPLOY-001: Build Angular App for Production

**As a** DevOps engineer
**I want** a multi-stage Docker build that produces optimized production bundles
**So that** the deployed assets are minified, tree-shaken, and content-hashed

**Acceptance Criteria**:
- Build uses Node.js 22 Alpine for small image size
- Production Angular build with `ng build --configuration=production`
- Output includes content hashes for cache busting (`main.[hash].js`)
- pnpm used for dependency installation with frozen lockfile
- Build artifacts placed in `/usr/share/nginx/html`

### US-DEPLOY-002: Serve Static Files via Nginx

**As a** user
**I want** the frontend served by a lightweight, production-grade web server
**So that** page loads are fast and reliable

**Acceptance Criteria**:
- nginx:alpine base image (minimal footprint)
- Static files served from `/usr/share/nginx/html`
- Gzip compression for text-based assets (HTML, CSS, JS, JSON)
- Correct MIME types for all assets

### US-DEPLOY-003: SPA Routing Support

**As a** user
**I want** deep links to work when navigating directly to routes like `/claims/123`
**So that** I can bookmark and share URLs

**Acceptance Criteria**:
- All non-file requests fall back to `/index.html`
- `try_files $uri $uri/ /index.html` configuration
- Angular router handles client-side routing after initial load

### US-DEPLOY-004: API Reverse Proxy to BFF

**As a** user
**I want** API requests proxied to the Portal BFF
**So that** the frontend doesn't need CORS configuration

**Acceptance Criteria**:
- Requests to `/api/*` proxied to `http://bff:8080/api/`
- Original headers preserved (`Host`, `X-Real-IP`, `X-Forwarded-For`)
- Proxy timeout configured for API responsiveness
- No CORS headers needed (same-origin from browser perspective)

### US-DEPLOY-005: Keycloak Reverse Proxy

**As a** user
**I want** OIDC requests proxied to Keycloak
**So that** authentication works without cross-origin issues

**Acceptance Criteria**:
- Requests to `/realms/*` proxied to `http://keycloak:8080/realms/`
- Discovery document (`.well-known/openid-configuration`) accessible
- Token endpoint accessible for refresh operations
- `silent-refresh.html` works with same-origin Keycloak

### US-DEPLOY-006: Docker Compose Integration

**As a** developer
**I want** the frontend added to Docker Compose
**So that** I can run the full stack locally with one command

**Acceptance Criteria**:
- Service defined in `deploy/docker-compose.apps.yml`
- Build context points to portal-frontend directory
- Port mapping configurable via environment variable
- Depends on `portal-bff` and `keycloak` services
- Health check verifies nginx is responding
- Network alias `portal` for service discovery

---

## Architecture

### Container Build Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  Stage 1: Build (node:22-alpine)                                │
│                                                                  │
│  1. COPY package.json pnpm-lock.yaml                            │
│  2. RUN corepack enable && pnpm install --frozen-lockfile       │
│  3. COPY . .                                                     │
│  4. RUN pnpm build                                               │
│                                                                  │
│  Output: dist/portal-frontend/browser/                          │
│          ├── index.html                                          │
│          ├── silent-refresh.html                                 │
│          ├── main.[hash].js                                      │
│          ├── polyfills.[hash].js                                │
│          └── styles.[hash].css                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Stage 2: Serve (nginx:alpine)                                  │
│                                                                  │
│  1. COPY --from=build dist/portal-frontend/browser → /html      │
│  2. COPY nginx.conf → /etc/nginx/nginx.conf                     │
│  3. EXPOSE 80                                                    │
│                                                                  │
│  Final image size: ~25MB                                         │
└─────────────────────────────────────────────────────────────────┘
```

### Request Routing

```
                                    ┌──────────────────────────┐
                                    │      portal-frontend     │
                                    │      (nginx:alpine)      │
                                    │                          │
    Browser ──────────────────────► │  :80                     │
                                    │                          │
    GET /                           │  → /index.html           │
    GET /dashboard                  │  → /index.html (SPA)     │
    GET /claims/123                 │  → /index.html (SPA)     │
    GET /main.abc123.js             │  → /main.abc123.js       │
    GET /silent-refresh.html        │  → /silent-refresh.html  │
                                    │                          │
    GET /api/portal/v1/dashboard    │  → proxy_pass bff:8080   │
    POST /api/portal/v1/claims      │  → proxy_pass bff:8080   │
                                    │                          │
    GET /realms/ird0/.well-known/.. │  → proxy_pass keycloak   │
    POST /realms/ird0/.../token     │  → proxy_pass keycloak   │
                                    └──────────────────────────┘
```

### Caching Strategy

| Pattern | Cache-Control | Rationale |
|---------|---------------|-----------|
| `*.[hash].(js|css)` | `max-age=31536000, immutable` | Content-hashed files never change |
| `index.html` | `no-cache` | Must check for new app versions |
| `silent-refresh.html` | `no-cache` | Critical for auth flow |
| Other assets | Browser default | Favicon, fonts, images |

---

## Implementation Specification

### Dockerfile

Location: `portal-frontend/Dockerfile`

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app

# Enable corepack for pnpm
RUN corepack enable

# Install dependencies first (layer caching)
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Copy source and build
COPY . .
RUN pnpm build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist/portal-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Nginx Configuration

Location: `portal-frontend/nginx.conf`

```nginx
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent"';
    access_log /var/log/nginx/access.log main;

    sendfile on;
    keepalive_timeout 65;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/javascript application/javascript
               application/json application/xml text/xml;

    server {
        listen 80;
        server_name _;
        root /usr/share/nginx/html;

        # Cache hashed assets (immutable)
        location ~* \.[a-f0-9]{16}\.(js|css)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # No cache for entry points
        location = /index.html {
            add_header Cache-Control "no-cache";
        }

        location = /silent-refresh.html {
            add_header Cache-Control "no-cache";
        }

        # API proxy to BFF
        location /api/ {
            proxy_pass http://bff:8080/api/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 30s;
            proxy_read_timeout 60s;
        }

        # Keycloak proxy for OIDC
        location /realms/ {
            proxy_pass http://keycloak:8080/realms/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_buffer_size 128k;
            proxy_buffers 4 256k;
            proxy_busy_buffers_size 256k;
        }

        # SPA fallback - must be last
        location / {
            try_files $uri $uri/ /index.html;
        }
    }
}
```

### Docker Compose Service

Location: `deploy/docker-compose.apps.yml` (addition)

```yaml
portal-frontend:
  build:
    context: ..
    dockerfile: portal-frontend/Dockerfile
  image: portal-frontend
  restart: unless-stopped
  ports:
    - "${PORTAL_FRONTEND_HOST_PORT}:80"
  deploy:
    resources:
      limits:
        cpus: '0.25'
        memory: 64M
      reservations:
        memory: 32M
  depends_on:
    portal-bff:
      condition: service_healthy
    keycloak:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:80/"]
    interval: 30s
    timeout: 5s
    retries: 3
    start_period: 10s
  networks:
    insurance-network:
      aliases:
        - ${PORTAL_FRONTEND_SERVICE_HOST}
```

### Environment Variables

Location: `.env.example` (additions)

```bash
# Portal Frontend
PORTAL_FRONTEND_HOST_PORT=4200
PORTAL_FRONTEND_SERVICE_HOST=portal
```

---

## Testing

### Build Verification

```bash
# Build the container
docker compose -f deploy/docker-compose.apps.yml build portal-frontend

# Verify image size
docker images portal-frontend
# Expected: ~25-30MB
```

### Functional Testing

```bash
# Start all services
docker compose up -d

# Verify static assets
curl -I http://localhost:4200/
# Expected: HTTP 200, Content-Type: text/html

curl -I http://localhost:4200/main.*.js
# Expected: HTTP 200, Cache-Control: public, immutable

# Verify SPA routing
curl http://localhost:4200/claims/123
# Expected: HTTP 200, returns index.html

# Verify API proxy
curl http://localhost:4200/api/portal/v1/dashboard
# Expected: 401 (unauthenticated) or 200 (with valid token)

# Verify Keycloak proxy
curl http://localhost:4200/realms/ird0/.well-known/openid-configuration
# Expected: HTTP 200, OIDC discovery document JSON
```

### End-to-End Authentication Test

1. Open http://localhost:4200 in browser
2. Should redirect to Keycloak login (via /realms/ird0/...)
3. Login as `viewer` / `viewer`
4. Should return to dashboard
5. User profile should display in sidenav
6. Logout should redirect to Keycloak and clear session

---

## Constraints

- nginx:alpine base image for minimal attack surface
- No runtime environment variables (build-time configuration only)
- Health check must not require authentication
- Container runs as non-root user (nginx default)
- All proxy targets use Docker DNS names

---

## Related Documentation

- [portal-deploy.md](portal-deploy.md) - Overview documentation
- [user-authentication.refined.md](user-authentication.refined.md) - Authentication implementation
- [docker.md](../topics/docker.md) - Docker multi-stage build patterns
- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture
