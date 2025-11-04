package com.miimetiq.keycloak.sync.health;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Readiness
@ApplicationScoped
public class SQLiteHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(SQLiteHealthCheck.class);

    @Inject
    AgroalDataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            if (resultSet.next() && resultSet.getInt(1) == 1) {
                LOG.debug("SQLite health check passed");
                return HealthCheckResponse
                        .named("sqlite")
                        .up()
                        .withData("database", "connected")
                        .build();
            } else {
                LOG.warn("SQLite health check query returned unexpected result");
                return HealthCheckResponse
                        .named("sqlite")
                        .down()
                        .withData("error", "Query returned unexpected result")
                        .build();
            }
        } catch (Exception e) {
            LOG.error("SQLite health check failed", e);
            return HealthCheckResponse
                    .named("sqlite")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
