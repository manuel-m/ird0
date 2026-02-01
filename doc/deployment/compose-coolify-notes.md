# Docker Compose Coolify Adaptations

This document outlines the changes needed to adapt the existing Docker Compose configuration for deployment via Coolify.

## Overview

The current project uses multiple Docker Compose files with the `include:` directive. Coolify supports this, but some adaptations are needed for optimal deployment.

### Current Structure

```
docker-compose.yml                    # Main entry point
├── include: docker-compose.infrastructure.yml
├── include: docker-compose.directory.yml
└── include: docker-compose.apps.yml
```

### Target Structure

For Coolify, create a single deployment-ready file:

```
deploy/
└── docker-compose.coolify.yml       # Flattened, production-ready
```

---

## Required Adaptations

### 1. Flatten Compose Files (Optional)

While Coolify supports `include:`, a single file simplifies:
- Debugging
- Variable management
- Coolify resource configuration

**Option A: Keep Include Structure**
```yaml
# deploy/docker-compose.coolify.yml
include:
  - path: ../docker-compose.infrastructure.yml
  - path: ../docker-compose.directory.yml
  - path: ../docker-compose.apps.yml
```

**Option B: Flatten to Single File**
Create `deploy/docker-compose.coolify.yml` with all services merged.

### 2. Remove External Port Mappings

Traefik handles routing; containers don't need exposed ports.

**Before (Development):**
```yaml
services:
  policyholders-svc:
    ports:
      - "8081:8080"
```

**After (Production):**
```yaml
services:
  policyholders-svc:
    expose:
      - "8080"  # Internal only
    # No ports: section
```

**Services to Modify:**
| Service | Dev Port | Action |
|---------|----------|--------|
| policyholders-svc | 8081 | Remove `ports`, add `expose` |
| experts-svc | 8082 | Remove `ports`, add `expose` |
| providers-svc | 8083 | Remove `ports`, add `expose` |
| insurers-svc | 8084 | Remove `ports`, add `expose` |
| incident-svc | 8085 | Remove `ports`, add `expose` |
| notification-svc | 8086 | Remove `ports`, add `expose` |
| portal-bff | 8090 | Remove `ports`, add `expose` |
| portal-frontend | 80 | Remove `ports` |
| keycloak | 8180 | Remove `ports`, add `expose: 8080` |
| postgres | 5432 | Remove `ports` (internal only) |
| vault | 8200 | Remove `ports` (internal only) |

### 3. Add Traefik Labels

Add routing labels for external access.

**Portal Frontend (Main Entry):**
```yaml
services:
  portal-frontend:
    labels:
      - traefik.enable=true
      - traefik.http.routers.portal.rule=Host(`${DOMAIN}`)
      - traefik.http.routers.portal.entrypoints=websecure
      - traefik.http.routers.portal.tls=true
      - traefik.http.routers.portal.tls.certresolver=letsencrypt
      - traefik.http.services.portal.loadbalancer.server.port=80
```

**Portal BFF (API):**
```yaml
services:
  portal-bff:
    labels:
      - traefik.enable=true
      - traefik.http.routers.portal-api.rule=Host(`${DOMAIN}`) && PathPrefix(`/api/portal`)
      - traefik.http.routers.portal-api.entrypoints=websecure
      - traefik.http.routers.portal-api.tls=true
      - traefik.http.routers.portal-api.tls.certresolver=letsencrypt
      - traefik.http.services.portal-api.loadbalancer.server.port=8080
```

**Keycloak:**
```yaml
services:
  keycloak:
    labels:
      - traefik.enable=true
      - traefik.http.routers.keycloak.rule=Host(`${DOMAIN}`) && PathPrefix(`/realms`, `/resources`, `/js`, `/admin`)
      - traefik.http.routers.keycloak.entrypoints=websecure
      - traefik.http.routers.keycloak.tls=true
      - traefik.http.routers.keycloak.tls.certresolver=letsencrypt
      - traefik.http.services.keycloak.loadbalancer.server.port=8080
```

### 4. Fix Relative Volume Paths

Coolify runs compose from project root. Adjust paths:

**Before:**
```yaml
volumes:
  - ../vault/policies:/vault/policies
  - ../keycloak/realm-export.json:/opt/keycloak/data/import/realm.json
```

