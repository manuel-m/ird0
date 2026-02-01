# Coolify Installation and Configuration

This guide covers installing and configuring Coolify as the deployment platform for the IRD0 Insurance Platform.

## What is Coolify?

Coolify is a self-hosted alternative to platforms like Heroku, Netlify, and Vercel. It provides:

- Git-based deployments
- Automatic SSL via Let's Encrypt
- Docker and Docker Compose support
- Built-in reverse proxy (Traefik)
- Environment variable management
- Deployment webhooks

## Prerequisites

- VPS with minimum 2GB RAM (4GB recommended)
- Ubuntu 22.04 or Debian 12
- Root or sudo access
- Domain name pointing to VPS IP
- Ports 80, 443, and 8000 available

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                          VPS                                │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                   Coolify Stack                        │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │  │
│  │  │   Coolify   │  │  Traefik    │  │  PostgreSQL │    │  │
│  │  │   (UI/API)  │  │  (Proxy)    │  │  (Coolify)  │    │  │
│  │  │   :8000     │  │  :80/:443   │  │             │    │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘    │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                              │
│                              │ Manages                      │
│                              ▼                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  Deployed Applications                 │  │
│  │                                                        │  │
│  │   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  │  │
│  │   │ GitLab  │  │  IRD0   │  │ Vault   │  │  Other  │  │  │
│  │   │         │  │  Stack  │  │         │  │  Apps   │  │  │
│  │   └─────────┘  └─────────┘  └─────────┘  └─────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Installation

### Step 1: Prepare the Server

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install required packages
sudo apt install -y curl wget git

# Ensure Docker is NOT installed (Coolify installs its own)
# If Docker is installed, Coolify will use the existing installation
```

### Step 2: Run Coolify Installer

```bash
# Download and run the official installer
curl -fsSL https://cdn.coollabs.io/coolify/install.sh | sudo bash
```

The installer will:
1. Install Docker if not present
2. Create Coolify's directory structure at `/data/coolify`
3. Start Coolify containers
4. Generate initial admin credentials

### Step 3: Access Coolify UI

1. Navigate to `http://<your-vps-ip>:8000`
2. Complete the registration form:
   - Email address
   - Password (save securely)
   - Instance name

### Step 4: Configure Domain

1. Point a domain to your VPS IP (e.g., `coolify.yourdomain.com`)
2. In Coolify UI, go to Settings → Configuration
3. Set the FQDN to your domain
4. Enable Let's Encrypt
5. Save and verify HTTPS access

### Step 5: Configure Let's Encrypt

1. Go to Settings → Configuration → SSL/TLS
2. Enter email for Let's Encrypt notifications
3. Save settings

Coolify will automatically:
- Request certificates for configured domains
- Handle certificate renewal
- Configure Traefik for HTTPS

---

## Post-Installation Configuration

### Add SSH Key for Deployments

1. Go to Settings → Keys & Tokens
2. Click "Add SSH Key"
3. Either:
   - Generate a new key pair
   - Import existing private key

This key is used to pull from private Git repositories.

### Configure Server Resources

1. Go to Servers → localhost
2. Set resource limits:
   - Max concurrent builds: 2
   - Build timeout: 1800s (30 min)
3. Configure cleanup policy:
   - Remove old images after: 7 days
   - Keep last N images: 5

### Enable Webhooks

1. Go to Settings → Configuration
2. Enable "Webhook Deployments"
3. Note the webhook URL format:
   ```
   https://coolify.yourdomain.com/api/v1/deploy?token=<TOKEN>&uuid=<APP_UUID>
   ```

---

## Project Structure

### Create Project

1. Go to Projects → Create
2. Name: `IRD0 Insurance Platform`
3. Description: `Production deployment of IRD0 microservices`

### Create Environments

1. Within the project, create environments:
   - **Production** - Main deployment
   - **Staging** (optional) - Pre-production testing

---

## Resource Types

Coolify supports multiple deployment methods:

### Docker Compose (Recommended for IRD0)

Best for multi-container applications:
- Full control over orchestration
- Uses existing docker-compose files
- Supports `include:` directive

### Docker Image

For single container deployments:
- Pull from registry
- Configure via environment variables

### Dockerfile

Build from source:
- Clone repository
- Build using Dockerfile
- Deploy built image

---

## Network Configuration

### Internal Network

Coolify creates a `coolify` network for service communication:

```yaml
networks:
  coolify:
    external: true
```

### Port Mapping

Services are exposed through Traefik labels:

```yaml
services:
  my-service:
    labels:
      - traefik.enable=true
      - traefik.http.routers.myservice.rule=Host(`myservice.yourdomain.com`)
      - traefik.http.services.myservice.loadbalancer.server.port=8080
```

### DNS Configuration

For multi-service deployments, configure:

| Subdomain | Service |
|-----------|---------|
| `app.yourdomain.com` | Portal Frontend |
| `api.yourdomain.com` | Portal BFF |
| `auth.yourdomain.com` | Keycloak |
| `gitlab.yourdomain.com` | GitLab |

