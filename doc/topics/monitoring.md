# Monitoring & Observability

## Overview

The IRD0 system uses Spring Boot Actuator for production-ready monitoring, health checks, and metrics collection. All services expose standardized endpoints for observability without custom instrumentation.

**Key Features:**
- Spring Boot Actuator endpoints (health, metrics, info)
- Health indicators (database, disk space)
- Micrometer metrics (JVM, HTTP, database)
- Structured logging (SLF4J + Logback)
- Docker Compose log aggregation

## Actuator Endpoints

### Enabled Endpoints

**Configuration (application.yml):**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

**Directory Services (Ports 8081, 8082, 8083):**

```bash
# Health check
curl http://localhost:8081/actuator/health

# Application info
curl http://localhost:8081/actuator/info

# Available metrics
curl http://localhost:8081/actuator/metrics

# List all endpoints
curl http://localhost:8081/actuator
```

**SFTP Server (Port 9090):**

```yaml
management:
  server:
    port: 9090    # Separate port (no web server on main port)
```

```bash
# Health check
curl http://localhost:9090/actuator/health

# Metrics
curl http://localhost:9090/actuator/metrics
```

### Health Endpoint

**URL:** `/actuator/health`

**Response (UP):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 250685575168,
        "free": 125685575168,
        "threshold": 10485760,
        "path": "/app/.",
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Response (DOWN):**
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "org.postgresql.util.PSQLException: Connection refused"
      }
    }
  }
}
```

**Status Codes:**
- 200: All components UP
- 503: Any component DOWN or OUT_OF_SERVICE

### Info Endpoint

**URL:** `/actuator/info`

**Response:**
```json
{
  "app": {
    "name": "directory-service",
    "version": "1.0.0",
    "description": "Multi-instance directory service"
  },
  "build": {
    "artifact": "directory",
    "name": "directory",
    "version": "1.0.0",
    "group": "com.ird0"
  }
}
```

**Customization:**
```yaml
info:
  app:
    name: Directory Service
    version: 1.0.0
    description: Multi-instance directory service
```

### Metrics Endpoint

**URL:** `/actuator/metrics`

**List Available Metrics:**
```bash
curl http://localhost:8081/actuator/metrics
```

**Response:**
```json
{
  "names": [
    "jvm.memory.used",
    "jvm.memory.max",
    "jvm.threads.live",
    "jvm.threads.daemon",
    "system.cpu.usage",
    "process.cpu.usage",
    "hikaricp.connections.active",
    "hikaricp.connections.idle",
    "http.server.requests",
    "logback.events"
  ]
}
```

**Query Specific Metric:**
```bash
curl http://localhost:8081/actuator/metrics/jvm.memory.used
```

**Response:**
```json
{
  "name": "jvm.memory.used",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 157286400
    }
  ],
  "availableTags": [
    {
      "tag": "area",
      "values": ["heap", "nonheap"]
    }
  ]
}
```

## Health Check Configuration

### Default Health Indicators

**Automatically Enabled:**

| Indicator | Checks | Status DOWN If |
|-----------|--------|----------------|
| `db` | Database connectivity | Connection fails |
| `diskSpace` | Available disk space | Free space < threshold |
| `ping` | Application responsive | Always UP (unless crashed) |

### Database Health Indicator

**Component:** Spring Boot auto-configured

**Implementation:**
- Executes `isValid()` on JDBC connection
- Checks connection pool health (HikariCP)
- Verifies database reachability

**Configuration:**
```yaml
management:
  endpoint:
    health:
      show-details: always    # Show component details
```

**Health Check SQL:**

HikariCP uses `SELECT 1` (PostgreSQL) for connection validation.

### Disk Space Health Indicator

**Component:** `DiskSpaceHealthIndicator`

**Configuration:**
```yaml
management:
  health:
    diskspace:
      threshold: 10MB    # Default: 10MB minimum free space
      path: /app         # Path to check
```

**Monitoring:**
- Checks free disk space at configured path
- Status DOWN if free space < threshold
- Important for: Log files, temporary SFTP downloads, metadata storage

### Custom Health Indicators (Future)

**SFTP Connection Health:**

```java
@Component
public class SftpHealthIndicator implements HealthIndicator {

    private final SessionFactory<LsEntry> sftpSessionFactory;

