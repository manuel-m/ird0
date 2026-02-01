# Deploying IRD0 to Coolify

This guide provides step-by-step instructions for deploying the IRD0 Insurance Platform to Coolify.

## Prerequisites

- Coolify installed and configured (see [coolify-setup.md](coolify-setup.md))
- GitLab repository with IRD0 code
- Domain configured for the application
- Secrets prepared (see [security-checklist.md](security-checklist.md))

## Deployment Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        Coolify / Traefik                        │
│                    (SSL termination, routing)                   │
│                         :80 → :443 redirect                     │
└────────────────────────────────────────────────────────────────┘
                                │
                                │ Routes by Host/Path
                                ▼
┌────────────────────────────────────────────────────────────────┐
│                        IRD0 Application Stack                   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                     Frontend Layer                         │  │
│  │  ┌──────────────────┐                                     │  │
│  │  │ Portal Frontend  │ ← app.domain.com                    │  │
│  │  │    (nginx)       │                                     │  │
│  │  └──────────────────┘                                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                │                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      API Layer                             │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │Portal BFF│  │Incident  │  │Notific.  │  │Directory │  │  │
│  │  │  :8080   │  │  :8080   │  │  :8080   │  │ Services │  │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                │                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   Infrastructure                           │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │PostgreSQL│  │ Keycloak │  │  Vault   │  │   SFTP   │  │  │
│  │  │  :5432   │  │  :8080   │  │  :8200   │  │  :2222   │  │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Create Docker Compose Resource

### 1.1 Navigate to Resource Creation

1. Log into Coolify UI
2. Go to Projects → IRD0 Insurance Platform
3. Click "Add New Resource"
4. Select "Docker Compose"

### 1.2 Configure Source

| Setting | Value |
|---------|-------|
| Source | Git Repository |
| Repository URL | `git@gitlab.yourdomain.com:ird0/ird0.git` |
| Branch | `main` |
| Build Pack | Docker Compose |
| Docker Compose Path | `deploy/docker-compose.coolify.yml` |

### 1.3 Configure SSH Key

1. Select the SSH key configured for GitLab
2. Test connection to verify access

---

## Step 2: Configure Environment Variables

### 2.1 Public Variables

Go to Resources → [IRD0] → Environment Variables → Add:

```bash
# Application Mode
ENV_MODE=production

# Domain Configuration
DOMAIN=app.yourdomain.com
KEYCLOAK_HOSTNAME=https://app.yourdomain.com

# PostgreSQL Host (internal)
POSTGRES_HOST=postgres
POSTGRES_PORT=5432

# Service URLs (internal)
INCIDENT_SERVICE_URL=http://incident-svc:8080
NOTIFICATION_SERVICE_URL=http://notification-svc:8080
POLICYHOLDERS_SERVICE_URL=http://policyholders-svc:8080
EXPERTS_SERVICE_URL=http://experts-svc:8080
PROVIDERS_SERVICE_URL=http://providers-svc:8080
INSURERS_SERVICE_URL=http://insurers-svc:8080

# Keycloak URLs (internal)
KEYCLOAK_INTERNAL_URL=http://keycloak:8080
KEYCLOAK_ISSUER_URI=https://app.yourdomain.com/realms/ird

# Java Options
JAVA_OPTS=-Xmx512m -Xms256m
```

### 2.2 Secrets

Use Coolify's secret management:

1. Go to Settings → Secrets → Add Secret
2. Add each secret:

| Secret Name | Description | Example Generation |
|-------------|-------------|-------------------|
| `POSTGRES_PASSWORD` | Database password | `openssl rand -base64 32` |
| `POSTGRES_USER` | Database user | `ird_production` |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin | `openssl rand -base64 32` |
| `KEYCLOAK_CLIENT_SECRET` | OAuth2 client secret | `openssl rand -base64 32` |
| `VAULT_TOKEN` | Vault access token | From Vault init |

