# IRD0 Operations Runbook

This runbook provides operational procedures for the IRD0 Insurance Platform in production.

## Quick Reference

### Service URLs

| Service | Internal URL | External URL |
|---------|--------------|--------------|
| Portal Frontend | http://portal-frontend:80 | https://app.yourdomain.com |
| Portal BFF | http://portal-bff:8080 | https://app.yourdomain.com/api/portal |
| Keycloak | http://keycloak:8080 | https://app.yourdomain.com/realms/* |
| Incident Service | http://incident-svc:8080 | (internal only) |
| Notification Service | http://notification-svc:8080 | (internal only) |
| Policyholders Service | http://policyholders-svc:8080 | (internal only) |
| Experts Service | http://experts-svc:8080 | (internal only) |
| Providers Service | http://providers-svc:8080 | (internal only) |
| Insurers Service | http://insurers-svc:8080 | (internal only) |

### Health Check Endpoints

| Service | Endpoint | Expected Response |
|---------|----------|-------------------|
| All Spring Boot services | `/actuator/health` | `{"status":"UP"}` |
| Keycloak | `/health` | `{"status":"UP"}` |
| PostgreSQL | pg_isready | Exit code 0 |
| Vault | `/v1/sys/health` | `{"sealed":false}` |

---

## Service Health Checks

### Quick Health Check Script

```bash
#!/bin/bash
# check-health.sh

DOMAIN="app.yourdomain.com"

services=(
  "portal-bff:actuator/health"
  "incident-svc:actuator/health"
  "notification-svc:actuator/health"
)

echo "Checking service health..."

for service in "${services[@]}"; do
  name="${service%%:*}"
  path="${service#*:}"

  status=$(docker exec $name curl -s http://localhost:8080/$path | jq -r '.status' 2>/dev/null)

  if [ "$status" == "UP" ]; then
    echo "✓ $name: UP"
  else
    echo "✗ $name: DOWN or DEGRADED"
  fi
done

# Check external access
http_code=$(curl -s -o /dev/null -w "%{http_code}" https://$DOMAIN)
if [ "$http_code" == "200" ]; then
  echo "✓ External access: OK"
else
  echo "✗ External access: FAILED (HTTP $http_code)"
fi
```

### Detailed Health Check

```bash
# Check all container health statuses
docker ps --format "table {{.Names}}\t{{.Status}}"

# Get detailed health for specific service
docker inspect --format='{{json .State.Health}}' incident-svc | jq

# Check actuator health with details
curl -s http://localhost:8085/actuator/health | jq
```

---

## Common Issues and Solutions

### 1. Service Won't Start

**Symptoms:**
- Container in "Restarting" state
- Health check failing
- Connection refused errors

**Diagnosis:**
```bash
# Check container logs
docker logs --tail 100 <container-name>

# Check if dependencies are ready
docker logs postgres | tail -20
docker logs keycloak | tail -20

# Check resource usage
docker stats --no-stream
```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Database not ready | Wait for postgres health check, check startup order |
| Out of memory | Increase container memory limit |
| Port conflict | Check no duplicate port bindings |
| Missing environment variable | Verify all required env vars in Coolify |
| Vault sealed | Unseal Vault (see Vault section) |

### 2. Authentication Failures

**Symptoms:**
- 401 Unauthorized responses
- Token validation errors
- Keycloak connection failures

**Diagnosis:**
```bash
# Check Keycloak health
docker logs keycloak | tail -50

# Verify token endpoint
curl -s https://app.yourdomain.com/realms/ird/.well-known/openid-configuration | jq

# Test token issuance
curl -X POST https://app.yourdomain.com/realms/ird/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=portal" \
  -d "client_secret=$CLIENT_SECRET"
```

**Solutions:**

| Issue | Solution |
|-------|----------|
| Keycloak down | Restart Keycloak, check database connection |
| Wrong client secret | Verify secret in Vault matches Keycloak |
| Token expired | Check token TTL settings |
| CORS error | Verify CORS configuration in portal-bff |
| Issuer mismatch | Verify OIDC issuer URL matches Keycloak hostname |

### 3. Database Connection Issues

**Symptoms:**
- "Connection refused" errors
- "Too many connections" errors
- Slow queries

**Diagnosis:**
```bash
# Check PostgreSQL status
docker logs postgres | tail -50

# Check active connections
docker exec postgres psql -U ird_production -c "SELECT count(*) FROM pg_stat_activity;"

# Check database size
docker exec postgres psql -U ird_production -c "\l+"
```

**Solutions:**

| Issue | Solution |
|-------|----------|
| Connection refused | Check postgres container is running |
| Too many connections | Increase max_connections or reduce pool size |
| Slow queries | Run VACUUM ANALYZE, check indexes |
| Disk full | Check volume usage, clean old data |

