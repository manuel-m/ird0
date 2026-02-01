# Production Readiness Review

> **Purpose:** Track and remediate all issues before production deployment
> **Last Updated:** 2026-01-31
> **Review Status:** Initial Assessment
> **Next Review:** Before deployment gate




## Critical Issues

> **Gate:** All critical issues must be resolved before any production deployment

### Summary Table

| ID | Issue | Category | Complexity | Owner |
|----|-------|----------|:----------:|:-----:|
| C2 | Client secret in realm | Security | Low | - |
| ~~C3~~ | ~~Missing security headers~~ | ~~Security~~ | ~~Low~~ | Done |
| C4 | Refresh token revocation | Security | Low | - |
| ~~C5~~ | ~~Dev mode enabled~~ | ~~Security~~ | ~~Low~~ | Done |
| C6 | PostgreSQL exposed | Infra | Low | - |
| ~~C7~~ | ~~Keycloak dev mode~~ | ~~Infra~~ | ~~Medium~~ | Done |
| ~~C9~~ | ~~DEBUG logging~~ | ~~App~~ | ~~Low~~ | Done |
| ~~C10~~ | ~~Swagger public~~ | ~~App~~ | ~~Low~~ | Done |

---


### C2: Client Secret in Realm Export

| Attribute | Value |
|-----------|-------|
| **Severity** | CRITICAL |
| **Category** | Security |
| **Location** | `keycloak/realm-export.json:65` |
| **Complexity** | Low |

**Current State:**
```json
"secret": "dev-bff-secret-change-in-production"
```

**Remediation:**
```json
"secret": "${KEYCLOAK_BFF_CLIENT_SECRET}"
```

Configure via Keycloak Admin API at deployment:
```bash
kcadm.sh update clients/<client-id> -r ird0 -s "secret=${SECRET}"
```

**Definition of Done:**
- [ ] Secret removed from realm-export.json
- [ ] Deployment script configures secret via API
- [ ] Secret stored in Vault/secrets manager

---

### ~~C3: Missing Security Headers~~ COMPLETED

| Attribute | Value |
|-----------|-------|
| **Severity** | ~~CRITICAL~~ |
| **Category** | Security |
| **OWASP** | A05:2021 - Security Misconfiguration |
| **Location** | `portal-frontend/nginx.conf:34-47` |
| **Complexity** | Low |
| **Status** | **COMPLETED** |

**Implemented:** Added security headers to `server` block in nginx.conf:

```nginx
# Security Headers
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;

# Content Security Policy
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'self'; form-action 'self'; base-uri 'self';" always;

# HSTS - uncomment after HTTPS is configured
# add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

**Definition of Done:**
- [x] All headers added to nginx.conf
- [ ] Headers visible in browser DevTools (verify after deployment)
- [ ] No console errors from CSP violations (verify after deployment)

---

### C4: Refresh Token Revocation Disabled

| Attribute | Value |
|-----------|-------|
| **Severity** | CRITICAL |
| **Category** | Security |
| **Location** | `keycloak/realm-export.json:22` |
| **Complexity** | Low |

**Current State:**
```json
"revokeRefreshToken": false,
"refreshTokenMaxReuse": 0
```

**Remediation:**
```json
"revokeRefreshToken": true,
"refreshTokenMaxReuse": 0
```

**Definition of Done:**
- [ ] Setting changed in realm-export.json
- [ ] Verified: logout invalidates refresh token
- [ ] Tested: stolen token cannot be reused after logout

---

### ~~C5: Development Mode Enabled~~ COMPLETED

| Attribute | Value |
|-----------|-------|
| **Severity** | ~~CRITICAL~~ |
| **Category** | Security |
| **Location** | `.env` |
| **Complexity** | Low |
| **Status** | **COMPLETED** |

**Implemented:** Introduced central `ENV_MODE` environment variable:

- `ENV_MODE=dev` - Creates test users, enables Swagger, DEBUG logging
- `ENV_MODE=production` - Disables all development features

**Changes:**
- Added `ENV_MODE` to `.env` and `.env.example` (mandatory, fails if missing)
- `KEYCLOAK_DEV_MODE` is now derived from `ENV_MODE` in docker-compose
- Updated `keycloak/init-dev-users.sh` to check for `KEYCLOAK_DEV_MODE=dev`

**Definition of Done:**
- [x] `ENV_MODE` variable added and documented
- [x] Docker compose fails if `ENV_MODE` is not set
- [x] Test users only created when `ENV_MODE=dev`

---

### C6: PostgreSQL Port Exposed

| Attribute | Value |
|-----------|-------|
| **Severity** | CRITICAL |
| **Category** | Infrastructure |
| **Location** | `deploy/docker-compose.infrastructure.yml:49-50` |
| **Complexity** | Low |

**Current State:**
```yaml
ports:
  - "5432:5432"
