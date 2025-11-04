---
name: docker-compose-orchestrator
description: Expert guidance for Docker Compose multi-service orchestration including service dependencies, health checks, networking, volumes, and environment configuration
---

# Docker Compose Orchestrator

Comprehensive guide for orchestrating multi-service applications with Docker Compose, focused on Kafka, Keycloak, and microservices patterns.

## Basic Structure

### Complete docker-compose.yml Template

```yaml
version: '3.8'

services:
  # Application service
  sync-agent:
    build:
      context: .
      dockerfile: src/main/docker/Dockerfile.jvm
    image: sync-agent:latest
    container_name: sync-agent
    ports:
      - "8088:8088"
    depends_on:
      kafka:
        condition: service_healthy
      keycloak:
        condition: service_healthy
    environment:
      # Kafka configuration
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
      KAFKA_SASL_MECHANISM: SCRAM-SHA-512
      KAFKA_SASL_JAAS_CONFIG: |
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="sync-admin"
        password="sync-admin-secret";

      # Keycloak configuration
      KC_BASE_URL: http://keycloak:8080
      KC_REALM: master
      KC_CLIENT_ID: sync-agent
      KC_CLIENT_SECRET: ${KC_CLIENT_SECRET}
      KC_WEBHOOK_HMAC_SECRET: ${KC_WEBHOOK_HMAC_SECRET}

      # Application configuration
      RECONCILE_INTERVAL_SECONDS: 120
      RECONCILE_PAGE_SIZE: 500
      RETENTION_MAX_BYTES: 268435456
      RETENTION_MAX_AGE_DAYS: 30

      # Database
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:sqlite:/data/sync.db
    volumes:
      - sync-data:/data
    networks:
      - sync-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8088/q/health/ready"]
      interval: 10s
      timeout: 3s
      retries: 10
      start_period: 30s

  # Kafka (KRaft mode - no Zookeeper)
  kafka:
    image: apache/kafka:3.7.0
    container_name: kafka
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      # KRaft configuration
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9094
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER

      # Listeners
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,SASL_PLAINTEXT://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,SASL_PLAINTEXT://kafka:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,SASL_PLAINTEXT:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

      # SASL/SCRAM configuration
      KAFKA_SASL_ENABLED_MECHANISMS: SCRAM-SHA-256,SCRAM-SHA-512
      KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: SCRAM-SHA-512

      # Cluster configuration
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_LOG_DIRS: /var/lib/kafka/data

      # Performance tuning
      KAFKA_NUM_NETWORK_THREADS: 3
      KAFKA_NUM_IO_THREADS: 8
      KAFKA_SOCKET_SEND_BUFFER_BYTES: 102400
      KAFKA_SOCKET_RECEIVE_BUFFER_BYTES: 102400
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - sync-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5
      start_period: 20s

  # Keycloak
  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    container_name: keycloak
    command:
      - start-dev
      - --health-enabled=true
    ports:
      - "8080:8080"
    environment:
      # Admin credentials
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}

      # Database (H2 for dev, use PostgreSQL for production)
      KC_DB: h2-file
      KC_DB_URL: jdbc:h2:file:/opt/keycloak/data/keycloak;DB_CLOSE_DELAY=-1

      # HTTP configuration
      KC_HTTP_ENABLED: true
      KC_HOSTNAME_STRICT: false
      KC_HOSTNAME_STRICT_HTTPS: false

      # Logging
      KC_LOG_LEVEL: INFO

      # Features
      KC_FEATURES: admin-fine-grained-authz,token-exchange
    volumes:
      - keycloak-data:/opt/keycloak/data
    networks:
      - sync-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 60s

  # PostgreSQL (for production Keycloak)
  postgres:
    image: postgres:16-alpine
    container_name: postgres
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - sync-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 10s
      timeout: 5s
      retries: 5
    profiles:
      - production

  # Prometheus (metrics collection)
  prometheus:
    image: prom/prometheus:v2.48.0
    container_name: prometheus
    ports:
      - "9090:9090"
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=30d'
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    networks:
      - sync-network
    restart: unless-stopped
    profiles:
      - monitoring

  # Grafana (visualization)
  grafana:
    image: grafana/grafana:10.2.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
      GF_INSTALL_PLUGINS: grafana-piechart-panel
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    networks:
      - sync-network
    depends_on:
      - prometheus
    restart: unless-stopped
    profiles:
      - monitoring

volumes:
  sync-data:
    driver: local
  kafka-data:
    driver: local
  keycloak-data:
    driver: local
  postgres-data:
    driver: local
  prometheus-data:
    driver: local
  grafana-data:
    driver: local

networks:
  sync-network:
    driver: bridge
```