    @Override
    public Health health() {
        try {
            Session<LsEntry> session = sftpSessionFactory.getSession();
            session.close();
            return Health.up().withDetail("sftp", "Connected").build();
        } catch (Exception e) {
            return Health.down().withDetail("sftp", e.getMessage()).build();
        }
    }
}
```

**Import Status Health:**

```java
@Component
public class ImportHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // Check last import time
        // Check import error rate
        // Return UP/DOWN based on criteria
    }
}
```

## Metrics Collection

### JVM Metrics

**Memory:**
- `jvm.memory.used`: Current memory usage (heap + non-heap)
- `jvm.memory.max`: Maximum memory available
- `jvm.memory.committed`: Memory guaranteed to be available

**Threads:**
- `jvm.threads.live`: Current live threads
- `jvm.threads.daemon`: Current daemon threads
- `jvm.threads.peak`: Peak thread count

**Garbage Collection:**
- `jvm.gc.pause`: GC pause duration
- `jvm.gc.memory.allocated`: Memory allocated during GC cycle
- `jvm.gc.memory.promoted`: Memory promoted to old generation

**CPU:**
- `system.cpu.usage`: System-wide CPU usage (0.0 to 1.0)
- `process.cpu.usage`: JVM process CPU usage (0.0 to 1.0)

### HTTP Metrics

**Requests:**
- `http.server.requests`: HTTP request count and duration

**Tags:**
- `uri`: Request URI (/api/policyholders, /actuator/health)
- `method`: HTTP method (GET, POST, PUT, DELETE)
- `status`: HTTP status code (200, 404, 500)
- `outcome`: Request outcome (SUCCESS, CLIENT_ERROR, SERVER_ERROR)

**Query Example:**
```bash
curl http://localhost:8081/actuator/metrics/http.server.requests?tag=uri:/api/policyholders
```

### Database Metrics (HikariCP)

**Connection Pool:**
- `hikaricp.connections.active`: Active connections in use
- `hikaricp.connections.idle`: Idle connections in pool
- `hikaricp.connections.pending`: Threads waiting for connection
- `hikaricp.connections.min`: Minimum pool size
- `hikaricp.connections.max`: Maximum pool size
- `hikaricp.connections.timeout`: Connection timeout count

**Monitoring Connection Pool Health:**
```bash
# Active connections
curl http://localhost:8081/actuator/metrics/hikaricp.connections.active

# Idle connections
curl http://localhost:8081/actuator/metrics/hikaricp.connections.idle

# Pending requests (should be 0)
curl http://localhost:8081/actuator/metrics/hikaricp.connections.pending
```

**Alerts:**
- `hikaricp.connections.pending > 0`: Pool exhausted, increase size
- `hikaricp.connections.active == hikaricp.connections.max`: Consider increasing pool size
- `hikaricp.connections.timeout > 0`: Connections timing out

### Custom Metrics (Future)

**SFTP Import Metrics:**

```java
@Service
public class CsvImportService {

    private final MeterRegistry meterRegistry;

    public ImportResult importFromCsv(File file) {
        Timer.Sample sample = Timer.start(meterRegistry);

        ImportResult result = doImport(file);

        sample.stop(Timer.builder("sftp.import.duration")
            .tag("status", "success")
            .register(meterRegistry));

        meterRegistry.counter("sftp.import.rows.new").increment(result.newRows());
        meterRegistry.counter("sftp.import.rows.updated").increment(result.updatedRows());
        meterRegistry.counter("sftp.import.rows.failed").increment(result.failedRows());

        return result;
    }
}
```

**Custom Metrics:**
- `sftp.import.duration`: Import processing time
- `sftp.import.rows.new`: New rows inserted
- `sftp.import.rows.updated`: Rows updated
- `sftp.import.rows.failed`: Failed rows

## SFTP Import Monitoring

### Log Monitoring

**Import Logs:**

```bash
# View import logs
docker compose logs -f policyholders | grep "import"

# Successful import
docker compose logs policyholders | grep "Import completed"

# Failed imports
docker compose logs policyholders | grep "ERROR"
```

**Log Output:**
```
INFO  File 'policyholders.csv' has been modified, will process
INFO  Starting batched CSV import with batch size: 500
INFO  Batched CSV import completed: 105 total, 5 new, 10 updated, 88 unchanged, 2 failed
INFO  Import completed for policyholders.csv: 105 total, 5 new, 10 updated, 88 unchanged, 2 failed
```

### Polling Frequency

**Configuration:**
```yaml
directory:
  sftp-import:
    polling:
      fixed-delay: 120000    # 2 minutes
```

**Monitoring:**
- Check logs for "Polling SFTP server" messages
- Expected: One poll every 2 minutes
- If missing: Check SFTP server connectivity

### Import Success Rate

**Calculate from Logs:**

```bash
# Count successful imports (last hour)
docker compose logs --since 1h policyholders | grep "Import completed" | wc -l

# Count failed imports
docker compose logs --since 1h policyholders | grep "Import.*ERROR" | wc -l
```

**Target:** >95% success rate

## Log Monitoring Strategy

### Structured Logging

**Configuration (application.yml):**

```yaml
logging:
  level:
    com.ird0.directory: INFO
    org.springframework.integration: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

