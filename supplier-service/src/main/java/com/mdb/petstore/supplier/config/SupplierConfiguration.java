package com.mdb.petstore.supplier.config;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class SupplierConfiguration {
    @Bean
    TransactionTemplate supplierTransactions(MongoDatabaseFactory factory) {
        return new TransactionTemplate(new MongoTransactionManager(factory));
    }

    @Bean
    DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            @Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory,
            @Value("${spring.jms.listener.auto-startup:true}") boolean autoStartup) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
