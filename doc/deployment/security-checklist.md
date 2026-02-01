# Production Security Checklist

This checklist must be completed before deploying the IRD0 Insurance Platform to production. Each item should be verified and signed off.

## Credentials & Secrets

### Password Generation

| Item | Command | Verified |
|------|---------|----------|
| PostgreSQL password | `openssl rand -base64 32` | [ ] |
| Keycloak admin password | `openssl rand -base64 32` | [ ] |
| Keycloak client secret | `openssl rand -base64 32` | [ ] |
| Vault root token | From Vault init | [ ] |
| SFTP host key | `ssh-keygen -t ed25519` | [ ] |

### Secret Storage

- [ ] All secrets stored in Vault (not environment files)
- [ ] No secrets in docker-compose files
- [ ] No secrets in Git repository
- [ ] `.env` file not committed (in .gitignore)
- [ ] Vault unseal keys distributed to multiple team members
- [ ] Secret rotation schedule documented

### Keycloak Security

- [ ] **CRITICAL:** Dev client secret removed from `realm-export.json`
- [ ] New production client secret generated and stored in Vault
- [ ] Test users removed from realm
- [ ] Admin user has strong password
- [ ] Admin console restricted (IP whitelist if possible)

---

## Network Security

### Port Exposure

| Service | Expected Exposure | Verified |
|---------|-------------------|----------|
| PostgreSQL (5432) | Internal only | [ ] |
| Vault (8200) | Internal only | [ ] |
| Redis (if used) | Internal only | [ ] |
| All Spring Boot services | Internal (via Traefik) | [ ] |
| Keycloak | Via Traefik (HTTPS) | [ ] |
| Portal Frontend | Via Traefik (HTTPS) | [ ] |
| SFTP (2222) | External (firewall limited) | [ ] |

### Firewall Configuration

```bash
# Verify these rules are in place
sudo ufw status

# Expected rules:
# 22/tcp    ALLOW   (SSH - restrict to known IPs)
# 80/tcp    ALLOW   (HTTP - Traefik redirect)
# 443/tcp   ALLOW   (HTTPS - Traefik)
# 2222/tcp  ALLOW   (SFTP - restrict to known IPs if possible)
```

Verification:
- [ ] UFW enabled and configured
- [ ] SSH access restricted to known IPs (if possible)
- [ ] No unnecessary ports exposed
- [ ] Port scan performed to verify

### Docker Network Isolation

- [ ] All services use internal Docker network
- [ ] Only Traefik-exposed services have `coolify` network
- [ ] No `network_mode: host` used
- [ ] Inter-container communication uses service names

---

## Application Security

### Environment Configuration

- [ ] `ENV_MODE=production` set
- [ ] `SPRING_PROFILES_ACTIVE` includes `production`
- [ ] Debug endpoints disabled
- [ ] Actuator endpoints secured

### API Security

- [ ] Swagger/OpenAPI UI disabled in production
- [ ] API documentation not publicly accessible
- [ ] Rate limiting configured (if applicable)
- [ ] Input validation enabled on all endpoints

Verify Swagger is disabled:
```bash
curl -s https://app.yourdomain.com/swagger-ui.html
# Should return 404 or redirect, not Swagger UI
```

### Security Headers

Verify these headers are present:

```bash
curl -I https://app.yourdomain.com
```

| Header | Expected Value | Verified |
|--------|----------------|----------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | [ ] |
| `X-Content-Type-Options` | `nosniff` | [ ] |
| `X-Frame-Options` | `DENY` or `SAMEORIGIN` | [ ] |
| `X-XSS-Protection` | `1; mode=block` | [ ] |
| `Content-Security-Policy` | Defined appropriately | [ ] |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | [ ] |

### CORS Configuration

- [ ] CORS origins restricted to known domains
- [ ] No wildcard (`*`) origins in production
- [ ] Credentials allowed only for authorized origins

Verify:
```bash
curl -H "Origin: https://malicious.com" \
  -I https://app.yourdomain.com/api/portal/v1/dashboard
# Should NOT include Access-Control-Allow-Origin: https://malicious.com
```

---

## SSL/TLS

### Certificate Configuration

- [ ] Let's Encrypt certificates active
- [ ] Certificate auto-renewal configured
- [ ] TLS 1.2 minimum enforced
- [ ] TLS 1.0/1.1 disabled

### HTTPS Verification

```bash
# Test SSL configuration
curl -sS https://www.ssllabs.com/ssltest/analyze.html?d=app.yourdomain.com
# Or use:
nmap --script ssl-enum-ciphers -p 443 app.yourdomain.com
```

| Check | Requirement | Verified |
|-------|-------------|----------|
| TLS Version | 1.2+ only | [ ] |
| Certificate Valid | Yes | [ ] |
| Certificate Chain | Complete | [ ] |
| HSTS Header | Enabled | [ ] |
| HTTP → HTTPS Redirect | Working | [ ] |

---

## Keycloak Security

### Authentication Settings