3. Reference secrets in environment variables:
```bash
POSTGRES_PASSWORD={{secrets.POSTGRES_PASSWORD}}
KEYCLOAK_ADMIN_PASSWORD={{secrets.KEYCLOAK_ADMIN_PASSWORD}}
```

---

## Step 3: Configure Domains

### 3.1 Add Domain

1. Go to Resources → [IRD0] → Settings → Domains
2. Add domain: `app.yourdomain.com`
3. Enable HTTPS
4. Select Let's Encrypt

### 3.2 DNS Configuration

Configure DNS records pointing to your VPS:

```
A    app.yourdomain.com     → <VPS_IP>
A    api.yourdomain.com     → <VPS_IP>  (if using subdomain for API)
```

### 3.3 Traefik Labels

The docker-compose.coolify.yml should include proper routing labels:

```yaml
services:
  portal-frontend:
    labels:
      - traefik.enable=true
      - traefik.http.routers.portal.rule=Host(`${DOMAIN}`)
      - traefik.http.routers.portal.tls=true
      - traefik.http.routers.portal.tls.certresolver=letsencrypt
      - traefik.http.services.portal.loadbalancer.server.port=80

  portal-bff:
    labels:
      - traefik.enable=true
      - traefik.http.routers.api.rule=Host(`${DOMAIN}`) && PathPrefix(`/api/portal`)
      - traefik.http.routers.api.tls=true
      - traefik.http.routers.api.tls.certresolver=letsencrypt

  keycloak:
    labels:
      - traefik.enable=true
      - traefik.http.routers.auth.rule=Host(`${DOMAIN}`) && PathPrefix(`/realms`, `/resources`, `/js`)
      - traefik.http.routers.auth.tls=true
      - traefik.http.routers.auth.tls.certresolver=letsencrypt
```

---

## Step 4: Health Check Configuration

### 4.1 Coolify Health Checks

Go to Resources → [IRD0] → Health Checks and configure:

| Service | Health URL | Expected | Interval |
|---------|------------|----------|----------|
| portal-frontend | `/` | 200 | 60s |
| portal-bff | `/actuator/health` | 200 | 30s |
| incident-svc | `/actuator/health` | 200 | 30s |
| notification-svc | `/actuator/health` | 200 | 30s |
| keycloak | `/health` | 200 | 30s |

### 4.2 Container Health Checks

Ensure docker-compose includes health checks:

```yaml
services:
  incident-svc:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

---

## Step 5: Deploy

### 5.1 Manual Deployment

1. Go to Resources → [IRD0]
2. Click "Deploy"
3. Monitor logs for progress

### 5.2 Monitor Deployment

Watch for these key events:

```
[1/12] Pulling postgres...
[2/12] Pulling keycloak...
...
[12/12] All services started
Health check passed for portal-frontend
Health check passed for incident-svc
...
Deployment complete
```

### 5.3 Verify Deployment

1. Check container status: All should be "Running"
2. Check health checks: All should be "Healthy"
3. Access https://app.yourdomain.com
4. Test Keycloak: https://app.yourdomain.com/realms/ird

---

## Step 6: Configure Auto-Deploy

### 6.1 Enable Auto-Deploy

1. Go to Resources → [IRD0] → Settings
2. Enable "Auto Deploy"
3. Configure:
   - Deploy on push to: `main`
   - Deploy on tag: `v*`

### 6.2 GitLab Webhook

1. Copy webhook URL from Coolify
2. In GitLab: Settings → Webhooks → Add
3. Configure:
   - URL: `https://coolify.yourdomain.com/api/v1/deploy?token=XXX&uuid=YYY`
   - Trigger: Push events, Tag push events
   - Enable SSL verification

### 6.3 CI/CD Integration

Add to `.gitlab-ci.yml`:

```yaml
deploy:production:
  stage: deploy
  script:
    - |
      curl -X POST "$COOLIFY_WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d '{"ref": "'$CI_COMMIT_SHA'", "environment": "production"}'
  only:
    - main
    - tags
  when: manual
```

---

