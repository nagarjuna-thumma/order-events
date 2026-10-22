package dev.nagarjuna.orders.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // SKIP LOCKED lets several instances of the service run the relay without sending the same row twice.
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    @Query("SELECT min(e.createdAt) FROM OutboxEvent e WHERE e.publishedAt IS NULL")
    Optional<Instant> findOldestUnpublishedCreatedAt();
}
