package com.risk.gateway.config;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Custom Sentinel block exception handler.
 * Returns HTTP 429 (Too Many Requests) for rate limiting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelExceptionHandler implements BlockRequestHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof BlockException) {
            BlockException blockException = (BlockException) ex;

            // Log the blocked request
            log.warn("Request blocked by Sentinel: resource={}, rule={}, type={}",
                    blockException.getRule() != null ? blockException.getRule().getResource() : "unknown",
                    blockException.getRule() != null ? blockException.getRule().getClass().getSimpleName() : "unknown",
                    blockException.getClass().getSimpleName());

            // Determine status code and message
            HttpStatus status;
            String errorCode;
            String message;

            if (blockException instanceof FlowException) {
                status = HttpStatus.TOO_MANY_REQUESTS;
                errorCode = "RATE_LIMIT_EXCEEDED";
                message = "Too many requests. Please try again later.";

            } else if (blockException instanceof DegradeException) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                errorCode = "SERVICE_DEGRADED";
                message = "Service is temporarily degraded. Please try again later.";

            } else if (blockException instanceof AuthorityException) {
                status = HttpStatus.FORBIDDEN;
                errorCode = "ACCESS_DENIED";
                message = "Access denied by authorization rules.";

            } else {
                status = HttpStatus.TOO_MANY_REQUESTS;
                errorCode = "REQUEST_BLOCKED";
                message = "Request blocked by Sentinel: " + blockException.getClass().getSimpleName();
            }

            // Build error response
            String jsonResponse;
            try {
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"success\":false,");
                json.append("\"errorCode\":\"").append(errorCode).append("\",");
                json.append("\"message\":\"").append(message).append("\",");
                json.append("\"httpStatus\":").append(status.value()).append(",");
                json.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
                json.append("\"resource\":\"").append(
                    blockException.getRule() != null ? blockException.getRule().getResource() : "unknown"
                ).append("\"");
                json.append("}");
                jsonResponse = json.toString();
            } catch (Exception e) {
                log.error("Failed to build error response", e);
                jsonResponse = "{\"success\":false,\"message\":\"Internal error\"}";
            }

            // Return ServerResponse
            return ServerResponse
                    .status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonResponse);
        }

        // If not a BlockException, propagate error
        return Mono.error(ex);
    }
}