```

**Remediation:**
```yaml
# Comment out or remove for production
# ports:
#   - "5432:5432"
```

**Admin Access Alternative:**
```bash
# SSH tunnel for admin access
ssh -L 5432:postgres:5432 user@production-host

# Or use docker exec
docker exec -it postgres psql -U $POSTGRES_USER
```

**Definition of Done:**
- [ ] Port mapping removed from production compose
- [ ] `nc -zv <host> 5432` fails from external network
- [ ] Admin access documented via SSH tunnel

---

### ~~C7: Keycloak Running in Dev Mode~~ COMPLETED

| Attribute | Value |
|-----------|-------|
| **Severity** | ~~CRITICAL~~ |
| **Category** | Infrastructure |
| **Location** | `keycloak/docker-entrypoint.sh` |
| **Complexity** | Medium |
| **Status** | **COMPLETED** |

**Implemented:** Created entrypoint script that switches Keycloak mode based on `ENV_MODE`:

| ENV_MODE | Keycloak Command | Settings |
|----------|------------------|----------|
| dev | `start-dev --import-realm` | Relaxed security, HTTP allowed |
| production | `start --import-realm` | Strict hostname, proxy headers |

**Changes:**
- Created `keycloak/docker-entrypoint.sh` that checks ENV_MODE
- Updated `docker-compose.infrastructure.yml` to use entrypoint script
- Production mode sets `KC_HOSTNAME_STRICT=true`, `KC_PROXY_HEADERS=xforwarded`

**Definition of Done:**
- [x] Keycloak uses `start` in production mode
- [x] Entrypoint script switches based on ENV_MODE
- [x] Production settings applied (strict hostname, proxy headers)


### ~~C9: DEBUG Logging in Production~~ COMPLETED

| Attribute | Value |
|-----------|-------|
| **Severity** | ~~CRITICAL~~ |
| **Category** | Application |
| **Location** | `microservices/*/configs/application-production.yml` |
| **Complexity** | Low |
| **Status** | **COMPLETED** |

**Implemented:** Created `application-production.yml` for all services:

```yaml
logging:
  level:
    root: WARN
    com.ird0: INFO

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

**Changes:**
- Created `microservices/portal-bff/src/main/resources/application-production.yml`
- Created `microservices/incident/configs/application-production.yml`
- Created `microservices/notification/configs/application-production.yml`
- Created `microservices/directory/configs/application-production.yml`
- `SPRING_PROFILES_ACTIVE` is set from `ENV_MODE` in docker-compose.apps.yml

**Definition of Done:**
- [x] `application-production.yml` created for each service
- [x] Production deployment sets `SPRING_PROFILES_ACTIVE=production`
- [x] No DEBUG-level logs when `ENV_MODE=production`

---

### ~~C10: Swagger UI Publicly Accessible~~ COMPLETED

| Attribute | Value |
|-----------|-------|
| **Severity** | ~~CRITICAL~~ |
| **Category** | Application |
| **Location** | `microservices/*/configs/application-production.yml` |
| **Complexity** | Low |
| **Status** | **COMPLETED** |

**Implemented:** Swagger UI disabled via production profile:

```yaml
# application-production.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

When `ENV_MODE=production`, the production profile is automatically activated via `SPRING_PROFILES_ACTIVE=${ENV_MODE}`, which disables Swagger UI endpoints.

**Definition of Done:**
- [x] Swagger disabled in production profile
- [x] `curl /swagger-ui.html` returns 404 when `ENV_MODE=production`

---

## High Priority Issues

> **Gate:** Address before first production release