- [ ] Password policy enforced (min length, complexity)
- [ ] Brute force protection enabled
- [ ] Session timeout configured (e.g., 8 hours)
- [ ] Refresh token rotation enabled (`revokeRefreshToken: true`)

### Client Configuration

Portal Client settings:
- [ ] Public client disabled (use confidential)
- [ ] Client secret rotated for production
- [ ] Valid redirect URIs limited to production domains
- [ ] Web origins configured correctly

### User Management

- [ ] Default admin password changed
- [ ] Test users removed
- [ ] Required actions configured (e.g., email verification)
- [ ] Self-registration disabled (unless required)

---

## Database Security

### PostgreSQL Configuration

- [ ] Strong password set (32+ characters)
- [ ] Default `postgres` user not used by application
- [ ] Application user has minimal required permissions
- [ ] No external port exposure (5432)

### Connection Security

- [ ] SSL/TLS for database connections (if supported)
- [ ] Connection pooling configured
- [ ] Idle connection timeout set

### Backup Security

- [ ] Backups encrypted
- [ ] Backup storage access restricted
- [ ] Backup retention policy defined
- [ ] Backup restoration tested

---

## Vault Security

### Production Mode

- [ ] Dev mode disabled
- [ ] File/Consul storage backend configured
- [ ] TLS enabled for Vault API
- [ ] Audit logging enabled

### Access Control

- [ ] Root token secured and rarely used
- [ ] AppRole authentication for services
- [ ] Least-privilege policies defined
- [ ] Token TTLs configured appropriately

### Operational Security

- [ ] Unseal keys distributed among 3+ team members
- [ ] Auto-unseal configured (optional, but recommended)
- [ ] Backup procedures in place
- [ ] Seal/unseal procedures documented

---

## Container Security

### Image Security

- [ ] Base images from trusted sources
- [ ] Images scanned for vulnerabilities
- [ ] No `latest` tags in production
- [ ] Multi-stage builds to minimize image size

### Runtime Security

- [ ] Containers run as non-root user
- [ ] Read-only root filesystem (where possible)
- [ ] Resource limits configured
- [ ] No privileged containers (except where required)

Verify container user:
```bash
docker exec incident-svc id
# Should not show uid=0(root)
```

### Secrets in Containers

- [ ] Secrets passed via environment variables (from Vault/Coolify)
- [ ] No secrets in Dockerfiles or compose files
- [ ] Secrets not logged

---

## Logging & Monitoring

### Log Security

- [ ] No secrets in logs
- [ ] Log levels appropriate (INFO, not DEBUG in production)
- [ ] Log rotation configured
- [ ] Logs stored securely

### Audit Logging

- [ ] Vault audit logging enabled
- [ ] Keycloak event logging enabled
- [ ] Application access logs enabled
- [ ] Log retention policy defined

### Monitoring

- [ ] Health checks configured and monitored
- [ ] Alerting configured for failures
- [ ] Uptime monitoring in place
- [ ] Resource usage monitoring

---

## Pre-Deployment Verification

### Test Suite

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Security scanning completed (SAST)
- [ ] Dependency scanning completed

### Deployment Testing

- [ ] Staging deployment successful
- [ ] Rollback procedure tested
- [ ] Health checks verified
- [ ] Performance baseline established

---

## Final Verification

### External Security Scan

Run external security scan:
```bash
# OWASP ZAP baseline scan
docker run -t owasp/zap2docker-stable zap-baseline.py \
  -t https://app.yourdomain.com
```

- [ ] No high-severity findings
- [ ] Medium findings documented and addressed or accepted
- [ ] Scan report saved

### Penetration Testing (If Required)

- [ ] Scope defined
- [ ] Testing window scheduled
- [ ] Findings addressed
- [ ] Re-test completed

---

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| Security Lead | | | |
| Operations | | | |
| Manager | | | |

---

## Post-Deployment Monitoring

After deployment, monitor for 48 hours:

- [ ] No authentication failures (beyond expected)
- [ ] No unusual error patterns
- [ ] Performance within acceptable range
- [ ] All health checks passing
- [ ] No security alerts triggered

---

## Emergency Procedures

### Security Incident Response

1. **Immediate:** Isolate affected services
2. **Assess:** Determine scope of incident
3. **Contain:** Stop ongoing attack/breach
4. **Notify:** Alert stakeholders
5. **Remediate:** Fix vulnerabilities
6. **Review:** Post-incident analysis

### Key Contacts

| Role | Contact |
|------|---------|
| Security Lead | |
| On-Call Engineer | |
| Cloud Provider Support | |
| Management | |

---

## Regular Security Tasks

| Task | Frequency | Last Completed |
|------|-----------|----------------|
| Rotate Keycloak client secrets | Quarterly | |
| Rotate database passwords | Quarterly | |
| Review access logs | Weekly | |
| Security patch review | Weekly | |
| Dependency updates | Monthly | |
| Full security audit | Annually | |
| Disaster recovery drill | Annually | |
