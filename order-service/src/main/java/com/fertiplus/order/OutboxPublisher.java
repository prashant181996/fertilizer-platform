package com.fertiplus.order;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxPublisher {
    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public OutboxPublisher(OutboxRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:10}")
    public void publish() {
        for (OutboxEvent e : repo.findTop500ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            if (!inFlight.add(e.id)) continue;
            try {
                kafka.send(e.topic, e.messageKey, e.payload).whenComplete((result, error) -> {
                    try {
                        if (error == null) markPublished(e.id);
                    } finally {
                        inFlight.remove(e.id);
                    }
                });
            } catch (Exception ex) {
                inFlight.remove(e.id);
            }
        }
    }

    public void markPublished(String id) {
        repo.findById(id).ifPresent(e -> {
            e.publishedAt = Instant.now();
            repo.save(e);
        });
    }
}
