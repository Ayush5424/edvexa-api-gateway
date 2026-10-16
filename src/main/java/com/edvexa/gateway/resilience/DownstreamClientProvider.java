package com.edvexa.gateway.resilience;

import com.edvexa.gateway.registry.ServiceEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DownstreamClientProvider {

    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public RestClient getRestClientForService(ServiceEntity service) {
        int connectTimeout = service.getConnectionTimeoutMs() > 0 ? service.getConnectionTimeoutMs() : 5000;
        int responseTimeout = service.getResponseTimeoutMs() > 0 ? service.getResponseTimeoutMs() : 10000;

        String cacheKey = connectTimeout + ":" + responseTimeout;
        return clientCache.computeIfAbsent(cacheKey, k -> createRestClient(connectTimeout, responseTimeout));
    }

    private RestClient createRestClient(int connectTimeoutMs, int responseTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_2)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(responseTimeoutMs));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
