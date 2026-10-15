package com.edvexa.gateway.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIdContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private CorrelationIdContext() {}

    public static String getOrGenerate(String incomingId) {
        if (incomingId != null && VALID_CORRELATION_ID.matcher(incomingId.trim()).matches()) {
            return incomingId.trim();
        }
        return UUID.randomUUID().toString();
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static String get() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : "";
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
