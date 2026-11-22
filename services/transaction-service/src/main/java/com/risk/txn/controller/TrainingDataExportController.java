package com.risk.txn.controller;

import com.risk.txn.dto.ApiResponse;
import com.risk.txn.dto.ExportRequest;
import com.risk.txn.dto.ExportTaskResponse;
import com.risk.txn.dto.ExportTaskStatus;
import com.risk.txn.exporter.ExportTask;
import com.risk.txn.exporter.TrainingDataExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for training data export.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/training-data")
@RequiredArgsConstructor
public class TrainingDataExportController {

    private final TrainingDataExporter exporter;

    /**
     * Create a new export task.
     *
     * @param request Export request
     * @return Export task response with task ID
     */
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<ExportTaskResponse>> exportTrainingData(
            @Valid @RequestBody ExportRequest request) {

        log.info("Received export request: format={}, startTime={}, endTime={}",
            request.getFormat(), request.getStartTime(), request.getEndTime());

        // 1. Create export task
        String taskId = UUID.randomUUID().toString();
        ExportTask task = exporter.createExportTask(taskId, request);

        // 2. Execute export asynchronously
        CompletableFuture.runAsync(() -> exporter.executeExport(task));

        // 3. Return task ID
        ExportTaskResponse response = ExportTaskResponse.builder()
            .taskId(taskId)
            .status(task.getStatus().name())
            .build();

        return ResponseEntity.ok(ApiResponse.<ExportTaskResponse>builder()
            .success(true)
            .data(response)
            .build());
    }

    /**
     * Get export task status.
     *
     * @param taskId Task ID
     * @return Export task status
     */
    @GetMapping("/export/{taskId}")
    public ResponseEntity<ApiResponse<ExportTaskStatus>> getExportStatus(
            @PathVariable String taskId) {

        log.debug("Querying export task status: taskId={}", taskId);

        ExportTaskStatus status = exporter.getTaskStatus(taskId);

        if (status == null) {
            return ResponseEntity.ok(ApiResponse.<ExportTaskStatus>builder()
                .success(false)
                .error("Task not found: " + taskId)
                .build());
        }

        return ResponseEntity.ok(ApiResponse.<ExportTaskStatus>builder()
            .success(true)
            .data(status)
            .build());
    }

    /**
     * Download exported file.
     *
     * @param taskId Task ID
     * @return Exported file
     */
    @GetMapping("/download/{taskId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String taskId) {
        log.info("Downloading export file: taskId={}", taskId);

        File file = exporter.getExportFile(taskId);

        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getName() + "\"")
            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .body(resource);
    }
}