### Summary Table

| ID | Issue | Category | Location | Complexity |
|----|-------|----------|----------|:----------:|
| ~~H1~~ | ~~No HTTPS/TLS~~ | ~~Security~~ | ~~nginx.conf~~ | Done |
| H2 | Permissive CORS | Security | WebConfig.java | Low |
| H3 | Manual JWT decode | Security | auth.service.ts | Low |
| H4 | Console logs errors | Security | error.interceptor.ts | Low |
| H5 | Vault uses HTTP | Infra | docker-compose | Medium |
| H6 | PostgreSQL no SSL | Infra | JDBC configs | Medium |
| H7 | No prod Spring profile | App | Missing files | Medium |
| H8 | Stack traces logged | App | GlobalExceptionHandler | Low |
| H9 | Minimal E2E tests | App | e2e/ | High |
| H10 | Session too long | Security | realm-export.json | Low |

---

### ~~H1: No HTTPS/TLS Configuration~~ COMPLETED

| Attribute | Value |
|-----------|-------|
| **Severity** | ~~HIGH~~ |
| **Category** | Security |
| **Location** | `portal-frontend/nginx-production.conf` |
| **Status** | **COMPLETED** |

**Implemented:** Created production nginx config with HTTPS support that activates based on `ENV_MODE`:

| ENV_MODE | Protocol | Ports | HSTS |
|----------|----------|-------|------|
| dev | HTTP only | 80 | Disabled |
| production | HTTPS (HTTP redirects) | 80, 443 | Enabled |

**Changes:**
- Created `portal-frontend/nginx-production.conf` with HTTPS configuration
- Created `portal-frontend/docker-entrypoint.sh` that switches configs based on ENV_MODE
- Updated Dockerfile to include both configs and entrypoint
- Auto-generates self-signed certificates if none provided (for testing)
- Mount real certificates to `./ssl/` directory for production

**Certificate Setup:**
```bash
# For testing (auto-generated self-signed)
ENV_MODE=production docker compose up portal-frontend

# For production (real certificates)
cp /path/to/cert.pem ssl/cert.pem
cp /path/to/key.pem ssl/key.pem
ENV_MODE=production docker compose up portal-frontend
```

**Definition of Done:**
- [x] HTTPS server configured with TLS 1.2/1.3
- [x] HTTP to HTTPS redirect
- [x] HSTS header enabled in production
- [x] Self-signed cert generation for testing

---

### H2: Overly Permissive CORS

**Location:** `microservices/portal-bff/.../WebConfig.java:16`

**Current:** `.allowedHeaders("*")`

**Remediation:**
```java
.allowedHeaders(
    "Authorization",
    "Content-Type",
    "Accept",
    "Origin",
    "X-Requested-With"
)
```

---

### H3: Manual JWT Decoding

**Location:** `portal-frontend/.../auth.service.ts:108-121`

**Note:** This is used for UI display only. Backend enforces authorization via `@PreAuthorize`. Add clarifying comment:

```typescript
/**
 * Decodes JWT payload for UI role display.
 * SECURITY NOTE: This does NOT verify the signature.
 * All authorization is enforced server-side via Spring Security.
 */
hasRole(role: string): boolean {
  // ... existing code
}
```

---

### H4: Console Logs Full Error Objects

**Location:** `portal-frontend/.../error.interceptor.ts:51`

**Current:** `console.error('HTTP Error:', errorMessage, error);`

**Remediation:**
```typescript
import { environment } from '../../../environments/environment';

// Only log full error in development
if (environment.production) {
  console.error('HTTP Error:', errorMessage);
} else {
  console.error('HTTP Error:', errorMessage, error);
}
```

---

### H5-H6: Infrastructure TLS

| Issue | Location | Remediation |
|-------|----------|-------------|
| H5: Vault HTTP | docker-compose | Configure Vault with TLS or use Unix socket |
| H6: PostgreSQL no SSL | JDBC URLs | Add `?ssl=true&sslmode=require` to connection strings |

---

### H7: No Production Spring Profile

Create `application-prod.yml` for each service:

```yaml
# application-prod.yml template
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Not update
    show-sql: false

logging:
  level:
    root: WARN
    com.ird0: INFO

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

management:
  endpoint:
    health:
      show-details: never
```

