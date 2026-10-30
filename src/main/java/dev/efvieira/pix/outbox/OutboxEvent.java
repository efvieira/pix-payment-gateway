package dev.efvieira.pix.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload, Instant now) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    /** Backoff exponencial: base * 2^(tentativas-1). Depois do limite, o evento vai para FAILED. */
    void markFailure(String error, Instant now, int maxAttempts, Duration baseBackoff) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
        } else {
            nextAttemptAt = now.plus(baseBackoff.multipliedBy(1L << (attempts - 1)));
        }
    }

    void retry(Instant now) {
        status = OutboxStatus.PENDING;
        attempts = 0;
        nextAttemptAt = now;
    }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
