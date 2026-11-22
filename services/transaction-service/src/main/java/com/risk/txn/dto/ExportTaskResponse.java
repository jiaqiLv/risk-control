package com.risk.txn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for export task creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTaskResponse {

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("status")
    private String status; // PROCESSING, COMPLETED, FAILED
}
