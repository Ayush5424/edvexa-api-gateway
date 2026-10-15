package com.edvexa.gateway.registry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

@Component
public class UrlValidatorUtil {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final String CLOUD_METADATA_IP = "169.254.169.254";

    private final boolean allowLoopback;

    public UrlValidatorUtil(@Value("${gateway.ssrf.allow-loopback:true}") boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Destination URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid destination URL syntax: " + rawUrl);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported scheme '" + scheme + "'. Only HTTP and HTTPS are permitted");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Destination URL must contain a valid host");
        }

        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) {
            throw new IllegalArgumentException("Destination URL contains an invalid port: " + uri.getPort());
        }

        // Prevent SSRF targeting cloud metadata service (AWS/GCP/Azure)
        if (CLOUD_METADATA_IP.equals(host) || host.contains("169.254.")) {
            throw new IllegalArgumentException("Destination URL targets a forbidden cloud metadata address");
        }

        // Prevent SSRF to loopback if loopback is explicitly disallowed
        if (!allowLoopback) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("Destination URL targets a forbidden local loopback address");
                }
            } catch (Exception ignored) {
                // Host resolution failure will be caught during actual network call
            }
        }
    }
}
