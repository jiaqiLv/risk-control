package com.risk.txn.exporter;

import com.risk.txn.dto.ExportRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Export task model for tracking export job status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTask {

    private String taskId;
    private ExportRequest request;
    private ExportStatus status;
    private int progress;
    private Long totalRecords;
    private Long processedRecords;
    private String filePath;
    private Long fileSize;
    private String error;
    private Instant createdAt;
    private Instant completedAt;

    public enum ExportStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