### 4. Memory Issues

**Symptoms:**
- OOMKilled containers
- Slow response times
- JVM OutOfMemoryError

**Diagnosis:**
```bash
# Check memory usage
docker stats --no-stream

# Check JVM memory (if accessible)
curl -s http://localhost:8085/actuator/metrics/jvm.memory.used | jq

# Check for OOM events
dmesg | grep -i oom
```

**Solutions:**
```yaml
# Increase memory limit in docker-compose
services:
  incident-svc:
    deploy:
      resources:
        limits:
          memory: 1G  # Increase from 768M
```

### 5. SSL/TLS Issues

**Symptoms:**
- Certificate errors
- HTTPS redirect loops
- Mixed content warnings

**Diagnosis:**
```bash
# Check certificate
echo | openssl s_client -connect app.yourdomain.com:443 2>/dev/null | openssl x509 -noout -dates

# Check Traefik logs
docker logs coolify-proxy | grep -i error

# Test HTTPS redirect
curl -I http://app.yourdomain.com
```

**Solutions:**

| Issue | Solution |
|-------|----------|
| Expired certificate | Regenerate via Coolify UI |
| Certificate mismatch | Verify domain configuration |
| HTTP redirect loop | Check Traefik configuration |

---

## Vault Operations

### Unsealing Vault

Vault seals itself on restart. To unseal:

```bash
# Check seal status
docker exec vault vault status

# If sealed, unseal with keys (need 3 of 5)
docker exec vault vault operator unseal <key1>
docker exec vault vault operator unseal <key2>
docker exec vault vault operator unseal <key3>

# Verify unsealed
docker exec vault vault status | grep Sealed
# Should show: Sealed    false
```

### Reading Secrets

```bash
# Login with token
docker exec -it vault vault login

# Read a secret
docker exec vault vault kv get secret/postgres

# List secrets
docker exec vault vault kv list secret/
```

### Rotating Secrets

```bash
# Generate new password
NEW_PASS=$(openssl rand -base64 32)

# Update in Vault
docker exec vault vault kv put secret/postgres \
  username="ird_production" \
  password="$NEW_PASS"

# Update in PostgreSQL
docker exec postgres psql -U postgres -c \
  "ALTER USER ird_production PASSWORD '$NEW_PASS'"

# Restart services to pick up new credentials
docker restart incident-svc notification-svc policyholders-svc
```

---

## Backup Procedures

### Database Backup

**Manual Backup:**
```bash
# Create backup
docker exec postgres pg_dumpall -U ird_production > backup_$(date +%Y%m%d).sql

# Compress
gzip backup_$(date +%Y%m%d).sql

# Verify backup
gunzip -c backup_*.sql.gz | head -100
```

**Automated Backup Script:**
```bash
#!/bin/bash
# backup-db.sh

BACKUP_DIR="/backups"
RETENTION_DAYS=30
DATE=$(date +%Y%m%d_%H%M%S)

# Create backup
docker exec postgres pg_dumpall -U ird_production | gzip > $BACKUP_DIR/db_$DATE.sql.gz

# Upload to S3 (optional)
# aws s3 cp $BACKUP_DIR/db_$DATE.sql.gz s3://your-bucket/backups/

# Clean old backups
find $BACKUP_DIR -name "db_*.sql.gz" -mtime +$RETENTION_DAYS -delete

echo "Backup completed: db_$DATE.sql.gz"
```

### Database Restore

```bash
# Stop services
docker stop incident-svc notification-svc portal-bff

# Restore
gunzip -c backup_file.sql.gz | docker exec -i postgres psql -U ird_production

# Start services
docker start incident-svc notification-svc portal-bff

# Verify
curl -s http://localhost:8085/actuator/health
```

### Vault Backup

```bash
# Create snapshot
docker exec vault vault operator raft snapshot save /vault/backup.snap

# Copy from container
docker cp vault:/vault/backup.snap ./vault_backup_$(date +%Y%m%d).snap

# Encrypt backup
gpg --encrypt --recipient admin@company.com vault_backup_*.snap
```

---

## Scaling Operations

### Horizontal Scaling

```yaml
# Update docker-compose to add replicas
services:
  incident-svc:
    deploy:
      replicas: 2
```

Then redeploy via Coolify.

### Vertical Scaling

Update resource limits in Coolify:
1. Resources → [IRD0] → [Service]
2. Increase memory/CPU limits
3. Redeploy

---

## Log Management

### Viewing Logs

```bash
# Real-time logs
docker logs -f incident-svc

# Last 100 lines
docker logs --tail 100 incident-svc

# Logs since timestamp
docker logs --since 2024-01-20T10:00:00 incident-svc

# All services (via Coolify UI)
# Resources → [IRD0] → Logs
```