Or use path-based routing:

| Path | Service |
|------|---------|
| `/` | Portal Frontend |
| `/api/portal/*` | Portal BFF |
| `/realms/*` | Keycloak |

---

## Health Checks

Coolify monitors application health:

### Container Health Checks

Coolify respects Docker health checks defined in compose files:

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

### Coolify Health Monitoring

1. Go to Resources → [Your App] → Health Checks
2. Configure:
   - Health check URL: `/actuator/health`
   - Expected status: `200`
   - Check interval: 60s
   - Restart on failure: Yes

---

## Logs and Monitoring

### Access Logs

1. Go to Resources → [Your App] → Logs
2. View real-time logs from all containers
3. Download historical logs

### Metrics (Optional)

Coolify can integrate with external monitoring:

```yaml
# Add Prometheus metrics endpoint
services:
  app:
    labels:
      - prometheus.io/scrape=true
      - prometheus.io/port=8080
      - prometheus.io/path=/actuator/prometheus
```

---

## Backup Configuration

### Coolify Backups

1. Go to Settings → Backup
2. Configure backup schedule
3. Set backup destination (S3, local, etc.)

### Database Backups

For application databases, configure separate backups:

```yaml
services:
  db-backup:
    image: prodrigestivill/postgres-backup-local
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: all
      SCHEDULE: "@daily"
      BACKUP_KEEP_DAYS: 7
    volumes:
      - ./backups:/backups
```

---

## Security Hardening

### Firewall Configuration

```bash
# Allow only necessary ports
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8000/tcp  # Coolify UI (restrict to VPN if possible)
sudo ufw enable
```

### Restrict Coolify UI Access

For additional security, restrict UI access:

1. Use VPN for Coolify UI access
2. Or configure IP whitelist:

```yaml
# In Traefik configuration
http:
  middlewares:
    admin-whitelist:
      ipWhiteList:
        sourceRange:
          - "your-office-ip/32"
          - "your-vpn-ip/32"
```

### Two-Factor Authentication

1. Go to Settings → Profile
2. Enable 2FA
3. Save backup codes securely

---

## Troubleshooting

### Coolify Won't Start

```bash
# Check Coolify containers
docker ps -a | grep coolify

# View logs
docker logs coolify

# Restart Coolify
cd /data/coolify/source
docker compose down
docker compose up -d
```

### SSL Certificate Issues

```bash
# Check Traefik logs
docker logs coolify-proxy

# Force certificate renewal
# Go to Resources → [App] → Settings → Regenerate SSL
```

### Deployment Failures

1. Check build logs in Coolify UI
2. Verify Git repository access
3. Check Docker build context
4. Verify environment variables

### Container Communication Issues

```bash
# Verify network
docker network ls | grep coolify

# Check container network membership
docker inspect <container> | grep -A 20 Networks

# Test connectivity
docker exec <container1> ping <container2>
```

---

## Coolify CLI (Optional)

Install the CLI for automation:

```bash
# Install
curl -fsSL https://get.coolify.io/cli | bash

# Login
coolify login

# List projects
coolify projects list

# Deploy
coolify deploy --project ird0 --env production
```

---

## Integration with GitLab

### Configure Git Repository

1. Go to Resources → Create → Docker Compose
2. Source: Git Repository
3. Repository URL: `git@gitlab.yourdomain.com:your-group/ird0.git`
4. Branch: `main`
5. Build Pack: Docker Compose
6. Docker Compose Path: `deploy/docker-compose.coolify.yml`

### Webhook Setup

1. In Coolify, go to Resources → [Your App] → Settings
2. Copy the Webhook URL
3. In GitLab, go to Settings → Webhooks
4. Add webhook:
   - URL: Coolify webhook URL
   - Secret Token: Generate and save
   - Trigger: Push events, Tag events
   - SSL: Verify

### Automatic Deployments

Configure in Coolify:
1. Resources → [Your App] → Settings
2. Enable "Auto Deploy"
3. Select branch: `main`
4. Deploy on: Push to branch

---

## Resource Limits

### Container Limits

Configure in docker-compose or Coolify UI:

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

### System-Wide Limits

1. Go to Servers → localhost → Settings
2. Configure:
   - Max containers: 50
   - Max networks: 10
   - Disk usage alert: 80%

---

## Updates

### Update Coolify

```bash
# Via UI: Settings → About → Check for Updates

# Via CLI:
cd /data/coolify/source
git pull
docker compose pull
docker compose up -d
```

### Update Applications

1. Push to Git repository
2. Webhook triggers deployment
3. Or manual: Resources → [App] → Deploy

---

## Next Steps

1. Complete Coolify installation
2. Configure domain and SSL
3. Set up project and environments
4. Configure application deployment - see [coolify-app-deployment.md](coolify-app-deployment.md)
5. Set up GitLab webhook integration
6. Test deployment workflow
