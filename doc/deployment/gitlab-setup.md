# Self-Hosted GitLab Installation

This guide covers deploying GitLab CE (Community Edition) as a Docker container on your VPS for CI/CD operations.

## Prerequisites

- VPS with minimum 4GB RAM (8GB recommended for GitLab + application stack)
- Docker and Docker Compose installed
- Domain name configured (e.g., `gitlab.yourdomain.com`)
- SSH access to VPS
- Ports 80, 443, and 2224 available

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                     VPS                         │
│  ┌───────────────────────────────────────────┐  │
│  │           Coolify / Traefik               │  │
│  │     (SSL termination, routing)            │  │
│  │         :80, :443 → internal              │  │
│  └───────────────────────────────────────────┘  │
│                      │                          │
│         ┌────────────┼────────────┐             │
│         ▼            ▼            ▼             │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐     │
│  │  GitLab   │ │   IRD0    │ │   Vault   │     │
│  │  :8929    │ │  Stack    │ │   :8200   │     │
│  └───────────┘ └───────────┘ └───────────┘     │
│         │                                       │
│         ▼                                       │
│  ┌───────────┐                                  │
│  │  GitLab   │                                  │
│  │  Runner   │                                  │
│  └───────────┘                                  │
└─────────────────────────────────────────────────┘
```

## Docker Compose Configuration

Create a directory for GitLab and add the following `docker-compose.yml`:

```yaml
# gitlab/docker-compose.yml
version: '3.8'

services:
  gitlab:
    image: gitlab/gitlab-ce:17.0.0-ce.0
    container_name: gitlab
    hostname: gitlab.yourdomain.com
    restart: unless-stopped
    environment:
      GITLAB_OMNIBUS_CONFIG: |
        # External URL (accessed via Coolify/Traefik)
        external_url 'https://gitlab.yourdomain.com'

        # SSH configuration (non-standard port)
        gitlab_rails['gitlab_shell_ssh_port'] = 2224

        # Disable built-in nginx HTTPS (Traefik handles SSL)
        letsencrypt['enable'] = false
        nginx['listen_https'] = false
        nginx['listen_port'] = 80

        # Reduce memory usage
        puma['worker_processes'] = 2
        sidekiq['max_concurrency'] = 10

        # Disable unused features to save resources
        prometheus_monitoring['enable'] = false
        grafana['enable'] = false

        # Email configuration (optional)
        # gitlab_rails['smtp_enable'] = true
        # gitlab_rails['smtp_address'] = "smtp.yourdomain.com"
        # gitlab_rails['smtp_port'] = 587
        # gitlab_rails['smtp_user_name'] = "gitlab@yourdomain.com"
        # gitlab_rails['smtp_password'] = "your-smtp-password"
        # gitlab_rails['smtp_domain'] = "yourdomain.com"
        # gitlab_rails['smtp_authentication'] = "login"
        # gitlab_rails['smtp_enable_starttls_auto'] = true

        # GitLab Pages (optional)
        # pages_external_url 'https://pages.yourdomain.com'
    ports:
      - "8929:80"      # HTTP (behind Traefik)
      - "2224:22"      # SSH for git operations
    volumes:
      - gitlab_config:/etc/gitlab
      - gitlab_logs:/var/log/gitlab
      - gitlab_data:/var/opt/gitlab
    shm_size: '256m'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/-/health"]
      interval: 60s
      timeout: 30s
      retries: 5
      start_period: 300s

  gitlab-runner:
    image: gitlab/gitlab-runner:v17.0.0
    container_name: gitlab-runner
    restart: unless-stopped
    depends_on:
      gitlab:
        condition: service_healthy
    volumes:
      - gitlab_runner_config:/etc/gitlab-runner
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - CI_SERVER_URL=http://gitlab:80
    healthcheck:
      test: ["CMD", "gitlab-runner", "verify", "--delete", "-n", "ird0-runner"]
      interval: 60s
      timeout: 10s
      retries: 3

volumes:
  gitlab_config:
  gitlab_logs:
  gitlab_data:
  gitlab_runner_config:
```

## Traefik Labels (for Coolify)

If deploying GitLab through Coolify, add these labels:

```yaml
services:
  gitlab:
    labels:
      - traefik.enable=true
      - traefik.http.routers.gitlab.rule=Host(`gitlab.yourdomain.com`)
      - traefik.http.routers.gitlab.tls=true
      - traefik.http.routers.gitlab.tls.certresolver=letsencrypt
      - traefik.http.services.gitlab.loadbalancer.server.port=80
```

## Installation Steps

### Step 1: Create Directory Structure

```bash
mkdir -p /opt/gitlab
cd /opt/gitlab
```

### Step 2: Create Docker Compose File

Copy the configuration above to `/opt/gitlab/docker-compose.yml` and customize:

- Replace `gitlab.yourdomain.com` with your actual domain
- Adjust memory settings if needed
- Configure SMTP for email notifications (optional)

### Step 3: Start GitLab

```bash
cd /opt/gitlab
docker compose up -d
```

> **Note:** Initial startup takes 3-5 minutes. GitLab is a large application.

### Step 4: Retrieve Initial Root Password

```bash
# Wait for GitLab to be healthy
docker compose logs -f gitlab | grep -m1 "gitlab Reconfigured!"

# Get the initial root password
docker exec gitlab cat /etc/gitlab/initial_root_password
```

**Important:** This password file is deleted after 24 hours. Save it immediately!

### Step 5: Access GitLab

1. Navigate to `https://gitlab.yourdomain.com`
2. Log in as `root` with the initial password
3. Change the root password immediately
4. Create your admin user account

