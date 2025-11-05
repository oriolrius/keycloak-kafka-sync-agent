package com.miimetiq.keycloak.sync.dashboard;

import com.miimetiq.keycloak.sync.domain.entity.SyncBatch;
import com.miimetiq.keycloak.sync.domain.entity.SyncOperation;
import com.miimetiq.keycloak.sync.domain.enums.OpType;
import com.miimetiq.keycloak.sync.domain.enums.OperationResult;
import com.miimetiq.keycloak.sync.repository.SyncBatchRepository;
import com.miimetiq.keycloak.sync.repository.SyncOperationRepository;
import com.miimetiq.keycloak.sync.service.RetentionService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST endpoint for dashboard data.
 * Provides summary statistics, operations timeline, and batch history.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dashboard", description = "Dashboard API for sync operations monitoring")
public class DashboardResource {

    private static final Logger LOG = Logger.getLogger(DashboardResource.class);

    @Inject
    SyncOperationRepository operationRepository;

    @Inject
    SyncBatchRepository batchRepository;

    @Inject
    RetentionService retentionService;

    /**
     * Get summary statistics for the dashboard.
     * Returns operations per hour, error rate, latency percentiles, and database usage.
     *
     * @return summary statistics
     */
    @GET
    @Path("/summary")
    @Operation(
        summary = "Get dashboard summary statistics",
        description = "Returns summary statistics including operations per hour, error rate, latency percentiles (p95/p99), and database usage"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Summary statistics retrieved successfully",
            content = @Content(schema = @Schema(implementation = SummaryResponse.class))
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public Response getSummary() {
        LOG.debug("GET /api/summary requested");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneHourAgo = now.minusHours(1);

            // Get operations from the last hour
            List<SyncOperation> recentOps = operationRepository.findByTimeRange(oneHourAgo, now);

            // Calculate ops per hour
            long opsPerHour = recentOps.size();

            // Calculate error rate
            long errorCount = recentOps.stream()
                    .filter(op -> op.getResult() == OperationResult.ERROR)
                    .count();
            double errorRate = opsPerHour > 0 ? (errorCount * 100.0 / opsPerHour) : 0.0;

            // Calculate latency percentiles (95th and 99th)
            List<Integer> durations = recentOps.stream()
                    .map(SyncOperation::getDurationMs)
                    .sorted()
                    .collect(Collectors.toList());

            Integer latencyP95 = calculatePercentile(durations, 95);
            Integer latencyP99 = calculatePercentile(durations, 99);

            // Get database usage
            Long dbUsageBytes = retentionService.getRetentionState().getApproxDbBytes();

            SummaryResponse response = new SummaryResponse(
                    opsPerHour,
                    errorRate,
                    latencyP95,
                    latencyP99,
                    dbUsageBytes,
                    now
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to retrieve summary statistics: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve summary statistics: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get paginated operations with optional filters.
     *
     * @param page      page number (0-indexed, default: 0)
     * @param pageSize  page size (default: 20)
     * @param startTime start of time range (ISO format, optional)
     * @param endTime   end of time range (ISO format, optional)
     * @param principal filter by principal (optional)
     * @param opType    filter by operation type (optional)
     * @param result    filter by result (optional)
     * @return paginated operations
     */
    @GET
    @Path("/operations")
    @Operation(
        summary = "Get paginated operations",
        description = "Returns paginated list of sync operations with optional filters for time range, principal, operation type, and result"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Operations retrieved successfully",
            content = @Content(schema = @Schema(implementation = OperationsPageResponse.class))
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid query parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public Response getOperations(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") int pageSize,
            @Parameter(description = "Start of time range (ISO 8601 format)", example = "2025-01-01T00:00:00")
            @QueryParam("startTime") String startTime,
            @Parameter(description = "End of time range (ISO 8601 format)", example = "2025-01-01T23:59:59")
            @QueryParam("endTime") String endTime,
            @Parameter(description = "Filter by principal username", example = "john.doe")
            @QueryParam("principal") String principal,
            @Parameter(description = "Filter by operation type", example = "SCRAM_UPSERT")
            @QueryParam("opType") String opType,
            @Parameter(description = "Filter by result status", example = "SUCCESS")
            @QueryParam("result") String result) {

        LOG.debugf("GET /api/operations requested: page=%d, pageSize=%d, startTime=%s, endTime=%s, principal=%s, opType=%s, result=%s",
                page, pageSize, startTime, endTime, principal, opType, result);

        try {
            // Build query based on filters
            StringBuilder query = new StringBuilder("1=1");
            List<Object> params = new ArrayList<>();

            // Time range filter
            LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime) : null;
            LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : null;

            if (start != null) {
                query.append(" and occurredAt >= ?").append(params.size() + 1);
                params.add(start);
            }
            if (end != null) {
                query.append(" and occurredAt <= ?").append(params.size() + 1);
                params.add(end);
            }

            // Principal filter
            if (principal != null && !principal.trim().isEmpty()) {
                query.append(" and principal = ?").append(params.size() + 1);
                params.add(principal);
            }

            // OpType filter
            if (opType != null && !opType.trim().isEmpty()) {
                try {
                    OpType parsedOpType = OpType.valueOf(opType.toUpperCase());
                    query.append(" and opType = ?").append(params.size() + 1);
                    params.add(parsedOpType);
                } catch (IllegalArgumentException e) {
                    return Response
                            .status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("Invalid opType: " + opType))
                            .build();
                }
            }

            // Result filter
            if (result != null && !result.trim().isEmpty()) {
                try {
                    OperationResult parsedResult = OperationResult.valueOf(result.toUpperCase());
                    query.append(" and result = ?").append(params.size() + 1);
                    params.add(parsedResult);
                } catch (IllegalArgumentException e) {
                    return Response
                            .status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("Invalid result: " + result))
                            .build();
                }
            }

            // Execute query with pagination
            List<SyncOperation> operations = operationRepository
                    .find(query.toString(), Sort.by("occurredAt").descending(), params.toArray())
                    .page(Page.of(page, pageSize))
                    .list();

            // Count total
            long total = operationRepository.count(query.toString(), params.toArray());

            // Convert to response DTOs
            List<OperationResponse> operationResponses = operations.stream()
                    .map(this::toOperationResponse)
                    .collect(Collectors.toList());

            OperationsPageResponse response = new OperationsPageResponse(
                    operationResponses,
                    page,
                    pageSize,
                    total
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to retrieve operations: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve operations: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get paginated batches.
     *
     * @param page     page number (0-indexed, default: 0)
     * @param pageSize page size (default: 20)
     * @return paginated batches
     */
    @GET
    @Path("/batches")
    @Operation(
        summary = "Get paginated batches",
        description = "Returns paginated list of sync batches (reconciliation cycles) ordered by start time descending"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Batches retrieved successfully",
            content = @Content(schema = @Schema(implementation = BatchesPageResponse.class))
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public Response getBatches(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") int pageSize) {

        LOG.debugf("GET /api/batches requested: page=%d, pageSize=%d", page, pageSize);

        try {
            // Get paginated batches sorted by start time descending
            List<SyncBatch> batches = batchRepository.findAllPaged(page, pageSize);

            // Count total
            long total = batchRepository.count();

            // Convert to response DTOs
            List<BatchResponse> batchResponses = batches.stream()
                    .map(this::toBatchResponse)
                    .collect(Collectors.toList());

            BatchesPageResponse response = new BatchesPageResponse(
                    batchResponses,
                    page,
                    pageSize,
                    total
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to retrieve batches: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve batches: " + e.getMessage()))
                    .build();
        }
    }

    // Helper methods

    /**
     * Calculate percentile from sorted list of integers.
     *
     * @param sortedValues sorted list of values
     * @param percentile   percentile to calculate (0-100)
     * @return percentile value or null if list is empty
     */
    private Integer calculatePercentile(List<Integer> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }

        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Convert SyncOperation entity to OperationResponse DTO.
     */
    private OperationResponse toOperationResponse(SyncOperation op) {
        return new OperationResponse(
                op.getId(),
                op.getCorrelationId(),
                op.getOccurredAt(),
                op.getRealm(),
                op.getClusterId(),
                op.getPrincipal(),
                op.getOpType() != null ? op.getOpType().name() : null,
                op.getMechanism() != null ? op.getMechanism().name() : null,
                op.getResult() != null ? op.getResult().name() : null,
                op.getErrorCode(),
                op.getErrorMessage(),
                op.getDurationMs()
        );
    }

    /**
     * Convert SyncBatch entity to BatchResponse DTO.
     */
    private BatchResponse toBatchResponse(SyncBatch batch) {
        return new BatchResponse(
                batch.getId(),
                batch.getCorrelationId(),
                batch.getStartedAt(),
                batch.getFinishedAt(),
                batch.getSource(),
                batch.getItemsTotal(),
                batch.getItemsSuccess(),
                batch.getItemsError()
        );
    }

    /**
     * Error response DTO.
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
