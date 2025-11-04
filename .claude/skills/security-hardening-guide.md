---
name: security-hardening-guide
description: Expert guidance for security hardening including mTLS configuration, OIDC integration, secret management, input validation, and OWASP best practices
---

# Security Hardening Guide

Comprehensive security best practices for production-ready microservices with focus on authentication, authorization, encryption, and secret management.

## 1. TLS/mTLS Configuration

### Kafka mTLS

**Generate certificates:**
```bash
# Create CA
openssl req -new -x509 -keyout ca-key.pem -out ca-cert.pem -days 365 -nodes

# Create server keystore
keytool -genkey -keyalg RSA -keysize 2048 -keystore kafka.server.keystore.jks \
  -validity 365 -storepass changeit -keypass changeit \
  -dname "CN=kafka,OU=IT,O=Company,L=City,ST=State,C=US"

# Sign certificate
keytool -certreq -keystore kafka.server.keystore.jks -alias localhost \
  -file cert-request.csr -storepass changeit

openssl x509 -req -CA ca-cert.pem -CAkey ca-key.pem \
  -in cert-request.csr -out cert-signed.pem -days 365 -CAcreateserial

# Import certificates
keytool -import -file ca-cert.pem -alias CARoot \
  -keystore kafka.server.keystore.jks -storepass changeit -noprompt

keytool -import -file cert-signed.pem -alias localhost \
  -keystore kafka.server.keystore.jks -storepass changeit

# Create truststore
keytool -import -file ca-cert.pem -alias CARoot \
  -keystore kafka.server.truststore.jks -storepass changeit -noprompt
```

**Kafka configuration:**
```properties
# server.properties
listeners=SSL://0.0.0.0:9093
advertised.listeners=SSL://kafka:9093
security.protocol=SSL

ssl.keystore.location=/certs/kafka.server.keystore.jks
ssl.keystore.password=changeit
ssl.key.password=changeit
ssl.truststore.location=/certs/kafka.server.truststore.jks
ssl.truststore.password=changeit

# Client authentication (mTLS)
ssl.client.auth=required

# Protocol versions
ssl.enabled.protocols=TLSv1.3,TLSv1.2
ssl.protocol=TLSv1.3

# Cipher suites (strong only)
ssl.cipher.suites=TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256
```

**Client configuration (Quarkus):**
```properties
kafka.bootstrap.servers=kafka:9093
kafka.security.protocol=SSL
kafka.ssl.truststore.location=/certs/client.truststore.jks
kafka.ssl.truststore.password=${TRUSTSTORE_PASSWORD}
kafka.ssl.keystore.location=/certs/client.keystore.jks
kafka.ssl.keystore.password=${KEYSTORE_PASSWORD}
kafka.ssl.key.password=${KEY_PASSWORD}

# Hostname verification
kafka.ssl.endpoint.identification.algorithm=https
```

### Quarkus HTTPS

```properties
# Enable HTTPS
quarkus.http.ssl-port=8443
quarkus.http.insecure-requests=disabled

# Certificates
quarkus.http.ssl.certificate.key-store-file=/certs/server.keystore.jks
quarkus.http.ssl.certificate.key-store-password=${KEYSTORE_PASSWORD}
quarkus.http.ssl.certificate.trust-store-file=/certs/server.truststore.jks
quarkus.http.ssl.certificate.trust-store-password=${TRUSTSTORE_PASSWORD}

# mTLS (require client certificates)
quarkus.http.ssl.client-auth=required

# TLS version
quarkus.http.ssl.protocols=TLSv1.3
```

## 2. Authentication & Authorization

### OIDC Integration with Keycloak

**Dependencies:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
```

**Configuration:**
```properties
# OIDC
quarkus.oidc.auth-server-url=https://keycloak:8443/realms/master
quarkus.oidc.client-id=sync-agent
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET}
quarkus.oidc.tls.verification=certificate-validation

# Token configuration
quarkus.oidc.token.issuer=${quarkus.oidc.auth-server-url}
quarkus.oidc.token.audience=account

# Session timeout
quarkus.oidc.token.refresh-expired=true
quarkus.oidc.token.lifespan-grace=10

# Roles mapping
quarkus.oidc.roles.source=accesstoken
quarkus.oidc.roles.role-claim-path=realm_access/roles
```

**Protected endpoints:**
```java
@Path("/api/admin")
@RolesAllowed("admin")
@ApplicationScoped
public class AdminResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/users")
    public List<User> getUsers() {
        String username = identity.getPrincipal().getName();
        Log.infof("Admin %s accessed user list", username);
        return userService.getAllUsers();
    }
}

@Path("/api/dashboard")
@RolesAllowed({"admin", "viewer"})
public class DashboardResource {

    @GET
    @Path("/summary")
    public Summary getSummary() {
        // Accessible by admin and viewer roles
    }
}

@Path("/api/public")
@PermitAll
public class PublicResource {
    // No authentication required
}
```

### Role-Based Access Control (RBAC)

```java
@ApplicationScoped
public class AuthorizationService {

