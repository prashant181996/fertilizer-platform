package com.fertiplus.order;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface OutboxRepository extends JpaRepository<OutboxEvent,String> { List<OutboxEvent> findTop500ByPublishedAtIsNullOrderByCreatedAtAsc(); }
