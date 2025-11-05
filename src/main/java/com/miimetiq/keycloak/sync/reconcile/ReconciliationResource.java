package com.miimetiq.keycloak.sync.reconcile;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * REST endpoint for manual reconciliation triggers and status checks.
 * <p>
 * Endpoints:
 * - POST /api/reconcile/trigger: manually trigger reconciliation
 * - GET /api/reconcile/status: check if reconciliation is currently running
 */
@Path("/api/reconcile")
@Produces(MediaType.APPLICATION_JSON)
public class ReconciliationResource {

    private static final Logger LOG = Logger.getLogger(ReconciliationResource.class);

    @Inject
    ReconciliationScheduler scheduler;

    /**
     * Manually trigger a reconciliation cycle.
     * <p>
     * Returns 202 Accepted with the reconciliation result if successful.
     * Returns 409 Conflict if a reconciliation is already in progress.
     * Returns 500 Internal Server Error if reconciliation fails.
     *
     * @return reconciliation result
     */
    @POST
    @Path("/trigger")
    public Response triggerReconciliation() {
        LOG.info("Manual reconciliation trigger requested via REST API");

        try {
            ReconciliationResult result = scheduler.triggerManualReconciliation();

            return Response
                    .status(Response.Status.ACCEPTED)
                    .entity(new TriggerResponse(
                            "Reconciliation completed successfully",
                            result.getCorrelationId(),
                            result.getSuccessfulOperations(),
                            result.getFailedOperations(),
                            result.getDurationMs()
                    ))
                    .build();

        } catch (ReconciliationScheduler.ReconciliationInProgressException e) {
            LOG.warn("Manual reconciliation rejected - already in progress");
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Reconciliation is already in progress"))
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Manual reconciliation failed: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Reconciliation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Check reconciliation status.
     * <p>
     * Returns whether a reconciliation is currently in progress.
     *
     * @return status response
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        boolean isRunning = scheduler.isReconciliationRunning();

        return Response
                .ok(new StatusResponse(isRunning))
                .build();
    }

    /**
     * Response DTO for successful reconciliation trigger.
     */
    public static class TriggerResponse {
        public String message;
        public String correlationId;
        public int successfulOperations;
        public int failedOperations;
        public long durationMs;

        public TriggerResponse() {
        }

        public TriggerResponse(String message, String correlationId, int successfulOperations,
                             int failedOperations, long durationMs) {
            this.message = message;
            this.correlationId = correlationId;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.durationMs = durationMs;
        }
    }

    /**
     * Response DTO for reconciliation status.
     */
    public static class StatusResponse {
        public boolean running;

        public StatusResponse() {
        }

        public StatusResponse(boolean running) {
            this.running = running;
        }
    }

    /**
     * Response DTO for errors.
     */
    public static class ErrorResponse {
        public String error;

        public ErrorResponse() {
        }

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