    public boolean canAccessRealm(SecurityIdentity identity, String realm) {
        // Check if user has permission to access specific realm
        return identity.hasRole("admin") ||
               identity.hasRole("realm-" + realm);
    }

    public boolean canManageUsers(SecurityIdentity identity) {
        return identity.hasRole("admin") ||
               identity.hasRole("user-manager");
    }

    public boolean canViewMetrics(SecurityIdentity identity) {
        return identity.hasRole("admin") ||
               identity.hasRole("viewer") ||
               identity.hasRole("operator");
    }
}

// Usage in resource
@Path("/api/realms/{realm}")
public class RealmResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    AuthorizationService authz;

    @GET
    public RealmInfo getRealmInfo(@PathParam("realm") String realm) {
        if (!authz.canAccessRealm(identity, realm)) {
            throw new ForbiddenException("Access denied to realm: " + realm);
        }
        return realmService.getInfo(realm);
    }
}
```

### API Key Authentication

```java
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthenticationFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "api.keys")
    Map<String, String> apiKeys;  // key -> username

    @Inject
    SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String apiKey = requestContext.getHeaderString("X-API-Key");

        if (apiKey == null || !apiKeys.containsKey(apiKey)) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid API key")
                    .build()
            );
            return;
        }

        // Set identity
        String username = apiKeys.get(apiKey);
        // ... set security context
    }
}
```

## 3. Secret Management

### Using Environment Variables

```properties
# application.properties - DO NOT hardcode secrets
kafka.sasl.jaas.config=${KAFKA_JAAS_CONFIG}
keycloak.client.secret=${KC_CLIENT_SECRET}
database.password=${DB_PASSWORD}

# .env file (DO NOT commit to git)
KAFKA_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="admin" password="secret";
KC_CLIENT_SECRET=your-client-secret
DB_PASSWORD=your-db-password
```

### HashiCorp Vault Integration

**Dependencies:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-vault</artifactId>
</dependency>
```

**Configuration:**
```properties
quarkus.vault.url=https://vault:8200
quarkus.vault.authentication.kubernetes.role=sync-agent

# Read secrets from Vault
quarkus.vault.secret-config-kv-path=secret/sync-agent

# Secrets mapping
kafka.password=${kafka-password}
keycloak.secret=${keycloak-secret}
```

### Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: sync-agent-secrets
type: Opaque
stringData:
  kafka-jaas-config: |
    org.apache.kafka.common.security.scram.ScramLoginModule required
    username="admin"
    password="secret";
  keycloak-client-secret: "your-secret"
  db-password: "your-password"

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sync-agent
spec:
  template:
    spec:
      containers:
      - name: sync-agent
        env:
        - name: KAFKA_JAAS_CONFIG
          valueFrom:
            secretKeyRef:
              name: sync-agent-secrets
              key: kafka-jaas-config
        - name: KC_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: sync-agent-secrets
              key: keycloak-client-secret
```

## 4. Input Validation

### Request Validation

```java
import javax.validation.constraints.*;

public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}$",
             message = "Username must be 3-64 alphanumeric characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be 12-128 characters")
    private String password;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^(SCRAM-SHA-256|SCRAM-SHA-512)$",
             message = "Invalid SCRAM mechanism")
    private String mechanism;

    // getters/setters
}

@Path("/api/users")
public class UserResource {

    @POST
    public Response createUser(@Valid CreateUserRequest request) {
        // Request is automatically validated
        userService.createUser(request);
        return Response.ok().build();
    }
}
```

### SQL Injection Prevention

```java
// ALWAYS use parameterized queries
@ApplicationScoped
public class UserRepository {

    @Inject
    AgroalDataSource dataSource;

    // ✅ GOOD: Parameterized query
    public List<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            // ...
        }
    }

    // ❌ BAD: String concatenation (SQL injection)
    public List<User> findByUsernameBad(String username) {
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        // NEVER DO THIS!
    }
}
```

### Path Traversal Prevention

```java
public class FileService {

    private static final Path BASE_PATH = Paths.get("/data");

    public byte[] readFile(String filename) {
        // Validate filename
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new SecurityException("Invalid filename");
        }

        Path filePath = BASE_PATH.resolve(filename).normalize();

        // Ensure resolved path is still under BASE_PATH
        if (!filePath.startsWith(BASE_PATH)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        return Files.readAllBytes(filePath);
    }
}
```

### XSS Prevention

```java
import org.owasp.encoder.Encode;

@Path("/api/messages")
public class MessageResource {

    @POST
    public Response postMessage(String content) {
        // Sanitize HTML
        String sanitized = Encode.forHtml(content);

        // Or use a library like OWASP Java HTML Sanitizer
        PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS);
        String safe = policy.sanitize(content);

