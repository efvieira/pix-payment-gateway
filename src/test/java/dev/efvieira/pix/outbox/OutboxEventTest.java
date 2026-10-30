package dev.efvieira.pix.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private final Instant now = Instant.parse("2026-01-10T12:00:00Z");

    @Test
    void appliesExponentialBackoffAndFailsAfterMaxAttempts() {
        var event = new OutboxEvent("charge", "tx", "pix.charge.paid", "{}", now);

        event.markFailure("broker down", now, 3, Duration.ofSeconds(2));
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        event.markFailure("broker down", now, 3, Duration.ofSeconds(2));
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(4));

        event.markFailure("broker down", now, 3, Duration.ofSeconds(2));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);

        event.retry(now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
    }
}
