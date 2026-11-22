package com.risk.gateway.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.risk.gateway.model.TransactionRequest;
import com.risk.gateway.model.TransactionResponse;
import com.risk.gateway.publisher.TransactionEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor for collecting transaction request and response data.
 * Captures data before and after request processing for async persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectionInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final TransactionEventPublisher eventPublisher;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        // Only collect data for transaction endpoints
        if (!path.contains("/transactions")) {
            return true;
        }

        try {
            // Read request body
            String requestBody = readRequestBody(request);

            // Parse TransactionRequest
            TransactionRequest transactionRequest = objectMapper.readValue(requestBody, TransactionRequest.class);

            // Create snapshot
            RequestSnapshot snapshot = RequestSnapshot.builder()
                .transactionId(transactionRequest.getTransactionId())
                .userId(transactionRequest.getUserId())
                .amount(transactionRequest.getTransactionAmt())
                .productCd(transactionRequest.getProductCd())
                .requestTimestamp(System.currentTimeMillis())
                .sourceIp(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .transactionRequest(transactionRequest)
                .attributes(extractAttributes(transactionRequest))
                .identity(extractIdentity(transactionRequest))
                .build();

            // Store in ThreadLocal
            RequestContext.setSnapshot(snapshot);

            log.debug("Request snapshot created: transactionId={}", snapshot.getTransactionId());

        } catch (Exception e) {
            log.error("Error creating request snapshot: {}", e.getMessage());
            // Don't block the request if snapshot fails
        }

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        String path = request.getRequestURI();

        // Only process transaction endpoints
        if (!path.contains("/transactions")) {
            return;
        }

        try {
            // Get snapshot from ThreadLocal
            RequestSnapshot snapshot = RequestContext.getSnapshot();

            if (snapshot == null) {
                log.debug("No snapshot found for request");
                return;
            }

            // Calculate response time
            long responseTime = System.currentTimeMillis() - snapshot.getRequestTimestamp();

            // Publish to Kafka (async, fire-and-forget)
            eventPublisher.publishTransactionFromRequestResponse(
                snapshot.getTransactionId(),
                snapshot,
                null, // Response object will be extracted from response body if needed
                responseTime
            );

            log.debug("Transaction event published: transactionId={}, responseTime={}ms",
                snapshot.getTransactionId(), responseTime);

        } catch (Exception e) {
            log.error("Error in afterCompletion: {}", e.getMessage());
        } finally {
            // Always clear ThreadLocal to prevent memory leaks
            RequestContext.clear();
        }
    }

    private String readRequestBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Error reading request body: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> extractAttributes(TransactionRequest request) {
        Map<String, Object> attributes = new HashMap<>();

        // Extract card attributes
        if (request.getCard1() != null) attributes.put("card1", request.getCard1());
        if (request.getCard2() != null) attributes.put("card2", request.getCard2());
        if (request.getCard3() != null) attributes.put("card3", request.getCard3());
        if (request.getCard4() != null) attributes.put("card4", request.getCard4());
        if (request.getCard5() != null) attributes.put("card5", request.getCard5());
        if (request.getCard6() != null) attributes.put("card6", request.getCard6());

        // Extract address attributes
        if (request.getAddr1() != null) attributes.put("addr1", request.getAddr1());
        if (request.getAddr2() != null) attributes.put("addr2", request.getAddr2());

        // Extract distance attributes
        if (request.getDist1() != null) attributes.put("dist1", request.getDist1());
        if (request.getDist2() != null) attributes.put("dist2", request.getDist2());

        return attributes;
    }

    private Map<String, Object> extractIdentity(TransactionRequest request) {
        Map<String, Object> identity = new HashMap<>();

        identity.put("deviceType", request.getDeviceType());
        identity.put("deviceInfo", request.getDeviceInfo());

        // Add more identity fields as needed

        return identity;
    }
}
