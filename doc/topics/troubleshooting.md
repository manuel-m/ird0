# Troubleshooting Guide

## Overview

This guide provides diagnostic procedures and solutions for common issues in the IRD0 system. Organized by symptom for quick reference.

**Diagnostic Tools:**
- Docker logs: `docker compose logs`
- Health checks: `curl http://localhost:port/actuator/health`
- Database queries: `docker compose exec postgres psql`
- SFTP testing: `sftp -P 2222 -i key user@localhost`

## Service Startup Issues

### Symptom: Service Fails to Start

**Possible Causes:**
- Port already in use
- Database not available
- Configuration error
- Missing dependencies

**Diagnostics:**

1. Check container status:
```bash
docker compose ps
```

2. View startup logs:
```bash
docker compose logs policyholders
```

3. Check for error messages:
```bash
docker compose logs policyholders | grep ERROR
docker compose logs policyholders | grep Exception
```

**Common Errors and Solutions:**

**Error: "Port 8081 is already in use"**
- **Cause:** Another process using the port
- **Solution:**
  ```bash
  # Find process
  lsof -i :8081
  # Kill process
  kill -9 <PID>
  # Or change port in docker-compose.yml
  ports:
    - "8091:8081"
  ```

**Error: "Failed to configure a DataSource"**
- **Cause:** Database configuration missing or incorrect
- **Solution:** Verify environment variables:
  ```bash
  docker compose config | grep POSTGRES
  ```

**Error: "Connection refused: postgres:5432"**
- **Cause:** PostgreSQL not running or not ready
- **Solution:**
  ```bash
  # Check PostgreSQL status
  docker compose ps postgres
  # Restart PostgreSQL
  docker compose restart postgres
  # Wait for health check
  docker compose exec postgres pg_isready -U directory_user
  ```

### Symptom: Service Starts But Crashes Immediately

**Diagnostics:**

1. Check exit code:
```bash
docker compose ps
# Look for "Exit 1" or "Exit 137" (OOM)
```

2. View full logs:
```bash
docker compose logs --tail=200 policyholders
```

**Common Causes:**

**OutOfMemoryError:**
- **Symptom:** `java.lang.OutOfMemoryError: Java heap space`
- **Solution:** Increase JVM memory:
  ```yaml
  policyholders:
    environment:
      JAVA_OPTS: -Xmx512m -Xms256m
  ```

**Configuration Validation Failure:**
- **Symptom:** `Binding validation errors`
- **Solution:** Check configuration properties in YAML files

**Missing Required Files:**
- **Symptom:** `FileNotFoundException: ./keys/sftp_client_key`
- **Solution:** Generate missing SSH keys or ensure volume mounts correct

## Database Connection Issues

### Symptom: "Connection Refused" Errors

**Error Messages:**
- `Connection refused: connect`
- `org.postgresql.util.PSQLException: Connection refused`
- `Could not open JDBC Connection`

**Diagnostics:**

1. Check PostgreSQL container:
```bash
docker compose ps postgres
# Should show "Up" and "healthy"
```

2. Check PostgreSQL logs:
```bash
docker compose logs postgres
```

3. Test connection manually:
```bash
docker compose exec postgres pg_isready -U directory_user
# Should output: "accepting connections"
```

4. Test database existence:
```bash
docker compose exec postgres psql -U directory_user -l
# Should list policyholders_db, experts_db, providers_db
```

**Solutions:**

**PostgreSQL not running:**
```bash
docker compose up -d postgres
```

**PostgreSQL not healthy:**
```bash
docker compose restart postgres
# Wait 10-15 seconds for health check
```

**Wrong database name:**
- Check `spring.datasource.url` in configuration
- Verify database created in init script

**Wrong credentials:**
- Check environment variables: `POSTGRES_USER`, `POSTGRES_PASSWORD`
- Verify credentials match in both postgres and service containers

### Symptom: Timeout Errors

**Error Messages:**
- `Connection attempt timed out`
- `PSQLException: The connection attempt failed`

**Causes:**
- Network issues between containers
- PostgreSQL overloaded
- Connection pool exhausted

**Diagnostics:**

1. Check network connectivity:
```bash
docker compose exec policyholders ping postgres
```

2. Check connection pool status:
```bash
curl http://localhost:8081/actuator/metrics/hikaricp.connections.active
curl http://localhost:8081/actuator/metrics/hikaricp.connections.pending
```

**Solutions:**

**Connection pool exhausted:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20    # Increase from default 10
```

**Increase connection timeout:**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 60000    # Increase to 60 seconds
```

## SFTP Import Failures

### Symptom: Files Not Imported

**Possible Causes:**
- SFTP server not reachable
- Authentication failure
- File format errors
- Polling disabled

**Diagnostics:**

1. Check SFTP import enabled:
```bash
docker compose logs policyholders | grep "sftp-import.enabled"
# Should show: directory.sftp-import.enabled=true
```

