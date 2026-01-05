package com.riskcontrol.gateway.controller;

import com.riskcontrol.common.error.ErrorCode;
import com.riskcontrol.common.response.Response;
import com.riskcontrol.gateway.model.RiskScoreRequest;
import com.riskcontrol.gateway.model.RiskScoreResponse;
import com.riskcontrol.gateway.service.RiskScoringService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskGatewayController {

    private final RiskScoringService riskScoringService;

    public RiskGatewayController(RiskScoringService riskScoringService) {
        this.riskScoringService = riskScoringService;
    }

    @PostMapping("/score")
    public Mono<Response<RiskScoreResponse>> getRiskScore(@Valid @RequestBody RiskScoreRequest request) {
        String traceId = MDC.get("traceId");

        return riskScoringService.calculateRiskScore(request)
                .map(response -> Response.success(response, traceId))
                .onErrorResume(error -> {
                    return Mono.just(Response.error(
                            ErrorCode.INTERNAL_ERROR,
                            error.getMessage(),
                            traceId
                    ));
                });
    }
}
