package com.fertiplus.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class OutboxClaimRepository {
    private static final String CLAIM_SQL = """
            WITH picked AS (
                SELECT id
                  FROM outbox_events
                 WHERE published_at IS NULL
                   AND (claimed_at IS NULL OR claimed_at < CURRENT_TIMESTAMP - INTERVAL '60 seconds')
                 ORDER BY created_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events AS event
               SET claimed_at = CURRENT_TIMESTAMP
              FROM picked
             WHERE event.id = picked.id
            RETURNING event.id, event.topic, event.message_key, event.payload,
                      event.published_at, event.created_at, event.claimed_at
            """;

    private final JdbcTemplate jdbc;

    public OutboxClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OutboxEvent> claimPending(int batchSize) {
        return jdbc.query(CLAIM_SQL, (rs, rowNum) -> {
            OutboxEvent event = new OutboxEvent(
                    rs.getString("topic"),
                    rs.getString("message_key"),
                    rs.getString("payload")
            );
            event.id = rs.getString("id");
            Timestamp published = rs.getTimestamp("published_at");
            Timestamp created = rs.getTimestamp("created_at");
            Timestamp claimed = rs.getTimestamp("claimed_at");
            event.publishedAt = published == null ? null : published.toInstant();
            event.createdAt = created == null ? null : created.toInstant();
            event.claimedAt = claimed == null ? null : claimed.toInstant();
            return event;
        }, batchSize);
    }

    public void markPublished(String id) {
        jdbc.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }
}