**After:**
```yaml
volumes:
  - ./deploy/vault/policies:/vault/policies
  - ./deploy/keycloak/realm-export.json:/opt/keycloak/data/import/realm.json
```

**Or use named volumes for data:**
```yaml
volumes:
  vault_data:
  postgres_data:

services:
  vault:
    volumes:
      - vault_data:/vault/data
```

### 5. Network Configuration

Add Coolify's network for Traefik routing:

```yaml
networks:
  coolify:
    external: true
  internal:
    driver: bridge

services:
  portal-frontend:
    networks:
      - coolify   # For Traefik access
      - internal  # For internal service communication

  postgres:
    networks:
      - internal  # Internal only, not exposed to Traefik
```

### 6. Environment Variables

**Keycloak Hostname:**

Change from development localhost to production domain:

```yaml
services:
  keycloak:
    environment:
      # Before:
      # KC_HOSTNAME: ${KEYCLOAK_HOSTNAME:-localhost:8180}

      # After:
      KC_HOSTNAME: https://${DOMAIN}
      KC_HOSTNAME_STRICT: "true"
      KC_HOSTNAME_STRICT_HTTPS: "true"
      KC_PROXY: edge  # Running behind Traefik
```

**Frontend API URL:**
```yaml
services:
  portal-frontend:
    environment:
      # Before:
      # API_URL: http://localhost:8090

      # After:
      API_URL: https://${DOMAIN}
```

### 7. Health Check Adjustments

Ensure health checks work without external ports:

```yaml
services:
  incident-svc:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      # Note: Uses localhost (inside container), not external port
```

### 8. Remove Development-Only Services

Remove or disable services not needed in production:

```yaml
# Comment out or remove in production
# services:
#   mailhog:
#     image: mailhog/mailhog
#   adminer:
#     image: adminer
```

---

## Complete Example

### deploy/docker-compose.coolify.yml