2. Check polling activity:
```bash
docker compose logs policyholders | grep "Polling SFTP"
# Should see messages every 2 minutes
```

3. Check for import errors:
```bash
docker compose logs policyholders | grep "Import.*ERROR"
docker compose logs policyholders | grep "Failed to process"
```

4. Test SFTP connection manually:
```bash
sftp -P 2222 -i keys/sftp_client_key policyholder-importer@localhost
sftp> ls
sftp> quit
```

**Solutions:**

**SFTP server not running:**
```bash
docker compose up -d sftp-server
```

**Authentication failure:**
- Verify SSH key exists: `ls -la keys/sftp_client_key`
- Verify key in authorized_keys: `cat keys/authorized_keys`
- Check SFTP server logs: `docker compose logs sftp-server | grep Authentication`

**No CSV files:**
- Place CSV files in `data/` directory
- Verify SFTP server can read: `docker compose exec sftp-server ls /app/data`

**Polling interval too long:**
- Default: 2 minutes
- Change in policyholders.yml: `polling.fixed-delay: 60000` (1 minute)

### Symptom: Import Reports Failed Rows

**Log Example:**
```
INFO  Import completed: 100 total, 80 new, 10 updated, 8 unchanged, 2 failed
```

**Diagnostics:**

1. Check detailed error logs:
```bash
docker compose logs policyholders | grep "Failed to parse row"
docker compose logs policyholders | grep "Validation error"
```

2. Inspect CSV file:
```bash
cat data/policyholders.csv
```

**Common Causes:**

**Missing required fields:**
- Required: name, type, email, phone
- Solution: Ensure all required columns present in CSV

**Invalid email format:**
- Error: "Invalid email format"
- Solution: Fix email addresses in CSV

**Malformed CSV:**
- Extra commas, missing quotes, incorrect encoding
- Solution: Validate CSV format, ensure UTF-8 encoding

**Duplicate emails:**
- Primary key violation on email (unique constraint)
- Solution: Remove duplicate entries from CSV

## CSV Processing Errors

### Symptom: High Failed Row Count

**Diagnostics:**

1. Enable DEBUG logging:
```yaml
logging:
  level:
    com.ird0.directory.service.CsvImportService: DEBUG
```

2. Check validation errors:
```bash
docker compose logs policyholders | grep "Validation failed"
docker compose logs policyholders | grep "Required field missing"
```

3. Inspect problematic CSV:
```bash
# Check for non-ASCII characters
file data/policyholders.csv
# Should show: "UTF-8 Unicode text"

# Check line endings
dos2unix -i data/policyholders.csv
```

**Common Issues:**

**Encoding Issues:**
- CSV not UTF-8
- Solution: Convert to UTF-8:
  ```bash
  iconv -f ISO-8859-1 -t UTF-8 input.csv > output.csv
  ```

**Line Ending Issues:**
- Windows CRLF vs Unix LF
- Solution: Convert line endings:
  ```bash
  dos2unix data/policyholders.csv
  ```

**Extra Columns:**
- CSV has more columns than expected
- Solution: Ensure header matches: name,type,email,phone,address,additionalInfo

**Missing Header:**
- CSV without header row
- Solution: Add header as first line

### Symptom: Import Performance Slow

**Diagnostics:**

1. Check batch size:
```bash
docker compose logs policyholders | grep "batch size"
```

2. Monitor database connections:
```bash
curl http://localhost:8081/actuator/metrics/hikaricp.connections.active
```

3. Check CSV file size:
```bash
ls -lh data/policyholders.csv
wc -l data/policyholders.csv
```

**Solutions:**

**Large CSV file (>10,000 rows):**
- Current batch size: 500
- Increase if sufficient memory:
  ```yaml
  directory:
    sftp-import:
      polling:
        batch-size: 1000
  ```

**Slow database:**
- Check PostgreSQL load
- Add indexes if needed (email already indexed)
- Consider connection pool tuning

## Performance Issues

### Symptom: Slow Response Times

**Diagnostics:**

1. Check JVM metrics:
```bash
curl http://localhost:8081/actuator/metrics/jvm.memory.used
curl http://localhost:8081/actuator/metrics/system.cpu.usage
```

2. Check HTTP request metrics:
```bash
curl http://localhost:8081/actuator/metrics/http.server.requests
```