## Advanced Patterns

### Health Checks

**Custom health check scripts:**
```yaml
healthcheck:
  test: ["CMD-SHELL", "/app/healthcheck.sh"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

**healthcheck.sh example:**
```bash
#!/bin/sh
set -e

# Check if application is responding
curl -f http://localhost:8088/q/health/ready || exit 1

# Check Kafka connectivity
curl -f http://localhost:8088/api/health/kafka || exit 1

# Check Keycloak connectivity
curl -f http://localhost:8088/api/health/keycloak || exit 1

exit 0
```

### Service Dependencies

**Wait for dependencies with conditions:**
```yaml
depends_on:
  kafka:
    condition: service_healthy
  keycloak:
    condition: service_healthy
  postgres:
    condition: service_started
```

**Alternative: Using wait-for script:**
```yaml
sync-agent:
  # ...
  entrypoint: ["/app/wait-for-it.sh", "kafka:9092", "--", "/app/start.sh"]
```

### Environment Variables

**Using .env file:**
```bash
# .env
KEYCLOAK_ADMIN_PASSWORD=supersecret
KC_CLIENT_SECRET=client-secret-here
KC_WEBHOOK_HMAC_SECRET=webhook-secret-here
POSTGRES_PASSWORD=postgres-secret
GRAFANA_PASSWORD=grafana-secret
```

**Variable substitution:**
```yaml
environment:
  DATABASE_URL: ${DATABASE_URL:-jdbc:sqlite:/data/sync.db}
  LOG_LEVEL: ${LOG_LEVEL:-INFO}
```

**Multiline environment variables:**
```yaml
environment:
  KAFKA_SASL_JAAS_CONFIG: |
    org.apache.kafka.common.security.scram.ScramLoginModule required
    username="${KAFKA_USERNAME}"
    password="${KAFKA_PASSWORD}";
```

### Networking

**Custom network with subnet:**
```yaml
networks:
  sync-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16
          gateway: 172.28.0.1
```

**Service-specific network configuration:**
```yaml
services:
  sync-agent:
    networks:
      sync-network:
        ipv4_address: 172.28.0.10
```

**Multiple networks:**
```yaml
services:
  sync-agent:
    networks:
      - frontend
      - backend

networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true  # No external access
```

### Volumes

**Named volumes with driver options:**
```yaml
volumes:
  kafka-data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /mnt/kafka-data

  sync-data:
    driver: local
    driver_opts:
      type: tmpfs
      device: tmpfs
      o: size=100m
```

**Bind mounts:**
```yaml
services:
  sync-agent:
    volumes:
      - ./config:/app/config:ro  # Read-only
      - ./data:/data:rw           # Read-write
      - ./logs:/app/logs:rw       # Logs directory
```

### Resource Limits

```yaml
services:
  sync-agent:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G

  kafka:
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 4G
        reservations:
          cpus: '2.0'
          memory: 2G
```

### Logging Configuration

```yaml
services:
  sync-agent:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
        labels: "service,environment"

  kafka:
    logging:
      driver: "syslog"
      options:
        syslog-address: "tcp://192.168.0.42:123"
        tag: "kafka"
```

## Profiles

**Development profile:**
```yaml
services:
  sync-agent-dev:
    extends:
      service: sync-agent
    environment:
      QUARKUS_PROFILE: dev
      LOG_LEVEL: DEBUG
    volumes:
      - ./target:/app:ro
    profiles:
      - dev
```

**Production profile:**
```yaml
services:
  keycloak:
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    depends_on:
      postgres:
        condition: service_healthy
    profiles:
      - production
```

**Usage:**
```bash
# Development
docker-compose --profile dev up

# Production with monitoring
docker-compose --profile production --profile monitoring up
```

## Initialization Scripts

### Kafka Setup Script

**init-kafka.sh:**
```bash
#!/bin/bash
set -e

echo "Waiting for Kafka to be ready..."
cub kafka-ready -b kafka:9092 1 30

echo "Creating SCRAM admin user..."
kafka-configs --bootstrap-server kafka:9092 \
  --alter --add-config 'SCRAM-SHA-512=[password=admin-secret]' \
  --entity-type users --entity-name sync-admin