### Step 6: Configure Runner

```bash
# Register the runner
docker exec -it gitlab-runner gitlab-runner register \
  --non-interactive \
  --url "https://gitlab.yourdomain.com" \
  --registration-token "YOUR_REGISTRATION_TOKEN" \
  --executor "docker" \
  --docker-image "docker:24.0" \
  --docker-privileged \
  --description "ird0-runner" \
  --tag-list "docker,ird0" \
  --run-untagged="true"
```

To get the registration token:
1. Go to Admin Area → CI/CD → Runners
2. Click "New instance runner"
3. Copy the registration token

## Post-Installation Configuration

### Create Project and Import from GitHub

1. Create a new blank project named `ird0`
2. Go to Project Settings → Repository → Mirroring repositories
3. Add GitHub repository as mirror source
4. Or use manual import:

```bash
# Clone from GitHub
git clone https://github.com/your-org/ird0.git
cd ird0

# Add GitLab as new remote
git remote add gitlab git@gitlab.yourdomain.com:your-group/ird0.git

# Push to GitLab
git push gitlab main --all
git push gitlab --tags
```

### Configure CI/CD Variables

Go to Project → Settings → CI/CD → Variables and add:

| Variable | Type | Protected | Masked |
|----------|------|-----------|--------|
| `POSTGRES_PASSWORD` | Variable | Yes | Yes |
| `KEYCLOAK_ADMIN_PASSWORD` | Variable | Yes | Yes |
| `KEYCLOAK_CLIENT_SECRET` | Variable | Yes | Yes |
| `VAULT_TOKEN` | Variable | Yes | Yes |
| `COOLIFY_WEBHOOK_URL` | Variable | Yes | No |
| `DOCKER_REGISTRY` | Variable | No | No |
| `DOCKER_REGISTRY_USER` | Variable | No | No |
| `DOCKER_REGISTRY_PASSWORD` | Variable | Yes | Yes |

### Enable Container Registry (Optional)

If you want to use GitLab's built-in container registry:

```ruby
# Add to GITLAB_OMNIBUS_CONFIG
registry_external_url 'https://registry.yourdomain.com'
registry['enable'] = true
```

## Security Hardening

### Disable Sign-Up

Admin Area → Settings → General → Sign-up restrictions:
- Uncheck "Sign-up enabled"

### Configure Two-Factor Authentication

Admin Area → Settings → General → Sign-in restrictions:
- Enable "Two-factor authentication" requirement

### Set Session Timeout

Admin Area → Settings → General → Account and limit:
- Set appropriate session timeout (e.g., 8 hours)

### Configure SSH Keys

1. Go to User Settings → SSH Keys
2. Add your SSH public key
3. Verify with: `ssh -T git@gitlab.yourdomain.com -p 2224`

## Backup Configuration

### Automated Backups

Add a backup container to the compose file:

```yaml
services:
  gitlab-backup:
    image: gitlab/gitlab-ce:17.0.0-ce.0
    container_name: gitlab-backup
    volumes:
      - gitlab_config:/etc/gitlab:ro
      - gitlab_data:/var/opt/gitlab:ro
      - ./backups:/backups
    entrypoint: /bin/bash
    command: |
      -c 'while true; do
        gitlab-backup create SKIP=artifacts,registry BACKUP=$(date +%Y%m%d)
        find /backups -mtime +7 -delete
        sleep 86400
      done'
```

### Manual Backup

```bash
docker exec gitlab gitlab-backup create
```

Backups are stored in: `/var/opt/gitlab/backups/`

### Restore from Backup

```bash
# Stop services that write to database
docker exec gitlab gitlab-ctl stop puma
docker exec gitlab gitlab-ctl stop sidekiq

# Restore backup
docker exec gitlab gitlab-backup restore BACKUP=timestamp_of_backup

# Restart
docker exec gitlab gitlab-ctl restart
```

## Troubleshooting

### GitLab Takes Long to Start

This is normal. Check logs:
```bash
docker compose logs -f gitlab
```

Wait for: `gitlab Reconfigured!`

### 502 Bad Gateway

GitLab is still starting or ran out of memory:
```bash
docker exec gitlab gitlab-ctl status
docker stats gitlab
```

### Runner Not Connecting

1. Check runner registration:
   ```bash
   docker exec gitlab-runner gitlab-runner list
   ```

2. Verify connectivity:
   ```bash
   docker exec gitlab-runner curl -s http://gitlab/-/health
   ```

### SSH Connection Issues

1. Verify SSH port mapping:
   ```bash
   ssh -T git@gitlab.yourdomain.com -p 2224 -v
   ```

2. Check SSH key is added in GitLab UI

### Memory Issues

GitLab recommends 4GB minimum. If running low:

```ruby
# Add to GITLAB_OMNIBUS_CONFIG
puma['worker_processes'] = 1
sidekiq['max_concurrency'] = 5
```

## Maintenance

### Update GitLab

```bash
cd /opt/gitlab

# Update image tag in docker-compose.yml
# Then:
docker compose pull gitlab
docker compose up -d gitlab
```

### View Logs

```bash
# All logs
docker compose logs -f

# Specific service
docker exec gitlab gitlab-ctl tail
```

### Check Health

```bash
docker exec gitlab gitlab-rake gitlab:check SANITIZE=true
```

## Next Steps

1. Configure CI/CD pipeline - see [gitlab-ci-guide.md](gitlab-ci-guide.md)
2. Set up Coolify - see [coolify-setup.md](coolify-setup.md)
3. Configure Vault - see [vault-production.md](vault-production.md)