**Log Levels:**

| Level | Usage |
|-------|-------|
| ERROR | Application errors, exceptions |
| WARN | Potential issues, deprecations |
| INFO | Startup, shutdown, import results |
| DEBUG | SQL queries, detailed flow |
| TRACE | Hibernate bind parameters, deep debugging |

### Docker Compose Log Access

**View Logs:**
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f policyholders

# Last 100 lines
docker compose logs --tail=100 policyholders

# Since timestamp
docker compose logs --since 2024-01-10T10:00:00 policyholders

# Filter by text
docker compose logs policyholders | grep ERROR
```

### Log File Storage (Future)

**Volume Mount for Logs:**

```yaml
policyholders:
  volumes:
    - ./logs:/app/logs
```

**Logback Configuration (logback-spring.xml):**

```xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>/app/logs/policyholders.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/app/logs/policyholders-%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
</appender>
```

## Alert Setup Examples

### Health Endpoint Polling

**External Monitoring (Prometheus, Uptime Robot, etc.):**

```yaml
# Poll health endpoints every 30 seconds
- job_name: 'ird0-services'
  scrape_interval: 30s
  metrics_path: '/actuator/health'
  static_configs:
    - targets:
        - 'localhost:8081'    # Policyholders
        - 'localhost:8082'    # Experts
        - 'localhost:8083'    # Providers
        - 'localhost:9090'    # SFTP Server
```

**Alert Rules:**
- Health status != "UP": Service down
- Response time > 5s: Performance degradation
- No response: Container crashed

### Log-Based Alerts

**ELK Stack (Elasticsearch, Logstash, Kibana):**

1. Ship logs to Elasticsearch via Logstash
2. Create Kibana alert for ERROR logs
3. Notify via email/Slack/PagerDuty

**Alert Conditions:**
- More than 10 ERROR logs in 5 minutes
- Any log containing "OutOfMemoryError"
- "Import failed" appears in logs

### Metric-Based Alerts (Grafana)

**Alert Examples:**

1. **High Memory Usage:**
   - Condition: `jvm.memory.used / jvm.memory.max > 0.9`
   - Alert: "JVM memory usage > 90%"

2. **Connection Pool Exhaustion:**
   - Condition: `hikaricp.connections.pending > 0`
   - Alert: "Database connection pool exhausted"

3. **High Error Rate:**
   - Condition: `http.server.requests{status=~"5.."} > 10/min`
   - Alert: "High HTTP 5xx error rate"

4. **Import Failures:**
   - Condition: Custom metric `sftp.import.rows.failed > 100`
   - Alert: "High SFTP import failure rate"

## Production Monitoring Recommendations

### APM Tools

**Options:**
- **New Relic**: Full-stack observability, automatic instrumentation
- **Datadog**: Infrastructure + APM, great Docker support
- **Dynatrace**: AI-powered insights, automatic root cause analysis
- **Elastic APM**: Open-source, integrates with ELK stack

**Integration:**

```yaml
# Example: Elastic APM Java agent
policyholders:
  environment:
    JAVA_TOOL_OPTIONS: -javaagent:/app/elastic-apm-agent.jar
    ELASTIC_APM_SERVICE_NAME: policyholders
    ELASTIC_APM_SERVER_URL: http://apm-server:8200
```

### Prometheus + Grafana Setup

**1. Enable Prometheus Endpoint:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**2. Add Prometheus Scrape Config:**

```yaml
scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'policyholders:8081'
          - 'experts:8082'
          - 'providers:8083'
```

**3. Create Grafana Dashboard:**
- JVM metrics (heap, threads, GC)
- HTTP request rates and latencies
- Database connection pool status
- Custom application metrics

### ELK Stack Integration

**1. Configure Logstash to Collect Docker Logs:**

```yaml
input {
  docker {
    host => "unix:///var/run/docker.sock"
    type => "docker"
  }
}

filter {
  if [type] == "docker" {
    grok {
      match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} - %{GREEDYDATA:message}" }
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
  }
}
```

**2. Create Kibana Dashboards:**
- Service health over time
- Error log frequency
- Import success/failure rates
- Response time percentiles

## Related Topics

- [USER_GUIDE.md#monitoring-and-health-checks](../USER_GUIDE.md#monitoring-and-health-checks) - Operational procedures
- [troubleshooting.md](troubleshooting.md) - Diagnostic procedures
- [reviews/sftp-import-review-2026-01-08.md](../reviews/sftp-import-review-2026-01-08.md) - Monitoring recommendations

## References

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus + Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.export.prometheus)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)
