package com.proaula.aula.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ModelosApiConfiguration {
    @Bean
    public RestClient modelosRestClient(
            RestClient.Builder builder,
            @Value("${app.models.base-url:http://localhost:8000}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        return builder.baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }
}