## Step 7: Post-Deployment Verification

### 7.1 Service Health Verification

```bash
# Check all health endpoints
for service in portal-bff incident-svc notification-svc policyholders-svc; do
  curl -s https://app.yourdomain.com/$service/actuator/health | jq '.status'
done
```

### 7.2 Authentication Test

```bash
# Get token from Keycloak
TOKEN=$(curl -s -X POST \
  https://app.yourdomain.com/realms/ird/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=portal" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "username=testuser" \
  -d "password=testpass" | jq -r '.access_token')

# Test authenticated endpoint
curl -H "Authorization: Bearer $TOKEN" \
  https://app.yourdomain.com/api/portal/v1/dashboard
```

### 7.3 Database Connectivity

```bash
# Check database connections via health endpoints
curl -s https://app.yourdomain.com/incident-svc/actuator/health | jq '.components.db'
```

---

## Rollback Procedures

### Immediate Rollback

1. Go to Resources → [IRD0] → Deployments
2. Find previous successful deployment
3. Click "Rollback to this deployment"

### Manual Rollback

```bash
# In GitLab CI/CD
git revert HEAD
git push origin main
# Or deploy specific tag
```

### Database Rollback

If database migrations caused issues:

1. Stop application services
2. Restore database from backup
3. Deploy previous version
4. Verify data integrity

---

## Scaling

### Horizontal Scaling

Add replicas in docker-compose:

```yaml
services:
  incident-svc:
    deploy:
      replicas: 2
      update_config:
        parallelism: 1
        delay: 10s
```

### Resource Allocation

Adjust per service needs:

```yaml
services:
  incident-svc:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
```

---

## Monitoring

### Log Access

1. Coolify UI: Resources → [IRD0] → Logs
2. Select service from dropdown
3. View real-time or historical logs

### Metrics (Optional)

Configure Prometheus scraping:

```yaml
services:
  incident-svc:
    labels:
      - prometheus.io/scrape=true
      - prometheus.io/port=8080
      - prometheus.io/path=/actuator/prometheus
```

---

## Maintenance

### Planned Maintenance

1. Enable maintenance mode (if implemented)
2. Stop deployment webhooks
3. Perform maintenance
4. Re-enable webhooks
5. Verify services

### Database Maintenance

```bash
# Connect to database container
docker exec -it postgres psql -U ird_production

# Vacuum and analyze
VACUUM ANALYZE;
```

### Certificate Renewal

Let's Encrypt certificates renew automatically. To force renewal:

1. Resources → [IRD0] → Settings
2. Click "Regenerate SSL Certificate"

---

## Troubleshooting

### Deployment Fails

1. Check build logs in Coolify
2. Verify Git repository access
3. Check environment variables
4. Verify docker-compose syntax

### Services Won't Start

1. Check container logs: Coolify UI → Logs
2. Verify health checks pass
3. Check dependent services (database, Keycloak)
4. Verify environment variables

### SSL Issues

1. Verify DNS points to correct IP
2. Check Traefik logs
3. Ensure port 80 is accessible (Let's Encrypt verification)
4. Try regenerating certificate

### Network Issues

1. Verify all services in same network
2. Check service names match configuration
3. Test internal connectivity:
   ```bash
   docker exec portal-bff curl http://incident-svc:8080/actuator/health
   ```

---

## Security Checklist (Pre-Deploy)

Before deploying to production, verify:

- [ ] All secrets configured via Coolify Secrets
- [ ] No secrets in docker-compose or environment files
- [ ] ENV_MODE=production
- [ ] HTTPS enabled and verified
- [ ] Health checks configured
- [ ] Auto-deploy tested
- [ ] Rollback procedure tested
- [ ] Database backups configured
- [ ] Monitoring in place

---

## Next Steps

1. Complete deployment verification
2. Run security checklist - see [security-checklist.md](security-checklist.md)
3. Review operations guide - see [runbook.md](runbook.md)
4. Set up monitoring and alerting
5. Document team procedures
