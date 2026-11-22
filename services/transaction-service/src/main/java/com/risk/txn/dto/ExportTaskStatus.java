package com.risk.txn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Status DTO for export task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTaskStatus {

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("status")
    private String status; // PROCESSING, COMPLETED, FAILED

    @JsonProperty("progress")
    private int progress; // 0-100

    @JsonProperty("totalRecords")
    private Long totalRecords;

    @JsonProperty("processedRecords")
    private Long processedRecords;

    @JsonProperty("fileSize")
    private Long fileSize;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("downloadUrl")
    private String downloadUrl;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("completedAt")
    private Instant completedAt;

    @JsonProperty("error")
    private String error;

    @JsonProperty("errorMessage")
    private String errorMessage;
}