```yaml
version: '3.8'

# External networks managed by Coolify
networks:
  coolify:
    external: true
  internal:
    driver: bridge

# Persistent volumes
volumes:
  postgres_data:
  vault_data:
  keycloak_data:

services:
  # ============================================
  # Infrastructure Services
  # ============================================

  postgres:
    image: postgres:16-alpine
    container_name: postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_MULTIPLE_DATABASES: policyholders_db,experts_db,providers_db,insurers_db,incidents_db,notifications_db
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./deploy/postgres/init-multiple-dbs.sh:/docker-entrypoint-initdb.d/init-multiple-dbs.sh:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - internal
    deploy:
      resources:
        limits:
          memory: 1G

  vault:
    image: hashicorp/vault:1.15
    container_name: vault
    restart: unless-stopped
    cap_add:
      - IPC_LOCK
    environment:
      VAULT_ADDR: "http://127.0.0.1:8200"
    volumes:
      - vault_data:/vault/data
      - ./deploy/vault/config:/vault/config:ro
    command: server -config=/vault/config/vault.hcl
    healthcheck:
      test: ["CMD", "vault", "status"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - internal

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: keycloak
    restart: unless-stopped
    command: start
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak_db
      KC_DB_USERNAME: ${POSTGRES_USER}
      KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
      KC_HOSTNAME: https://${DOMAIN}
      KC_HOSTNAME_STRICT: "true"
      KC_PROXY: edge
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
    volumes:
      - keycloak_data:/opt/keycloak/data
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 120s
    labels:
      - traefik.enable=true
      - traefik.http.routers.keycloak.rule=Host(`${DOMAIN}`) && PathPrefix(`/realms`, `/resources`, `/js`, `/admin`)
      - traefik.http.routers.keycloak.entrypoints=websecure
      - traefik.http.routers.keycloak.tls=true
      - traefik.http.routers.keycloak.tls.certresolver=letsencrypt
      - traefik.http.services.keycloak.loadbalancer.server.port=8080
    networks:
      - coolify
      - internal
    deploy:
      resources:
        limits:
          memory: 1G

  # ============================================
  # Directory Services
  # ============================================

  policyholders-svc:
    image: ${REGISTRY:-}ird0/directory:${TAG:-latest}
    container_name: policyholders-svc
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: policyholders
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/policyholders_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      JAVA_OPTS: ${JAVA_OPTS:--Xmx512m}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - internal
    deploy:
      resources:
        limits:
          memory: 768M

  # Similar configuration for: experts-svc, providers-svc, insurers-svc

  # ============================================
  # Application Services
  # ============================================

  incident-svc:
    image: ${REGISTRY:-}ird0/incident:${TAG:-latest}
    container_name: incident-svc
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/incidents_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      NOTIFICATION_SERVICE_URL: http://notification-svc:8080
      JAVA_OPTS: ${JAVA_OPTS:--Xmx512m}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - internal
    deploy:
      resources:
        limits:
          memory: 768M

  notification-svc:
    image: ${REGISTRY:-}ird0/notification:${TAG:-latest}
    container_name: notification-svc
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/notifications_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      JAVA_OPTS: ${JAVA_OPTS:--Xmx512m}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - internal
    deploy:
      resources:
        limits:
          memory: 768M

  portal-bff:
    image: ${REGISTRY:-}ird0/portal-bff:${TAG:-latest}
    container_name: portal-bff
    restart: unless-stopped
    environment:
      INCIDENT_SERVICE_URL: http://incident-svc:8080
      POLICYHOLDERS_SERVICE_URL: http://policyholders-svc:8080
      EXPERTS_SERVICE_URL: http://experts-svc:8080
      PROVIDERS_SERVICE_URL: http://providers-svc:8080
      INSURERS_SERVICE_URL: http://insurers-svc:8080
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: https://${DOMAIN}/realms/ird
      JAVA_OPTS: ${JAVA_OPTS:--Xmx512m}
    depends_on:
      - incident-svc
      - policyholders-svc
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    labels:
      - traefik.enable=true
      - traefik.http.routers.portal-api.rule=Host(`${DOMAIN}`) && PathPrefix(`/api/portal`)
      - traefik.http.routers.portal-api.entrypoints=websecure
      - traefik.http.routers.portal-api.tls=true
      - traefik.http.routers.portal-api.tls.certresolver=letsencrypt
      - traefik.http.services.portal-api.loadbalancer.server.port=8080
    networks:
      - coolify
      - internal
    deploy:
      resources:
        limits:
          memory: 768M

  # ============================================
  # Frontend
  # ============================================

  portal-frontend:
    image: ${REGISTRY:-}ird0/portal-frontend:${TAG:-latest}
    container_name: portal-frontend
    restart: unless-stopped
    environment:
      API_URL: https://${DOMAIN}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:80/"]
      interval: 30s
      timeout: 10s
      retries: 3
    labels:
      - traefik.enable=true
      - traefik.http.routers.portal.rule=Host(`${DOMAIN}`)
      - traefik.http.routers.portal.entrypoints=websecure
      - traefik.http.routers.portal.tls=true
      - traefik.http.routers.portal.tls.certresolver=letsencrypt
      - traefik.http.services.portal.loadbalancer.server.port=80
      # SPA routing - redirect 404 to index.html
      - traefik.http.middlewares.portal-spa.errors.status=404
      - traefik.http.middlewares.portal-spa.errors.service=portal
      - traefik.http.middlewares.portal-spa.errors.query=/
    networks:
      - coolify
      - internal
    deploy:
      resources:
        limits:
          memory: 256M
```

---

## Migration Checklist

Before switching to production compose:

- [ ] All services use internal networks
- [ ] No external ports exposed (except through Traefik)
- [ ] Traefik labels added to public services
- [ ] Volume paths corrected for project root
- [ ] Environment variables use production values
- [ ] Health checks use internal endpoints
- [ ] Development services removed/disabled
- [ ] Resource limits set appropriately
- [ ] Secrets not hardcoded in compose file

---

## Testing Before Deployment

1. Test locally with modified compose:
   ```bash
   docker compose -f deploy/docker-compose.coolify.yml config
   ```

2. Validate syntax:
   ```bash
   docker compose -f deploy/docker-compose.coolify.yml --dry-run up
   ```

3. Test with Coolify-like environment:
   ```bash
   DOMAIN=localhost docker compose -f deploy/docker-compose.coolify.yml up
   ```

---

## Next Steps

1. Create `deploy/docker-compose.coolify.yml`
2. Move/copy required configuration files to `deploy/`
3. Test configuration locally
4. Deploy to Coolify - see [coolify-app-deployment.md](coolify-app-deployment.md)
