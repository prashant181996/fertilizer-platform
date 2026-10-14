package com.fertiplus.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxPublisher {
    private final OutboxClaimRepository claims;
    private final KafkaTemplate<String, String> kafka;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    @Value("${outbox.batch-size:100}")
    private int batchSize;

    public OutboxPublisher(OutboxClaimRepository claims, KafkaTemplate<String, String> kafka) {
        this.claims = claims;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:10}")
    public void publish() {
        for (OutboxEvent event : claims.claimPending(batchSize)) {
            if (!inFlight.add(event.id)) {
                continue;
            }
            try {
                kafka.send(event.topic, event.messageKey, event.payload).whenComplete((result, error) -> {
                    try {
                        if (error == null) {
                            claims.markPublished(event.id);
                        }
                        // On failure, the claim lease expires after 60 seconds and another poll can retry.
                    } finally {
                        inFlight.remove(event.id);
                    }
                });
            } catch (Exception ex) {
                inFlight.remove(event.id);
                // Keep claimed_at; another instance retries after the lease expires.
            }
        }
    }
}
