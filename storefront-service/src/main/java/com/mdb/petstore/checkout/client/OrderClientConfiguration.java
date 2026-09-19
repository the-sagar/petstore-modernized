package com.mdb.petstore.checkout.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OrderClientConfiguration {

    @Bean
    public RestClient orderProcessingRestClient(
            @Value("${petstore.order-processing.base-url}") String baseUrl,
            @Value("${petstore.order-processing.connect-timeout:3s}") Duration connectTimeout,
            @Value("${petstore.order-processing.read-timeout:5s}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
