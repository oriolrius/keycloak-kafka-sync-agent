package com.miimetiq.keycloak.sync.dashboard;

import java.util.List;

/**
 * Response DTO for paginated batches list.
 */
public class BatchesPageResponse {
    /**
     * List of batches for the current page
     */
    public List<BatchResponse> batches;

    /**
     * Current page number (0-indexed)
     */
    public int page;

    /**
     * Page size
     */
    public int pageSize;

    /**
     * Total number of batches
     */
    public long total;

    /**
     * Total number of pages
     */
    public int totalPages;

    public BatchesPageResponse() {
    }

    public BatchesPageResponse(List<BatchResponse> batches, int page,
                              int pageSize, long total) {
        this.batches = batches;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
}
