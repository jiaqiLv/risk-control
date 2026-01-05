package com.riskcontrol.common.util;

import com.fasterxml.uuid.Generators;
import org.slf4j.MDC;

import java.util.UUID;

public class IdGenerator {

    private static final com.fasterxml.uuid.impl.TimeBasedGenerator timeBasedGenerator =
            Generators.timeBasedGenerator();

    public static String generateRequestId() {
        return timeBasedGenerator.generate().toString();
    }

    public static String generateEventId() {
        return "evt_" + timeBasedGenerator.generate().toString();
    }

    public static String getTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateRequestId();
            MDC.put("traceId", traceId);
        }
        return traceId;
    }
}
