package com.fertiplus.order;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class OrderEventConfig {
    @Bean
    NewTopic ordersCreatedTopic() {
        return TopicBuilder.name("orders.created").partitions(12).replicas(1).build();
    }
}
