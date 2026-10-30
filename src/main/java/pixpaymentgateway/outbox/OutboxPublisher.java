package pixpaymentgateway.outbox;

import pixpaymentgateway.shared.PixProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Lê os eventos pendentes e publica no Kafka usando o txid como chave, o que preserva a
 * ordem por cobrança. A entrega é at-least-once, então os consumidores devem ser idempotentes.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final PixProperties.Outbox config;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                           PixProperties props, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.config = props.outbox();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${pix.outbox.poll-interval:PT1S}")
    @Transactional
    public int publishPending() {
        var batch = repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                OutboxStatus.PENDING, clock.instant(), Limit.of(config.batchSize()));
        int published = 0;
        for (var event : batch) {
            try {
                kafka.send(config.topic(), event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished(clock.instant());
                published++;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.markFailure(e.getMessage(), clock.instant(), config.maxAttempts(), config.baseBackoff());
                log.warn("Falha ao publicar evento {} (tentativa {}): {}", event.getId(), event.getAttempts(), e.getMessage());
            }
        }
        return published;
    }
}
