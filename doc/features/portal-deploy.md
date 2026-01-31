# Portal Frontend Deployment

## Overview

The Portal Frontend is an Angular SPA deployed as a containerized nginx service. It serves static assets and proxies API requests to the Portal BFF and Keycloak identity provider.

## Architecture

```
                    ┌─────────────────────────────────────────┐
                    │          portal-frontend                 │
                    │          (nginx:alpine)                  │
                    │                                          │
    Browser ───────►│  :80                                     │
                    │   ├── /api/*  ──────────► portal-bff:8080│
                    │   ├── /realms/* ────────► keycloak:8080  │
                    │   └── /* (SPA) ─────────► /index.html    │
                    └─────────────────────────────────────────┘
```

## Build Process

The container uses a multi-stage Docker build:

1. **Build Stage** (Node.js 22 Alpine)
   - Install dependencies with pnpm
   - Build Angular app with production configuration
   - Output: optimized bundles with content hashing

2. **Serve Stage** (nginx:alpine)
   - Copy built assets from build stage
   - Configure nginx with custom configuration
   - Expose port 80

## Proxy Configuration

Nginx reverse proxy handles:

| Path | Target | Purpose |
|------|--------|---------|
| `/api/*` | `http://bff:8080` | API requests to Portal BFF |
| `/realms/*` | `http://keycloak:8080` | OIDC endpoints for authentication |
| `/*` | `/index.html` | SPA fallback routing |

## Caching Strategy

| Asset Type | Cache Control |
|------------|---------------|
| Hashed JS/CSS (`*.hash.js`, `*.hash.css`) | 1 year, immutable |
| `index.html` | no-cache |
| `silent-refresh.html` | no-cache |
| Other assets | Default browser caching |

## Environment Configuration

The Angular app uses relative paths and expects:

- **Production**: API requests are proxied via nginx
- **Development**: API requests are proxied via `ng serve` proxy

| Variable | Default | Description |
|----------|---------|-------------|
| `PORTAL_FRONTEND_HOST_PORT` | `4200` | Host port for accessing the frontend |
| `PORTAL_FRONTEND_SERVICE_HOST` | `portal` | Docker network alias |

## Docker Compose Integration

```yaml
portal-frontend:
  build:
    context: ..
    dockerfile: portal-frontend/Dockerfile
  ports:
    - "${PORTAL_FRONTEND_HOST_PORT}:80"
  depends_on:
    - portal-bff
    - keycloak
```

## Health Check

Nginx serves a static health endpoint via the root path. Docker health check verifies the server responds to HTTP requests.

## Security Considerations

- Gzip compression enabled for text-based assets
- Static files served read-only
- No sensitive data in container (build-time configuration only)
- HTTPS termination expected at load balancer/ingress level

## Related Documentation

- [user-authentication.refined.md](user-authentication.refined.md) - Authentication flow
- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture
- [docker.md](../topics/docker.md) - Docker configuration