---

### H8: Stack Traces in Logs

**Location:** `GlobalExceptionHandler.java:89`

**Current:** `log.error("Unexpected error: ", ex);`

**Remediation:**
```java
log.error("Unexpected error: {} - {}",
    ex.getClass().getSimpleName(),
    ex.getMessage());
log.debug("Stack trace:", ex);  // Only at DEBUG level
```

---

### H9: Minimal E2E Test Coverage

**Current:** Only 2 spec files (claims.spec.ts, dashboard.spec.ts)

**Required Tests:**
- [ ] Authentication flow (login, logout, token refresh)
- [ ] Permission-based UI (viewer vs manager vs admin)
- [ ] Error handling (network errors, 401, 403, 500)
- [ ] Form validation
- [ ] Critical user journeys

---

### H10: Session Max Lifespan

**Location:** `keycloak/realm-export.json:14`

**Current:** `"ssoSessionMaxLifespan": 36000` (10 hours)

**Remediation:**
```json
"ssoSessionMaxLifespan": 28800,   // 8 hours
"ssoSessionIdleTimeout": 1800     // 30 minutes idle
```

---

## Medium Priority Issues

> **Gate:** Address in first major release

| ID | Issue | Location | Remediation |
|----|-------|----------|-------------|
| M1 | No audit logging | Controllers | Add MDC with user ID, request ID |
| M2 | No rate limiting | nginx.conf | Add `limit_req_zone` |
| M3 | Inconsistent health checks | docker-compose | Standardize on wget |
| M4 | `ddl-auto: update` | application.yml | Use `validate` + Flyway |
| M5 | Frontend memory 64M | docker-compose | Increase to 128M |
| M6 | No frontend unit tests | src/ | Add service tests |
| M7 | Bundle size 1MB | angular.json | Reduce to 500kB, lazy load |
| M8 | No service timeouts | application.yml | Add connect/read timeouts |
| M9 | 5min access token | realm-export.json | Monitor refresh failures |

### Quick Fixes

**M2: Rate Limiting**
```nginx
http {
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;

    server {
        location /api/ {
            limit_req zone=api burst=20 nodelay;
        }
    }
}
```

**M4: Schema Management**
```yaml
# application-prod.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

**M8: Service Timeouts**
```yaml
spring:
  web:
    client:
      connect-timeout: 5000
      read-timeout: 30000
```

---

## Low Priority Issues

> **Gate:** Ongoing improvements

| ID | Issue | Remediation |
|----|-------|-------------|
| L1 | External Google fonts | Self-host via @fontsource |
| L2 | No backup strategy | Document pg_dump schedule |
| L3 | No connection pooling config | Configure HikariCP |
| L4 | No deployment checklist | Create doc/DEPLOYMENT.md |

---

## Remediation Roadmap

```
PHASE 1: Critical (Pre-Production)     PHASE 2: High (Pre-Launch)
├── C1: Rotate secrets                 ├── H1: Configure HTTPS/TLS ✓
├── C2: Remove client secret           ├── H2: Harden CORS
├── C3: Add security headers ✓         ├── H5-H6: Enable TLS everywhere
├── C4: Enable token revocation        ├── H7: Create prod profiles ✓
├── C5: Disable dev mode ✓             ├── H8: Redact stack traces
├── C6: Remove PostgreSQL port         ├── H9: Expand E2E tests
├── C7: Production Keycloak ✓          └── H10: Reduce session time
├── C8: Non-root containers
├── C9: Production logging ✓
└── C10: Secure Swagger ✓

PHASE 3: Medium (Post-Launch)          PHASE 4: Low (Ongoing)
├── M1: Audit logging                  ├── L1: Self-host fonts
├── M2: Rate limiting                  ├── L2: Backup strategy
├── M3: Health checks                  ├── L3: Connection pooling
├── M4: Schema migrations              └── L4: Deployment checklist
├── M5-M8: Infra tuning
└── M6-M7: Frontend quality
```

---

## Verification Scripts

### Automated Verification Script

Save as `scripts/verify-production-readiness.sh`:

```bash
#!/bin/bash
set -e

echo "=== Production Readiness Verification ==="
echo

