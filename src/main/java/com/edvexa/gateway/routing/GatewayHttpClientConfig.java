package com.edvexa.gateway.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class GatewayHttpClientConfig {

    @Bean
    public HttpClient gatewayHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER) // Prevent SSRF via downstream open redirects
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Bean
    public JdkClientHttpRequestFactory gatewayClientHttpRequestFactory(HttpClient gatewayHttpClient) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(gatewayHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Bean
    public RestClient.Builder gatewayRestClientBuilder(JdkClientHttpRequestFactory factory) {
        return RestClient.builder()
                .requestFactory(factory);
    }
}