        messageService.save(safe);
        return Response.ok().build();
    }
}
```

## 5. CORS Configuration

```properties
# Restrict CORS in production
quarkus.http.cors=true
quarkus.http.cors.origins=https://dashboard.company.com
quarkus.http.cors.methods=GET,POST,PUT,DELETE
quarkus.http.cors.headers=Content-Type,Authorization
quarkus.http.cors.exposed-headers=X-Request-ID
quarkus.http.cors.access-control-max-age=86400
quarkus.http.cors.access-control-allow-credentials=true
```

## 6. Rate Limiting

```java
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import java.time.Duration;

@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class RateLimitFilter implements ContainerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String clientId = getClientId(requestContext);
        Bucket bucket = buckets.computeIfAbsent(clientId, k ->
            Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build()
        );

        if (!bucket.tryConsume(1)) {
            requestContext.abortWith(
                Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity("Rate limit exceeded")
                    .build()
            );
        }
    }

    private String getClientId(ContainerRequestContext context) {
        // Use API key, IP address, or authenticated user
        String apiKey = context.getHeaderString("X-API-Key");
        return apiKey != null ? apiKey : context.getHeaderString("X-Forwarded-For");
    }
}
```

## 7. Audit Logging

```java
@ApplicationScoped
public class AuditLogger {

    @Inject
    SecurityIdentity identity;

    public void logAccess(String resource, String action, boolean success) {
        String username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
        String timestamp = Instant.now().toString();
        String result = success ? "SUCCESS" : "FAILURE";

        // Log to structured format (JSON)
        Log.infof("{\"timestamp\":\"%s\",\"user\":\"%s\",\"action\":\"%s\",\"resource\":\"%s\",\"result\":\"%s\"}",
            timestamp, username, action, resource, result);

        // Or use dedicated audit log
        AuditEvent event = new AuditEvent(timestamp, username, action, resource, result);
        auditRepository.save(event);
    }
}

// Usage
@Path("/api/users")
public class UserResource {

    @Inject
    AuditLogger auditLogger;

    @POST
    @Path("/{id}/delete")
    public Response deleteUser(@PathParam("id") String userId) {
        try {
            userService.delete(userId);
            auditLogger.logAccess("user:" + userId, "DELETE", true);
            return Response.ok().build();
        } catch (Exception e) {
            auditLogger.logAccess("user:" + userId, "DELETE", false);
            throw e;
        }
    }
}
```

## 8. Security Headers

```java
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();

        // Prevent clickjacking
        headers.add("X-Frame-Options", "DENY");

        // XSS protection
        headers.add("X-XSS-Protection", "1; mode=block");

        // Content type sniffing
        headers.add("X-Content-Type-Options", "nosniff");

        // HTTPS only
        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Content Security Policy
        headers.add("Content-Security-Policy",
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");

        // Referrer policy
        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions policy
        headers.add("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }
}
```

## 9. Container Security

### Dockerfile Hardening

```dockerfile
# Use specific version, not :latest
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.18

# Run as non-root user
USER 1000

# Copy application
COPY --chown=1000:1000 target/quarkus-app /app

# Read-only filesystem (where possible)
WORKDIR /app
USER 1000

# Drop all capabilities
RUN setcap -r /usr/bin/java || true

EXPOSE 8088

CMD ["java", "-jar", "quarkus-run.jar"]
```

### Docker Compose Security

```yaml
services:
  sync-agent:
    image: sync-agent:latest
    user: "1000:1000"

    # Read-only root filesystem
    read_only: true
    tmpfs:
      - /tmp
      - /app/tmp

    # Drop all capabilities
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE

    # No new privileges
    security_opt:
      - no-new-privileges:true

    # Limited resources
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G

    # Network isolation
    networks:
      - internal
```

## 10. Dependency Scanning

### OWASP Dependency Check

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.0</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <skipProvidedScope>false</skipProvidedScope>
        <skipRuntimeScope>false</skipRuntimeScope>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Snyk Integration

```yaml
# .github/workflows/security.yml
name: Security Scan

on: [push, pull_request]

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: snyk/actions/maven@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --severity-threshold=high
```

## Security Checklist

- [ ] TLS 1.3 enabled for all connections
- [ ] mTLS configured for Kafka
- [ ] OIDC authentication enabled
- [ ] Role-based access control implemented
- [ ] Secrets stored in vault/environment variables
- [ ] Input validation on all endpoints
- [ ] SQL injection prevention (parameterized queries)
- [ ] XSS prevention (output encoding)
- [ ] CORS properly configured
- [ ] Rate limiting enabled
- [ ] Audit logging implemented
- [ ] Security headers set
- [ ] Container runs as non-root
- [ ] Read-only filesystem where possible
- [ ] Dependency scanning in CI/CD
- [ ] Regular security updates applied

## Resources

- OWASP Top 10: https://owasp.org/www-project-top-ten/
- Quarkus Security: https://quarkus.io/guides/security
- Kafka Security: https://kafka.apache.org/documentation/#security
- Keycloak Security: https://www.keycloak.org/docs/latest/securing_apps/
