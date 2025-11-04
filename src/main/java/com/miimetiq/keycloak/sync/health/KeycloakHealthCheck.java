package com.miimetiq.keycloak.sync.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;

@Readiness
@ApplicationScoped
public class KeycloakHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(KeycloakHealthCheck.class);

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @Override
    public HealthCheckResponse call() {
        try {
            // Create a trust manager that accepts all certificates (for dev/testing)
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            // Check Keycloak health endpoint
            URL url = new URL(keycloakUrl + "/health/ready");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
                ((javax.net.ssl.HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
            }

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode == 200) {
                LOG.debug("Keycloak health check passed");
                return HealthCheckResponse
                        .named("keycloak")
                        .up()
                        .withData("url", keycloakUrl)
                        .withData("status_code", responseCode)
                        .build();
            } else {
                LOG.warn("Keycloak health check returned non-200 status: " + responseCode);
                return HealthCheckResponse
                        .named("keycloak")
                        .down()
                        .withData("url", keycloakUrl)
                        .withData("status_code", responseCode)
                        .build();
            }
        } catch (Exception e) {
            LOG.error("Keycloak health check failed", e);
            return HealthCheckResponse
                    .named("keycloak")
                    .down()
                    .withData("url", keycloakUrl)
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