echo "Kafka initialization complete"
```

**Integrate in compose:**
```yaml
services:
  kafka-init:
    image: apache/kafka:3.7.0
    depends_on:
      kafka:
        condition: service_healthy
    volumes:
      - ./scripts/init-kafka.sh:/init-kafka.sh
    command: ["/init-kafka.sh"]
    networks:
      - sync-network
    restart: "no"
```

### Keycloak Setup Script

**init-keycloak.sh:**
```bash
#!/bin/bash
set -e

echo "Waiting for Keycloak..."
until curl -f http://keycloak:8080/health/ready; do
  sleep 5
done

# Get admin token
TOKEN=$(curl -s -X POST http://keycloak:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  | jq -r '.access_token')

# Create sync-agent client
curl -X POST http://keycloak:8080/admin/realms/master/clients \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "sync-agent",
    "enabled": true,
    "serviceAccountsEnabled": true,
    "standardFlowEnabled": false,
    "directAccessGrantsEnabled": false
  }'

echo "Keycloak initialization complete"
```

## Useful Commands

```bash
# Start all services
docker-compose up -d

# Start specific services
docker-compose up -d kafka keycloak

# View logs
docker-compose logs -f sync-agent
docker-compose logs --tail=100 kafka

# Execute commands in container
docker-compose exec sync-agent /bin/bash
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Restart service
docker-compose restart sync-agent

# Stop and remove containers
docker-compose down

# Stop and remove volumes
docker-compose down -v

# Rebuild images
docker-compose build --no-cache sync-agent

# Scale services (for stateless services)
docker-compose up -d --scale sync-agent=3

# Check service health
docker-compose ps
docker inspect --format='{{json .State.Health}}' sync-agent | jq

# View resource usage
docker stats

# Clean up
docker-compose down -v --rmi all --remove-orphans
```

## Production Considerations

### Security

```yaml
services:
  sync-agent:
    # Run as non-root user
    user: "1000:1000"

    # Drop capabilities
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE

    # Read-only root filesystem
    read_only: true
    tmpfs:
      - /tmp
      - /app/tmp

    # Security options
    security_opt:
      - no-new-privileges:true
```

### SSL/TLS

```yaml
services:
  kafka:
    environment:
      KAFKA_LISTENERS: SSL://0.0.0.0:9093
      KAFKA_SSL_KEYSTORE_LOCATION: /certs/kafka.keystore.jks
      KAFKA_SSL_KEYSTORE_PASSWORD: ${KEYSTORE_PASSWORD}
      KAFKA_SSL_KEY_PASSWORD: ${KEY_PASSWORD}
      KAFKA_SSL_TRUSTSTORE_LOCATION: /certs/kafka.truststore.jks
      KAFKA_SSL_TRUSTSTORE_PASSWORD: ${TRUSTSTORE_PASSWORD}
    volumes:
      - ./certs:/certs:ro
```

### High Availability

```yaml
services:
  kafka-1:
    # ... kafka configuration
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9094,2@kafka-2:9094,3@kafka-3:9094

  kafka-2:
    # ... kafka configuration
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9094,2@kafka-2:9094,3@kafka-3:9094

  kafka-3:
    # ... kafka configuration
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9094,2@kafka-2:9094,3@kafka-3:9094
```

## Troubleshooting

```bash
# Check container logs
docker-compose logs sync-agent | grep ERROR

# Inspect network
docker network inspect keykloak-kafka-sync-agent_sync-network

# Test connectivity
docker-compose exec sync-agent ping kafka
docker-compose exec sync-agent curl http://keycloak:8080

# View environment variables
docker-compose exec sync-agent env

# Check health status
docker-compose exec sync-agent curl http://localhost:8088/q/health

# Debug startup issues
docker-compose up sync-agent  # Without -d to see output

# Check resource usage
docker stats --no-stream
```

## Best Practices

1. **Use health checks** for all services
2. **Set proper restart policies** (unless-stopped for services)
3. **Use named volumes** for persistent data
4. **Implement proper dependency order** with depends_on conditions
5. **Use .env file** for secrets (add to .gitignore)
6. **Set resource limits** to prevent resource exhaustion
7. **Use specific image tags** (not :latest)
8. **Implement graceful shutdown** with proper stop signals
9. **Use networks** to isolate services
10. **Add init containers** for setup tasks

## Resources

- Docker Compose Specification: https://docs.docker.com/compose/compose-file/
- Health Check Guide: https://docs.docker.com/engine/reference/builder/#healthcheck
- Networking: https://docs.docker.com/compose/networking/
- Environment Variables: https://docs.docker.com/compose/environment-variables/
