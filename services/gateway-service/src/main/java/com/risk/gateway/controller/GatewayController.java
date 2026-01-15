package com.risk.gateway.controller;

import com.risk.gateway.model.TransactionRequest;
import com.risk.gateway.model.TransactionResponse;
import com.risk.gateway.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for gateway endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayService gatewayService;

    /**
     * Process a single transaction.
     *
     * @param request Transaction request
     * @return Transaction response
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> processTransaction(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received transaction request: transactionId={}, userId={}, amount={}",
                request.getTransactionId(),
                request.getUserId(),
                request.getTransactionAmt());
        TransactionResponse response = gatewayService.processTransaction(request);
        return ResponseEntity.ok(response);
    }
}
