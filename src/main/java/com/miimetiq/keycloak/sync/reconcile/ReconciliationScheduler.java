package com.miimetiq.keycloak.sync.reconcile;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler for periodic reconciliation between Keycloak and Kafka.
 * <p>
 * This service triggers reconciliation at a configured interval to ensure
 * continuous synchronization. It implements overlap prevention to avoid
 * running multiple reconciliation cycles simultaneously.
 * <p>
 * Configuration:
 * - reconcile.interval-seconds: interval between reconciliation cycles (default: 120s)
 * - reconcile.scheduler-enabled: enable/disable scheduled reconciliation (default: true)
 */
@ApplicationScoped
public class ReconciliationScheduler {

    private static final Logger LOG = Logger.getLogger(ReconciliationScheduler.class);

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    ReconcileConfig reconcileConfig;

    // Flag to prevent overlapping executions
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Scheduled reconciliation job.
     * <p>
     * Runs every 120 seconds (2 minutes) by default.
     * Skips execution if a previous reconciliation is still running.
     * <p>
     * Note: The interval is currently hard-coded. To change it, modify this annotation.
     * Future enhancement: support dynamic configuration via application.properties.
     */
    @Scheduled(
            every = "120s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "reconciliation-scheduler"
    )
    void scheduledReconciliation() {
        // Check if scheduler is enabled
        if (!reconcileConfig.schedulerEnabled()) {
            LOG.trace("Scheduled reconciliation is disabled");
            return;
        }

        // Check if already running (double-check even with SKIP policy)
        if (!isRunning.compareAndSet(false, true)) {
            LOG.warn("Skipping scheduled reconciliation - previous execution still running");
            return;
        }

        try {
            LOG.info("Starting scheduled reconciliation");
            long startTime = System.currentTimeMillis();

            ReconciliationResult result = reconciliationService.performReconciliation("SCHEDULED");

            long duration = System.currentTimeMillis() - startTime;
            LOG.infof("Scheduled reconciliation completed: correlation_id=%s, success=%d, errors=%d, duration=%dms",
                    result.getCorrelationId(),
                    result.getSuccessfulOperations(),
                    result.getFailedOperations(),
                    duration);

        } catch (Exception e) {
            LOG.errorf(e, "Scheduled reconciliation failed: %s", e.getMessage());
            // Don't rethrow - we want the scheduler to continue running
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Trigger manual reconciliation.
     * <p>
     * This method is called by the REST endpoint to manually trigger reconciliation.
     * It respects the same overlap prevention as the scheduled job.
     *
     * @return the reconciliation result
     * @throws ReconciliationInProgressException if reconciliation is already running
     */
    public ReconciliationResult triggerManualReconciliation() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new ReconciliationInProgressException("Reconciliation is already in progress");
        }

        try {
            LOG.info("Starting manual reconciliation");
            long startTime = System.currentTimeMillis();

            ReconciliationResult result = reconciliationService.performReconciliation("MANUAL");

            long duration = System.currentTimeMillis() - startTime;
            LOG.infof("Manual reconciliation completed: correlation_id=%s, success=%d, errors=%d, duration=%dms",
                    result.getCorrelationId(),
                    result.getSuccessfulOperations(),
                    result.getFailedOperations(),
                    duration);

            return result;

        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Check if reconciliation is currently running.
     *
     * @return true if reconciliation is in progress
     */
    public boolean isReconciliationRunning() {
        return isRunning.get();
    }

    /**
     * Exception thrown when attempting to trigger manual reconciliation
     * while another reconciliation is already in progress.
     */
    public static class ReconciliationInProgressException extends RuntimeException {
        public ReconciliationInProgressException(String message) {
            super(message);
        }
    }
}
