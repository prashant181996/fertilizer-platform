package com.fertiplus.order;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class OrderEventConfig {
    @Value("${app.kafka.topic-partitions:48}")
    private int partitions;

    @Value("${app.kafka.topic-replication-factor:1}")
    private int replicationFactor;

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    NewTopic ordersCreatedTopic() {
        return topic("orders.created");
    }

    @Bean
    NewTopic inventoryReservedTopic() {
        return topic("inventory.reserved");
    }

    @Bean
    NewTopic inventoryRejectedTopic() {
        return topic("inventory.rejected");
    }

    @Bean
    NewTopic paymentRequestedTopic() {
        return topic("payment.requested");
    }

    @Bean
    NewTopic paymentAuthorizedTopic() {
        return topic("payment.authorized");
    }

    @Bean
    NewTopic paymentFailedTopic() {
        return topic("payment.failed");
    }

    @Bean
    NewTopic inventoryReleaseTopic() {
        return topic("inventory.release");
    }

    @Bean
    NewTopic ordersConfirmedTopic() {
        return topic("orders.confirmed");
    }

    @Bean
    NewTopic ordersCreatedDlt() {
        return topic("orders.created.DLT");
    }

    @Bean
    NewTopic inventoryReservedDlt() {
        return topic("inventory.reserved.DLT");
    }

    @Bean
    NewTopic inventoryRejectedDlt() {
        return topic("inventory.rejected.DLT");
    }

    @Bean
    NewTopic paymentRequestedDlt() {
        return topic("payment.requested.DLT");
    }

    @Bean
    NewTopic paymentAuthorizedDlt() {
        return topic("payment.authorized.DLT");
    }

    @Bean
    NewTopic paymentFailedDlt() {
        return topic("payment.failed.DLT");
    }

    @Bean
    NewTopic inventoryReleaseDlt() {
        return topic("inventory.release.DLT");
    }

    @Bean
    NewTopic ordersConfirmedDlt() {
        return topic("orders.confirmed.DLT");
    }
}
