package com.miimetiq.keycloak.sync.dashboard;

import java.util.List;

/**
 * Response DTO for paginated operations list.
 */
public class OperationsPageResponse {
    /**
     * List of operations for the current page
     */
    public List<OperationResponse> operations;

    /**
     * Current page number (0-indexed)
     */
    public int page;

    /**
     * Page size
     */
    public int pageSize;

    /**
     * Total number of operations matching the filters
     */
    public long total;

    /**
     * Total number of pages
     */
    public int totalPages;

    public OperationsPageResponse() {
    }

    public OperationsPageResponse(List<OperationResponse> operations, int page,
                                 int pageSize, long total) {
        this.operations = operations;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
}