3. Enable SQL logging:
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
```

**Solutions:**

**High memory usage:**
- Increase JVM heap:
  ```yaml
  environment:
    JAVA_OPTS: -Xmx1g -Xms512m
  ```

**Slow queries:**
- Add database indexes
- Optimize JPA queries
- Use pagination for large result sets

**High CPU usage:**
- Check for infinite loops in logs
- Profile application with Java profiler
- Consider scaling horizontally

### Symptom: Memory Leaks

**Diagnostics:**

```bash
# Monitor memory over time
watch -n 5 "curl -s http://localhost:8081/actuator/metrics/jvm.memory.used | jq '.measurements[0].value'"
```

**Solutions:**

**Connection leaks:**
- Monitor: `hikaricp.connections.active` should decrease after requests
- Enable leak detection:
  ```yaml
  spring:
    datasource:
      hikari:
        leak-detection-threshold: 60000
  ```

**Session leaks:**
- Check SFTP sessions closed properly
- Review `SftpPollingFlowConfig` for proper session management

## Docker Issues

### Symptom: Container Crashes or Restarts

**Diagnostics:**

1. Check container status:
```bash
docker compose ps
docker inspect <container_id>
```

2. Check exit code:
- Exit 0: Normal shutdown
- Exit 1: Application error
- Exit 137: OOM (out of memory killed by system)
- Exit 143: Terminated by SIGTERM

3. Check Docker logs:
```bash
docker compose logs --tail=100 policyholders
```

**Solutions:**

**Exit 137 (OOM):**
- Increase Docker memory limit
- Reduce JVM heap size to fit in container limit
- Check for memory leaks

**Repeated restarts:**
- Application crashes on startup
- Check logs for startup errors
- Verify configuration and dependencies

### Symptom: Volume Permission Issues

**Error Messages:**
- `Permission denied` when accessing files
- `Cannot write to /app/data`

**Diagnostics:**

```bash
# Check volume permissions
docker compose exec policyholders ls -la /app/data
docker compose exec policyholders ls -la /app/keys
```

**Solutions:**

**Wrong file ownership:**
```bash
# Fix ownership on host
sudo chown -R $USER:$USER ./data ./keys
chmod -R 755 ./data
chmod 600 ./keys/*
```

**Read-only mount issue:**
- SFTP server data volume is read-only by design: `./data:/app/data:ro`
- Don't try to write to it from container

## Debugging Tools and Techniques

### Docker Commands

```bash
# Shell into running container
docker compose exec policyholders sh

# Check Java version
docker compose exec policyholders java -version

# View environment variables
docker compose exec policyholders env

# Check disk space
docker compose exec policyholders df -h

# Check running processes
docker compose exec policyholders ps aux
```

### Database Queries

```bash
# Connect to PostgreSQL
docker compose exec postgres psql -U directory_user -d policyholders_db

# Count entries
SELECT COUNT(*) FROM directory_entry;

# Check recent entries
SELECT * FROM directory_entry ORDER BY id DESC LIMIT 10;

# Check for duplicates
SELECT email, COUNT(*) FROM directory_entry GROUP BY email HAVING COUNT(*) > 1;

# Exit
\q
```

### SFTP Testing

```bash
# Test SFTP connection
sftp -v -P 2222 -i keys/sftp_client_key policyholder-importer@localhost

# List files
sftp> ls -la

# Download file
sftp> get policyholders.csv

# Check file timestamp
sftp> ls -l policyholders.csv

# Exit
sftp> quit
```

### Log Analysis

```bash
# Count errors in last hour
docker compose logs --since 1h policyholders | grep ERROR | wc -l

# Find specific error pattern
docker compose logs policyholders | grep -A 10 "NullPointerException"

# Export logs to file
docker compose logs policyholders > policyholders.log

# Monitor logs in real-time
docker compose logs -f policyholders | grep -i "import\|error"
```

## Recovery Procedures

### Restart Single Service

```bash
docker compose restart policyholders
```

### Restart All Services

```bash
docker compose restart
```

### Full System Reset

```bash
# Stop and remove containers (keeps volumes)
docker compose down

# Rebuild and start
docker compose up --build

# Or with fresh database (WARNING: deletes data)
docker compose down -v
docker compose up --build
```

### Database Restore

**For backup/restore procedures, see [USER_GUIDE.md#backup-and-restore](../USER_GUIDE.md#backup-and-restore)**

```bash
# Restore from backup
docker compose exec -T postgres psql -U directory_user policyholders_db < backup.sql
```

### Rollback to Previous Version

```bash
# Tag current version
docker compose build
docker tag ird0-policyholders:latest ird0-policyholders:backup

# Pull previous version (if using registry)
docker pull myregistry/ird0-policyholders:previous

# Or rebuild from previous git commit
git checkout <commit-hash>
docker compose up --build
```

## Related Topics

- [USER_GUIDE.md#troubleshooting](../USER_GUIDE.md#troubleshooting) - Operational procedures
- [monitoring.md](monitoring.md) - Health checks and metrics
- [database.md](database.md) - Database operations
- [sftp-import.md](sftp-import.md) - SFTP import details
- [ssh-keys.md](ssh-keys.md) - SSH authentication issues

## References

- [Docker Compose CLI Reference](https://docs.docker.com/compose/reference/)
- [Spring Boot Troubleshooting](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html)
- [PostgreSQL Error Codes](https://www.postgresql.org/docs/current/errcodes-appendix.html)
