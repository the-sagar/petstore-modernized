package com.mdb.petstore.supplier.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SupplierClientConfiguration {
    // Keep the dedicated client inside SupplierClient so existing RestClient injection is unchanged.
    @Bean
    public SupplierClient supplierClient(@Value("${petstore.supplier.base-url}") String baseUrl,
            @Value("${petstore.supplier.connect-timeout:3s}") Duration connectTimeout,
            @Value("${petstore.supplier.read-timeout:5s}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new SupplierClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build());
    }
}
