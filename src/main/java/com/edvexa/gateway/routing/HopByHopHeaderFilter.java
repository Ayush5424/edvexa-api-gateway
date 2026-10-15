package com.edvexa.gateway.routing;

import com.edvexa.gateway.observability.CorrelationIdContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

@Component
public class HopByHopHeaderFilter {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    public HttpHeaders filterHeaders(Enumeration<String> headerNames, java.util.function.Function<String, Enumeration<String>> headerValuesGetter) {
        HttpHeaders cleanHeaders = new HttpHeaders();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!HOP_BY_HOP_HEADERS.contains(lower)) {
                    Enumeration<String> values = headerValuesGetter.apply(name);
                    if (values != null) {
                        while (values.hasMoreElements()) {
                            cleanHeaders.add(name, values.nextElement());
                        }
                    }
                }
            }
        }

        // Ensure Correlation ID is always propagated downstream
        String correlationId = CorrelationIdContext.get();
        if (correlationId != null && !correlationId.isBlank()) {
            cleanHeaders.set(CorrelationIdContext.CORRELATION_ID_HEADER, correlationId);
        }

        return cleanHeaders;
    }

    public HttpHeaders filterDownstreamResponseHeaders(HttpHeaders downstreamHeaders) {
        HttpHeaders clean = new HttpHeaders();
        downstreamHeaders.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!HOP_BY_HOP_HEADERS.contains(lower)) {
                clean.put(name, values);
            }
        });

        // Ensure correlation ID is present in response headers
        String correlationId = CorrelationIdContext.get();
        if (correlationId != null && !correlationId.isBlank()) {
            clean.set(CorrelationIdContext.CORRELATION_ID_HEADER, correlationId);
        }

        return clean;
    }
}