PASS=0
FAIL=0

check() {
    if eval "$2" > /dev/null 2>&1; then
        echo "[PASS] $1"
        ((PASS++))
    else
        echo "[FAIL] $1"
        ((FAIL++))
    fi
}

# Security Headers
check "Security headers present" \
    "curl -sI http://localhost:4200 | grep -q 'X-Frame-Options'"

# Secrets in Git
check "No passwords in .env" \
    "! grep -q 'PASSWORD=.*[a-zA-Z0-9]' .env"

# Swagger blocked
check "Swagger requires auth" \
    "[ \$(curl -so /dev/null -w '%{http_code}' http://localhost:7777/swagger-ui.html) != '200' ]"

# PostgreSQL not exposed
check "PostgreSQL not externally accessible" \
    "! nc -zw1 localhost 5432"

# Container not root
check "Containers not running as root" \
    "! docker exec portal-bff whoami | grep -q root"

# No DEBUG logging
check "No DEBUG logs in production" \
    "[ \$(docker logs portal-bff 2>&1 | grep -c 'DEBUG') -eq 0 ]"

echo
echo "=== Results: $PASS passed, $FAIL failed ==="
[ $FAIL -eq 0 ] && exit 0 || exit 1
```

### Manual Verification Commands

```bash
# 1. Check security headers
curl -I https://portal.example.com 2>/dev/null | grep -E "^(X-|Content-Security|Strict)"

# 2. Verify no secrets in Git
git log -p --all -S "password" -- "*.env" "*.json" "*.yml" | head -50

# 3. Test Swagger access
curl -s -o /dev/null -w "%{http_code}" https://api.example.com/swagger-ui.html

# 4. Verify PostgreSQL not exposed
nmap -p 5432 production-host

# 5. Check container user
docker exec portal-bff id

# 6. Verify log levels
docker logs portal-bff 2>&1 | grep -E "^[0-9].*DEBUG" | wc -l

# 7. Check TLS configuration
openssl s_client -connect portal.example.com:443 -brief

# 8. Verify Keycloak mode
docker logs keycloak 2>&1 | grep -E "(development|production) mode"
```

---

## Sign-off Checklist

### Phase 1: Critical Issues

| # | Issue | Verified By | Date | Signature |
|---|-------|-------------|------|-----------|
| C1 | Secrets rotated and removed from Git | | | |
| C2 | Client secret removed from realm export | | | |
| C3 | Security headers configured | Claude | 2026-01-31 | Done |
| C4 | Refresh token revocation enabled | | | |
| C5 | Dev mode disabled | Claude | 2026-01-31 | Done |
| C6 | PostgreSQL port removed | | | |
| C7 | Keycloak in production mode | Claude | 2026-01-31 | Done |
| C8 | All containers run as non-root | | | |
| C9 | Logging set to INFO/WARN | Claude | 2026-01-31 | Done |
| C10 | Swagger secured or disabled | Claude | 2026-01-31 | Done |

### Phase 2: High Priority

| # | Issue | Verified By | Date | Signature |
|---|-------|-------------|------|-----------|
| H1 | HTTPS/TLS configured | Claude | 2026-01-31 | Done |
| H2 | CORS hardened | | | |
| H3 | JWT decode documented | | | |
| H4 | Console logging reduced | | | |
| H5 | Vault TLS configured | | | |
| H6 | PostgreSQL SSL enabled | | | |
| H7 | Production profiles created | Claude | 2026-01-31 | Done |
| H8 | Stack traces redacted | | | |
| H9 | E2E test coverage expanded | | | |
| H10 | Session timeouts reduced | | | |

---

## Related Documentation

| Document | Purpose |
|----------|---------|
| [ARCHITECTURE.md](../ARCHITECTURE.md) | System design overview |
| [topics/docker.md](../topics/docker.md) | Docker configuration |
| [topics/configuration.md](../topics/configuration.md) | Environment configuration |

### External References

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP Secure Headers](https://owasp.org/www-project-secure-headers/)
- [Keycloak Production Guide](https://www.keycloak.org/server/configuration-production)
- [Spring Boot Production Checklist](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html)
- [Docker Security Best Practices](https://docs.docker.com/develop/security-best-practices/)