### Log Search

```bash
# Search for errors
docker logs incident-svc 2>&1 | grep -i error

# Search for specific request
docker logs incident-svc 2>&1 | grep "incident-id-here"

# Count error occurrences
docker logs incident-svc 2>&1 | grep -c ERROR
```

### Log Export

```bash
# Export logs to file
docker logs incident-svc > incident_logs_$(date +%Y%m%d).log 2>&1

# Compress
gzip incident_logs_*.log
```

---

## Deployment Operations

### Manual Deployment

Via Coolify UI:
1. Resources → [IRD0]
2. Click "Deploy"
3. Monitor logs

### Rollback

1. Resources → [IRD0] → Deployments
2. Find previous successful deployment
3. Click "Rollback"
4. Confirm and monitor

### Zero-Downtime Deployment

Coolify handles rolling updates automatically when:
- Health checks are configured
- Multiple replicas exist

For single-replica services:
- Brief downtime during container replacement
- Health check must pass before traffic routing

---

## Monitoring & Alerting

### Key Metrics to Monitor

| Metric | Warning Threshold | Critical Threshold |
|--------|-------------------|-------------------|
| CPU Usage | 70% | 90% |
| Memory Usage | 80% | 95% |
| Disk Usage | 70% | 85% |
| Response Time (p95) | 500ms | 2000ms |
| Error Rate | 1% | 5% |
| Health Check Failures | 1 | 3 consecutive |

### Health Check Script for Monitoring

```bash
#!/bin/bash
# monitor.sh - Run via cron every minute

DOMAIN="app.yourdomain.com"
ALERT_ENDPOINT="https://your-alerting-service/webhook"

# Check health
status=$(curl -s -o /dev/null -w "%{http_code}" https://$DOMAIN/api/portal/v1/health)

if [ "$status" != "200" ]; then
  curl -X POST "$ALERT_ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "{\"service\": \"ird0\", \"status\": \"$status\", \"message\": \"Health check failed\"}"
fi
```

---

## Emergency Procedures

### Service Outage

1. **Assess:** Check Coolify dashboard for container status
2. **Diagnose:** Check logs for error patterns
3. **Recover:**
   - Restart affected container: `docker restart <container>`
   - If persistent, check dependencies (DB, Keycloak)
   - If critical, rollback to previous version

### Security Incident

1. **Isolate:** Block external access if needed
   ```bash
   sudo ufw deny 443
   ```
2. **Assess:** Check logs for unauthorized access
3. **Contain:** Revoke compromised credentials
4. **Notify:** Alert stakeholders
5. **Remediate:** Fix vulnerability, redeploy
6. **Restore:** Re-enable access after verification

### Data Corruption

1. **Stop:** Halt affected services
2. **Assess:** Determine extent of corruption
3. **Restore:** Restore from latest clean backup
4. **Verify:** Run data integrity checks
5. **Resume:** Restart services

---

## Maintenance Windows

### Planned Maintenance Procedure

1. **Announce:** Notify users 24 hours in advance
2. **Backup:** Create fresh backup before maintenance
3. **Disable:** Turn off auto-deployment webhooks
4. **Maintain:** Perform maintenance tasks
5. **Verify:** Run health checks and smoke tests
6. **Enable:** Re-enable webhooks
7. **Monitor:** Watch for issues for 1 hour

### Database Maintenance

```bash
# Vacuum and analyze (can run without downtime)
docker exec postgres psql -U ird_production -c "VACUUM ANALYZE;"

# Reindex (may cause temporary slowdown)
docker exec postgres psql -U ird_production -d incidents_db -c "REINDEX DATABASE incidents_db;"
```

---

## Contact Information

| Role | Name | Contact | Availability |
|------|------|---------|--------------|
| Primary On-Call | | | 24/7 |
| Secondary On-Call | | | 24/7 |
| Database Admin | | | Business hours |
| Security Lead | | | Business hours + emergency |
| Management | | | Business hours |

### Escalation Path

1. Primary On-Call
2. Secondary On-Call (15 min no response)
3. Tech Lead (30 min no response)
4. Management (critical issues only)

---

## Appendix: Useful Commands

```bash
# Container management
docker ps                           # List running containers
docker stats                        # Resource usage
docker logs -f <container>          # Follow logs
docker exec -it <container> sh      # Shell access

# Coolify CLI
coolify projects list               # List projects
coolify deploy                      # Trigger deployment

# Database
docker exec postgres psql -U ird_production  # PostgreSQL shell
docker exec postgres pg_dump -U ird_production incidents_db > backup.sql

# Network debugging
docker exec incident-svc curl http://postgres:5432  # Test connectivity
docker network inspect coolify      # View network config

# SSL
openssl s_client -connect app.yourdomain.com:443  # Check certificate
```
