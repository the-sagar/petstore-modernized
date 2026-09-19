package com.mdb.petstore.orderprocessing.order.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@Configuration(proxyBeanMethods = false)
public class OrderMessagingConfiguration {
    @Bean
    DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            @Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory,
            @Value("${spring.jms.listener.auto-startup:true}") boolean autoStartup) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // Commit message consumption only after successful Mongo handling; not an XA transaction.
        factory.setSessionTransacted(true);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
